from rest_framework import serializers

from .models import Categoria, Custodia, Notificacion, Objeto, Reporte, Usuario


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

    class Meta:
        model = Custodia
        fields = [
            "id", "codigo", "objeto", "recibidoPor", "fechaRecepcion",
            "observaciones", "estado", "tokenQr", "etiquetaUrl", "constanciaUrl",
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
