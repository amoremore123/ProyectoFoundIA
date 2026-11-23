"""H70: versiones restaurables e historial de restauraciones (solo lectura)."""
from datetime import timedelta

import bcrypt
from django.utils import timezone
from rest_framework.test import APITestCase

from .models import Respaldo, Restauracion, Usuario
from .tests import PASSWORD, token_de


class VersionesTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(
            nombre="Admin", apellido="QA", correo="admin-h70@prueba.invalid",
            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.biblioteca = Usuario.objects.create(
            nombre="Beto", apellido="Biblioteca", correo="biblioteca-h70@prueba.invalid",
            password=hash_, rol="BIBLIOTECA", estado="ACTIVO")
        self.ahora = timezone.now().replace(microsecond=0)

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def crear(self, tipo, horas, segundos=0, estado="COMPLETADO", eliminado=False):
        momento = self.ahora - timedelta(hours=horas) + timedelta(seconds=segundos)
        marca = momento.strftime("%Y%m%dT%H%M%SZ")
        nombre = (f"foundia_objetos_perdidos_db_{marca}.sql.gz" if tipo == "BASE"
                  else f"foundia_fotos_{marca}.tar.gz")
        return Respaldo.objects.create(
            tipo=tipo, nombre_archivo=nombre, huella_sha256="c" * 64, tamano_bytes=2048,
            estado=estado, iniciado_en=momento, finalizado_en=momento,
            eliminado_en=self.ahora if eliminado else None)

    def versiones(self, consulta=""):
        self.como(self.admin)
        r = self.client.get(f"/api/admin/respaldos/versiones/{consulta}")
        self.assertEqual(r.status_code, 200, r.content)
        return r.data

    def test_par_completo_disponible_con_su_marca(self):
        base = self.crear("BASE", horas=2)
        fotos = self.crear("FOTOS", horas=2, segundos=3)
        # Un respaldo de fotos de otra ejecución no se mezcla
        self.crear("FOTOS", horas=30)

        datos = self.versiones()
        self.assertEqual(datos["count"], 1)
        v = datos["results"][0]
        self.assertEqual(v["version"], base.iniciado_en.strftime("%Y%m%dT%H%M%SZ"))
        self.assertTrue(v["disponible"])
        self.assertIsNone(v["motivo"])
        self.assertEqual(v["base"]["id"], base.id)
        self.assertEqual(v["fotos"]["id"], fotos.id)
        self.assertEqual(v["fotos"]["nombreArchivo"], fotos.nombre_archivo)
        self.assertIsNone(v["base"]["eliminadoEn"])

    def test_elige_las_fotos_mas_cercanas(self):
        self.crear("BASE", horas=5)
        self.crear("FOTOS", horas=5, segundos=1800)
        cercana = self.crear("FOTOS", horas=5, segundos=4)
        self.assertEqual(self.versiones()["results"][0]["fotos"]["id"], cercana.id)

    def test_sin_fotos_de_la_misma_ejecucion_no_esta_disponible(self):
        self.crear("BASE", horas=2)
        self.crear("FOTOS", horas=2, segundos=3601)  # más de 1 hora después
        v = self.versiones()["results"][0]
        self.assertFalse(v["disponible"])
        self.assertEqual(v["motivo"], "No hay respaldo de fotos de la misma ejecución.")
        self.assertIsNone(v["fotos"])

    def test_archivos_borrados_por_la_retencion(self):
        self.crear("BASE", horas=200, eliminado=True)
        self.crear("FOTOS", horas=200, segundos=2, eliminado=True)
        self.crear("BASE", horas=100)
        self.crear("FOTOS", horas=100, segundos=2, eliminado=True)

        resultados = self.versiones()["results"]
        self.assertEqual([v["disponible"] for v in resultados], [False, False])
        self.assertTrue(all(v["motivo"] == "Los archivos se borraron por la retención." for v in resultados))
        self.assertIsNotNone(resultados[1]["base"]["eliminadoEn"])

    def test_fallidos_y_en_proceso_no_son_versiones(self):
        self.crear("BASE", horas=3, estado="FALLIDO")
        self.crear("BASE", horas=2, estado="EN_PROCESO")
        ok = self.crear("BASE", horas=1)
        self.crear("FOTOS", horas=1, segundos=2)
        self.crear("FOTOS", horas=3, segundos=2, estado="FALLIDO")
        resultados = self.versiones()["results"]
        self.assertEqual([v["base"]["id"] for v in resultados], [ok.id])

    def test_una_foto_fallida_no_cuenta_como_par(self):
        self.crear("BASE", horas=1)
        self.crear("FOTOS", horas=1, segundos=2, estado="FALLIDO")
        v = self.versiones()["results"][0]
        self.assertFalse(v["disponible"])
        self.assertIsNone(v["fotos"])

    def test_orden_y_paginacion(self):
        for h in range(12, 0, -1):
            self.crear("BASE", horas=h * 3)
            self.crear("FOTOS", horas=h * 3, segundos=2)
        primera = self.versiones()
        self.assertEqual(primera["count"], 12)
        self.assertEqual(len(primera["results"]), 10)
        fechas = [v["fecha"] for v in primera["results"]]
        self.assertEqual(fechas, sorted(fechas, reverse=True))
        self.assertTrue(all(v["disponible"] for v in primera["results"]))
        segunda = self.versiones("?page=2")
        self.assertEqual(len(segunda["results"]), 2)
        # La última de la página 2 también encuentra su par
        self.assertTrue(all(v["fotos"] is not None for v in segunda["results"]))

    def test_sin_respaldos_lista_vacia(self):
        self.assertEqual(self.versiones()["results"], [])

    def test_la_lista_de_respaldos_incluye_eliminado_en(self):
        self.crear("BASE", horas=1, eliminado=True)
        self.como(self.admin)
        fila = self.client.get("/api/admin/respaldos/").data["results"][0]
        self.assertIsNotNone(fila["eliminadoEn"])

    def test_solo_admin(self):
        self.assertEqual(self.client.get("/api/admin/respaldos/versiones/").status_code, 401)
        self.como(self.biblioteca)
        self.assertEqual(self.client.get("/api/admin/respaldos/versiones/").status_code, 403)


class RestauracionesTests(APITestCase):
    def setUp(self):
        hash_ = bcrypt.hashpw(PASSWORD.encode(), bcrypt.gensalt(rounds=4)).decode()
        self.admin = Usuario.objects.create(
            nombre="Admin", apellido="QA", correo="admin-h70r@prueba.invalid",
            password=hash_, rol="ADMIN", estado="ACTIVO")
        self.biblioteca = Usuario.objects.create(
            nombre="Beto", apellido="Biblioteca", correo="biblioteca-h70r@prueba.invalid",
            password=hash_, rol="BIBLIOTECA", estado="ACTIVO")
        ahora = timezone.now()
        self.ensayo = Restauracion.objects.create(
            version="20261120T020849Z", modo="ENSAYO", estado="COMPLETADA",
            respaldo_base="foundia_objetos_perdidos_db_20261120T020849Z.sql.gz",
            respaldo_fotos="foundia_fotos_20261120T020849Z.tar.gz",
            iniciado_en=ahora - timedelta(minutes=10), finalizado_en=ahora - timedelta(minutes=9),
            mensaje="objetos 21 -> 15")
        self.aplicada = Restauracion.objects.create(
            version="20261120T020849Z", modo="APLICADA", estado="COMPLETADA",
            respaldo_base="foundia_objetos_perdidos_db_20261120T020849Z.sql.gz",
            respaldo_fotos="foundia_fotos_20261120T020849Z.tar.gz",
            respaldo_seguridad="20261122T231502Z", responsable="Leonel",
            iniciado_en=ahora, finalizado_en=ahora, mensaje="objetos 21 -> 15. Sesiones cerradas.")

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def test_lista_la_mas_reciente_primero_y_detalle(self):
        self.como(self.admin)
        r = self.client.get("/api/admin/restauraciones/")
        self.assertEqual(r.status_code, 200)
        self.assertEqual([f["id"] for f in r.data["results"]], [self.aplicada.id, self.ensayo.id])
        fila = r.data["results"][0]
        self.assertEqual(fila["modo"], "APLICADA")
        self.assertEqual(fila["respaldoSeguridad"], "20261122T231502Z")
        self.assertEqual(fila["responsable"], "Leonel")
        self.assertEqual(fila["respaldoFotos"], "foundia_fotos_20261120T020849Z.tar.gz")

        detalle = self.client.get(f"/api/admin/restauraciones/{self.ensayo.id}/")
        self.assertEqual(detalle.status_code, 200)
        self.assertIsNone(detalle.data["respaldoSeguridad"])
        self.assertEqual(self.client.get("/api/admin/restauraciones/999999/").status_code, 404)

    def test_no_se_escribe_desde_el_panel(self):
        self.como(self.admin)
        cuerpo = {"version": "20261120T020849Z", "modo": "APLICADA"}
        self.assertEqual(self.client.post("/api/admin/restauraciones/", cuerpo, format="json").status_code, 405)
        ruta = f"/api/admin/restauraciones/{self.ensayo.id}/"
        self.assertEqual(self.client.patch(ruta, {"estado": "FALLIDA"}, format="json").status_code, 405)
        self.assertEqual(self.client.delete(ruta).status_code, 405)
        self.assertEqual(self.client.post("/api/admin/respaldos/versiones/").status_code, 405)
        self.assertEqual(Restauracion.objects.count(), 2)

    def test_solo_admin(self):
        self.assertEqual(self.client.get("/api/admin/restauraciones/").status_code, 401)
        self.como(self.biblioteca)
        self.assertEqual(self.client.get("/api/admin/restauraciones/").status_code, 403)
