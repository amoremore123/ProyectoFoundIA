import re
from urllib.parse import urlencode

from django.conf import settings
from rest_framework import serializers

from .models import (
    Campana, Categoria, Custodia, Notificacion, Objeto, Reclamo, Reporte, ReporteMensaje, Respaldo, Usuario,
)


class LoginSerializer(serializers.Serializer):
    correo = serializers.EmailField()
    password = serializers.CharField()


class UsuarioSerializer(serializers.ModelSerializer):
    # El password NUNCA se expone en ninguna respuesta.
    class Meta:
        model = Usuario
        fields = ["id", "nombre", "apellido", "correo", "rol", "estado", "fecha_registro"]
        read_only_fields = ["id", "fecha_registro"]


class CategoriaSerializer(serializers.ModelSerializer):
    def validate_nombre(self, value):
        nombre = value.strip()
        if not nombre:
            raise serializers.ValidationError("El nombre no puede quedar vacío.")
        duplicadas = Categoria.objects.filter(nombre__iexact=nombre)
        if self.instance:
            duplicadas = duplicadas.exclude(pk=self.instance.pk)
        if duplicadas.exists():
            raise serializers.ValidationError("Ya existe una categoría con ese nombre.")
        return nombre

    def validate(self, attrs):
        if set(self.initial_data) - {"nombre", "descripcion", "estado"}:
            raise serializers.ValidationError("Solo se admiten nombre, descripción y estado.")
        return attrs

    class Meta:
        model = Categoria
        fields = ["id", "nombre", "descripcion", "estado", "fecha_creacion"]
        read_only_fields = ["id", "fecha_creacion"]


class ObjetoSerializer(serializers.ModelSerializer):
    categoria_nombre = serializers.SerializerMethodField()
    publicado_por = serializers.SerializerMethodField()
    categoria = serializers.PrimaryKeyRelatedField(
        queryset=Categoria.objects.all(), write_only=True
    )
    usuario = serializers.PrimaryKeyRelatedField(
        queryset=Usuario.objects.all(), write_only=True
    )

    class Meta:
        model = Objeto
        fields = [
            "id",
            "nombre",
            "descripcion",
            "ubicacion",
            "latitud",
            "longitud",
            "fecha_objeto",
            "tipo",
            "estado",
            "fecha_publicacion",
            "fecha_actualizacion",
            "categoria",
            "usuario",
            "categoria_nombre",
            "publicado_por",
        ]
        read_only_fields = ["id", "fecha_publicacion", "fecha_actualizacion"]

    def get_categoria_nombre(self, obj) -> str:
        return obj.categoria.nombre if obj.categoria else ""

    def get_publicado_por(self, obj) -> str:
        if not obj.usuario:
            return ""
        return f"{obj.usuario.nombre} {obj.usuario.apellido}".strip()


class ReporteSerializer(serializers.ModelSerializer):
    class Meta:
        model = Reporte
        fields = [
            "id",
            "usuario",
            "objeto",
            "motivo",
            "descripcion",
            "estado",
            "fecha_reporte",
            "fecha_resolucion",
            "resolucion",
            "resuelto_por",
        ]
        read_only_fields = fields


class CustodiaObjetoSerializer(serializers.ModelSerializer):
    """H41: datos del objeto para la bandeja de custodia, sin datos del autor."""

    categoria = serializers.SerializerMethodField()

    class Meta:
        model = Objeto
        fields = ["id", "nombre", "descripcion", "ubicacion", "fecha_objeto", "tipo", "estado", "categoria"]

    def get_categoria(self, obj) -> str:
        return obj.categoria.nombre if obj.categoria else ""


class CustodiaSerializer(serializers.ModelSerializer):
    objeto = CustodiaObjetoSerializer(read_only=True)
    recibidoPor = serializers.SerializerMethodField()
    fechaRecepcion = serializers.DateTimeField(source="fecha_recepcion", read_only=True)
    tokenQr = serializers.CharField(source="token_qr", read_only=True)
    etiquetaUrl = serializers.SerializerMethodField()
    constanciaUrl = serializers.SerializerMethodField()
    disposicion = serializers.SerializerMethodField()

    class Meta:
        model = Custodia
        fields = [
            "id", "codigo", "objeto", "recibidoPor", "fechaRecepcion",
            "observaciones", "estado", "tokenQr", "etiquetaUrl", "constanciaUrl", "disposicion",
        ]
        read_only_fields = fields

    def get_recibidoPor(self, obj) -> dict:
        return {
            "id": obj.recibido_por_id,
            "nombre": f"{obj.recibido_por.nombre} {obj.recibido_por.apellido}".strip(),
        }

    def get_etiquetaUrl(self, obj) -> str:
        return f"/api/admin/custodias/{obj.pk}/etiqueta/"

    def get_constanciaUrl(self, obj) -> str:
        return f"/api/admin/custodias/{obj.pk}/constancia/"

    def get_disposicion(self, obj):
        # H54: resumen del acta si el objeto ya se dispuso
        try:
            d = obj.disposicion
        except Exception:  # RelatedObjectDoesNotExist
            return None
        return {
            "codigoActa": d.codigo_acta,
            "destino": d.destino,
            "motivo": d.motivo,
            "fecha": d.fecha_disposicion,
            "responsable": f"{d.responsable.nombre} {d.responsable.apellido}".strip(),
            "actaUrl": f"/api/admin/custodias/{obj.pk}/acta/",
        }


class CustodiaCreateSerializer(serializers.Serializer):
    objetoId = serializers.IntegerField()
    observaciones = serializers.CharField(
        required=False, allow_blank=True, max_length=1000, trim_whitespace=True
    )

    def validate(self, attrs):
        if set(self.initial_data) - {"objetoId", "observaciones"}:
            raise serializers.ValidationError(
                "Solo se admiten objetoId y observaciones; el responsable y la fecha los registra el servidor."
            )
        return attrs


class ReclamoSerializer(serializers.ModelSerializer):
    objeto = CustodiaObjetoSerializer(read_only=True)
    reclamante = serializers.SerializerMethodField()
    resueltoPor = serializers.SerializerMethodField()
    fechaReclamo = serializers.DateTimeField(source="fecha_reclamo", read_only=True)
    respuestaVerificacion = serializers.CharField(source="respuesta_verificacion", read_only=True)
    fechaResolucion = serializers.DateTimeField(source="fecha_resolucion", read_only=True)
    motivoResolucion = serializers.CharField(source="motivo_resolucion", read_only=True)
    qrRetiroExpira = serializers.DateTimeField(source="qr_retiro_expira", read_only=True)
    qrRetiroUsadoEn = serializers.DateTimeField(source="qr_retiro_usado_en", read_only=True)
    qrRetiroVigente = serializers.SerializerMethodField()
    qrRetiroUrl = serializers.SerializerMethodField()
    otroAceptado = serializers.SerializerMethodField()

    class Meta:
        model = Reclamo
        fields = [
            "id", "objeto", "reclamante", "estado", "fechaReclamo",
            "resueltoPor", "fechaResolucion", "motivoResolucion",
            "qrRetiroExpira", "qrRetiroUsadoEn", "qrRetiroVigente", "qrRetiroUrl", "respuestaVerificacion",
            "otroAceptado",
        ]
        read_only_fields = fields

    def get_reclamante(self, obj) -> dict:
        return {
            "id": obj.usuario_id,
            "nombre": f"{obj.usuario.nombre} {obj.usuario.apellido}".strip(),
        }

    def get_resueltoPor(self, obj):
        if not obj.resuelto_por_id:
            return None
        return {
            "id": obj.resuelto_por_id,
            "nombre": f"{obj.resuelto_por.nombre} {obj.resuelto_por.apellido}".strip(),
        }

    def get_otroAceptado(self, obj) -> bool:
        """Otra persona ya tiene aceptado (o retiró) este objeto: no se puede aceptar este reclamo."""
        return Reclamo.objects.filter(
            objeto_id=obj.objeto_id, estado__in=("ACEPTADO", "RETIRADO")).exclude(pk=obj.pk).exists()

    def get_qrRetiroVigente(self, obj) -> bool:
        from .reclamos_custodia import token_retiro_vigente
        return token_retiro_vigente(obj)

    def get_qrRetiroUrl(self, obj):
        if obj.estado != "ACEPTADO" or not obj.qr_retiro_token:
            return None
        return f"/api/admin/reclamos/{obj.pk}/qr-retiro/"


class ReclamoResolucionSerializer(serializers.Serializer):
    decision = serializers.ChoiceField(choices=("ACEPTADO", "RECHAZADO"))
    motivo = serializers.CharField(max_length=1000, allow_blank=False, trim_whitespace=True)

    def validate(self, attrs):
        if set(self.initial_data) != {"decision", "motivo"}:
            raise serializers.ValidationError(
                "Solo se admiten decision y motivo; el responsable y la fecha los registra el servidor."
            )
        return attrs


class RetiroSerializer(serializers.Serializer):
    token = serializers.CharField(max_length=64, allow_blank=False, trim_whitespace=True)
    # Token de la etiqueta o el enlace completo que trae su QR
    etiqueta = serializers.CharField(max_length=1000, allow_blank=False, trim_whitespace=True)


class NotificacionSerializer(serializers.ModelSerializer):
    class Meta:
        model = Notificacion
        fields = [
            "id",
            "usuario",
            "titulo",
            "mensaje",
            "tipo",
            "leida",
            "fecha_creacion",
        ]
        read_only_fields = ["id", "fecha_creacion"]


class RespaldoSerializer(serializers.ModelSerializer):
    """H38: solo lectura; el servicio de respaldo escribe la fila."""

    nombreArchivo = serializers.CharField(source="nombre_archivo", read_only=True)
    huellaSha256 = serializers.CharField(source="huella_sha256", read_only=True)
    tamanoBytes = serializers.IntegerField(source="tamano_bytes", read_only=True)
    iniciadoEn = serializers.DateTimeField(source="iniciado_en", read_only=True)
    finalizadoEn = serializers.DateTimeField(source="finalizado_en", read_only=True)

    class Meta:
        model = Respaldo
        fields = [
            "id", "tipo", "nombreArchivo", "huellaSha256", "tamanoBytes",
            "estado", "iniciadoEn", "finalizadoEn", "mensaje",
        ]
        read_only_fields = fields


class CampanaSerializer(serializers.ModelSerializer):
    """H60: campaña de difusión. El servidor pone el autor, las fechas de registro y el enlace."""

    ORIGEN = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")

    fechaInicio = serializers.DateField(source="fecha_inicio")
    fechaFin = serializers.DateField(source="fecha_fin")
    descripcion = serializers.CharField(
        max_length=500, required=False, allow_blank=True, allow_null=True, trim_whitespace=True)
    nombre = serializers.CharField(min_length=3, max_length=120, trim_whitespace=True)
    origen = serializers.CharField(min_length=3, max_length=40, trim_whitespace=True)
    destino = serializers.CharField(max_length=255, required=False, trim_whitespace=True)
    enlace = serializers.SerializerMethodField()
    estado = serializers.SerializerMethodField()
    creadaPor = serializers.SerializerMethodField()
    finalizadaEn = serializers.DateTimeField(source="finalizada_en", read_only=True)
    fechaCreacion = serializers.DateTimeField(source="fecha_creacion", read_only=True)
    fechaActualizacion = serializers.DateTimeField(source="fecha_actualizacion", read_only=True)

    class Meta:
        model = Campana
        fields = [
            "id", "nombre", "descripcion", "origen", "destino", "enlace",
            "fechaInicio", "fechaFin", "estado", "finalizadaEn", "creadaPor", "fechaCreacion", "fechaActualizacion",
        ]
        read_only_fields = ["id"]

    def get_enlace(self, obj) -> str:
        return f"{settings.FOUNDIA_WEB_URL}{obj.destino}?{urlencode({'origen': obj.origen})}"

    def get_estado(self, obj) -> str:
        return obj.estado()

    def get_creadaPor(self, obj) -> dict:
        return {"id": obj.creada_por_id,
                "nombre": f"{obj.creada_por.nombre} {obj.creada_por.apellido}".strip()}

    def validate_origen(self, valor):
        if self.instance is not None and valor != self.instance.origen:
            raise serializers.ValidationError("El origen no se puede cambiar: el enlace ya pudo compartirse.")
        if not self.ORIGEN.match(valor):
            raise serializers.ValidationError("Usa solo minúsculas, números y guiones (por ejemplo, semana-perdidos-2026).")
        repetido = Campana.objects.filter(origen=valor)
        if self.instance is not None:
            repetido = repetido.exclude(pk=self.instance.pk)
        if repetido.exists():
            raise serializers.ValidationError("Ya existe una campaña con ese origen.")
        return valor

    def validate_destino(self, valor):
        valor = valor or "/"
        # Solo rutas de la propia web: nada de «https://…» ni «//otro-dominio»
        if not valor.startswith("/") or valor.startswith("//") or "\\" in valor or "?" in valor or "#" in valor:
            raise serializers.ValidationError("Debe ser una ruta de la web que empiece con «/», sin dominio ni parámetros.")
        return valor

    def validate(self, attrs):
        inicio = attrs.get("fecha_inicio", getattr(self.instance, "fecha_inicio", None))
        fin = attrs.get("fecha_fin", getattr(self.instance, "fecha_fin", None))
        if inicio and fin and fin < inicio:
            raise serializers.ValidationError({"fechaFin": "Debe ser igual o posterior a la fecha de inicio."})
        if "descripcion" in attrs and not attrs["descripcion"]:
            attrs["descripcion"] = None
        return attrs


def _persona(usuario, con_estado=False):
    if usuario is None:
        return None
    datos = {"id": usuario.id, "nombre": f"{usuario.nombre} {usuario.apellido}".strip()}
    if con_estado:
        datos["estado"] = usuario.estado
    return datos


class ReporteMensajeSerializer(serializers.ModelSerializer):
    """H56: reporte de un mensaje del chat con el texto y el autor, sin correos ni teléfonos."""

    fechaReporte = serializers.DateTimeField(source="creado_en", read_only=True)
    conversacionId = serializers.IntegerField(source="mensaje.conversacion_id", read_only=True)
    mensaje = serializers.SerializerMethodField()
    autor = serializers.SerializerMethodField()
    reportadoPor = serializers.SerializerMethodField()
    resueltoPor = serializers.SerializerMethodField()
    fechaResolucion = serializers.DateTimeField(source="fecha_resolucion", read_only=True)

    class Meta:
        model = ReporteMensaje
        fields = ["id", "estado", "motivo", "fechaReporte", "conversacionId", "mensaje", "autor",
                  "reportadoPor", "accion", "resolucion", "resueltoPor", "fechaResolucion"]
        read_only_fields = fields

    def get_mensaje(self, obj) -> dict:
        m = obj.mensaje
        return {"id": m.id, "texto": m.texto, "imagenUrl": m.imagen_url,
                "enviadoEn": serializers.DateTimeField().to_representation(m.enviado_en)}

    def get_autor(self, obj) -> dict:
        return _persona(obj.mensaje.autor, con_estado=True)

    def get_reportadoPor(self, obj) -> dict:
        return _persona(obj.usuario)

    def get_resueltoPor(self, obj):
        return _persona(obj.resuelto_por)
