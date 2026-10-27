"""H26: catálogo administrativo, sin modificar la base del equipo."""
from datetime import date

from rest_framework.test import APITestCase

from .models import Categoria, Objeto, Usuario
from .tests import token_de


class CategoriasSprint2Tests(APITestCase):
    def setUp(self):
        self.admin = Usuario.objects.create(nombre="Admin", apellido="QA", correo="admin@qa.invalid",
                                            password="no-se-usa", rol="ADMIN")
        self.usuario = Usuario.objects.create(nombre="Usuario", apellido="QA", correo="usuario@qa.invalid",
                                              password="no-se-usa")
        self.categoria = Categoria.objects.create(nombre="Mochilas")
        self.objeto = Objeto.objects.create(usuario=self.usuario, categoria=self.categoria,
                                            nombre="Mochila QA", descripcion="Prueba", tipo="PERDIDO",
                                            fecha_objeto=date.today())
        self.ruta = f"/api/admin/categorias/{self.categoria.id}/"

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def test_solo_admin_puede_crear_editar_o_desactivar(self):
        self.assertEqual(self.client.get("/api/admin/categorias/").status_code, 401)
        self.como(self.usuario)
        for method, ruta, data in (("post", "/api/admin/categorias/", {"nombre": "Otra"}),
                                  ("patch", self.ruta, {"estado": False}),
                                  ("delete", self.ruta, {})):
            self.assertEqual(getattr(self.client, method)(ruta, data, format="json").status_code, 403)
        self.categoria.refresh_from_db()
        self.assertTrue(self.categoria.estado)

    def test_editar_conserva_id_y_asociaciones(self):
        self.como(self.admin)
        r = self.client.patch(self.ruta, {"nombre": "  Bolsos  ", "descripcion": "Catálogo"}, format="json")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["nombre"], "Bolsos")
        self.objeto.refresh_from_db()
        self.assertEqual(self.objeto.categoria_id, self.categoria.id)
        self.assertEqual(self.objeto.categoria.nombre, "Bolsos")

    def test_nombres_vacios_duplicados_y_campos_ajenos_son_400(self):
        self.como(self.admin)
        for data in ({"nombre": "   "}, {"nombre": "mochilas"}, {"nombre": "Mochilas", "id": 999}):
            self.assertEqual(self.client.post("/api/admin/categorias/", data, format="json").status_code, 400)
        otra = Categoria.objects.create(nombre="Llaves", estado=False)
        self.assertEqual(self.client.patch(self.ruta, {"nombre": " llaves "}, format="json").status_code, 400)
        self.assertTrue(Categoria.objects.filter(pk=otra.pk).exists())
        self.assertEqual(Categoria.objects.count(), 2)

    def test_delete_es_desactivacion_logica_repetible(self):
        self.como(self.admin)
        for _ in range(2):
            self.assertEqual(self.client.delete(self.ruta).status_code, 204)
        self.categoria.refresh_from_db()
        self.assertFalse(self.categoria.estado)
        self.assertTrue(Objeto.objects.filter(pk=self.objeto.pk, categoria=self.categoria).exists())
        r = self.client.patch(self.ruta, {"estado": True}, format="json")
        self.assertEqual(r.status_code, 200)
        self.assertTrue(r.data["estado"])

    def test_categoria_inexistente_es_404(self):
        self.como(self.admin)
        self.assertEqual(self.client.patch("/api/admin/categorias/999999/", {"estado": False}, format="json").status_code, 404)
