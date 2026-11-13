"""H54: disposición final de objetos no reclamados (donado o descartado) y su acta en PDF.

Reglas:
- Solo se dispone de custodias EN_CUSTODIA cuyo plazo (CUSTODIA_PLAZO_DIAS) ya venció.
- Si hay un reclamo ACEPTADO (con QR de retiro en curso) no se dispone: el dueño está por recogerlo.
- Nada se borra: la custodia pasa a DISPUESTO, el objeto a OCULTO y queda un acta con responsable y fecha.
"""
import io
import os
from datetime import timedelta

from django.utils import timezone
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm, mm
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

DESTINOS = {"DONADO": "Donado", "DESCARTADO": "Descartado"}
MOTIVO_MIN = 10
MOTIVO_MAX = 1000
PLAZO_POR_DEFECTO = 60


def plazo_dias() -> int:
    """Plazo de custodia en días (variable CUSTODIA_PLAZO_DIAS; mínimo 1)."""
    try:
        valor = int(os.environ.get("CUSTODIA_PLAZO_DIAS", PLAZO_POR_DEFECTO))
    except (TypeError, ValueError):
        valor = PLAZO_POR_DEFECTO
    return max(1, valor)


def limite_vencimiento(ahora=None):
    """Las custodias recibidas en o antes de esta fecha ya vencieron."""
    return (ahora or timezone.now()) - timedelta(days=plazo_dias())


def dias_en_custodia(custodia, ahora=None) -> int:
    return max(0, ((ahora or timezone.now()) - custodia.fecha_recepcion).days)


def codigo_acta(custodia) -> str:
    return f"ACTA-{custodia.codigo}"


def _fecha_local(valor) -> str:
    if valor is None:
        return "—"
    if timezone.is_aware(valor):
        valor = timezone.localtime(valor)
    return valor.strftime("%d/%m/%Y %H:%M")


def acta_pdf(disposicion) -> bytes:
    """Acta A4 de disposición final. No incluye correo ni teléfono de nadie."""
    custodia = disposicion.custodia
    objeto = custodia.objeto
    buffer = io.BytesIO()
    documento = SimpleDocTemplate(
        buffer, pagesize=A4, leftMargin=2 * cm, rightMargin=2 * cm, topMargin=2 * cm, bottomMargin=2 * cm,
        title=f"Acta {disposicion.codigo_acta}",
    )
    estilos = getSampleStyleSheet()
    titulo = ParagraphStyle("TituloActa", parent=estilos["Title"], fontSize=17,
                            textColor=colors.HexColor("#12355B"), spaceAfter=4)
    subtitulo = ParagraphStyle("SubtituloActa", parent=estilos["Normal"], fontSize=10,
                               textColor=colors.HexColor("#555555"), spaceAfter=14)
    normal = estilos["Normal"]
    celda = ParagraphStyle("CeldaActa", parent=normal, fontSize=8.5, leading=10.5)

    responsable = f"{disposicion.responsable.nombre} {disposicion.responsable.apellido}".strip()
    datos = [
        ["Acta", disposicion.codigo_acta],
        ["Código de custodia", custodia.codigo],
        ["Objeto", Paragraph(objeto.nombre, celda)],
        ["Categoría", objeto.categoria.nombre if objeto.categoria else "—"],
        ["Descripción", Paragraph(objeto.descripcion or "—", celda)],
        ["Recibido el", _fecha_local(custodia.fecha_recepcion)],
        ["Días en custodia", f"{disposicion.dias_en_custodia} (plazo: {disposicion.plazo_dias} días)"],
        ["Destino", DESTINOS.get(disposicion.destino, disposicion.destino)],
        ["Motivo", Paragraph(disposicion.motivo, celda)],
        ["Responsable", responsable],
        ["Fecha de disposición", _fecha_local(disposicion.fecha_disposicion)],
    ]
    tabla = Table(datos, colWidths=[4.2 * cm, 11.6 * cm])
    tabla.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#E8EEF5")),
        ("TEXTCOLOR", (0, 0), (0, -1), colors.HexColor("#12355B")),
        ("FONTNAME", (0, 0), (0, -1), "Helvetica-Bold"),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#B8C7D9")),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    firma = Table([["", ""], ["Firma del responsable", "Firma de quien recibe el objeto"]],
                  colWidths=[7.9 * cm, 7.9 * cm])
    firma.setStyle(TableStyle([
        ("LINEABOVE", (0, 0), (0, 0), 0.8, colors.black),
        ("LINEABOVE", (1, 0), (1, 0), 0.8, colors.black),
        ("TOPPADDING", (0, 0), (-1, 0), 26),
        ("FONTSIZE", (0, 1), (-1, 1), 8),
        ("TEXTCOLOR", (0, 1), (-1, 1), colors.HexColor("#555555")),
    ]))
    documento.build([
        Paragraph("Acta de disposición final de objeto", titulo),
        Paragraph("FoundIA · El objeto cumplió el plazo de custodia sin ser reclamado. "
                  "El registro se conserva; nada se elimina del sistema.", subtitulo),
        tabla,
        Spacer(1, 18 * mm),
        firma,
    ])
    return buffer.getvalue()
