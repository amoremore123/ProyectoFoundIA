"""H70: restaurar una versión anterior desde el panel, con doble confirmación y modo mantenimiento.

El panel no tiene acceso a los respaldos ni a las herramientas de MySQL: solo deja la restauración
SOLICITADA. El servicio de respaldo la atiende (scripts/respaldo.sh → scripts/restaurar_version.sh), con
el respaldo de seguridad previo y todas las guardas del script. Mientras haya una restauración solicitada o
en proceso, las dos APIs rechazan las escrituras con 503 (modo mantenimiento).
"""
import json
import time

from django.db import DatabaseError, transaction
from django.http import HttpResponse
from django.utils import timezone
from rest_framework import serializers, status
from rest_framework.exceptions import ValidationError
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import Respaldo, Restauracion
from .permissions import EsAdmin
from .serializers import RestauracionSerializer

TEXTO_CONFIRMACION = "RESTAURAR"
ESTADOS_EN_CURSO = ("SOLICITADA", "EN_PROCESO")


def restauracion_en_curso():
    return Restauracion.objects.filter(modo="APLICADA", estado__in=ESTADOS_EN_CURSO).first()


def version_disponible(version):
    """La versión como la muestra GET /respaldos/versiones/ (H70), o None si no existe."""
    from .views import _version  # misma regla de disponibilidad que la lista de versiones

    base = (Respaldo.objects.filter(tipo="BASE", estado="COMPLETADO", nombre_archivo__contains=f"_{version}")
            .order_by("-iniciado_en").first())
    if base is None:
        return None
    fotos = list(Respaldo.objects.filter(tipo="FOTOS", estado="COMPLETADO"))
    return _version(base, fotos)


class RestaurarSerializer(serializers.Serializer):
    confirmacionVersion = serializers.CharField(max_length=20, error_messages={
        "required": "Escribe la versión que vas a restaurar para confirmar."})
    confirmacionTexto = serializers.CharField(max_length=20, error_messages={
        "required": f"Escribe {TEXTO_CONFIRMACION} para confirmar."})


class RestaurarVersionView(APIView):
    """POST /api/admin/respaldos/versiones/{version}/restaurar/ — solo ADMIN.

    Doble confirmación: {"confirmacionVersion": "<la misma versión>", "confirmacionTexto": "RESTAURAR"}.
    Responde 202 con la restauración SOLICITADA; el servicio de respaldo la ejecuta en unos segundos.
    """

    permission_classes = [IsAuthenticated, EsAdmin]

    def post(self, request, version):
        datos = RestaurarSerializer(data=request.data)
        datos.is_valid(raise_exception=True)
        if datos.validated_data["confirmacionVersion"].strip() != version:
            raise ValidationError({"confirmacionVersion": "No coincide con la versión elegida. No se hizo nada."})
        if datos.validated_data["confirmacionTexto"].strip() != TEXTO_CONFIRMACION:
            raise ValidationError({"confirmacionTexto": f"Escribe {TEXTO_CONFIRMACION} en mayúsculas para confirmar."})
        detalle = version_disponible(version)
        if detalle is None:
            return Response({"detail": "No existe esa versión."}, status=status.HTTP_404_NOT_FOUND)
        if not detalle["disponible"]:
            return Response({"detail": f"La versión no se puede restaurar: {detalle['motivo']}"},
                            status=status.HTTP_409_CONFLICT)
        with transaction.atomic():
            # Bloquea la tabla de restauraciones en curso: dos administradores no piden dos a la vez
            if Restauracion.objects.select_for_update().filter(modo="APLICADA", estado__in=ESTADOS_EN_CURSO).exists():
                return Response({"detail": "Ya hay una restauración en curso. Espera a que termine."},
                                status=status.HTTP_409_CONFLICT)
            responsable = f"{request.user.nombre} {request.user.apellido}".strip()[:120]
            restauracion = Restauracion.objects.create(
                version=version, modo="APLICADA", estado="SOLICITADA",
                respaldo_base=detalle["base"]["nombreArchivo"], respaldo_fotos=detalle["fotos"]["nombreArchivo"],
                responsable=responsable, iniciado_en=timezone.now(),
                mensaje="Solicitada desde el panel. El servicio de respaldo la atenderá en unos segundos; "
                        "mientras tanto, FoundIA está en modo mantenimiento.")
        return Response(RestauracionSerializer(restauracion).data, status=status.HTTP_202_ACCEPTED)


class ModoMantenimientoMiddleware:
    """Mientras una restauración está solicitada o en proceso, rechaza las escrituras de /api/admin/ con 503.

    Las lecturas siguen (para ver el avance) y también el inicio de sesión. Si la base no responde y la última
    vez había mantenimiento (la restauración reemplaza la base unos segundos), también responde 503.
    """

    VIGENCIA_SEGUNDOS = 2
    RECUERDA_MANTENIMIENTO_SEGUNDOS = 120
    METODOS_DE_ESCRITURA = ("POST", "PUT", "PATCH", "DELETE")

    def __init__(self, get_response):
        self.get_response = get_response
        self._consultado_en = 0.0
        self._en_mantenimiento = False
        self._ultimo_mantenimiento = 0.0

    def _hay_mantenimiento(self):
        ahora = time.monotonic()
        if ahora - self._consultado_en < self.VIGENCIA_SEGUNDOS:
            return self._en_mantenimiento
        try:
            self._en_mantenimiento = restauracion_en_curso() is not None
        except DatabaseError:
            # La base se está reemplazando: se mantiene el mantenimiento si lo hubo hace poco
            self._en_mantenimiento = ahora - self._ultimo_mantenimiento < self.RECUERDA_MANTENIMIENTO_SEGUNDOS
        if self._en_mantenimiento:
            self._ultimo_mantenimiento = ahora
        self._consultado_en = ahora
        return self._en_mantenimiento

    def __call__(self, request):
        if (request.method in self.METODOS_DE_ESCRITURA and request.path.startswith("/api/admin/")
                and not request.path.startswith("/api/admin/auth/") and self._hay_mantenimiento()):
            cuerpo = {"detail": "FoundIA está restaurando una versión anterior de los datos. Intenta de nuevo en unos minutos.",
                      "status": 503}
            cuerpo["mensaje"] = cuerpo["detail"]
            respuesta = HttpResponse(json.dumps(cuerpo, ensure_ascii=False), status=503,
                                     content_type="application/json; charset=utf-8")
            respuesta["Retry-After"] = "60"
            return respuesta
        return self.get_response(request)
