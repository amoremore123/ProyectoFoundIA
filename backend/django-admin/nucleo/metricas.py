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

from .models import Objeto, filtro_dias_lima


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
    # Días del calendario de Lima (antes se contaban en UTC)
    objetos = filtro_dias_lima(objetos, "fecha_publicacion", rango.get("desde"), rango.get("hasta"))
    ubicaciones, sin_ubicacion = ubicaciones_con_mas_reportes(objetos)
    return {
        "rango": {"desde": rango["desde"].isoformat() if "desde" in rango else None,
                  "hasta": rango["hasta"].isoformat() if "hasta" in rango else None,
                  "campo": "fecha_publicacion", "zonaHoraria": "America/Lima"},
        "objetos": {"perdidos": objetos.filter(tipo="PERDIDO").count(),
                    "encontrados": objetos.filter(tipo="ENCONTRADO").count(),
                    "recuperados": objetos.filter(tipo="PERDIDO", estado="RECUPERADO").count(),
                    "activos": objetos.filter(estado="ACTIVO").count()},
        "recuperacion": tiempo_recuperacion(objetos),
        "categoriasTop": categorias_con_mas_reportes(objetos),
        "ubicacionesTop": ubicaciones,
        "sinUbicacion": sin_ubicacion,
    }


MESES_IMPACTO = 6
_NOMBRES_MES = ("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic")


def _restar_meses(fecha, meses):
    total = fecha.year * 12 + (fecha.month - 1) - meses
    return fecha.replace(year=total // 12, month=total % 12 + 1, day=1)


def _promedio_dias_devolucion(devueltos):
    """Días entre la publicación y la devolución, para perdidos y encontrados (H30 mide solo perdidos)."""
    duraciones = [(r - p).total_seconds() for p, r in devueltos.values_list("fecha_publicacion", "fecha_recuperacion")
                  if p is not None and r is not None and r >= p]
    return round(sum(duraciones) / len(duraciones) / 86400, 1) if duraciones else None


def metricas_impacto(hoy=None):
    """Impacto de FoundIA: cuántos objetos volvieron a su dueño y por qué vía.

    - Devuelto = objeto en estado RECUPERADO con fecha de recuperación.
    - «En la biblioteca» = su custodia terminó RETIRADO (entrega verificada con QR, H44).
      El resto se resolvió entre personas (contacto, chat o en persona).
    - Solo conteos: ningún dato de quién perdió, encontró o retiró.
    """
    from datetime import timedelta

    from django.db.models.functions import TruncMonth
    from django.utils import timezone

    from .models import Custodia

    ahora = timezone.now()
    hoy = hoy or timezone.localdate()
    inicio = _restar_meses(hoy.replace(day=1), MESES_IMPACTO - 1)

    devueltos = Objeto.objects.filter(estado="RECUPERADO", fecha_recuperacion__isnull=False)
    biblioteca = Q(custodia__estado="RETIRADO")
    filas = (devueltos.filter(fecha_recuperacion__date__gte=inicio)
             .annotate(mes=TruncMonth("fecha_recuperacion"))
             .values("mes")
             .annotate(total=Count("id"), biblioteca=Count("id", filter=biblioteca)))
    por_clave = {f["mes"].strftime("%Y-%m"): f for f in filas}
    por_mes = []
    for i in range(MESES_IMPACTO):
        mes = _restar_meses(inicio, -i)
        clave = mes.strftime("%Y-%m")
        fila = por_clave.get(clave, {"total": 0, "biblioteca": 0})
        por_mes.append({
            "mes": clave, "etiqueta": _NOMBRES_MES[mes.month - 1],
            "total": fila["total"], "biblioteca": fila["biblioteca"],
            "personas": fila["total"] - fila["biblioteca"],
        })

    visibles = Objeto.objects.filter(estado__in=ESTADOS_VISIBLES)
    perdidos = visibles.filter(tipo="PERDIDO")
    encontrados = visibles.filter(tipo="ENCONTRADO")

    def porcentaje(parte, todo):
        return round(parte * 100 / todo, 1) if todo else None

    perdidos_recuperados = perdidos.filter(estado="RECUPERADO").count()
    encontrados_devueltos = encontrados.filter(estado="RECUPERADO").count()
    custodias = Custodia.objects.aggregate(
        enCustodia=Count("id", filter=Q(estado="EN_CUSTODIA")),
        entregadas=Count("id", filter=Q(estado="RETIRADO")),
        dispuestas=Count("id", filter=Q(estado="DISPUESTO")),
    )
    return {
        "devueltos": devueltos.count(),
        "devueltosUltimos30Dias": devueltos.filter(fecha_recuperacion__gte=ahora - timedelta(days=30)).count(),
        "enBiblioteca": devueltos.filter(biblioteca).count(),
        "porMes": por_mes,
        "tiempoPromedioDias": _promedio_dias_devolucion(devueltos),
        "perdidosRecuperadosPorcentaje": porcentaje(perdidos_recuperados, perdidos.count()),
        "encontradosDevueltosPorcentaje": porcentaje(encontrados_devueltos, encontrados.count()),
        "custodia": custodias,
    }


DECIMALES_MAPA = 4  # unos 11 m: agrupa lugares cercanos y no expone la ubicación exacta de nadie
TIPOS_MAPA = ("PERDIDO", "ENCONTRADO")


def metricas_mapa(params):
    """Mapa de pérdidas: lugares del campus con más objetos, agrupados por cercanía.

    Usa las coordenadas que se guardan al publicar (M4). Cuenta perdidos y encontrados por punto,
    con el nombre de lugar más usado ahí. Excluye las publicaciones eliminadas u ocultas.
    """
    entrada = RangoMetricasSerializer(data=params)
    entrada.is_valid(raise_exception=True)
    tipo = (params.get("tipo") or "").upper()
    if tipo and tipo not in TIPOS_MAPA:
        raise ValidationError({"tipo": "Usa PERDIDO o ENCONTRADO."})
    objetos = Objeto.objects.exclude(estado__in=("ELIMINADO", "OCULTO"))
    if entrada.validated_data.get("desde"):
        objetos = objetos.filter(fecha_publicacion__date__gte=entrada.validated_data["desde"])
    if entrada.validated_data.get("hasta"):
        objetos = objetos.filter(fecha_publicacion__date__lte=entrada.validated_data["hasta"])
    if tipo:
        objetos = objetos.filter(tipo=tipo)

    con_coordenadas = objetos.filter(latitud__isnull=False, longitud__isnull=False)
    grupos = {}
    for lat, lng, tipo_objeto, ubicacion in con_coordenadas.values_list("latitud", "longitud", "tipo", "ubicacion"):
        clave = (round(float(lat), DECIMALES_MAPA), round(float(lng), DECIMALES_MAPA))
        g = grupos.setdefault(clave, {"perdidos": 0, "encontrados": 0, "lugares": Counter()})
        g["perdidos" if tipo_objeto == "PERDIDO" else "encontrados"] += 1
        if ubicacion and ubicacion.strip():
            g["lugares"][ubicacion.strip()] += 1
    puntos = [{
        "lat": lat, "lng": lng, "total": g["perdidos"] + g["encontrados"],
        "perdidos": g["perdidos"], "encontrados": g["encontrados"],
        "lugar": g["lugares"].most_common(1)[0][0] if g["lugares"] else None,
    } for (lat, lng), g in grupos.items()]
    puntos.sort(key=lambda p: (-p["total"], p["lugar"] or ""))
    return {
        "puntos": puntos,
        "conCoordenadas": con_coordenadas.count(),
        "sinCoordenadas": objetos.count() - con_coordenadas.count(),
    }
