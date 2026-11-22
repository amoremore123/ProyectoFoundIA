"""H57: Django borra los dispositivos de push al cerrar sesión y al suspender una cuenta."""
from datetime import datetime, timedelta, timezone as dt_timezone

import bcrypt
import jwt
from rest_framework.test import APITestCase

from .authentication import get_jwt_secret
from .models import Dispositivo, MensajeChat, ReporteMensaje, Usuario
from .tests import PASSWORD, token_de


def token_con_jti(usuario, jti):
    exp = datetime.now(dt_timezone.utc) + timedelta(hours=1)
    return jwt.encode({"sub": str(usuario.id), "rol": usuario.rol, "exp": exp, "jti": jti, "ver": 0},
                      get_jwt_secret(), "HS256")


class DispositivosDjangoTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        crear = lambda nombre, rol, correo: Usuario.objects.create(
            nombre=nombre, apellido="QA", correo=correo, password=hash_, rol=rol, estado="ACTIVO")
        self.admin = crear("Admin", "ADMIN", "admin-h57@prueba.invalid")
        self.luis = crear("Luis", "USUARIO", "luis-h57@prueba.invalid")

    def dispositivo(self, usuario, token, jti=None):
        return Dispositivo.objects.create(usuario=usuario, token=token, plataforma="ANDROID", sesion_jti=jti)

    def test_cerrar_sesion_borra_solo_el_dispositivo_de_esa_sesion(self):
        self.dispositivo(self.admin, "tok-sesion-1", jti="a" * 32)
        self.dispositivo(self.admin, "tok-sesion-2", jti="b" * 32)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_con_jti(self.admin, 'a' * 32)}")
        self.assertEqual(self.client.post("/api/admin/auth/logout/").status_code, 200)
        self.assertEqual(list(Dispositivo.objects.values_list("token", flat=True)), ["tok-sesion-2"])

    def test_cerrar_en_todos_borra_todos_los_de_la_cuenta(self):
        self.dispositivo(self.admin, "tok-1", jti="a" * 32)
        self.dispositivo(self.admin, "tok-2")
        self.dispositivo(self.luis, "tok-luis")
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")
        self.assertEqual(self.client.post("/api/admin/auth/logout-all/").status_code, 200)
        self.assertEqual(list(Dispositivo.objects.values_list("token", flat=True)), ["tok-luis"])

    def test_suspender_desde_usuarios_borra_sus_dispositivos(self):
        self.dispositivo(self.luis, "tok-luis")
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")
        r = self.client.patch(f"/api/admin/usuarios/{self.luis.id}/", {"estado": "SUSPENDIDO"}, format="json")
        self.assertEqual(r.status_code, 200)
        self.assertFalse(Dispositivo.objects.exists())

    def test_reactivar_no_borra_nada(self):
        self.luis.estado = "SUSPENDIDO"
        self.luis.save()
        self.dispositivo(self.luis, "tok-luis")
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")
        self.client.patch(f"/api/admin/usuarios/{self.luis.id}/", {"estado": "ACTIVO"}, format="json")
        self.assertTrue(Dispositivo.objects.exists())

    def test_suspender_por_un_reporte_del_chat_borra_sus_dispositivos(self):
        self.dispositivo(self.luis, "tok-luis")
        mensaje = MensajeChat.objects.create(conversacion_id=1, autor=self.luis, texto="spam")
        reporte = ReporteMensaje.objects.create(mensaje=mensaje, usuario=self.admin, motivo="spam")
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")
        r = self.client.post(f"/api/admin/reportes-mensajes/{reporte.id}/resolver/",
                             {"accion": "SUSPENDER", "resolucion": "Envío masivo"}, format="json")
        self.assertEqual(r.status_code, 200)
        self.assertFalse(Dispositivo.objects.exists())
