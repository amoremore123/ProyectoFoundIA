"""H56: el panel ve y resuelve los reportes de mensajes del chat (solo ADMIN)."""
import bcrypt
from rest_framework.test import APITestCase

from .models import MensajeChat, Notificacion, ReporteMensaje, Usuario
from .tests import PASSWORD, token_de

RUTA = "/api/admin/reportes-mensajes/"


class ReportesMensajeTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        crear = lambda nombre, rol, correo: Usuario.objects.create(
            nombre=nombre, apellido="QA", correo=correo, password=hash_, rol=rol, estado="ACTIVO")
        self.admin = crear("Admin", "ADMIN", "admin-h56@prueba.invalid")
        self.biblioteca = crear("Beto", "BIBLIOTECA", "biblio-h56@prueba.invalid")
        self.ana = crear("Ana", "USUARIO", "ana-h56@prueba.invalid")
        self.luis = crear("Luis", "USUARIO", "luis-h56@prueba.invalid")
        self.mensaje = MensajeChat.objects.create(conversacion_id=7, autor=self.luis, texto="mensaje ofensivo")
        self.reporte = ReporteMensaje.objects.create(mensaje=self.mensaje, usuario=self.ana, motivo="Me insultó")

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def resolver(self, accion, resolucion="Lenguaje ofensivo en el chat", **extra):
        return self.client.post(f"{RUTA}{self.reporte.id}/resolver/",
                                {"accion": accion, "resolucion": resolucion, **extra}, format="json")

    def avisos_de(self, usuario):
        return Notificacion.objects.filter(usuario=usuario, tipo="SISTEMA")

    def test_permisos(self):
        self.assertEqual(self.client.get(RUTA).status_code, 401)
        for quien in (self.biblioteca, self.ana):
            self.como(quien)
            self.assertEqual(self.client.get(RUTA).status_code, 403)
            self.assertEqual(self.resolver("DESCARTAR").status_code, 403)
        self.reporte.refresh_from_db()
        self.assertEqual(self.reporte.estado, "PENDIENTE")

    def test_el_admin_ve_el_texto_y_el_autor_sin_correos(self):
        self.como(self.admin)
        r = self.client.get(RUTA)
        self.assertEqual(r.status_code, 200)
        fila = r.data["results"][0]
        self.assertEqual(fila["mensaje"]["texto"], "mensaje ofensivo")
        self.assertEqual(fila["autor"], {"id": self.luis.id, "nombre": "Luis QA", "estado": "ACTIVO"})
        self.assertEqual(fila["reportadoPor"]["id"], self.ana.id)
        self.assertEqual(fila["conversacionId"], 7)
        self.assertEqual(fila["estado"], "PENDIENTE")
        self.assertNotIn("@", str(r.data))

    def test_descartar(self):
        self.como(self.admin)
        r = self.resolver("DESCARTAR", "No hay ofensa")
        self.assertEqual(r.status_code, 200)
        self.assertEqual((r.data["estado"], r.data["accion"], r.data["repetido"]), ("RECHAZADO", "DESCARTAR", False))
        self.assertEqual(r.data["resueltoPor"]["id"], self.admin.id)
        self.assertFalse(self.avisos_de(self.luis).exists())
        self.luis.refresh_from_db()
        self.assertEqual(self.luis.estado, "ACTIVO")

    def test_advertir_avisa_al_autor_sin_decir_quien_reporto(self):
        self.como(self.admin)
        r = self.resolver("ADVERTIR")
        self.assertEqual((r.status_code, r.data["estado"]), (200, "RESUELTO"))
        aviso = self.avisos_de(self.luis).get()
        self.assertIn("Lenguaje ofensivo en el chat", aviso.mensaje)
        self.assertNotIn("Ana", aviso.titulo + aviso.mensaje)
        self.assertFalse(self.avisos_de(self.ana).exists())
        self.luis.refresh_from_db()
        self.assertEqual(self.luis.estado, "ACTIVO")

    def test_suspender_suspende_al_autor(self):
        self.como(self.admin)
        r = self.resolver("SUSPENDER")
        self.assertEqual((r.status_code, r.data["estado"]), (200, "RESUELTO"))
        self.luis.refresh_from_db()
        self.assertEqual(self.luis.estado, "SUSPENDIDO")
        self.assertEqual(r.data["autor"]["estado"], "SUSPENDIDO")

    def test_repetir_es_seguro_y_cambiar_da_409(self):
        self.como(self.admin)
        self.assertEqual(self.resolver("ADVERTIR").status_code, 200)
        otra = self.resolver("ADVERTIR")
        self.assertEqual((otra.status_code, otra.data["repetido"]), (200, True))
        self.assertEqual(self.avisos_de(self.luis).count(), 1)
        self.assertEqual(self.resolver("SUSPENDER").status_code, 409)
        self.assertEqual(self.resolver("ADVERTIR", "Otro texto").status_code, 409)
        self.luis.refresh_from_db()
        self.assertEqual(self.luis.estado, "ACTIVO")

    def test_validaciones(self):
        self.como(self.admin)
        self.assertEqual(self.resolver("BORRAR").status_code, 400)
        self.assertEqual(self.resolver("DESCARTAR", "  ").status_code, 400)
        self.assertEqual(self.resolver("DESCARTAR", "ok").status_code, 400)
        self.assertEqual(self.resolver("DESCARTAR", "x" * 1001).status_code, 400)
        self.assertEqual(self.resolver("DESCARTAR", resueltoPor=99).status_code, 400)
        self.assertEqual(self.client.post(f"{RUTA}99999/resolver/", {"accion": "DESCARTAR", "resolucion": "abc"},
                                          format="json").status_code, 404)
        self.reporte.refresh_from_db()
        self.assertEqual(self.reporte.estado, "PENDIENTE")

    def test_no_puede_suspender_su_propia_cuenta(self):
        propio = MensajeChat.objects.create(conversacion_id=8, autor=self.admin, texto="respuesta del personal")
        reporte = ReporteMensaje.objects.create(mensaje=propio, usuario=self.ana, motivo="No me gustó")
        self.como(self.admin)
        r = self.client.post(f"{RUTA}{reporte.id}/resolver/", {"accion": "SUSPENDER", "resolucion": "prueba"},
                             format="json")
        self.assertEqual(r.status_code, 400)
        self.admin.refresh_from_db()
        self.assertEqual(self.admin.estado, "ACTIVO")

    def test_filtro_por_estado_y_sin_alta_ni_borrado(self):
        self.como(self.admin)
        self.resolver("DESCARTAR", "No procede")
        self.assertEqual(self.client.get(f"{RUTA}?estado=PENDIENTE").data["count"], 0)
        self.assertEqual(self.client.get(f"{RUTA}?estado=RECHAZADO").data["count"], 1)
        self.assertEqual(self.client.get(f"{RUTA}?estado=BORRADO").status_code, 400)
        self.assertEqual(self.client.post(RUTA, {}, format="json").status_code, 405)
        self.assertEqual(self.client.delete(f"{RUTA}{self.reporte.id}/").status_code, 405)
