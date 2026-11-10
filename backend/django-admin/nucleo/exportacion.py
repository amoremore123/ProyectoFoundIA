"""H32: exportación del tablero administrativo a Excel y PDF.

Los dos formatos se construyen con el mismo diccionario que devuelve
`GET /api/admin/dashboard/`, de modo que las cifras del archivo y las del
tablero no puedan separarse. El rango se valida antes de generar el archivo.
"""
import io

from openpyxl import Workbook
from openpyxl.styles import Alignment, Font, PatternFill
from openpyxl.utils import get_column_letter
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm
from reportlab.platypus import PageBreak, Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

AZUL = colors.HexColor("#12355B")
GRIS = colors.HexColor("#E8EEF5")
BORDE = colors.HexColor("#B8C7D9")
SIN_DATO = "—"

FORMATO_XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
FORMATO_PDF = "application/pdf"


def nombre_archivo(datos, extension, emitido):
    """Nombre estable y legible: incluye el rango y la fecha de emisión."""
    rango = datos["rango"]
    desde, hasta = rango["desde"], rango["hasta"]
    if desde and hasta:
        base = f"foundia-reporte_{desde}_{hasta}"
    elif desde:
        base = f"foundia-reporte_desde-{desde}"
    elif hasta:
        base = f"foundia-reporte_hasta-{hasta}"
    else:
        base = f"foundia-reporte_completo-{emitido.strftime('%Y-%m-%d')}"
    return f"{base}.{extension}"


def _texto_rango(rango):
    desde, hasta = rango["desde"], rango["hasta"]
    if desde and hasta:
        return f"Del {_fecha_corta(desde)} al {_fecha_corta(hasta)}"
    if desde:
        return f"Desde el {_fecha_corta(desde)}"
    if hasta:
        return f"Hasta el {_fecha_corta(hasta)}"
    return "Todo el historial"


def _fecha_corta(iso):
    anio, mes, dia = str(iso).split("-")
    return f"{dia}/{mes}/{anio}"


def _numero(valor):
    return SIN_DATO if valor is None else valor


def _encabezado(hoja, fila, titulo, columnas):
    hoja.cell(row=fila, column=1, value=titulo).font = Font(bold=True, size=12, color="12355B")
    fila += 1
    for columna, texto in enumerate(columnas, start=1):
        celda = hoja.cell(row=fila, column=columna, value=texto)
        celda.font = Font(bold=True, color="FFFFFF")
        celda.fill = PatternFill("solid", fgColor="12355B")
        celda.alignment = Alignment(horizontal="center")
    return fila + 1


def _anchos(hoja, anchos):
    for indice, ancho in enumerate(anchos, start=1):
        hoja.column_dimensions[get_column_letter(indice)].width = ancho


def excel_reporte(datos, responsable, emitido):
    """Libro XLSX con resumen, rankings y estado actual del sistema."""
    libro = Workbook()
    hoja = libro.active
    hoja.title = "Resumen"
    _anchos(hoja, [32, 26, 18, 18])

    hoja["A1"] = "FoundIA · Reporte del tablero"
    hoja["A1"].font = Font(bold=True, size=16, color="12355B")
    metadatos = [
        ("Periodo", _texto_rango(datos["rango"])),
        ("Campo del periodo", "Fecha de publicación"),
        ("Zona horaria", datos["rango"]["zonaHoraria"]),
        ("Emitido", emitido.strftime("%d/%m/%Y %H:%M")),
        ("Responsable", responsable),
    ]
    for indice, (etiqueta, valor) in enumerate(metadatos, start=3):
        hoja.cell(row=indice, column=1, value=etiqueta).font = Font(bold=True)
        hoja.cell(row=indice, column=2, value=valor)

    fila = 9
    fila = _encabezado(hoja, fila, "Objetos del periodo", ["Indicador", "Valor"])
    objetos = datos["objetos"]
    for etiqueta, clave in (("Perdidos", "perdidos"), ("Encontrados", "encontrados"),
                            ("Recuperados", "recuperados"), ("Activos", "activos")):
        hoja.cell(row=fila, column=1, value=etiqueta)
        hoja.cell(row=fila, column=2, value=objetos[clave])
        fila += 1

    fila += 1
    fila = _encabezado(hoja, fila, "Tiempo de recuperación",
                       ["Indicador", "Valor", "Unidad"])
    recuperacion = datos["recuperacion"]
    for etiqueta, clave, unidad in (
            ("Promedio", "promedioHoras", "horas"),
            ("Promedio", "promedioDias", "días"),
            ("Mediana", "medianaHoras", "horas"),
            ("Mínimo", "minimoHoras", "horas"),
            ("Máximo", "maximoHoras", "horas"),
            ("Muestra", "muestra", "recuperaciones"),
            ("Sin fecha", "sinFecha", "recuperaciones"),
            ("Fechas incoherentes", "fechasIncoherentes", "registros")):
        hoja.cell(row=fila, column=1, value=etiqueta)
        hoja.cell(row=fila, column=2, value=_numero(recuperacion[clave]))
        hoja.cell(row=fila, column=3, value=unidad)
        fila += 1

    fila += 1
    fila = _encabezado(hoja, fila, "Estado actual del sistema",
                       ["Indicador", "Valor"])
    for etiqueta, clave in (("Usuarios registrados", "usuarios"),
                            ("Objetos activos", "objetosActivos"),
                            ("Reportes pendientes", "reportesPendientes"),
                            ("Categorías", "categorias")):
        hoja.cell(row=fila, column=1, value=etiqueta)
        hoja.cell(row=fila, column=2, value=datos[clave])
        fila += 1
    hoja.cell(row=fila + 1, column=1,
              value="Las cifras del periodo usan fecha de publicación; el estado actual no se filtra.")

    categorias = libro.create_sheet("Categorías")
    _anchos(categorias, [10, 34, 14, 14, 16])
    fila = _encabezado(categorias, 1, "Categorías con más publicaciones",
                       ["Puesto", "Categoría", "Total", "Perdidos", "Encontrados"])
    if datos["categoriasTop"]:
        for puesto, categoria in enumerate(datos["categoriasTop"], start=1):
            for columna, valor in enumerate(
                    (puesto, categoria["nombre"], categoria["total"],
                     categoria["perdidos"], categoria["encontrados"]), start=1):
                categorias.cell(row=fila, column=columna, value=valor)
            fila += 1
    else:
        categorias.cell(row=fila, column=2, value="Sin publicaciones en el periodo")

    ubicaciones = libro.create_sheet("Ubicaciones")
    _anchos(ubicaciones, [10, 34, 14, 14, 16])
    fila = _encabezado(ubicaciones, 1, "Ubicaciones con más publicaciones",
                       ["Puesto", "Ubicación", "Total", "Perdidos", "Encontrados"])
    if datos["ubicacionesTop"]:
        for puesto, ubicacion in enumerate(datos["ubicacionesTop"], start=1):
            for columna, valor in enumerate(
                    (puesto, ubicacion["nombre"], ubicacion["total"],
                     ubicacion["perdidos"], ubicacion["encontrados"]), start=1):
                ubicaciones.cell(row=fila, column=columna, value=valor)
            fila += 1
    else:
        ubicaciones.cell(row=fila, column=2, value="Sin publicaciones en el periodo")
    ubicaciones.cell(row=fila + 1, column=2,
                     value=f"Publicaciones sin ubicación: {datos['sinUbicacion']}")

    buffer = io.BytesIO()
    libro.save(buffer)
    return buffer.getvalue()


def _tabla_pdf(filas, anchos, alineaciones=None):
    tabla = Table(filas, colWidths=anchos, repeatRows=1)
    estilo = [
        ("BACKGROUND", (0, 0), (-1, 0), AZUL),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("FONTNAME", (0, 0), (-1, 0), "Helvetica-Bold"),
        ("FONTNAME", (0, 1), (-1, -1), "Helvetica"),
        ("FONTSIZE", (0, 0), (-1, -1), 8.5),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("GRID", (0, 0), (-1, -1), 0.4, BORDE),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, GRIS]),
        ("LEFTPADDING", (0, 0), (-1, -1), 5),
        ("RIGHTPADDING", (0, 0), (-1, -1), 5),
        ("TOPPADDING", (0, 0), (-1, -1), 4),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
    ]
    if alineaciones:
        estilo.extend(alineaciones)
    tabla.setStyle(TableStyle(estilo))
    return tabla


def _pie_pagina(canvas, documento):
    canvas.saveState()
    canvas.setFont("Helvetica", 7.5)
    canvas.setFillColor(colors.HexColor("#555555"))
    canvas.drawString(2 * cm, 1.1 * cm, "FoundIA · Reporte del tablero")
    canvas.drawRightString(A4[0] - 2 * cm, 1.1 * cm, f"Página {documento.page}")
    canvas.restoreState()


def pdf_reporte(datos, responsable, emitido):
    """PDF A4 con tablas, fecha de emisión y responsable."""
    buffer = io.BytesIO()
    documento = SimpleDocTemplate(
        buffer,
        pagesize=A4,
        leftMargin=2 * cm,
        rightMargin=2 * cm,
        topMargin=2 * cm,
        bottomMargin=2 * cm,
        title="FoundIA · Reporte del tablero",
        author=responsable,
        pageCompression=0,
    )
    estilos = getSampleStyleSheet()
    titulo = ParagraphStyle(
        "TituloH32", parent=estilos["Title"], fontSize=17,
        textColor=AZUL, spaceAfter=4,
    )
    subtitulo = ParagraphStyle(
        "SubtituloH32", parent=estilos["Normal"], fontSize=9.5,
        textColor=colors.HexColor("#555555"), spaceAfter=12,
    )
    seccion = ParagraphStyle(
        "SeccionH32", parent=estilos["Heading2"], fontSize=11.5,
        textColor=AZUL, spaceBefore=12, spaceAfter=6,
    )
    normal = estilos["Normal"]

    elementos = [
        Paragraph("Reporte del tablero", titulo),
        Paragraph(
            "FoundIA · Plataforma de objetos perdidos y encontrados. "
            "Indicadores del periodo y estado actual del sistema.",
            subtitulo,
        ),
        _tabla_pdf([
            ["Periodo", _texto_rango(datos["rango"])],
            ["Campo del periodo", "Fecha de publicación"],
            ["Zona horaria", datos["rango"]["zonaHoraria"]],
            ["Emitido", emitido.strftime("%d/%m/%Y %H:%M")],
            ["Responsable", responsable],
        ], [4.2 * cm, 12.6 * cm]),
        Paragraph("Objetos del periodo", seccion),
    ]

    objetos = datos["objetos"]
    elementos.append(_tabla_pdf([
        ["Indicador", "Valor"],
        ["Perdidos", str(objetos["perdidos"])],
        ["Encontrados", str(objetos["encontrados"])],
        ["Recuperados", str(objetos["recuperados"])],
        ["Activos", str(objetos["activos"])],
    ], [11.2 * cm, 5.6 * cm], [("ALIGN", (1, 0), (1, -1), "RIGHT")]))

    recuperacion = datos["recuperacion"]
    elementos.append(Paragraph("Tiempo de recuperación", seccion))
    elementos.append(_tabla_pdf([
        ["Indicador", "Valor", "Unidad"],
        ["Promedio", str(_numero(recuperacion["promedioHoras"])), "horas"],
        ["Promedio", str(_numero(recuperacion["promedioDias"])), "días"],
        ["Mediana", str(_numero(recuperacion["medianaHoras"])), "horas"],
        ["Mínimo", str(_numero(recuperacion["minimoHoras"])), "horas"],
        ["Máximo", str(_numero(recuperacion["maximoHoras"])), "horas"],
        ["Muestra", str(recuperacion["muestra"]), "recuperaciones"],
        ["Sin fecha", str(recuperacion["sinFecha"]), "recuperaciones"],
        ["Fechas incoherentes", str(recuperacion["fechasIncoherentes"]), "registros"],
    ], [6.8 * cm, 5.0 * cm, 5.0 * cm], [("ALIGN", (1, 0), (1, -1), "RIGHT")]))

    elementos.append(Paragraph("Estado actual del sistema", seccion))
    elementos.append(_tabla_pdf([
        ["Indicador", "Valor"],
        ["Usuarios registrados", str(datos["usuarios"])],
        ["Objetos activos", str(datos["objetosActivos"])],
        ["Reportes pendientes", str(datos["reportesPendientes"])],
        ["Categorías", str(datos["categorias"])],
    ], [11.2 * cm, 5.6 * cm], [("ALIGN", (1, 0), (1, -1), "RIGHT")]))
    elementos.append(Spacer(1, 4))
    elementos.append(Paragraph(
        "Las cifras del periodo usan fecha de publicación; el estado actual no se filtra.",
        ParagraphStyle("NotaH32", parent=normal, fontSize=8,
                       textColor=colors.HexColor("#555555")),
    ))

    elementos.append(PageBreak())
    elementos.append(Paragraph("Categorías con más publicaciones", seccion))
    filas = [["Puesto", "Categoría", "Total", "Perdidos", "Encontrados"]]
    if datos["categoriasTop"]:
        for puesto, categoria in enumerate(datos["categoriasTop"], start=1):
            filas.append([str(puesto), categoria["nombre"], str(categoria["total"]),
                          str(categoria["perdidos"]), str(categoria["encontrados"])])
    else:
        filas.append(["—", "Sin publicaciones en el periodo", "0", "0", "0"])
    elementos.append(_tabla_pdf(
        filas, [2.0 * cm, 8.0 * cm, 2.2 * cm, 2.3 * cm, 2.3 * cm],
        [("ALIGN", (2, 0), (-1, -1), "RIGHT")]))

    elementos.append(Paragraph("Ubicaciones con más publicaciones", seccion))
    filas = [["Puesto", "Ubicación", "Total", "Perdidos", "Encontrados"]]
    if datos["ubicacionesTop"]:
        for puesto, ubicacion in enumerate(datos["ubicacionesTop"], start=1):
            filas.append([str(puesto), ubicacion["nombre"], str(ubicacion["total"]),
                          str(ubicacion["perdidos"]), str(ubicacion["encontrados"])])
    else:
        filas.append(["—", "Sin publicaciones en el periodo", "0", "0", "0"])
    elementos.append(_tabla_pdf(
        filas, [2.0 * cm, 8.0 * cm, 2.2 * cm, 2.3 * cm, 2.3 * cm],
        [("ALIGN", (2, 0), (-1, -1), "RIGHT")]))
    elementos.append(Spacer(1, 6))
    elementos.append(Paragraph(
        f"Publicaciones sin ubicación: {datos['sinUbicacion']}", normal))

    documento.build(elementos, onFirstPage=_pie_pagina, onLaterPages=_pie_pagina)
    return buffer.getvalue()
