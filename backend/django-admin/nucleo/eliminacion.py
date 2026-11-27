"""H34: eliminación real de la cuenta (derecho de cancelación, Ley 29733).

La cuenta no se borra de la tabla (la referencian custodias, reclamos y disposiciones que deben quedar):
se anonimiza. Se borran los datos que solo sirven a la persona, se conservan sin su texto libre los registros
que pertenecen a otros o a la biblioteca, y las fotos se anotan para que Spring las borre del volumen.
Ver docs/api/sprint4-h34-eliminacion-real.md.
"""
import secrets
from datetime import timedelta

import bcrypt
from django.conf import settings
from django.core.mail import send_mail
from django.db import transaction
from django.db.models import Q
from django.utils import timezone
from rest_framework import mixins, serializers, status, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import ValidationError
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from .firebase import revocar_al_confirmar as revocar_firebase
from .models import (
    ZONA_LIMA, ArchivoPorBorrar, AvisoCoincidencia, BloqueoChat, Contacto, Conversacion, Dispositivo, Foto,
    Invitacion, LecturaChat, MensajeChat, Notificacion, Objeto, PreferenciaNotificacion, Reclamo,
    RecordatorioObjeto, Reporte, ReporteMensaje, SolicitudEliminacion, TokenRecuperacionPassword,
    TokenRevocado, Usuario,
)
from .permissions import EsAdmin

PLAZO_DIAS_HABILES = 10  # Reglamento de la Ley 29733 (D.S. 003-2013-JUS), confirmado por el equipo
NOMBRE_ANONIMO = "Cuenta eliminada"
TEXTO_ELIMINADO = "[eliminado]"
DESCRIPCION_ELIMINADA = "[eliminada a pedido de la persona]"
MENSAJE_ELIMINADO = "Mensaje eliminado"
PUBLICACIONES_QUE_SE_BORRAN = ("ACTIVO", "OCULTO", "ELIMINADO")
PUBLICACIONES_QUE_SE_CONSERVAN = ("EN_CUSTODIA", "RECUPERADO")
RECLAMOS_ABIERTOS = ("PENDIENTE", "ACEPTADO")


def correo_anonimo(usuario_id):
    return f"eliminada-{usuario_id}@foundia.invalid"


def fecha_limite(fecha_solicitud):
    """10 días hábiles (lunes a viernes, en Lima) desde la solicitud. No descuenta feriados."""
    dia = fecha_solicitud.astimezone(ZONA_LIMA).date()
    habiles = 0
    while habiles < PLAZO_DIAS_HABILES:
        dia += timedelta(days=1)
        if dia.weekday() < 5:
            habiles += 1
    return dia


def efecto(usuario):
    """Qué se va a borrar, anonimizar y conservar. Sirve de vista previa y de resumen."""
    objetos = Objeto.objects.filter(usuario=usuario)
    se_borran = objetos.filter(estado__in=PUBLICACIONES_QUE_SE_BORRAN)
    mensajes = MensajeChat.objects.filter(autor=usuario)
    return {
        "publicacionesQueSeEliminan": se_borran.count(),
        "publicacionesQueSeConservan": objetos.filter(estado__in=PUBLICACIONES_QUE_SE_CONSERVAN).count(),
        "fotosDePublicaciones": Foto.objects.filter(objeto__in=se_borran).count(),
        "fotoDePerfil": bool(usuario.foto_perfil_url),
        "mensajesDelChat": mensajes.count(),
        "fotosDelChat": mensajes.exclude(imagen_url__isnull=True).exclude(imagen_url="").count(),
        "avisos": Notificacion.objects.filter(usuario=usuario).count(),
        "dispositivos": Dispositivo.objects.filter(usuario=usuario).count(),
        "reclamosQueSeRetiran": Reclamo.objects.filter(usuario=usuario, estado__in=RECLAMOS_ABIERTOS).count(),
        "reclamosQueSeConservan": Reclamo.objects.filter(usuario=usuario).exclude(estado__in=RECLAMOS_ABIERTOS).count(),
        "contactos": Contacto.objects.filter(usuario_emisor=usuario).count(),
        "reportes": Reporte.objects.filter(usuario=usuario).count() + ReporteMensaje.objects.filter(usuario=usuario).count(),
    }


def anonimizar(usuario):
    """Borra o anonimiza los datos de la persona. Llamar dentro de una transacción."""
    resumen = efecto(usuario)
    archivos = []
    if usuario.foto_perfil_url:
        archivos.append(usuario.foto_perfil_url)

    # Publicaciones: las propias se eliminan con sus fotos; las de la biblioteca se conservan sin ubicación
    objetos = Objeto.objects.filter(usuario=usuario)
    se_borran = list(objetos.filter(estado__in=PUBLICACIONES_QUE_SE_BORRAN).values_list("id", flat=True))
    fotos = Foto.objects.filter(objeto_id__in=se_borran)
    archivos += [f.url for f in fotos if f.url]
    fotos.delete()
    Objeto.objects.filter(id__in=se_borran).update(
        estado="ELIMINADO", descripcion=DESCRIPCION_ELIMINADA, ubicacion=None, latitud=None, longitud=None)
    objetos.filter(estado__in=PUBLICACIONES_QUE_SE_CONSERVAN).update(ubicacion=None, latitud=None, longitud=None)

    # Chat: su texto y sus fotos se borran; las conversaciones quedan en solo lectura
    mensajes = MensajeChat.objects.filter(autor=usuario)
    archivos += [m.imagen_url for m in mensajes if m.imagen_url]
    mensajes.update(texto=MENSAJE_ELIMINADO, imagen_url=None)
    Conversacion.objects.filter(Q(usuario_a=usuario) | Q(usuario_b=usuario)).update(estado="SOLO_LECTURA")
    LecturaChat.objects.filter(usuario=usuario).delete()
    BloqueoChat.objects.filter(Q(bloqueador=usuario) | Q(bloqueado=usuario)).delete()

    # Avisos: primero se sueltan las referencias que no se borran en cascada
    avisos = list(Notificacion.objects.filter(usuario=usuario).values_list("id", flat=True))
    AvisoCoincidencia.objects.filter(notificacion_id__in=avisos).update(notificacion_id=None)
    RecordatorioObjeto.objects.filter(notificacion_id__in=avisos).update(notificacion_id=None)
    Notificacion.objects.filter(id__in=avisos).delete()

    for modelo in (Dispositivo, TokenRevocado, PreferenciaNotificacion, TokenRecuperacionPassword, Invitacion):
        modelo.objects.filter(usuario=usuario).delete()

    # Registros de otros o de la biblioteca: se conservan sin el texto libre de la persona
    Reclamo.objects.filter(usuario=usuario, estado__in=RECLAMOS_ABIERTOS).update(
        estado="RETIRADO", qr_retiro_token=None, qr_retiro_expira=None)
    Reclamo.objects.filter(usuario=usuario).update(respuesta_verificacion=None)
    Contacto.objects.filter(usuario_emisor=usuario).update(mensaje=TEXTO_ELIMINADO)
    Reporte.objects.filter(usuario=usuario).exclude(descripcion__isnull=True).update(descripcion=TEXTO_ELIMINADO)
    ReporteMensaje.objects.filter(usuario=usuario).update(motivo=TEXTO_ELIMINADO)

    # La cuenta: sin nada que identifique a la persona y sin forma de volver a usarla
    clave = bcrypt.hashpw(secrets.token_urlsafe(32).encode(), bcrypt.gensalt(rounds=4)).decode()
    Usuario.objects.filter(pk=usuario.pk).update(
        nombre=NOMBRE_ANONIMO, apellido="", correo=correo_anonimo(usuario.pk), password=clave,
        estado="ELIMINADO", verificado=False, codigo_verificacion=None, foto_perfil_url=None,
        intentos_login=0, bloqueado_hasta=None, sesion_version=usuario.sesion_version + 1)

    ArchivoPorBorrar.objects.bulk_create([ArchivoPorBorrar(ruta=r) for r in dict.fromkeys(archivos)])
    revocar_firebase(usuario.pk)  # H72: después de confirmar la transacción
    resumen["archivosPorBorrar"] = len(dict.fromkeys(archivos))
    return resumen


def _enviar_confirmacion(correo, nombre):
    try:
        send_mail(
            "FoundIA | Tu cuenta fue eliminada",
            f"Hola {nombre},\n\nAtendimos tu solicitud de eliminación. Tu cuenta de FoundIA y tus datos personales "
            "fueron eliminados o anonimizados, como lo pide la Ley 29733.\n\n"
            "Se conservan sin tus datos los registros que la biblioteca debe guardar (por ejemplo, objetos en "
            "custodia). Las copias de respaldo se borran solas en unos días.\n\n"
            "Si no pediste esto, responde a este correo.\n\nEquipo FoundIA\n",
            settings.DEFAULT_FROM_EMAIL, [correo], fail_silently=False)
        return True
    except Exception:  # noqa: BLE001 - un fallo de correo no deshace la eliminación
        return False


class AtenderSerializer(serializers.Serializer):
    confirmacion = serializers.CharField(max_length=150, error_messages={
        "required": "Escribe el correo de la cuenta para confirmar.", "blank": "Escribe el correo de la cuenta para confirmar."})


class RechazarSerializer(serializers.Serializer):
    motivo = serializers.CharField(min_length=3, max_length=500, trim_whitespace=True)


class SolicitudEliminacionSerializer(serializers.ModelSerializer):
    fechaSolicitud = serializers.DateTimeField(source="fecha_solicitud", read_only=True)
    fechaLimite = serializers.SerializerMethodField()
    vencida = serializers.SerializerMethodField()
    usuario = serializers.SerializerMethodField()
    atendidaPor = serializers.SerializerMethodField()
    fechaAtencion = serializers.DateTimeField(source="fecha_atencion", read_only=True)
    motivoRechazo = serializers.CharField(source="motivo_rechazo", read_only=True)
    requiereReaplicar = serializers.SerializerMethodField()

    class Meta:
        model = SolicitudEliminacion
        fields = ["id", "estado", "fechaSolicitud", "fechaLimite", "vencida", "usuario", "atendidaPor",
                  "fechaAtencion", "motivoRechazo", "requiereReaplicar"]
        read_only_fields = fields

    def get_fechaLimite(self, obj):
        return str(fecha_limite(obj.fecha_solicitud))

    def get_vencida(self, obj) -> bool:
        return obj.estado == "PENDIENTE" and timezone.now().astimezone(ZONA_LIMA).date() > fecha_limite(obj.fecha_solicitud)

    def get_usuario(self, obj) -> dict:
        u = obj.usuario
        return {"id": u.id, "nombre": f"{u.nombre} {u.apellido}".strip(), "correo": u.correo, "rol": u.rol,
                "estado": u.estado}

    def get_atendidaPor(self, obj):
        a = obj.atendida_por
        return None if a is None else {"id": a.id, "nombre": f"{a.nombre} {a.apellido}".strip()}

    def get_requiereReaplicar(self, obj) -> bool:
        # Una restauración (H70) pudo traer de vuelta la cuenta: hay que volver a anonimizarla
        return obj.estado == "ATENDIDA" and obj.usuario.estado != "ELIMINADO"


class SolicitudEliminacionViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    """H34 - Atender las solicitudes de eliminación de cuenta. Solo ADMIN.

    GET / (filtro ?estado=) · GET /{id}/ con la vista previa de lo que se hará ·
    POST /{id}/atender/ {"confirmacion": "<correo>"} · POST /{id}/rechazar/ {"motivo": "..."}.
    """

    serializer_class = SolicitudEliminacionSerializer
    permission_classes = [IsAuthenticated, EsAdmin]

    def get_queryset(self):
        consulta = SolicitudEliminacion.objects.select_related("usuario", "atendida_por").order_by("fecha_solicitud", "id")
        estado = self.request.query_params.get("estado")
        if estado is not None:
            if estado not in SolicitudEliminacion.ESTADOS:
                raise ValidationError({"estado": "Usa PENDIENTE, ATENDIDA o RECHAZADA."})
            consulta = consulta.filter(estado=estado)
        return consulta

    def retrieve(self, request, *args, **kwargs):
        solicitud = self.get_object()
        datos = SolicitudEliminacionSerializer(solicitud).data
        datos["efecto"] = efecto(solicitud.usuario) if solicitud.estado == "PENDIENTE" else None
        return Response(datos)

    def _pendiente_para(self, request, pk):
        solicitud = (SolicitudEliminacion.objects.select_for_update()
                     .select_related("usuario").filter(pk=pk).first())
        if solicitud is None:
            return None, Response({"detail": "No encontrado."}, status=status.HTTP_404_NOT_FOUND)
        if solicitud.estado != "PENDIENTE":
            return None, Response({"detail": f"La solicitud ya está {solicitud.estado}."}, status=status.HTTP_409_CONFLICT)
        if solicitud.usuario_id == request.user.pk:
            return None, Response({"detail": "No puedes atender tu propia solicitud: pídeselo a otro administrador."},
                                  status=status.HTTP_409_CONFLICT)
        return solicitud, None

    @action(detail=True, methods=["post"])
    def atender(self, request, pk=None):
        datos = AtenderSerializer(data=request.data)
        datos.is_valid(raise_exception=True)
        with transaction.atomic():
            solicitud, error = self._pendiente_para(request, pk)
            if error:
                return error
            usuario = Usuario.objects.select_for_update().get(pk=solicitud.usuario_id)
            if usuario.rol != "USUARIO":
                return Response({"detail": "Es una cuenta del personal: primero cambia su rol a USUARIO (H36), "
                                           "porque firmó custodias y resoluciones."}, status=status.HTTP_409_CONFLICT)
            if datos.validated_data["confirmacion"].strip().lower() != usuario.correo.lower():
                raise ValidationError({"confirmacion": "No coincide con el correo de la cuenta. No se hizo nada."})
            correo, nombre = usuario.correo, usuario.nombre
            resumen = anonimizar(usuario)
            ahora = timezone.now()
            SolicitudEliminacion.objects.filter(pk=solicitud.pk).update(
                estado="ATENDIDA", atendida_por=request.user, fecha_atencion=ahora)
        # Fuera del bloque: el correo sale solo si la eliminación ya se confirmó (si algo falla, no se llega aquí)
        correo_enviado = _enviar_confirmacion(correo, nombre)
        solicitud = self.get_queryset().get(pk=solicitud.pk)
        respuesta = SolicitudEliminacionSerializer(solicitud).data
        respuesta["resumen"] = resumen
        respuesta["correoEnviado"] = correo_enviado
        return Response(respuesta)

    @action(detail=False, methods=["post"])
    def reaplicar(self, request):
        """H70: después de restaurar una versión, vuelve a anonimizar las cuentas eliminadas que regresaron."""
        return Response({"cuentasAnonimizadasDeNuevo": reaplicar_eliminaciones()})

    @action(detail=True, methods=["post"])
    def rechazar(self, request, pk=None):
        datos = RechazarSerializer(data=request.data)
        datos.is_valid(raise_exception=True)
        with transaction.atomic():
            solicitud, error = self._pendiente_para(request, pk)
            if error:
                return error
            SolicitudEliminacion.objects.filter(pk=solicitud.pk).update(
                estado="RECHAZADA", atendida_por=request.user, fecha_atencion=timezone.now(),
                motivo_rechazo=datos.validated_data["motivo"])
        return Response(SolicitudEliminacionSerializer(self.get_queryset().get(pk=solicitud.pk)).data)


def reaplicar_eliminaciones():
    """Vuelve a anonimizar las cuentas de solicitudes ATENDIDA que una restauración (H70) trajo de vuelta."""
    vueltas = 0
    for solicitud in SolicitudEliminacion.objects.filter(estado="ATENDIDA").select_related("usuario"):
        usuario = Usuario.objects.filter(pk=solicitud.usuario_id).first()
        if usuario is None or usuario.estado == "ELIMINADO":
            continue
        with transaction.atomic():
            anonimizar(Usuario.objects.select_for_update().get(pk=usuario.pk))
        vueltas += 1
    return vueltas
