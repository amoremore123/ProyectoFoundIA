"""H69: notificaciones masivas desde el panel, con vista previa, límite, confirmación y registro."""
import hashlib
import hmac
import json
from datetime import datetime, timedelta, timezone as dt_timezone

from django.conf import settings
from django.db import transaction
from django.db.models import Count, Q
from django.utils import timezone
from rest_framework import mixins, serializers, status, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import ValidationError
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from .models import ZONA_LIMA, EnvioMasivo, Notificacion, Usuario
from .permissions import EsAdmin

MAXIMO_DESTINATARIOS = 1000
MAXIMO_ENVIOS_POR_DIA = 3
VIGENCIA_CONFIRMACION = timedelta(minutes=10)
TAMANO_MUESTRA = 5
GRUPOS = {
    "TODOS": Q(),
    "USUARIOS": Q(rol="USUARIO"),
    "BIBLIOTECA": Q(rol="BIBLIOTECA"),
}


def destinatarios_de(grupo):
    # Solo cuentas activas: las suspendidas y eliminadas no reciben avisos
    return Usuario.objects.filter(GRUPOS[grupo], estado="ACTIVO").order_by("id")


class AvisoMasivoSerializer(serializers.Serializer):
    titulo = serializers.CharField(min_length=3, max_length=150, trim_whitespace=True)
    mensaje = serializers.CharField(min_length=3, max_length=1000, trim_whitespace=True)
    enlace = serializers.CharField(max_length=255, required=False, allow_blank=True, allow_null=True,
                                   trim_whitespace=True)
    destinatarios = serializers.ChoiceField(choices=tuple(GRUPOS), error_messages={
        "invalid_choice": "Usa TODOS, USUARIOS o BIBLIOTECA."})

    def validate_enlace(self, valor):
        if not valor:
            return None
        # Solo rutas de la propia web, como las campañas (H60) y los avisos destacados (H71)
        if not valor.startswith("/") or valor.startswith("//") or "\\" in valor or "?" in valor or "#" in valor:
            raise serializers.ValidationError("Debe ser una ruta de la web que empiece con «/», sin dominio ni parámetros.")
        return valor


class EnviarMasivoSerializer(AvisoMasivoSerializer):
    confirmacion = serializers.CharField(max_length=200, error_messages={
        "required": "Pide la vista previa y envía su código de confirmación."})


def _firma(autor_id, datos, total, expira):
    contenido = json.dumps([autor_id, datos["titulo"], datos["mensaje"], datos.get("enlace"),
                            datos["destinatarios"], total, expira], ensure_ascii=False, separators=(",", ":"))
    return hmac.new(settings.SECRET_KEY.encode(), ("h69:" + contenido).encode(), hashlib.sha256).hexdigest()


def codigo_confirmacion(autor_id, datos, total, ahora=None):
    expira = int(((ahora or timezone.now()) + VIGENCIA_CONFIRMACION).timestamp())
    return f"{expira}.{_firma(autor_id, datos, total, expira)}", expira


def confirmacion_valida(codigo, autor_id, datos, total, ahora=None):
    """El código solo vale para el mismo administrador, contenido, grupo y número de cuentas, y sin vencer."""
    try:
        expira_texto, firma = codigo.split(".", 1)
        expira = int(expira_texto)
    except (ValueError, AttributeError):
        return False
    if expira < (ahora or timezone.now()).timestamp():
        return False
    return hmac.compare_digest(firma, _firma(autor_id, datos, total, expira))


def _inicio_del_dia_en_lima(ahora=None):
    local = (ahora or timezone.now()).astimezone(ZONA_LIMA)
    return local.replace(hour=0, minute=0, second=0, microsecond=0).astimezone(dt_timezone.utc)


def _total_o_error(grupo):
    total = destinatarios_de(grupo).count()
    if total == 0:
        raise ValidationError({"destinatarios": "No hay cuentas activas en ese grupo."})
    if total > MAXIMO_DESTINATARIOS:
        raise ValidationError({"destinatarios": f"Son {total} cuentas; el máximo por envío es {MAXIMO_DESTINATARIOS}. "
                                                "Elige un grupo más chico."})
    return total


class EnvioMasivoSerializer(serializers.ModelSerializer):
    totalDestinatarios = serializers.IntegerField(source="total_destinatarios", read_only=True)
    leidas = serializers.SerializerMethodField()
    autor = serializers.SerializerMethodField()
    enviadoEn = serializers.DateTimeField(source="enviado_en", read_only=True)

    class Meta:
        model = EnvioMasivo
        fields = ["id", "titulo", "mensaje", "enlace", "destinatarios", "totalDestinatarios", "leidas",
                  "autor", "enviadoEn"]
        read_only_fields = fields

    def get_leidas(self, obj) -> int:
        valor = getattr(obj, "leidas", None)
        return valor if valor is not None else obj.notificaciones.filter(leida=True).count()

    def get_autor(self, obj) -> dict:
        return {"id": obj.autor_id, "nombre": f"{obj.autor.nombre} {obj.autor.apellido}".strip()}


class EnvioMasivoViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    """H69 - Notificaciones masivas, solo ADMIN.

    POST vista-previa/ (no envía) · POST / (envía con el código de la vista previa) · GET historial,
    detalle y destinatarios/. Un envío no se edita ni se borra.
    """

    serializer_class = EnvioMasivoSerializer
    permission_classes = [IsAuthenticated, EsAdmin]

    def get_queryset(self):
        return (EnvioMasivo.objects.select_related("autor")
                .annotate(leidas=Count("notificaciones", filter=Q(notificaciones__leida=True)))
                # Con la anotación (GROUP BY) Django no aplica el orden del modelo: se pide explícito
                .order_by("-enviado_en", "-id"))

    @action(detail=False, methods=["post"], url_path="vista-previa")
    def vista_previa(self, request):
        datos = AvisoMasivoSerializer(data=request.data)
        datos.is_valid(raise_exception=True)
        datos = datos.validated_data
        total = _total_o_error(datos["destinatarios"])
        codigo, expira = codigo_confirmacion(request.user.id, datos, total)
        muestra = destinatarios_de(datos["destinatarios"])[:TAMANO_MUESTRA]
        return Response({
            "destinatarios": total,
            "muestra": [{"id": u.id, "nombre": f"{u.nombre} {u.apellido}".strip(), "rol": u.rol} for u in muestra],
            "confirmacion": codigo,
            "expiraEn": datetime.fromtimestamp(expira, tz=dt_timezone.utc).isoformat().replace("+00:00", "Z"),
        })

    def create(self, request, *args, **kwargs):
        datos = EnviarMasivoSerializer(data=request.data)
        datos.is_valid(raise_exception=True)
        datos = datos.validated_data
        with transaction.atomic():
            ahora = timezone.now()
            # Bloquea los envíos de hoy: dos administradores a la vez no pasan juntos el límite
            hoy = list(EnvioMasivo.objects.select_for_update().filter(enviado_en__gte=_inicio_del_dia_en_lima(ahora)))
            if len(hoy) >= MAXIMO_ENVIOS_POR_DIA:
                return Response({"detail": f"Ya se hicieron {MAXIMO_ENVIOS_POR_DIA} envíos masivos hoy. "
                                           "Inténtalo mañana."}, status=status.HTTP_429_TOO_MANY_REQUESTS)
            cuentas = list(destinatarios_de(datos["destinatarios"]))
            total = len(cuentas)
            if total == 0 or total > MAXIMO_DESTINATARIOS:
                _total_o_error(datos["destinatarios"])
            if not confirmacion_valida(datos["confirmacion"], request.user.id, datos, total, ahora):
                raise ValidationError({"confirmacion": "El código no vale: venció o cambió el contenido o el número de "
                                                       "cuentas. Vuelve a pedir la vista previa."})
            envio = EnvioMasivo.objects.create(
                titulo=datos["titulo"], mensaje=datos["mensaje"], enlace=datos.get("enlace"),
                destinatarios=datos["destinatarios"], total_destinatarios=total, autor=request.user,
                enviado_en=ahora)
            # Tipo SISTEMA: la app los muestra como cualquier aviso y salen por el canal «sistema» (H68)
            Notificacion.objects.bulk_create([
                Notificacion(usuario=u, titulo=envio.titulo, mensaje=envio.mensaje, tipo="SISTEMA",
                             enlace=envio.enlace, leida=False, fecha_creacion=ahora, envio_masivo=envio)
                for u in cuentas
            ], batch_size=500)
        envio = self.get_queryset().get(pk=envio.pk)
        return Response(EnvioMasivoSerializer(envio).data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["get"])
    def destinatarios(self, request, pk=None):
        envio = self.get_object()
        avisos = (Notificacion.objects.filter(envio_masivo=envio).select_related("usuario").order_by("usuario_id"))
        pagina = self.paginate_queryset(avisos)
        return self.get_paginated_response([
            {"id": n.usuario_id, "nombre": f"{n.usuario.nombre} {n.usuario.apellido}".strip(),
             "rol": n.usuario.rol, "leida": n.leida}
            for n in pagina
        ])
