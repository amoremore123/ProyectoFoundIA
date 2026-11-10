"""H37: cierre de sesión individual y en todos los dispositivos."""
from datetime import datetime, timedelta, timezone as dt_timezone

import bcrypt
import jwt
from rest_framework.test import APITestCase

from .authentication import get_jwt_secret, sello_credencial
from .models import TokenRevocado, Usuario

PASSWORD = "Segura123!"


class CierreSesionTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(
            nombre="Admin", apellido="QA", correo="admin-h37@prueba.invalid",
            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.otro = Usuario.objects.create(
            nombre="Otro", apellido="QA", correo="otro-h37@prueba.invalid",
            password=hash_, rol="ADMIN", estado="ACTIVO")

    def login(self, correo="admin-h37@prueba.invalid"):
        respuesta = self.client.post(
            "/api/admin/auth/login/",
            {"correo": correo, "password": PASSWORD},
            format="json",
        )
        self.assertEqual(respuesta.status_code, 200, respuesta.data)
        return respuesta.data["token"]

    def como(self, token):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token}")

    def token_de(self, usuario, **claims):
        ahora = datetime.now(dt_timezone.utc)
        payload = {
            "sub": usuario.correo,
            "rol": usuario.rol,
            "iat": ahora,
            "exp": ahora + timedelta(hours=1),
            "cred": sello_credencial(usuario),
        }
        payload.update(claims)
        return jwt.encode(payload, get_jwt_secret(), "HS256")

    def tablero(self, token):
        self.como(token)
        return self.client.get("/api/admin/dashboard/")

    def test_login_emite_jti_y_version_de_sesion(self):
        token = self.login()
        payload = jwt.decode(token, get_jwt_secret(), algorithms=["HS256"])

        self.assertEqual(len(payload["jti"]), 32)
        self.assertEqual(payload["ver"], 0)
        self.assertEqual(TokenRevocado.objects.count(), 0)

    def test_cerrar_sesion_revoca_solo_el_token_actual(self):
        primero = self.login()
        segundo = self.login()
        jti = jwt.decode(primero, get_jwt_secret(), algorithms=["HS256"])["jti"]

        self.como(primero)
        respuesta = self.client.post("/api/admin/auth/logout/")
        self.assertEqual(respuesta.status_code, 200, respuesta.data)
        self.assertEqual(respuesta.data["mensaje"], "Sesión cerrada")

        revocado = TokenRevocado.objects.get(jti=jti)
        self.assertEqual(revocado.usuario_id, self.admin.id)
        self.assertGreater(revocado.expira_en, datetime.now(dt_timezone.utc))

        self.assertEqual(self.tablero(primero).status_code, 401)
        self.assertEqual(self.tablero(segundo).status_code, 200)

    def test_cerrar_sesion_sin_token_es_401(self):
        self.assertEqual(self.client.post("/api/admin/auth/logout/").status_code, 401)
        self.assertEqual(self.client.post("/api/admin/auth/logout-all/").status_code, 401)

    def test_cerrar_todas_invalida_la_cuenta_pero_no_a_otra(self):
        primero = self.login()
        segundo = self.login()
        ajeno = self.login("otro-h37@prueba.invalid")

        self.como(primero)
        respuesta = self.client.post("/api/admin/auth/logout-all/")
        self.assertEqual(respuesta.status_code, 200, respuesta.data)
        self.assertEqual(
            respuesta.data["mensaje"],
            "Sesiones cerradas en todos los dispositivos")

        self.admin.refresh_from_db()
        self.assertEqual(self.admin.sesion_version, 1)
        self.assertEqual(self.tablero(primero).status_code, 401)
        self.assertEqual(self.tablero(segundo).status_code, 401)
        self.assertEqual(self.tablero(ajeno).status_code, 200)

        nuevo = self.login()
        payload = jwt.decode(nuevo, get_jwt_secret(), algorithms=["HS256"])
        self.assertEqual(payload["ver"], 1)
        self.assertEqual(self.tablero(nuevo).status_code, 200)

    def test_token_con_version_anterior_es_rechazado(self):
        token = self.token_de(self.admin, jti="a" * 32, ver=0)
        self.admin.sesion_version = 1
        self.admin.save(update_fields=["sesion_version"])

        self.assertEqual(self.tablero(token).status_code, 401)

    def test_token_legado_sin_version_solo_vale_mientras_la_cuenta_este_en_cero(self):
        legado = self.token_de(self.admin)
        self.assertEqual(self.tablero(legado).status_code, 200)

        # Sin jti no se puede revocar solo ese token: se cierran todas.
        self.como(legado)
        respuesta = self.client.post("/api/admin/auth/logout/")
        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(
            respuesta.data["mensaje"],
            "Sesión cerrada en todos los dispositivos")

        self.admin.refresh_from_db()
        self.assertEqual(self.admin.sesion_version, 1)
        self.assertEqual(self.tablero(legado).status_code, 401)

        nuevo = self.login()
        self.assertEqual(self.tablero(nuevo).status_code, 200)

    def test_token_con_jti_revocado_es_rechazado(self):
        jti = "b" * 32
        token = self.token_de(self.admin, jti=jti, ver=0)
        self.assertEqual(self.tablero(token).status_code, 200)

        TokenRevocado.objects.create(
            jti=jti,
            usuario=self.admin,
            expira_en=datetime.now(dt_timezone.utc) + timedelta(hours=1),
        )
        self.assertEqual(self.tablero(token).status_code, 401)

    def test_version_o_jti_malformados_son_rechazados(self):
        for claims in ({"ver": True}, {"ver": "1"}, {"ver": -1},
                       {"jti": 123}, {"jti": ""}, {"jti": "x" * 65}):
            with self.subTest(claims=claims):
                token = self.token_de(self.admin, **claims)
                self.assertEqual(self.tablero(token).status_code, 401)

    def test_cierre_es_idempotente_en_la_base(self):
        token = self.login()
        jti = jwt.decode(token, get_jwt_secret(), algorithms=["HS256"])["jti"]
        self.como(token)
        self.assertEqual(self.client.post("/api/admin/auth/logout/").status_code, 200)

        # Un segundo cierre con el mismo token ya no autentica, pero la fila
        # no se duplica ni se pierde.
        self.assertEqual(self.client.post("/api/admin/auth/logout/").status_code, 401)
        self.assertEqual(TokenRevocado.objects.filter(jti=jti).count(), 1)
