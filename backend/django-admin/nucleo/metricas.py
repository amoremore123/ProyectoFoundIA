"""Indicadores públicos agregados, disponibles únicamente para administradores.

H29: conteos por tipo y estado.
H30: tiempo promedio de recuperación de los objetos perdidos.
H31: categorías y ubicaciones con más publicaciones (reportes de objetos).
"""
import re
from collections import Counter

from django.db.models import Count, Q
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


ESTADOS_VISIBLES = ("ACTIVO", "RECUPERADO")
TOP_RANKING = 5
SEGUNDOS_POR_HORA = 3600


def tiempo_recuperacion(objetos):
    """H30: promedio entre la publicación y la confirmación de recuperación.

    Solo cuenta objetos PERDIDOS en estado RECUPERADO con fecha de recuperación.
    Los recuperados antiguos sin fecha no se inventan: se informan en `sinFecha`.
    Una fecha de recuperación anterior a la publicación es un dato incoherente y
    también queda fuera del promedio (`fechasIncoherentes`).
    """
    recuperados = objetos.filter(tipo="PERDIDO", estado="RECUPERADO")
    duraciones = []
    sin_fecha = 0
    incoherentes = 0
    for publicado, recuperado in recuperados.values_list("fecha_publicacion", "fecha_recuperacion"):
        if recuperado is None or publicado is None:
            sin_fecha += 1
            continue
        segundos = (recuperado - publicado).total_seconds()
        if segundos < 0:
            incoherentes += 1
            continue
        duraciones.append(segundos)

    if duraciones:
        horas = sorted(d / SEGUNDOS_POR_HORA for d in duraciones)
        promedio = sum(horas) / len(horas)
        mitad = len(horas) // 2
        mediana = horas[mitad] if len(horas) % 2 else (horas[mitad - 1] + horas[mitad]) / 2
        resumen = {"promedioHoras": round(promedio, 1), "promedioDias": round(promedio / 24, 1),
                   "medianaHoras": round(mediana, 1), "minimoHoras": round(horas[0], 1),
                   "maximoHoras": round(horas[-1], 1)}
    else:
        resumen = {"promedioHoras": None, "promedioDias": None, "medianaHoras": None,
                   "minimoHoras": None, "maximoHoras": None}
    return {**resumen, "muestra": len(duraciones), "sinFecha": sin_fecha,
            "fechasIncoherentes": incoherentes}


def categorias_con_mas_reportes(objetos, limite=TOP_RANKING):
    """H31: categorías ordenadas por cantidad de publicaciones visibles."""
    filas = (objetos.values("categoria_id", "categoria__nombre")
             .annotate(total=Count("id"),
                       perdidos=Count("id", filter=Q(tipo="PERDIDO")),
                       encontrados=Count("id", filter=Q(tipo="ENCONTRADO")))
             .order_by("-total", "categoria__nombre")[:limite])
    return [{"id": f["categoria_id"], "nombre": f["categoria__nombre"], "total": f["total"],
             "perdidos": f["perdidos"], "encontrados": f["encontrados"]} for f in filas]


def _clave_ubicacion(texto):
    """Agrupa "Biblioteca", " biblioteca " y "BIBLIOTECA" como el mismo lugar."""
    return re.sub(r"\s+", " ", texto).strip().casefold()


def ubicaciones_con_mas_reportes(objetos, limite=TOP_RANKING):
    """H31: la ubicación es texto libre; se normaliza para contar y se muestra
    la forma escrita más frecuente. Las publicaciones sin ubicación se cuentan aparte."""
    grupos = {}
    sin_ubicacion = 0
    for ubicacion, tipo in objetos.order_by("fecha_publicacion", "id").values_list("ubicacion", "tipo"):
        clave = _clave_ubicacion(ubicacion or "")
        if not clave:
            sin_ubicacion += 1
            continue
        grupo = grupos.setdefault(clave, {"formas": Counter(), "total": 0, "perdidos": 0, "encontrados": 0})
        grupo["formas"][re.sub(r"\s+", " ", ubicacion).strip()] += 1
        grupo["total"] += 1
        if tipo == "PERDIDO":
            grupo["perdidos"] += 1
        elif tipo == "ENCONTRADO":
            grupo["encontrados"] += 1

    def nombre(grupo):
        # La forma más usada; ante empate, la que se escribió primero (resultado estable)
        return max(grupo["formas"].items(), key=lambda par: par[1])[0]

    ordenados = sorted(grupos.values(), key=lambda g: (-g["total"], nombre(g).casefold()))[:limite]
    lista = [{"nombre": nombre(g), "total": g["total"], "perdidos": g["perdidos"],
              "encontrados": g["encontrados"]} for g in ordenados]
    return lista, sin_ubicacion


def metricas_dashboard(params):
    if set(params) - {"desde", "hasta"}:
        raise ValidationError("Filtros admitidos: desde y hasta (AAAA-MM-DD).")
    serializer = RangoMetricasSerializer(data=params)
    serializer.is_valid(raise_exception=True)
    rango = serializer.validated_data
    objetos = Objeto.objects.filter(estado__in=ESTADOS_VISIBLES)
    if "desde" in rango:
        objetos = objetos.filter(fecha_publicacion__date__gte=rango["desde"])
    if "hasta" in rango:
        objetos = objetos.filter(fecha_publicacion__date__lte=rango["hasta"])
    ubicaciones, sin_ubicacion = ubicaciones_con_mas_reportes(objetos)
    return {
        "rango": {"desde": rango["desde"].isoformat() if "desde" in rango else None,
                  "hasta": rango["hasta"].isoformat() if "hasta" in rango else None,
                  "campo": "fecha_publicacion", "zonaHoraria": "UTC"},
        "objetos": {"perdidos": objetos.filter(tipo="PERDIDO").count(),
                    "encontrados": objetos.filter(tipo="ENCONTRADO").count(),
                    "recuperados": objetos.filter(tipo="PERDIDO", estado="RECUPERADO").count(),
                    "activos": objetos.filter(estado="ACTIVO").count()},
        "recuperacion": tiempo_recuperacion(objetos),
        "categoriasTop": categorias_con_mas_reportes(objetos),
        "ubicacionesTop": ubicaciones,
        "sinUbicacion": sin_ubicacion,
    }
