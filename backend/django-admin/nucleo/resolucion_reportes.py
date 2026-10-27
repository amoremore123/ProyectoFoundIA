from django.db import transaction
from django.shortcuts import get_object_or_404
from django.utils import timezone
from rest_framework import serializers
from rest_framework.exceptions import APIException, ValidationError
from rest_framework.response import Response

from .models import Reporte


class ConflictoResolucion(APIException):
    status_code = 409
    default_detail = "El reporte ya tiene una resolución diferente."


class ResolucionSerializer(serializers.Serializer):
    estado = serializers.ChoiceField(choices=("RESUELTO", "RECHAZADO"))
    resolucion = serializers.CharField(max_length=1000, allow_blank=False, trim_whitespace=True)


@transaction.atomic
def resolver_reporte(request, pk):
    if not isinstance(request.data, dict) or set(request.data) != {"estado", "resolucion"}:
        raise ValidationError("Solo se admiten estado y resolución; actor y fecha los registra el servidor.")
    serializer = ResolucionSerializer(data=request.data)
    serializer.is_valid(raise_exception=True)
    reporte = get_object_or_404(Reporte.objects.select_for_update(), pk=pk)
    estado = serializer.validated_data["estado"]
    texto = serializer.validated_data["resolucion"]
    repetido = False
    if reporte.estado not in ("PENDIENTE", "REVISADO"):
        if (reporte.estado != estado or reporte.resolucion != texto
                or not reporte.resuelto_por_id or not reporte.fecha_resolucion):
            raise ConflictoResolucion()
        repetido = True
    elif reporte.fecha_resolucion or reporte.resuelto_por_id or reporte.resolucion:
        raise ConflictoResolucion("El reporte tiene antecedentes incoherentes; requiere revisión.")
    else:
        reporte.estado = estado
        reporte.resolucion = texto
        reporte.resuelto_por = request.user
        reporte.fecha_resolucion = timezone.now().replace(microsecond=0)
        reporte.save(update_fields=["estado", "resolucion", "resuelto_por", "fecha_resolucion"])
    return Response({"id": reporte.pk, "estado": reporte.estado, "resolucion": reporte.resolucion,
                     "resueltoPorId": reporte.resuelto_por_id,
                     "fechaResolucion": serializers.DateTimeField().to_representation(reporte.fecha_resolucion),
                     "repetido": repetido})
