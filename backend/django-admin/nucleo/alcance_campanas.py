"""H67: métricas de alcance por origen de campaña (las visitas y registros los cuenta Spring)."""
from collections import Counter
from datetime import datetime, time, timedelta, timezone as dt_timezone

from django.utils.dateparse import parse_date
from rest_framework.exceptions import ValidationError

from .models import ZONA_LIMA, AlcanceCampana, hoy_en_lima

MAXIMO_DIAS = 366
DIAS_POR_DEFECTO = 30


def _fecha(texto, campo):
    try:
        valor = parse_date(texto)
    except ValueError:
        valor = None
    if valor is None:
        raise ValidationError({campo: "Usa el formato AAAA-MM-DD."})
    return valor


def rango(params, desde_defecto, hasta_defecto):
    """Fechas de Lima pedidas en ?desde= y ?hasta=, o las de por defecto."""
    desde = _fecha(params["desde"], "desde") if params.get("desde") else desde_defecto
    hasta = _fecha(params["hasta"], "hasta") if params.get("hasta") else hasta_defecto
    if desde > hasta:
        raise ValidationError({"desde": "Debe ser igual o anterior a «hasta»."})
    if (hasta - desde).days + 1 > MAXIMO_DIAS:
        raise ValidationError({"hasta": f"El rango no puede pasar de {MAXIMO_DIAS} días."})
    return desde, hasta


def rango_por_defecto_tablero():
    hoy = hoy_en_lima()
    return hoy - timedelta(days=DIAS_POR_DEFECTO - 1), hoy


def rango_por_defecto_campana(campana):
    hoy = hoy_en_lima()
    fin = min(campana.fecha_fin, hoy)
    return (campana.fecha_inicio, fin) if campana.fecha_inicio <= fin else (campana.fecha_inicio, campana.fecha_inicio)


def _limites_utc(desde, hasta):
    # Del inicio del día «desde» al inicio del día siguiente a «hasta», en hora de Lima
    inicio = datetime.combine(desde, time.min, tzinfo=ZONA_LIMA).astimezone(dt_timezone.utc)
    fin = datetime.combine(hasta + timedelta(days=1), time.min, tzinfo=ZONA_LIMA).astimezone(dt_timezone.utc)
    return inicio, fin


def _eventos(desde, hasta, campanas=None):
    inicio, fin = _limites_utc(desde, hasta)
    consulta = AlcanceCampana.objects.filter(fecha__gte=inicio, fecha__lt=fin)
    if campanas is not None:
        consulta = consulta.filter(campana_id__in=campanas)
    return consulta.values_list("campana_id", "tipo", "fecha")


def conversion(visitas, registros):
    return round(registros * 100 / visitas, 1) if visitas else 0.0


def tablero(campanas, desde, hasta):
    conteo = Counter((campana_id, tipo) for campana_id, tipo, _ in _eventos(desde, hasta))
    filas = []
    for c in campanas:
        visitas, registros = conteo[(c.id, "VISITA")], conteo[(c.id, "REGISTRO")]
        filas.append({"id": c.id, "nombre": c.nombre, "origen": c.origen, "estado": c.estado(),
                      "visitas": visitas, "registros": registros, "conversion": conversion(visitas, registros)})
    filas.sort(key=lambda f: (-f["visitas"], -f["registros"], f["id"]))
    return {
        "desde": str(desde), "hasta": str(hasta), "campanas": filas,
        "total": {"visitas": sum(f["visitas"] for f in filas), "registros": sum(f["registros"] for f in filas)},
    }


def detalle(campana, desde, hasta):
    por_dia = Counter()
    for _, tipo, fecha in _eventos(desde, hasta, [campana.id]):
        por_dia[(fecha.astimezone(ZONA_LIMA).date(), tipo)] += 1
    dias = []
    dia = desde
    while dia <= hasta:
        dias.append({"fecha": str(dia), "visitas": por_dia[(dia, "VISITA")], "registros": por_dia[(dia, "REGISTRO")]})
        dia += timedelta(days=1)
    visitas = sum(d["visitas"] for d in dias)
    registros = sum(d["registros"] for d in dias)
    return {
        "campana": {"id": campana.id, "nombre": campana.nombre, "origen": campana.origen},
        "desde": str(desde), "hasta": str(hasta),
        "visitas": visitas, "registros": registros, "conversion": conversion(visitas, registros),
        "porDia": dias,
    }


