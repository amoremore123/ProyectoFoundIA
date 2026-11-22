"""H72: Django revoca las sesiones de Firebase Auth al suspender o cerrar en todos los dispositivos."""
import json
from unittest import mock

import bcrypt
import jwt
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from django.test import TestCase, override_settings
from rest_framework.test import APITestCase

from . import firebase
from .models import MensajeChat, ReporteMensaje, Usuario
from .tests import PASSWORD, token_de

CLAVE = rsa.generate_private_key(public_exponent=65537, key_size=2048)
PEM = CLAVE.private_bytes(serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
                          serialization.NoEncryption()).decode()
CREDENCIALES = json.dumps({"type": "service_account", "project_id": "foundia-prueba", "private_key": PEM,
                           "client_email": "firebase-adminsdk@foundia-prueba.iam.gserviceaccount.com",
                           "token_uri": "https://oauth2.prueba/token"})


class Respuesta:
    def __init__(self, cuerpo):
        self.cuerpo = cuerpo.encode()

    def read(self):
        return self.cuerpo

    def __enter__(self):
        return self

    def __exit__(self, *a):
        return False


@override_settings(FIREBASE_CREDENCIALES_JSON=CREDENCIALES, FIREBASE_PROYECTO_ID="",
                   FIREBASE_IDENTITYTOOLKIT_URL="https://identitytoolkit.prueba")
class RevocarFirebaseTests(TestCase):
    def test_revoca_con_el_uid_estable_y_el_token_de_google(self):
        pedidos = []

        def urlopen(pedido, timeout=None):
            pedidos.append(pedido)
            if pedido.full_url.endswith("/token"):
                return Respuesta('{"access_token": "acceso-prueba", "expires_in": 3600}')
            return Respuesta("{}")

        with mock.patch("nucleo.firebase.urllib.request.urlopen", side_effect=urlopen):
            self.assertTrue(firebase.revocar(42))
        token, update = pedidos
        asercion = dict(p.split("=", 1) for p in token.data.decode().split("&"))["assertion"]
        datos = jwt.decode(asercion, CLAVE.public_key(), algorithms=["RS256"], audience="https://oauth2.prueba/token")
        self.assertEqual(datos["iss"], "firebase-adminsdk@foundia-prueba.iam.gserviceaccount.com")
        self.assertEqual(update.full_url, "https://identitytoolkit.prueba/v1/projects/foundia-prueba/accounts:update")
        self.assertEqual(update.headers["Authorization"], "Bearer acceso-prueba")
        cuerpo = json.loads(update.data)
        self.assertEqual(cuerpo["localId"], "foundia-42")
        self.assertIn("validSince", cuerpo)

    def test_si_google_falla_no_rompe(self):
        with mock.patch("nucleo.firebase.urllib.request.urlopen", side_effect=OSError("sin red")):
            self.assertFalse(firebase.revocar(42))

    def test_se_programa_despues_de_confirmar(self):
        with mock.patch("nucleo.firebase.threading.Thread") as hilo, self.captureOnCommitCallbacks(execute=True):
            firebase.revocar_al_confirmar(7)
        hilo.assert_called_once()
        self.assertEqual(hilo.call_args.kwargs["args"], (7,))


@override_settings(FIREBASE_CREDENCIALES_JSON="")
class SinFirebaseTests(TestCase):
    def test_sin_credenciales_no_hace_nada(self):
        with mock.patch("nucleo.firebase.urllib.request.urlopen") as urlopen:
            self.assertFalse(firebase.revocar(42))
            firebase.revocar_al_confirmar(42)
        urlopen.assert_not_called()


class RevocadoDesdeElPanelTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        crear = lambda nombre, rol, correo: Usuario.objects.create(
            nombre=nombre, apellido="QA", correo=correo, password=hash_, rol=rol, estado="ACTIVO")
        self.admin = crear("Admin", "ADMIN", "admin-h72@prueba.invalid")
        self.luis = crear("Luis", "USUARIO", "luis-h72@prueba.invalid")
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")

    def test_suspender_revoca_y_reactivar_no(self):
        with mock.patch("nucleo.views.revocar_firebase") as revocar:
            self.client.patch(f"/api/admin/usuarios/{self.luis.id}/", {"estado": "SUSPENDIDO"}, format="json")
            revocar.assert_called_once_with(self.luis.id)
            self.client.patch(f"/api/admin/usuarios/{self.luis.id}/", {"estado": "ACTIVO"}, format="json")
            revocar.assert_called_once()

    def test_cerrar_en_todos_revoca(self):
        with mock.patch("nucleo.views.revocar_firebase") as revocar:
            self.assertEqual(self.client.post("/api/admin/auth/logout-all/").status_code, 200)
        revocar.assert_called_once_with(self.admin.id)

    def test_suspender_por_reporte_del_chat_revoca(self):
        mensaje = MensajeChat.objects.create(conversacion_id=1, autor=self.luis, texto="spam")
        reporte = ReporteMensaje.objects.create(mensaje=mensaje, usuario=self.admin, motivo="spam")
        with mock.patch("nucleo.reportes_mensaje.revocar_firebase") as revocar:
            r = self.client.post(f"/api/admin/reportes-mensajes/{reporte.id}/resolver/",
                                 {"accion": "SUSPENDER", "resolucion": "Envío masivo"}, format="json")
        self.assertEqual(r.status_code, 200)
        revocar.assert_called_once_with(self.luis.id)
