"""H43: token y PDF del QR de retiro (un solo uso, 7 días)."""
import io
import secrets
from datetime import timedelta

import qrcode
from django.utils import timezone
from reportlab.lib import colors
from reportlab.lib.pagesizes import A5
from reportlab.lib.units import cm
from reportlab.lib.utils import ImageReader
from reportlab.pdfgen import canvas

DIAS_VIGENCIA_RETIRO = 7


def generar_token_retiro() -> str:
    return secrets.token_urlsafe(32)


def fecha_expiracion_retiro():
    return (timezone.now() + timedelta(days=DIAS_VIGENCIA_RETIRO)).replace(microsecond=0)


def token_retiro_vigente(reclamo, ahora=None) -> bool:
    ahora = ahora or timezone.now()
    return bool(
        reclamo.qr_retiro_token
        and reclamo.qr_retiro_expira
        and reclamo.qr_retiro_usado_en is None
        and reclamo.qr_retiro_expira > ahora
        and reclamo.estado == "ACEPTADO"
    )


def _fecha(valor) -> str:
    if valor is None:
        return "—"
    if timezone.is_aware(valor):
        valor = timezone.localtime(valor)
    return valor.strftime("%d/%m/%Y %H:%M")


def _imagen_qr(token: str) -> ImageReader:
    qr = qrcode.QRCode(error_correction=qrcode.constants.ERROR_CORRECT_M, box_size=8, border=2)
    qr.add_data(token)
    qr.make(fit=True)
    imagen = qr.make_image(fill_color="black", back_color="white")
    buffer = io.BytesIO()
    imagen.save(buffer, format="PNG")
    buffer.seek(0)
    return ImageReader(buffer)


def qr_retiro_pdf(reclamo) -> bytes:
    """Comprobante de retiro con QR de un solo uso y fecha de vencimiento."""
    buffer = io.BytesIO()
    ancho, alto = A5
    pdf = canvas.Canvas(buffer, pagesize=A5)
    pdf.setTitle(f"Retiro {reclamo.objeto.nombre}")

    pdf.setFillColor(colors.HexColor("#12355B"))
    pdf.rect(0, alto - 1.6 * cm, ancho, 1.6 * cm, fill=1, stroke=0)
    pdf.setFillColor(colors.white)
    pdf.setFont("Helvetica-Bold", 14)
    pdf.drawString(1 * cm, alto - 1.05 * cm, "FoundIA · QR de retiro")

    qr = _imagen_qr(reclamo.qr_retiro_token)
    lado = 6.2 * cm
    pdf.drawImage(qr, 1 * cm, 3.2 * cm, width=lado, height=lado)

    pdf.setFillColor(colors.HexColor("#12355B"))
    pdf.setFont("Helvetica-Bold", 11)
    pdf.drawString(7.6 * cm, alto - 2.6 * cm, "Objeto")
    pdf.setFillColor(colors.black)
    pdf.setFont("Helvetica", 9)
    pdf.drawString(7.6 * cm, alto - 3.15 * cm, (reclamo.objeto.nombre or "")[:32])
    pdf.setFont("Helvetica-Bold", 10)
    pdf.drawString(7.6 * cm, alto - 4.0 * cm, "Reclamante")
    pdf.setFont("Helvetica", 9)
    pdf.drawString(7.6 * cm, alto - 4.5 * cm,
                   f"{reclamo.usuario.nombre} {reclamo.usuario.apellido}".strip()[:34])
    pdf.setFont("Helvetica-Bold", 10)
    pdf.drawString(7.6 * cm, alto - 5.4 * cm, "Vence")
    pdf.setFont("Helvetica", 9)
    pdf.drawString(7.6 * cm, alto - 5.9 * cm, _fecha(reclamo.qr_retiro_expira))
    pdf.setFont("Helvetica", 8)
    pdf.setFillColor(colors.HexColor("#555555"))
    pdf.drawString(7.6 * cm, alto - 7.0 * cm, "Un solo uso.")
    pdf.drawString(7.6 * cm, alto - 7.5 * cm, "Presenta este QR al retirar.")
    pdf.drawString(1 * cm, 1.7 * cm, f"Reclamo #{reclamo.id} · Válido hasta {_fecha(reclamo.qr_retiro_expira)}")
    pdf.drawString(1 * cm, 1.25 * cm, "FoundIA · Biblioteca")

    pdf.showPage()
    pdf.save()
    return buffer.getvalue()
