"""H35: los dos backends comparten la invalidación por recuperación de contraseña."""
from datetime import timedelta
import hashlib
import hmac
from types import SimpleNamespace
from unittest.mock import patch

import bcrypt
import jwt
from django.utils import timezone
from rest_framework.test import APITestCase

from .authentication import get_jwt_secret, sello_credencial
from .models import Usuario

SIN_ARGUMENTO = object()


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

    def sello(self):
        datos = b"foundia-session-v1\0" + self.admin.correo.encode("utf-8") + b"\0" + self.admin.password.encode("utf-8")
        return hmac.new(get_jwt_secret().encode("utf-8"), datos, hashlib.sha256).hexdigest()

    def token_con_sello(self, sello=SIN_ARGUMENTO):
        return jwt.encode({"sub": self.admin.correo, "iat": self.ahora,
                           "exp": self.ahora + timedelta(hours=1), "cred": self.sello() if sello is SIN_ARGUMENTO else sello},
                          get_jwt_secret(), "HS256")

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
        self.assertEqual(payload["cred"], self.sello())
        self.assertEqual(payload["sub"], self.admin.correo)
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + respuesta.data["token"])
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 200)

    def test_cuentas_sin_cambio_conservan_compatibilidad_con_sus_tokens(self):
        self.admin.password_actualizado_en = None
        self.admin.save(update_fields=["password_actualizado_en"])
        self.assertEqual(self.consultar().status_code, 200)

    def test_legado_del_mismo_segundo_tambien_se_rechaza_tras_el_cambio(self):
        limite = int(self.admin.password_actualizado_en.timestamp())
        self.assertEqual(self.consultar(limite).status_code, 401)

    def test_token_por_correo_de_spring_es_compatible(self):
        token = self.token_con_sello()
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + token)
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 200)

    def test_rol_de_la_base_prevalece_sobre_el_claim(self):
        self.admin.rol = "USUARIO"
        self.admin.save(update_fields=["rol"])
        token = jwt.encode({"sub": self.admin.correo, "rol": "ADMIN", "iat": self.ahora,
                            "exp": self.ahora + timedelta(hours=1), "cred": self.sello()}, get_jwt_secret(), "HS256")
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + token)
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 403)

    def test_sello_anterior_se_rechaza_aunque_el_cambio_sea_en_el_mismo_segundo(self):
        anterior = self.token_con_sello()
        self.admin.password = bcrypt.hashpw(b"Nueva456!", bcrypt.gensalt(rounds=4)).decode()
        self.admin.password_actualizado_en = self.ahora.replace(microsecond=0)
        self.admin.save(update_fields=["password", "password_actualizado_en"])
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + anterior)
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 401)

    def test_nuevo_login_no_exige_esperar_otro_segundo(self):
        self.admin.password_actualizado_en = self.ahora.replace(microsecond=0)
        self.admin.save(update_fields=["password_actualizado_en"])
        respuesta = self.client.post("/api/admin/auth/login/", {"correo": self.admin.correo, "password": "Actual123!"}, format="json")
        self.assertEqual(respuesta.status_code, 200)
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + respuesta.data["token"])
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 200)

    def test_sello_invalido_no_vuelve_al_modo_legado(self):
        self.admin.password_actualizado_en = None
        self.admin.save(update_fields=["password_actualizado_en"])
        for sello in (None, "", "x" * 64, "a" * 64, True, [], {}):
            with self.subTest(sello=sello):
                self.client.credentials(HTTP_AUTHORIZATION="Bearer " + self.token_con_sello(sello))
                self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 401)

    def test_sello_nuevo_sin_fecha_de_emision_se_rechaza(self):
        self.admin.password_actualizado_en = None
        self.admin.save(update_fields=["password_actualizado_en"])
        token = jwt.encode({"sub": self.admin.correo, "exp": self.ahora + timedelta(hours=1), "cred": self.sello()}, get_jwt_secret(), "HS256")
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + token)
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 401)

    def test_vector_hmac_coincide_con_spring(self):
        with patch.dict("os.environ", {"JWT_SECRET": "secreto-de-pruebas-con-al-menos-32-caracteres!!"}):
            usuario = SimpleNamespace(correo="ana@foundia.dev", password="hash")
            self.assertEqual(sello_credencial(usuario), "b5fbc51c55bd84fa2b8d75e4fad7f42da3abe1fadf0e1ca7a72b6829aa41f295")

    def test_cambio_de_hash_sin_fecha_tambien_revoca_un_token_nuevo(self):
        self.admin.password_actualizado_en = None
        self.admin.save(update_fields=["password_actualizado_en"])
        anterior = self.token_con_sello()
        self.admin.password = bcrypt.hashpw(b"Otra789!", bcrypt.gensalt(rounds=4)).decode()
        self.admin.save(update_fields=["password"])
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + anterior)
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 401)

    def test_correo_legado_solo_antes_de_recuperar(self):
        token = jwt.encode({"sub": self.admin.correo, "exp": self.ahora + timedelta(hours=1)}, get_jwt_secret(), "HS256")
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + token)
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 401)
        self.admin.password_actualizado_en = None
        self.admin.save(update_fields=["password_actualizado_en"])
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 200)

    def test_sello_valido_no_omite_la_expiracion(self):
        token = jwt.encode({"sub": self.admin.correo, "iat": self.ahora - timedelta(hours=2),
                            "exp": self.ahora - timedelta(hours=1), "cred": self.sello()}, get_jwt_secret(), "HS256")
        self.client.credentials(HTTP_AUTHORIZATION="Bearer " + token)
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 401)
