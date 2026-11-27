"""H70: restaurar una versión desde el panel, con doble confirmación y modo mantenimiento."""
from datetime import timedelta

import bcrypt
from django.utils import timezone
from rest_framework.test import APITestCase

from .models import Respaldo, Restauracion, SolicitudEliminacion, Usuario
from .tests import PASSWORD, token_de


class RestaurarDesdeElPanelTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        crear = lambda nombre, rol: Usuario.objects.create(  # noqa: E731
            nombre=nombre, apellido="QA", correo=f"{nombre.lower()}-h70p@prueba.invalid", password=hash_, rol=rol,
            estado="ACTIVO")
        self.admin = crear("Ada", "ADMIN")
        self.biblio = crear("Beto", "BIBLIOTECA")
        self.ana = crear("Ana", "USUARIO")
        momento = timezone.now().replace(microsecond=0) - timedelta(hours=3)
        self.version = momento.strftime("%Y%m%dT%H%M%SZ")
        for tipo, nombre in (("BASE", f"foundia_objetos_perdidos_db_{self.version}.sql.gz"),
                             ("FOTOS", f"foundia_fotos_{self.version}.tar.gz")):
            Respaldo.objects.create(tipo=tipo, nombre_archivo=nombre, huella_sha256="a" * 64, tamano_bytes=10,
                                    estado="COMPLETADO", iniciado_en=momento, finalizado_en=momento)

    def ruta(self, version=None):
        return f"/api/admin/respaldos/versiones/{version or self.version}/restaurar/"

    def pedir(self, quien=None, version=None, **cuerpo):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(quien or self.admin)}")
        datos = {"confirmacionVersion": version or self.version, "confirmacionTexto": "RESTAURAR"}
        datos.update(cuerpo)
        return self.client.post(self.ruta(version), datos, format="json")

    def test_pedir_deja_la_restauracion_solicitada(self):
        r = self.pedir()
        self.assertEqual(r.status_code, 202, r.data)
        self.assertEqual((r.data["estado"], r.data["modo"], r.data["version"]), ("SOLICITADA", "APLICADA", self.version))
        self.assertEqual(r.data["responsable"], "Ada QA")
        self.assertEqual(r.data["respaldoFotos"], f"foundia_fotos_{self.version}.tar.gz")

    def test_doble_confirmacion(self):
        for cuerpo, campo in (({"confirmacionVersion": "20200101T000000Z"}, "confirmacionVersion"),
                              ({"confirmacionTexto": "restaurar"}, "confirmacionTexto"),
                              ({"confirmacionTexto": "SI"}, "confirmacionTexto")):
            with self.subTest(cuerpo=cuerpo):
                r = self.pedir(**cuerpo)
                self.assertEqual(r.status_code, 400)
                self.assertIn(campo, r.data)
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")
        self.assertEqual(self.client.post(self.ruta(), {}, format="json").status_code, 400)
        self.assertEqual(Restauracion.objects.count(), 0)

    def test_version_inexistente_o_no_disponible(self):
        self.assertEqual(self.pedir(version="20200101T000000Z").status_code, 404)
        Respaldo.objects.filter(tipo="FOTOS").update(eliminado_en=timezone.now())
        r = self.pedir()
        self.assertEqual(r.status_code, 409)
        self.assertIn("retención", r.data["detail"])
        self.assertEqual(Restauracion.objects.count(), 0)

    def test_una_a_la_vez(self):
        self.assertEqual(self.pedir().status_code, 202)
        # Mientras está en curso no se crea otra: 503 (modo mantenimiento) o 409 (la vista) si el chequeo de
        # mantenimiento todavía está en su caché de 2 segundos
        self.assertIn(self.pedir().status_code, (409, 503))
        Restauracion.objects.update(estado="EN_PROCESO")
        self.assertIn(self.pedir().status_code, (409, 503))
        self.assertEqual(Restauracion.objects.count(), 1)
        Restauracion.objects.update(estado="COMPLETADA")
        self.assertEqual(self.pedir().status_code, 202)
        self.assertEqual(Restauracion.objects.filter(estado="SOLICITADA").count(), 1)

    def test_solo_admin(self):
        self.assertEqual(self.client.post(self.ruta(), {}, format="json").status_code, 401)
        for quien in (self.biblio, self.ana):
            with self.subTest(rol=quien.rol):
                self.assertEqual(self.pedir(quien).status_code, 403)
        self.assertEqual(Restauracion.objects.count(), 0)

    def test_modo_mantenimiento_en_el_panel(self):
        Restauracion.objects.create(version=self.version, modo="APLICADA", estado="SOLICITADA",
                                    respaldo_base="b", respaldo_fotos="f", iniciado_en=timezone.now())
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")
        r = self.client.post("/api/admin/campanas/", {"nombre": "Algo"}, format="json")
        self.assertEqual(r.status_code, 503)
        self.assertEqual(r["Retry-After"], "60")
        self.assertIn("restaurando una versión anterior", r.json()["mensaje"])
        # Las lecturas y el inicio de sesión siguen
        self.assertEqual(self.client.get("/api/admin/restauraciones/").status_code, 200)
        self.client.credentials()
        r = self.client.post("/api/admin/auth/login/", {"correo": "ada-h70p@prueba.invalid", "password": PASSWORD},
                             format="json")
        self.assertEqual(r.status_code, 200)

    def test_un_ensayo_no_activa_el_mantenimiento(self):
        Restauracion.objects.create(version=self.version, modo="ENSAYO", estado="EN_PROCESO",
                                    respaldo_base="b", respaldo_fotos="f", iniciado_en=timezone.now())
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")
        self.assertNotEqual(self.client.post("/api/admin/campanas/", {"nombre": "Algo"}, format="json").status_code, 503)

    def test_reaplicar_eliminaciones_desde_el_panel(self):
        rosa = Usuario.objects.create(nombre="Rosa", apellido="QA", correo="rosa-h70p@prueba.invalid",
                                      password="x", rol="USUARIO", estado="ACTIVO")
        SolicitudEliminacion.objects.create(usuario=rosa, estado="ATENDIDA")  # una restauración la trajo de vuelta
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.admin)}")
        r = self.client.post("/api/admin/solicitudes-eliminacion/reaplicar/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data, {"cuentasAnonimizadasDeNuevo": 1})
        rosa.refresh_from_db()
        self.assertEqual(rosa.estado, "ELIMINADO")
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(self.ana)}")
        self.assertEqual(self.client.post("/api/admin/solicitudes-eliminacion/reaplicar/").status_code, 403)
