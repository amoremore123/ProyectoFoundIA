"""H32: exportación del tablero a Excel y PDF con las mismas cifras."""
import io
import re
from datetime import date, datetime, timezone

from openpyxl import load_workbook
from rest_framework.test import APITestCase

from .models import Categoria, Objeto, Usuario
from .tests import token_de


def utc(dia, hora=12):
    return datetime(2026, 10, dia, hora, tzinfo=timezone.utc)


def texto_pdf(contenido):
    """Deshace los escapes de las cadenas PDF (paréntesis y octal latino-1)."""
    texto = contenido.decode("latin-1")

    def desescapar(coincidencia):
        valor = coincidencia.group(1)
        if valor.isdigit():
            return chr(int(valor, 8))
        return valor

    return re.sub(r"\\([0-7]{3}|[()\\])", desescapar, texto)


class ExportacionTableroTests(APITestCase):
    def setUp(self):
        self.admin = Usuario.objects.create(
            nombre="Admin", apellido="QA", correo="admin-h32@prueba.invalid",
            password="no-se-usa", rol="ADMIN", estado="ACTIVO")
        self.biblioteca = Usuario.objects.create(
            nombre="Beto", apellido="Biblioteca", correo="biblioteca-h32@prueba.invalid",
            password="no-se-usa", rol="BIBLIOTECA", estado="ACTIVO")
        self.usuario = Usuario.objects.create(
            nombre="Ana", apellido="QA", correo="ana-h32@prueba.invalid",
            password="no-se-usa", rol="USUARIO", estado="ACTIVO")
        self.mochilas = Categoria.objects.create(nombre="Mochilas")
        self.celulares = Categoria.objects.create(nombre="Celulares")

        self.crear("PERDIDO", "ACTIVO", 5, self.mochilas, "Biblioteca")
        self.crear("PERDIDO", "RECUPERADO", 6, self.mochilas, "Biblioteca",
                   fecha_recuperacion=utc(7, 18))
        self.crear("ENCONTRADO", "ACTIVO", 7, self.celulares, "Laboratorio")
        self.crear("ENCONTRADO", "ACTIVO", 5, self.celulares, "")
        self.crear("PERDIDO", "OCULTO", 8, self.mochilas, "Patio")
        self.crear("ENCONTRADO", "ELIMINADO", 9, self.celulares, "Patio")
        self.crear("PERDIDO", "ACTIVO", 1, self.mochilas, "Biblioteca")

    def crear(self, tipo, estado, dia, categoria, ubicacion, **extra):
        return Objeto.objects.create(
            usuario=self.usuario, categoria=categoria, nombre="Objeto QA",
            descripcion="Dato conocido", tipo=tipo, estado=estado,
            ubicacion=ubicacion, fecha_objeto=date(2026, 10, dia),
            fecha_publicacion=utc(dia), **extra)

    def como(self, usuario):
        self.client.credentials(HTTP_AUTHORIZATION=f"Bearer {token_de(usuario)}")

    def tablero(self, **filtros):
        self.como(self.admin)
        respuesta = self.client.get("/api/admin/dashboard/", filtros)
        self.assertEqual(respuesta.status_code, 200, respuesta.data)
        return respuesta.data

    def exportar(self, formato, **filtros):
        self.como(self.admin)
        return self.client.get(f"/api/admin/dashboard/exportar.{formato}", filtros)

    def valores(self, hoja):
        """Etiqueta de la columna A -> valores de la columna B (o C si hay unidad)."""
        resultado = {}
        for fila in hoja.iter_rows(min_col=1, max_col=3, values_only=True):
            etiqueta = fila[0]
            if etiqueta is None:
                continue
            resultado.setdefault(etiqueta, []).append(fila[1])
        return resultado

    def test_solo_administrador_activo_exporta(self):
        self.assertEqual(self.client.get("/api/admin/dashboard/exportar.xlsx").status_code, 401)
        self.assertEqual(self.client.get("/api/admin/dashboard/exportar.pdf").status_code, 401)

        for usuario in (self.biblioteca, self.usuario):
            self.como(usuario)
            self.assertEqual(self.client.get("/api/admin/dashboard/exportar.xlsx").status_code, 403)
            self.assertEqual(self.client.get("/api/admin/dashboard/exportar.pdf").status_code, 403)

        self.admin.estado = "SUSPENDIDO"
        self.admin.save(update_fields=["estado"])
        self.como(self.admin)
        self.assertEqual(self.client.get("/api/admin/dashboard/exportar.xlsx").status_code, 403)

    def test_excel_coincide_con_el_tablero(self):
        filtros = {"desde": "2026-10-05", "hasta": "2026-10-07"}
        datos = self.tablero(**filtros)
        respuesta = self.exportar("xlsx", **filtros)

        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(
            respuesta["Content-Type"],
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        self.assertEqual(
            respuesta["Content-Disposition"],
            'attachment; filename="foundia-reporte_2026-10-05_2026-10-07.xlsx"')
        self.assertEqual(respuesta["X-Content-Type-Options"], "nosniff")
        self.assertTrue(respuesta.content.startswith(b"PK"))

        libro = load_workbook(io.BytesIO(respuesta.content))
        self.assertEqual(libro.sheetnames, ["Resumen", "Categorías", "Ubicaciones"])
        resumen = libro["Resumen"]
        valores = self.valores(resumen)

        self.assertEqual(valores["Periodo"], ["Del 05/10/2026 al 07/10/2026"])
        self.assertEqual(valores["Campo del periodo"], ["Fecha de publicación"])
        self.assertEqual(valores["Zona horaria"], ["UTC"])
        self.assertIn("Admin QA (admin-h32@prueba.invalid)", valores["Responsable"])
        self.assertRegex(valores["Emitido"][0], r"\d{2}/\d{2}/\d{4} \d{2}:\d{2}")

        self.assertEqual(valores["Perdidos"][0], datos["objetos"]["perdidos"])
        self.assertEqual(valores["Encontrados"][0], datos["objetos"]["encontrados"])
        self.assertEqual(valores["Recuperados"][0], datos["objetos"]["recuperados"])
        self.assertEqual(valores["Activos"][0], datos["objetos"]["activos"])
        self.assertEqual(valores["Promedio"], [
            datos["recuperacion"]["promedioHoras"], datos["recuperacion"]["promedioDias"]])
        self.assertEqual(valores["Mediana"][0], datos["recuperacion"]["medianaHoras"])
        self.assertEqual(valores["Muestra"][0], datos["recuperacion"]["muestra"])
        self.assertEqual(valores["Sin fecha"][0], datos["recuperacion"]["sinFecha"])
        self.assertEqual(valores["Fechas incoherentes"][0],
                         datos["recuperacion"]["fechasIncoherentes"])

        self.assertEqual(valores["Usuarios registrados"][0], datos["usuarios"])
        self.assertEqual(valores["Objetos activos"][0], datos["objetosActivos"])
        self.assertEqual(valores["Reportes pendientes"][0], datos["reportesPendientes"])
        self.assertEqual(valores["Categorías"][0], datos["categorias"])

        categorias = libro["Categorías"]
        filas_categorias = list(categorias.iter_rows(min_row=3, values_only=True))
        self.assertEqual([(f[1], f[2], f[3], f[4]) for f in filas_categorias],
                         [(c["nombre"], c["total"], c["perdidos"], c["encontrados"])
                          for c in datos["categoriasTop"]])

        ubicaciones = libro["Ubicaciones"]
        filas_ubicaciones = list(ubicaciones.iter_rows(min_row=3, max_row=2 + len(
            datos["ubicacionesTop"]), values_only=True))
        self.assertEqual([(f[1], f[2], f[3], f[4]) for f in filas_ubicaciones],
                         [(u["nombre"], u["total"], u["perdidos"], u["encontrados"])
                          for u in datos["ubicacionesTop"]])
        self.assertIn(f"Publicaciones sin ubicación: {datos['sinUbicacion']}",
                      [f[1] for f in ubicaciones.iter_rows(min_row=3, values_only=True)])

    def test_pdf_coincide_con_el_tablero_y_muestra_emision_y_responsable(self):
        filtros = {"desde": "2026-10-05", "hasta": "2026-10-07"}
        datos = self.tablero(**filtros)
        respuesta = self.exportar("pdf", **filtros)

        self.assertEqual(respuesta.status_code, 200)
        self.assertEqual(respuesta["Content-Type"], "application/pdf")
        self.assertEqual(
            respuesta["Content-Disposition"],
            'attachment; filename="foundia-reporte_2026-10-05_2026-10-07.pdf"')
        self.assertTrue(respuesta.content.startswith(b"%PDF"))

        # El PDF se genera sin comprimir para que sus textos sean verificables.
        texto = texto_pdf(respuesta.content)
        for esperado in (
                "Reporte del tablero",
                "Del 05/10/2026 al 07/10/2026",
                "Emitido",
                "Responsable",
                "Admin QA (admin-h32@prueba.invalid)",
                "Objetos del periodo",
                "Tiempo de recuperación",
                "Estado actual del sistema",
                "Categorías con más publicaciones",
                "Ubicaciones con más publicaciones",
                f"Publicaciones sin ubicación: {datos['sinUbicacion']}",
                "Página 1"):
            self.assertIn(esperado, texto)

        # Las cifras clave viajan como texto en las tablas.
        for cifra in (datos["objetos"]["perdidos"], datos["objetos"]["encontrados"],
                      datos["objetos"]["recuperados"], datos["objetos"]["activos"]):
            self.assertIn(f"({cifra}) Tj", texto)
        for categoria in datos["categoriasTop"]:
            self.assertIn(categoria["nombre"], texto)
        for ubicacion in datos["ubicacionesTop"]:
            self.assertIn(ubicacion["nombre"], texto)

    def test_rango_invalido_invertido_o_filtro_desconocido_es_400(self):
        for filtros in ({"desde": "ayer"}, {"hasta": "2026-02-30"},
                        {"desde": "2026-10-08", "hasta": "2026-10-01"},
                        {"usuario": self.usuario.pk}):
            for formato in ("xlsx", "pdf"):
                with self.subTest(filtros=filtros, formato=formato):
                    self.assertEqual(self.exportar(formato, **filtros).status_code, 400)

    def test_rango_vacio_genera_archivos_validos_con_ceros(self):
        filtros = {"desde": "2027-01-01", "hasta": "2027-01-31"}
        datos = self.tablero(**filtros)
        self.assertEqual(datos["objetos"],
                         {"perdidos": 0, "encontrados": 0, "recuperados": 0, "activos": 0})

        libro = load_workbook(io.BytesIO(self.exportar("xlsx", **filtros).content))
        valores = self.valores(libro["Resumen"])
        self.assertEqual(valores["Perdidos"][0], 0)
        self.assertEqual(valores["Encontrados"][0], 0)
        self.assertEqual(valores["Muestra"][0], 0)
        self.assertEqual(valores["Promedio"][0], "—")
        self.assertEqual(
            [f[1] for f in libro["Categorías"].iter_rows(min_row=3, values_only=True)],
            ["Sin publicaciones en el periodo"])
        self.assertEqual(
            [f[1] for f in libro["Ubicaciones"].iter_rows(min_row=3, values_only=True)],
            ["Sin publicaciones en el periodo", "Publicaciones sin ubicación: 0"])

        pdf = self.exportar("pdf", **filtros)
        self.assertTrue(pdf.content.startswith(b"%PDF"))
        self.assertIn("Sin publicaciones en el periodo", texto_pdf(pdf.content))

    def test_sin_rango_exporta_todo_el_historial(self):
        datos = self.tablero()
        respuesta = self.exportar("xlsx")
        self.assertEqual(
            respuesta["Content-Disposition"],
            'attachment; filename="foundia-reporte_completo-'
            + datetime.now(timezone.utc).strftime("%Y-%m-%d") + '.xlsx"')
        valores = self.valores(load_workbook(io.BytesIO(respuesta.content))["Resumen"])
        self.assertEqual(valores["Periodo"], ["Todo el historial"])
        self.assertEqual(valores["Perdidos"][0], datos["objetos"]["perdidos"])
        self.assertEqual(valores["Encontrados"][0], datos["objetos"]["encontrados"])

        pdf = self.exportar("pdf")
        self.assertRegex(
            pdf["Content-Disposition"],
            r'^attachment; filename="foundia-reporte_completo-\d{4}-\d{2}-\d{2}\.pdf"$')
