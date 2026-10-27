"""Indicadores públicos agregados, disponibles únicamente para administradores."""
from rest_framework import serializers
from rest_framework.exceptions import ValidationError

from .models import Objeto


class RangoMetricasSerializer(serializers.Serializer):
    desde = serializers.DateField(required=False)
    hasta = serializers.DateField(required=False)

    def validate(self, attrs):
        if attrs.get("desde") and attrs.get("hasta") and attrs["desde"] > attrs["hasta"]:
            raise serializers.ValidationError("La fecha desde no puede ser posterior a hasta.")
        return attrs


def metricas_dashboard(params):
    if set(params) - {"desde", "hasta"}:
        raise ValidationError("Filtros admitidos: desde y hasta (AAAA-MM-DD).")
    serializer = RangoMetricasSerializer(data=params)
    serializer.is_valid(raise_exception=True)
    rango = serializer.validated_data
    objetos = Objeto.objects.filter(estado__in=("ACTIVO", "RECUPERADO"))
    if "desde" in rango:
        objetos = objetos.filter(fecha_publicacion__date__gte=rango["desde"])
    if "hasta" in rango:
        objetos = objetos.filter(fecha_publicacion__date__lte=rango["hasta"])
    return {
        "rango": {"desde": rango["desde"].isoformat() if "desde" in rango else None,
                  "hasta": rango["hasta"].isoformat() if "hasta" in rango else None,
                  "campo": "fecha_publicacion", "zonaHoraria": "UTC"},
        "objetos": {"perdidos": objetos.filter(tipo="PERDIDO").count(),
                    "encontrados": objetos.filter(tipo="ENCONTRADO").count(),
                    "recuperados": objetos.filter(tipo="PERDIDO", estado="RECUPERADO").count(),
                    "activos": objetos.filter(estado="ACTIVO").count()},
    }
