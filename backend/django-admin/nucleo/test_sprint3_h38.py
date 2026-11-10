"""H38: historial de respaldos y último estado para el panel."""
from datetime import timedelta

import bcrypt
from django.utils import timezone
from rest_framework.test import APITestCase

from .models import Respaldo, Usuario
from .tests import PASSWORD, token_de


class RespaldosTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(
            nombre="Admin", apellido="QA", correo="admin-h38@prueba.invalid",
            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.biblioteca = Usuario.objects.create(
            nombre="Beto", apellido="Biblioteca", correo="biblioteca-h38@prueba.invalid",
            password=hash_, rol="BIBLIOTECA", estado="ACTIVO")
        self.usuario = Usuario.objects.create(
            nombre="Ana", apellido="Pérez", correo="ana-h38@prueba.invalid",
            password=hash_, rol="USUARIO", estado="ACTIVO")

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def crear(self, nombre, estado="COMPLETADO", horas=0):
        return Respaldo.objects.create(
            nombre_archivo=nombre,
            huella_sha256="a" * 64 if estado == "COMPLETADO" else None,
            tamano_bytes=2048 if estado == "COMPLETADO" else None,
            estado=estado,
            iniciado_en=timezone.now() - timedelta(hours=horas),
            finalizado_en=timezone.now() - timedelta(hours=horas) if estado != "EN_PROCESO" else None,
            mensaje=None if estado == "COMPLETADO" else "prueba",
        )

    def test_solo_admin_puede_consultar_el_historial(self):
        self.assertEqual(self.client.get("/api/admin/respaldos/").status_code, 401)

        for usuario in (self.usuario, self.biblioteca):
            self.como(usuario)
            self.assertEqual(self.client.get("/api/admin/respaldos/").status_code, 403)
            self.assertEqual(self.client.get("/api/admin/respaldos/ultimo/").status_code, 403)

        self.como(self.admin)
        self.assertEqual(self.client.get("/api/admin/respaldos/").status_code, 200)
        self.assertEqual(self.client.get("/api/admin/respaldos/ultimo/").status_code, 200)

    def test_el_historial_es_solo_lectura(self):
        self.como(self.admin)
        self.assertEqual(self.client.post("/api/admin/respaldos/", {}, format="json").status_code, 405)

        respaldo = self.crear("foundia_objetos_perdidos_db_20261109T030000Z.sql.gz")
        self.assertEqual(
            self.client.delete(f"/api/admin/respaldos/{respaldo.id}/").status_code, 405)
        self.assertEqual(
            self.client.patch(f"/api/admin/respaldos/{respaldo.id}/", {}, format="json").status_code, 405)
        self.assertTrue(Respaldo.objects.filter(pk=respaldo.pk).exists())

    def test_lista_muestra_el_mas_reciente_primero_y_campos_del_contrato(self):
        antiguo = self.crear("foundia_objetos_perdidos_db_20261108T030000Z.sql.gz", horas=24)
        reciente = self.crear("foundia_objetos_perdidos_db_20261109T030000Z.sql.gz", horas=0)
        self.como(self.admin)

        r = self.client.get("/api/admin/respaldos/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["count"], 2)
        self.assertEqual(
            [fila["id"] for fila in r.data["results"]],
            [reciente.id, antiguo.id])

        fila = r.data["results"][0]
        self.assertEqual(fila["nombreArchivo"], reciente.nombre_archivo)
        self.assertEqual(fila["huellaSha256"], "a" * 64)
        self.assertEqual(fila["tamanoBytes"], 2048)
        self.assertEqual(fila["estado"], "COMPLETADO")
        self.assertIn("iniciadoEn", fila)
        self.assertIn("finalizadoEn", fila)
        self.assertNotIn("nombre_archivo", fila)

    def test_detalle_y_ultimo_devuelven_el_mismo_respaldo(self):
        self.crear("foundia_objetos_perdidos_db_20261108T030000Z.sql.gz", horas=24)
        reciente = self.crear("foundia_objetos_perdidos_db_20261109T030000Z.sql.gz", horas=0)
        self.como(self.admin)

        detalle = self.client.get(f"/api/admin/respaldos/{reciente.id}/")
        ultimo = self.client.get("/api/admin/respaldos/ultimo/")
        self.assertEqual(detalle.status_code, 200)
        self.assertEqual(ultimo.status_code, 200)
        self.assertEqual(ultimo.data["ultimo"]["id"], reciente.id)
        self.assertEqual(ultimo.data["ultimo"], detalle.data)

    def test_ultimo_sin_respaldos_devuelve_null(self):
        self.como(self.admin)
        r = self.client.get("/api/admin/respaldos/ultimo/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data, {"ultimo": None})

    def test_ultimo_refleja_un_fallo(self):
        self.crear("foundia_objetos_perdidos_db_20261109T030000Z.sql.gz",
                   estado="FALLIDO", horas=0)
        self.como(self.admin)
        r = self.client.get("/api/admin/respaldos/ultimo/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual(r.data["ultimo"]["estado"], "FALLIDO")
        self.assertEqual(r.data["ultimo"]["mensaje"], "prueba")
