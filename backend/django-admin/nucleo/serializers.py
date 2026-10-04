from rest_framework import serializers

from .models import Categoria, Notificacion, Objeto, Reporte, Usuario


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
        ]
        read_only_fields = ["id", "fecha_reporte"]


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
