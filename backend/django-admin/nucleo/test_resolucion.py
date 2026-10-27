"""H28: resolución auditada, permisos, repetición y conservación de antecedentes."""
from datetime import date

from rest_framework.test import APITestCase

from .models import Categoria, Objeto, Reporte, Usuario
from .tests import token_de


class ResolucionReportesTests(APITestCase):
    def setUp(self):
        self.admin = Usuario.objects.create(nombre="Admin", apellido="QA", correo="admin@qa.invalid",
                                            password="no-se-usa", rol="ADMIN")
        self.usuario = Usuario.objects.create(nombre="Ana", apellido="QA", correo="ana@qa.invalid",
                                              password="no-se-usa")
        categoria = Categoria.objects.create(nombre="Pruebas")
        self.objeto = Objeto.objects.create(usuario=self.usuario, categoria=categoria, nombre="Objeto QA",
                                            descripcion="Prueba", tipo="PERDIDO", fecha_objeto=date.today())
        self.reporte = Reporte.objects.create(usuario=self.usuario, objeto=self.objeto, motivo="Contenido inapropiado")
        self.ruta = f"/api/admin/reportes/{self.reporte.pk}/resolver/"
        self.datos = {"estado": "RESUELTO", "resolucion": "Revisado por administración"}

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def test_sesion_y_admin_son_obligatorios(self):
        self.assertEqual(self.client.post(self.ruta, self.datos, format="json").status_code, 401)
        self.como(self.usuario)
        self.assertEqual(self.client.post(self.ruta, self.datos, format="json").status_code, 403)
        self.admin.estado = "SUSPENDIDO"
        self.admin.save()
        self.como(self.admin)
        self.assertEqual(self.client.post(self.ruta, self.datos, format="json").status_code, 403)
        self.reporte.refresh_from_db()
        self.assertEqual(self.reporte.estado, "PENDIENTE")

    def test_resolver_y_repetir_conserva_actor_fecha_y_denuncia(self):
        self.como(self.admin)
        r = self.client.post(self.ruta, self.datos, format="json")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["resueltoPorId"], self.admin.pk)
        self.assertFalse(r.data["repetido"])
        fecha = r.data["fechaResolucion"]
        r = self.client.post(self.ruta, self.datos, format="json")
        self.assertTrue(r.data["repetido"])
        self.assertEqual(r.data["fechaResolucion"], fecha)
        self.reporte.refresh_from_db()
        self.assertEqual(self.reporte.usuario_id, self.usuario.pk)
        self.assertEqual(self.reporte.objeto_id, self.objeto.pk)
        self.assertEqual(self.reporte.motivo, "Contenido inapropiado")
        self.objeto.refresh_from_db()
        self.assertEqual(self.objeto.estado, "ACTIVO")

    def test_no_sobrescribe_resolucion_ni_admite_actor_fecha_o_ids_del_cliente(self):
        self.como(self.admin)
        for extra in ({"resueltoPorId": self.usuario.pk}, {"fechaResolucion": "2000-01-01"}, {"usuario": self.admin.pk}):
            self.assertEqual(self.client.post(self.ruta, self.datos | extra, format="json").status_code, 400)
        self.assertEqual(self.client.post(self.ruta, self.datos | {"resolucion": "   "}, format="json").status_code, 400)
        self.client.post(self.ruta, self.datos, format="json")
        self.assertEqual(self.client.post(self.ruta, {"estado": "RECHAZADO", "resolucion": "Otra"}, format="json").status_code, 409)
        self.reporte.refresh_from_db()
        self.assertEqual(self.reporte.resolucion, self.datos["resolucion"])

    def test_revisado_puede_resolverse_y_no_hay_borrado_o_asignacion_masiva(self):
        self.como(self.admin)
        self.reporte.estado = "REVISADO"
        self.reporte.save()
        self.assertEqual(self.client.patch(self.ruta.replace("resolver/", ""), self.datos, format="json").status_code, 200)
        ruta = self.ruta.replace("resolver/", "")
        self.assertEqual(self.client.delete(ruta).status_code, 405)
        self.assertEqual(self.client.put(ruta, self.datos, format="json").status_code, 405)
        self.assertEqual(self.client.post("/api/admin/reportes/", {}, format="json").status_code, 405)

    def test_inexistente_devuelve_404(self):
        self.como(self.admin)
        self.assertEqual(self.client.post("/api/admin/reportes/999999/resolver/", self.datos, format="json").status_code, 404)
