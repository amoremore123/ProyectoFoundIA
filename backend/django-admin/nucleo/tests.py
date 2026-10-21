"""H15 - Administrador: gestionar usuarios y publicaciones.

Ejecutar:  python manage.py test nucleo --settings=config.settings_test
(base SQLite en memoria: no toca la base del equipo)
"""
from datetime import date, datetime, timedelta, timezone as dt_timezone

import bcrypt
import jwt
from rest_framework.test import APITestCase

from .authentication import get_jwt_secret
from .models import Categoria, Objeto, Usuario

PASSWORD = "Segura123!"


def token_de(usuario, horas=1):
    exp = datetime.now(dt_timezone.utc) + timedelta(hours=horas)
    return jwt.encode({"sub": str(usuario.id), "rol": usuario.rol, "exp": exp}, get_jwt_secret(), "HS256")


class PanelAdministracionTests(APITestCase):

    def test_bloqueo_compartido_impide_login_y_token_anterior(self):
        self.admin.bloqueado_hasta = datetime.now(dt_timezone.utc) + timedelta(minutes=15)
        self.admin.save()
        r = self.client.post('/api/admin/auth/login/', {'correo': self.admin.correo, 'password': PASSWORD}, format='json')
        self.assertEqual(r.status_code, 423)
        self.assertNotIn('token', r.data)
        self.como(self.admin)
        self.assertEqual(self.client.get('/api/admin/usuarios/').status_code, 401)

    def test_login_admin_bloquea_cinco_fallos_y_recupera_al_vencer(self):
        for intento in range(5):
            r = self.client.post('/api/admin/auth/login/', {'correo': self.admin.correo, 'password': 'incorrecta'}, format='json')
            self.assertEqual(r.status_code, 423 if intento == 4 else 401)
        self.admin.refresh_from_db()
        self.assertEqual(self.admin.intentos_login, 5)
        self.assertIsNotNone(self.admin.bloqueado_hasta)
        self.admin.bloqueado_hasta = datetime.now(dt_timezone.utc) - timedelta(seconds=1)
        self.admin.save()
        r = self.client.post('/api/admin/auth/login/', {'correo': self.admin.correo.upper(), 'password': PASSWORD}, format='json')
        self.assertEqual(r.status_code, 200)
        self.admin.refresh_from_db()
        self.assertEqual(self.admin.intentos_login, 0)
        self.assertIsNone(self.admin.bloqueado_hasta)

    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(nombre="Admin", apellido="QA", correo="admin@prueba.invalid",
                                            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.ana = Usuario.objects.create(nombre="Ana", apellido="Pérez", correo="ana@prueba.invalid",
                                          password=hash_, rol="USUARIO", estado="ACTIVO")
        categoria = Categoria.objects.create(nombre="Mochilas QA")
        self.pub = Objeto.objects.create(usuario=self.ana, categoria=categoria, nombre="Mochila QA",
                                         descripcion="Azul", ubicacion="Biblioteca",
                                         fecha_objeto=date.today(), tipo="PERDIDO", estado="ACTIVO")

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    # --------------------------------------------------------------- acceso
    def test_admin_lista_usuarios_y_publicaciones(self):
        self.como(self.admin)
        r = self.client.get("/api/admin/usuarios/")
        self.assertEqual(r.status_code, 200)
        correos = [u["correo"] for u in r.data["results"]]
        self.assertIn("ana@prueba.invalid", correos)
        self.assertNotIn("password", r.data["results"][0])

        r = self.client.get("/api/admin/publicaciones/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["results"][0]["nombre"], "Mochila QA")

    def test_visitante_sin_token_no_accede(self):
        for ruta in ("/api/admin/usuarios/", "/api/admin/publicaciones/",
                     f"/api/admin/usuarios/{self.ana.id}/", f"/api/admin/publicaciones/{self.pub.id}/"):
            self.assertEqual(self.client.get(ruta).status_code, 401, ruta)
        r = self.client.patch(f"/api/admin/usuarios/{self.ana.id}/", {"estado": "SUSPENDIDO"}, format="json")
        self.assertEqual(r.status_code, 401)
        self.ana.refresh_from_db()
        self.assertEqual(self.ana.estado, "ACTIVO")

    def test_usuario_normal_no_accede_ni_por_api_directa(self):
        self.como(self.ana)
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 403)
        self.assertEqual(self.client.get("/api/admin/publicaciones/").status_code, 403)
        r = self.client.patch(f"/api/admin/publicaciones/{self.pub.id}/", {"estado": "OCULTO"}, format="json")
        self.assertEqual(r.status_code, 403)
        self.pub.refresh_from_db()
        self.assertEqual(self.pub.estado, "ACTIVO")

    def test_usuario_normal_no_inicia_sesion_en_el_panel(self):
        r = self.client.post("/api/admin/auth/login/", {"correo": "ana@prueba.invalid", "password": PASSWORD},
                             format="json")
        self.assertEqual(r.status_code, 403)
        self.assertNotIn("token", r.data)

    def test_admin_suspendido_pierde_el_acceso(self):
        self.admin.estado = "SUSPENDIDO"
        self.admin.save()
        self.como(self.admin)
        self.assertEqual(self.client.get("/api/admin/usuarios/").status_code, 403)
        r = self.client.post("/api/admin/auth/login/", {"correo": "admin@prueba.invalid", "password": PASSWORD},
                             format="json")
        self.assertEqual(r.status_code, 401)

    # ------------------------------------------------------------- usuarios
    def test_suspender_y_activar_usuario(self):
        self.como(self.admin)
        r = self.client.patch(f"/api/admin/usuarios/{self.ana.id}/", {"estado": "SUSPENDIDO"}, format="json")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["estado"], "SUSPENDIDO")
        self.ana.refresh_from_db()
        self.assertEqual(self.ana.estado, "SUSPENDIDO")

        r = self.client.patch(f"/api/admin/usuarios/{self.ana.id}/", {"estado": "ACTIVO"}, format="json")
        self.assertEqual(r.status_code, 200)
        self.ana.refresh_from_db()
        self.assertEqual(self.ana.estado, "ACTIVO")

    def test_admin_no_se_suspende_a_si_mismo(self):
        self.como(self.admin)
        r = self.client.patch(f"/api/admin/usuarios/{self.admin.id}/", {"estado": "SUSPENDIDO"}, format="json")
        self.assertEqual(r.status_code, 400)
        self.admin.refresh_from_db()
        self.assertEqual(self.admin.estado, "ACTIVO")

    def test_no_cambia_rol_correo_ni_estado_invalido(self):
        self.como(self.admin)
        for cuerpo in ({"rol": "ADMIN"}, {"estado": "SUSPENDIDO", "rol": "ADMIN"},
                       {"correo": "otro@prueba.invalid"}, {"estado": "BORRADO"}):
            r = self.client.patch(f"/api/admin/usuarios/{self.ana.id}/", cuerpo, format="json")
            self.assertEqual(r.status_code, 400, cuerpo)
        self.ana.refresh_from_db()
        self.assertEqual((self.ana.rol, self.ana.estado, self.ana.correo),
                         ("USUARIO", "ACTIVO", "ana@prueba.invalid"))

    def test_no_crea_ni_borra_usuarios_fisicamente(self):
        self.como(self.admin)
        r = self.client.post("/api/admin/usuarios/", {"nombre": "X", "apellido": "Y", "correo": "x@y.z",
                                                      "password": "a", "rol": "ADMIN"}, format="json")
        self.assertEqual(r.status_code, 405)
        self.assertEqual(self.client.delete(f"/api/admin/usuarios/{self.ana.id}/").status_code, 405)
        self.assertEqual(self.client.put(f"/api/admin/usuarios/{self.ana.id}/", {"estado": "ACTIVO"},
                                         format="json").status_code, 405)
        self.assertTrue(Usuario.objects.filter(pk=self.ana.id).exists())

    # -------------------------------------------------------- publicaciones
    def test_ocultar_mostrar_y_eliminar_logicamente(self):
        self.como(self.admin)
        ruta = f"/api/admin/publicaciones/{self.pub.id}/"
        self.assertEqual(self.client.patch(ruta, {"estado": "OCULTO"}, format="json").data["estado"], "OCULTO")
        self.assertEqual(self.client.patch(ruta, {"estado": "ACTIVO"}, format="json").data["estado"], "ACTIVO")
        r = self.client.patch(ruta, {"estado": "ELIMINADO"}, format="json")
        self.assertEqual(r.status_code, 200)
        self.pub.refresh_from_db()
        self.assertEqual(self.pub.estado, "ELIMINADO")
        self.assertTrue(Objeto.objects.filter(pk=self.pub.id).exists())  # borrado lógico

    def test_eliminada_no_se_vuelve_a_mostrar(self):
        self.pub.estado = "ELIMINADO"
        self.pub.save()
        self.como(self.admin)
        r = self.client.patch(f"/api/admin/publicaciones/{self.pub.id}/", {"estado": "ACTIVO"}, format="json")
        self.assertEqual(r.status_code, 400)
        self.pub.refresh_from_db()
        self.assertEqual(self.pub.estado, "ELIMINADO")

    def test_delete_fisico_y_edicion_de_contenido_bloqueados(self):
        self.como(self.admin)
        ruta = f"/api/admin/publicaciones/{self.pub.id}/"
        self.assertEqual(self.client.delete(ruta).status_code, 405)
        r = self.client.patch(ruta, {"nombre": "Cambiado por admin"}, format="json")
        self.assertEqual(r.status_code, 400)
        self.assertEqual(self.client.patch(ruta, {"estado": "RECUPERADO"}, format="json").status_code, 400)
        self.pub.refresh_from_db()
        self.assertEqual((self.pub.nombre, self.pub.estado), ("Mochila QA", "ACTIVO"))
        self.assertTrue(Objeto.objects.filter(pk=self.pub.id).exists())

    def test_id_inexistente_da_404_sin_datos(self):
        self.como(self.admin)
        r = self.client.patch("/api/admin/publicaciones/999999/", {"estado": "OCULTO"}, format="json")
        self.assertEqual(r.status_code, 404)
        self.assertNotIn("ana@prueba.invalid", str(r.data))
