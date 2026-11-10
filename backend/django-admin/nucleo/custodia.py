"""H41: token QR firmado y documentos PDF de custodia.

El QR no contiene datos personales: solo el código de custodia firmado con
HMAC-SHA-256 (JWT HS256) usando el mismo secreto del backend. La etiqueta se
imprime y la constancia se entrega a quien deja el objeto.
"""
import io
from datetime import datetime, timezone as dt_timezone

import jwt
import qrcode
from django.utils import timezone
from reportlab.lib import colors
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import cm, mm
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas
from reportlab.platypus import Paragraph, SimpleDocTemplate, Spacer, Table, TableStyle

from .authentication import get_jwt_secret

TIPO_TOKEN_CUSTODIA = "custodia"


def firmar_token_custodia(custodia) -> str:
    """Token firmado que viaja en el QR. No incluye nombre, correo ni teléfono."""
    return jwt.encode(
        {
            "sub": custodia.codigo,
            "objeto": custodia.objeto_id,
            "tipo": TIPO_TOKEN_CUSTODIA,
            "iat": datetime.now(dt_timezone.utc),
        },
        get_jwt_secret(),
        "HS256",
    )


def verificar_token_custodia(token: str) -> dict:
    """Devuelve el contenido del token o lanza jwt.InvalidTokenError."""
    datos = jwt.decode(token, get_jwt_secret(), algorithms=["HS256"])
    if datos.get("tipo") != TIPO_TOKEN_CUSTODIA or not datos.get("sub"):
        raise jwt.InvalidTokenError("Token de custodia inválido")
    return datos


def _imagen_qr(token: str) -> ImageReader:
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=2)
    qr.add_data(token)
    qr.make(fit=True)
    imagen = qr.make_image(fill_color="black", back_color="white")
    buffer = io.BytesIO()
    imagen.save(buffer, format="PNG")
    buffer.seek(0)
    return ImageReader(buffer)


def _fecha_local(valor) -> str:
    if valor is None:
        return "—"
    if timezone.is_aware(valor):
        valor = timezone.localtime(valor)
    return valor.strftime("%d/%m/%Y %H:%M")


def etiqueta_pdf(custodia) -> bytes:
    """Etiqueta de 10 x 7 cm con código, QR y datos del objeto (sin datos personales)."""
    buffer = io.BytesIO()
    ancho, alto = 10 * cm, 7 * cm
    pdf = canvas.Canvas(buffer, pagesize=(ancho, alto))
    pdf.setTitle(f"Etiqueta {custodia.codigo}")

    pdf.setFillColor(colors.HexColor("#12355B"))
    pdf.rect(0, alto - 1.35 * cm, ancho, 1.35 * cm, fill=1, stroke=0)
    pdf.setFillColor(colors.white)
    pdf.setFont("Helvetica-Bold", 12)
    pdf.drawString(0.5 * cm, alto - 0.9 * cm, "FoundIA · Custodia")

    qr = _imagen_qr(custodia.token_qr)
    lado = 4.6 * cm
    pdf.drawImage(qr, 0.5 * cm, 0.55 * cm, width=lado, height=lado)

    pdf.setFillColor(colors.HexColor("#12355B"))
    pdf.setFont("Helvetica-Bold", 13)
    pdf.drawString(5.4 * cm, alto - 2.35 * cm, custodia.codigo)

    pdf.setFillColor(colors.black)
    pdf.setFont("Helvetica", 8)
    pdf.drawString(5.4 * cm, alto - 3.05 * cm, "Objeto en custodia")
    nombre = (custodia.objeto.nombre or "")[:34]
    pdf.setFont("Helvetica-Bold", 8)
    pdf.drawString(5.4 * cm, alto - 3.55 * cm, nombre)
    pdf.setFont("Helvetica", 7)
    pdf.drawString(5.4 * cm, alto - 4.15 * cm, f"Recibido: {_fecha_local(custodia.fecha_recepcion)}")
    pdf.drawString(5.4 * cm, alto - 4.65 * cm, "Escanee el QR para verificar")
    pdf.drawString(5.4 * cm, alto - 5.15 * cm, "Sin datos personales")

    pdf.showPage()
    pdf.save()
    return buffer.getvalue()


def constancia_pdf(custodia) -> bytes:
    """Constancia A4 de recepción para quien entrega el objeto."""
    buffer = io.BytesIO()
    documento = SimpleDocTemplate(
        buffer,
        pagesize=A4,
        leftMargin=2 * cm,
        rightMargin=2 * cm,
        topMargin=2 * cm,
        bottomMargin=2 * cm,
        title=f"Constancia {custodia.codigo}",
    )
    estilos = getSampleStyleSheet()
    titulo = ParagraphStyle(
        "TituloFoundIA", parent=estilos["Title"], fontSize=17,
        textColor=colors.HexColor("#12355B"), spaceAfter=4,
    )
    subtitulo = ParagraphStyle(
        "SubtituloFoundIA", parent=estilos["Normal"], fontSize=10,
        textColor=colors.HexColor("#555555"), spaceAfter=14,
    )
    normal = estilos["Normal"]
    celda = ParagraphStyle("CeldaFoundIA", parent=normal, fontSize=8.5, leading=10.5)

    objeto = custodia.objeto
    categoria = objeto.categoria.nombre if objeto.categoria else "—"
    responsable = f"{custodia.recibido_por.nombre} {custodia.recibido_por.apellido}".strip()

    datos = [
        ["Código de custodia", custodia.codigo],
        ["Objeto", Paragraph(objeto.nombre, celda)],
        ["Categoría", categoria],
        ["Descripción", Paragraph(objeto.descripcion or "—", celda)],
        ["Ubicación registrada", Paragraph(objeto.ubicacion or "—", celda)],
        ["Fecha de recepción", _fecha_local(custodia.fecha_recepcion)],
        ["Recibido por", responsable],
        ["Observaciones", Paragraph(custodia.observaciones or "—", celda)],
    ]
    tabla = Table(datos, colWidths=[4.2 * cm, 11.6 * cm])
    tabla.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (0, -1), colors.HexColor("#E8EEF5")),
        ("TEXTCOLOR", (0, 0), (0, -1), colors.HexColor("#12355B")),
        ("FONTNAME", (0, 0), (0, -1), "Helvetica-Bold"),
        ("FONTNAME", (1, 0), (1, -1), "Helvetica"),
        ("FONTSIZE", (0, 0), (-1, -1), 9),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("GRID", (0, 0), (-1, -1), 0.4, colors.HexColor("#B8C7D9")),
        ("LEFTPADDING", (0, 0), (-1, -1), 6),
        ("RIGHTPADDING", (0, 0), (-1, -1), 6),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))

    firma = Table(
        [["", ""], ["Firma de quien entrega", "Firma del responsable"]],
        colWidths=[7.9 * cm, 7.9 * cm],
    )
    firma.setStyle(TableStyle([
        ("LINEABOVE", (0, 0), (0, 0), 0.8, colors.black),
        ("LINEABOVE", (1, 0), (1, 0), 0.8, colors.black),
        ("TOPPADDING", (0, 0), (-1, 0), 26),
        ("FONTSIZE", (0, 1), (-1, 1), 8),
        ("TEXTCOLOR", (0, 1), (-1, 1), colors.HexColor("#555555")),
    ]))

    elementos = [
        Paragraph("Constancia de recepción de objeto", titulo),
        Paragraph(
            "FoundIA · Plataforma de objetos perdidos y encontrados. "
            "Este documento acredita la entrega del objeto para su custodia.",
            subtitulo,
        ),
        tabla,
        Spacer(1, 18 * mm),
        Paragraph(
            "El objeto queda bajo custodia de la biblioteca. Para retirarlo se "
            "debe presentar la constancia o el código de retiro vigente.",
            normal,
        ),
        Spacer(1, 16 * mm),
        firma,
    ]
    documento.build(elementos)
    return buffer.getvalue()
