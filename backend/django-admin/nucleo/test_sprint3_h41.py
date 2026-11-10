"""H41: recepción en custodia con código único, QR firmado y constancia."""
import re
from datetime import date

import bcrypt
from rest_framework.test import APITestCase

from .custodia import verificar_token_custodia
from .models import Categoria, Custodia, Objeto, Usuario
from .tests import PASSWORD, token_de


class CustodiaRecepcionTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(
            nombre="Admin", apellido="QA", correo="admin-h41@prueba.invalid",
            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.biblioteca = Usuario.objects.create(
            nombre="Beto", apellido="Biblioteca", correo="biblioteca-h41@prueba.invalid",
            password=hash_, rol="BIBLIOTECA", estado="ACTIVO")
        self.ana = Usuario.objects.create(
            nombre="Ana", apellido="Pérez", correo="ana-h41@prueba.invalid",
            password=hash_, rol="USUARIO", estado="ACTIVO")
        self.categoria = Categoria.objects.create(nombre="Mochilas H41")
        self.encontrado = self.crear_objeto("Mochila azul", "ENCONTRADO")
        self.encontrado2 = self.crear_objeto("Laptop gris", "ENCONTRADO")

    def crear_objeto(self, nombre, tipo):
        return Objeto.objects.create(
            usuario=self.ana, categoria=self.categoria, nombre=nombre,
            descripcion=f"Descripción de {nombre}", ubicacion="Biblioteca central",
            fecha_objeto=date.today(), tipo=tipo, estado="ACTIVO")

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def recibir(self, objeto, usuario=None):
        self.como(usuario or self.biblioteca)
        return self.client.post("/api/admin/custodias/",
                                {"objetoId": objeto.id, "observaciones": "Recibido en mostrador"},
                                format="json")

    def test_recepcion_asigna_codigo_unico_y_cambia_estado(self):
        r = self.recibir(self.encontrado)
        self.assertEqual(r.status_code, 201, r.data)
        self.assertRegex(r.data["codigo"], r"^FND-\d{4}-0001$")
        self.assertEqual(r.data["estado"], "EN_CUSTODIA")
        self.assertEqual(r.data["recibidoPor"]["nombre"], "Beto Biblioteca")
        self.assertEqual(r.data["objeto"]["id"], self.encontrado.id)
        self.assertIn("/etiqueta/", r.data["etiquetaUrl"])
        self.assertIn("/constancia/", r.data["constanciaUrl"])

        self.encontrado.refresh_from_db()
        self.assertEqual(self.encontrado.estado, "EN_CUSTODIA")
        custodia = Custodia.objects.get(objeto=self.encontrado)
        datos = verificar_token_custodia(custodia.token_qr)
        self.assertEqual(datos["sub"], custodia.codigo)
        self.assertEqual(datos["objeto"], self.encontrado.id)
        self.assertEqual(datos["tipo"], "custodia")

    def test_codigos_consecutivos_por_anio(self):
        primero = self.recibir(self.encontrado)
        segundo = self.recibir(self.encontrado2)
        self.assertEqual(primero.status_code, 201)
        self.assertEqual(segundo.status_code, 201)
        anio = date.today().year
        self.assertEqual(primero.data["codigo"], f"FND-{anio}-0001")
        self.assertEqual(segundo.data["codigo"], f"FND-{anio}-0002")

    def test_no_se_registra_dos_veces_el_mismo_objeto(self):
        self.assertEqual(self.recibir(self.encontrado).status_code, 201)
        r = self.recibir(self.encontrado)
        self.assertEqual(r.status_code, 409)
        self.assertEqual(Custodia.objects.filter(objeto=self.encontrado).count(), 1)

    def test_solo_biblioteca_o_admin_registra(self):
        r = self.recibir(self.encontrado, usuario=self.ana)
        self.assertEqual(r.status_code, 403)
        self.assertFalse(Custodia.objects.exists())

        r = self.recibir(self.encontrado2, usuario=self.admin)
        self.assertEqual(r.status_code, 201)

    def test_rechaza_objetos_no_disponibles(self):
        perdido = self.crear_objeto("Objeto perdido", "PERDIDO")
        self.assertEqual(self.recibir(perdido).status_code, 400)
        eliminado = self.crear_objeto("Objeto eliminado", "ENCONTRADO")
        eliminado.estado = "ELIMINADO"
        eliminado.save(update_fields=["estado"])
        self.assertEqual(self.recibir(eliminado).status_code, 400)
        self.assertFalse(Custodia.objects.exists())

    def test_etiqueta_y_constancia_pdf_sin_datos_del_autor(self):
        r = self.recibir(self.encontrado)
        custodia = Custodia.objects.get(objeto=self.encontrado)
        self.como(self.biblioteca)

        etiqueta = self.client.get(f"/api/admin/custodias/{custodia.id}/etiqueta/")
        self.assertEqual(etiqueta.status_code, 200)
        self.assertEqual(etiqueta["Content-Type"], "application/pdf")
        self.assertTrue(etiqueta.content.startswith(b"%PDF"))
        self.assertIn(custodia.codigo.encode(), etiqueta.content)
        self.assertNotIn(self.ana.correo.encode(), etiqueta.content)
        self.assertNotIn(self.ana.nombre.encode(), etiqueta.content)

        constancia = self.client.get(f"/api/admin/custodias/{custodia.id}/constancia/")
        self.assertEqual(constancia.status_code, 200)
        self.assertTrue(constancia.content.startswith(b"%PDF"))
        self.assertNotIn(self.ana.correo.encode(), constancia.content)

    def test_verifica_el_token_del_qr_y_rechaza_manipulados(self):
        r = self.recibir(self.encontrado)
        custodia = Custodia.objects.get(objeto=self.encontrado)
        self.como(self.biblioteca)

        valido = self.client.get("/api/admin/custodias/verificar/",
                                 {"token": custodia.token_qr})
        self.assertEqual(valido.status_code, 200)
        self.assertTrue(valido.data["valido"])
        self.assertEqual(valido.data["codigo"], r.data["codigo"])
        self.assertEqual(valido.data["objeto"]["id"], self.encontrado.id)

        manipulado = custodia.token_qr[:-4] + "AAAA"
        self.assertEqual(
            self.client.get("/api/admin/custodias/verificar/", {"token": manipulado}).status_code,
            400)
        self.assertEqual(
            self.client.get("/api/admin/custodias/verificar/", {"token": ""}).status_code, 400)

    def test_lista_filtra_por_texto_estado_y_categoria(self):
        self.recibir(self.encontrado)
        self.como(self.biblioteca)
        r = self.client.get("/api/admin/custodias/", {"q": "Mochila"})
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["count"], 1)
        self.assertEqual(r.data["results"][0]["codigo"][:4], "FND-")
        self.assertEqual(self.client.get("/api/admin/custodias/",
                                         {"estado": "RETIRADO"}).data["count"], 0)
        self.assertEqual(self.client.get("/api/admin/custodias/",
                                         {"categoriaId": self.categoria.id}).data["count"], 1)
        self.assertEqual(self.client.get("/api/admin/custodias/",
                                         {"estado": "OTRO"}).status_code, 400)
