"""H56: resolver los reportes de mensajes del chat (reglas de H28 y suspensión de H15)."""
from django.db import transaction
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import serializers, status
from rest_framework.exceptions import ValidationError
from rest_framework.response import Response

from .firebase import revocar_al_confirmar as revocar_firebase
from .models import Dispositivo, Notificacion, ReporteMensaje, Usuario
from .resolucion_reportes import ConflictoResolucion


class ResolverMensajeSerializer(serializers.Serializer):
    accion = serializers.ChoiceField(choices=tuple(ReporteMensaje.ACCIONES))
    resolucion = serializers.CharField(min_length=3, max_length=1000, allow_blank=False, trim_whitespace=True)


def _advertir(autor, resolucion):
    # No se menciona quién reportó: solo la advertencia y su motivo
    Notificacion.objects.create(
        usuario=autor,
        titulo="Advertencia por un mensaje del chat",
        mensaje=("Un mensaje tuyo en el chat fue reportado y revisado por administración. "
                 f"Motivo: {resolucion} Respeta las normas de convivencia de FoundIA."),
        tipo="SISTEMA",
    )


@transaction.atomic
def resolver_reporte_mensaje(request, pk):
    if not isinstance(request.data, dict) or set(request.data) != {"accion", "resolucion"}:
        raise ValidationError("Solo se admiten accion y resolucion; el responsable y la fecha los registra el servidor.")
    datos = ResolverMensajeSerializer(data=request.data)
    datos.is_valid(raise_exception=True)
    accion = datos.validated_data["accion"]
    texto = datos.validated_data["resolucion"]

    reporte = get_object_or_404(
        ReporteMensaje.objects.select_for_update().select_related("mensaje__autor"), pk=pk)
    autor_id = reporte.mensaje.autor_id

    if reporte.estado != "PENDIENTE":
        # Repetir exactamente lo mismo es seguro; otra resolución, no (como H28)
        if reporte.accion != accion or reporte.resolucion != texto:
            raise ConflictoResolucion()
        return Response(_respuesta(reporte, repetido=True))

    if accion == "SUSPENDER" and autor_id == request.user.pk:
        return Response({"detail": "No puedes suspender tu propia cuenta de administrador."},
                        status=status.HTTP_400_BAD_REQUEST)

    if accion == "ADVERTIR":
        _advertir(reporte.mensaje.autor, texto)
    elif accion == "SUSPENDER":
        autor = Usuario.objects.select_for_update().get(pk=autor_id)
        if autor.estado != "SUSPENDIDO":
            autor.estado = "SUSPENDIDO"
            autor.save(update_fields=["estado", "fecha_actualizacion"])
        Dispositivo.objects.filter(usuario_id=autor.pk).delete()  # H57: sin push para una cuenta suspendida
        revocar_firebase(autor.pk)  # H72
        # La respuesta debe mostrar al autor ya suspendido, no la copia leída antes
        reporte.mensaje.autor = autor

    reporte.estado = ReporteMensaje.ACCIONES[accion]
    reporte.accion = accion
    reporte.resolucion = texto
    reporte.resuelto_por = request.user
    reporte.fecha_resolucion = timezone.now().replace(microsecond=0)
    reporte.save(update_fields=["estado", "accion", "resolucion", "resuelto_por", "fecha_resolucion"])
    return Response(_respuesta(reporte, repetido=False))


def _respuesta(reporte, repetido):
    from .serializers import ReporteMensajeSerializer
    datos = ReporteMensajeSerializer(reporte).data
    datos["repetido"] = repetido
    return datos
