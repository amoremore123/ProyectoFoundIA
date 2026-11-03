"""H35: los dos backends comparten la invalidación por recuperación de contraseña."""
from datetime import timedelta

import bcrypt
import jwt
from django.utils import timezone
from rest_framework.test import APITestCase

from .authentication import get_jwt_secret
from .models import Usuario


class SesionTrasRecuperacionTests(APITestCase):
    def setUp(self):
        self.ahora = timezone.now()
        self.admin = Usuario.objects.create(
            nombre="Admin", apellido="QA", correo="admin-h35@prueba.invalid",
            password=bcrypt.hashpw(b"Actual123!", bcrypt.gensalt(rounds=4)).decode(),
            rol="ADMIN", estado="ACTIVO",
            password_actualizado_en=self.ahora - timedelta(seconds=2),
        )

    def consultar(self, emitido=None):
        payload = {"sub": str(self.admin.id), "exp": self.ahora + timedelta(hours=1)}
        if emitido is not None:
            payload["iat"] = emitido
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + jwt.encode(payload, get_jwt_secret(), "HS256"))
        return self.client.get("/api/admin/usuarios/")

    def test_sesion_anterior_al_cambio_responde_401(self):
        self.assertEqual(self.consultar(self.ahora - timedelta(minutes=1)).status_code, 401)

    def test_token_sin_fecha_no_sirve_despues_del_cambio(self):
        self.assertEqual(self.consultar().status_code, 401)

    def test_nuevo_login_emite_fecha_y_permite_el_panel(self):
        respuesta = self.client.post("/api/admin/auth/login/", {
            "correo": self.admin.correo, "password": "Actual123!",
        }, format="json")
        self.assertEqual(respuesta.status_code, 200)
        payload = jwt.decode(respuesta.data["token"], get_jwt_secret(), algorithms=["HS256"])
        self.assertIn("iat", payload)
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + respuesta.data["token"])
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 200)

    def test_cuentas_sin_cambio_conservan_compatibilidad_con_sus_tokens(self):
        self.admin.password_actualizado_en = None
        self.admin.save(update_fields=["password_actualizado_en"])
        self.assertEqual(self.consultar().status_code, 200)

    def test_precision_del_limite_coincide_con_spring(self):
        limite = int(self.admin.password_actualizado_en.timestamp())
        self.assertEqual(self.consultar(limite).status_code, 200)
