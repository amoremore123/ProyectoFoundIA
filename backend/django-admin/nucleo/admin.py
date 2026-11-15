"""Administración interna de los modelos de FoundIA."""
from django.contrib import admin

from .models import (
    CambioRol,
    Categoria,
    Coincidencia,
    Contacto,
    Custodia,
    Disposicion,
    Foto,
    Notificacion,
    Objeto,
    Reclamo,
    Reporte,
    Respaldo,
    SecuenciaCustodia,
    TokenRevocado,
    Usuario,
)


class SoloConsultaAdmin(admin.ModelAdmin):
    def has_add_permission(self, request):
        return False

    def has_change_permission(self, request, obj=None):
        return False

    def has_delete_permission(self, request, obj=None):
        return False


@admin.register(Usuario)
class UsuarioAdmin(SoloConsultaAdmin):
    exclude = ("password",)
    list_display = ("id", "nombre", "apellido", "correo", "rol", "estado", "fecha_registro")
    search_fields = ("nombre", "apellido", "correo")
    list_filter = ("rol", "estado")


@admin.register(CambioRol)
class CambioRolAdmin(SoloConsultaAdmin):
    list_display = ("id", "usuario", "rol_anterior", "rol_nuevo", "cambiado_por", "fecha_cambio")
    list_filter = ("rol_anterior", "rol_nuevo")
    search_fields = ("usuario__correo", "cambiado_por__correo")


@admin.register(Categoria)
class CategoriaAdmin(admin.ModelAdmin):
    list_display = ("id", "nombre", "estado", "fecha_creacion")
    search_fields = ("nombre", "descripcion")
    list_filter = ("estado",)


class FotoInline(admin.TabularInline):
    model = Foto
    fields = ("nombre_archivo", "url", "fecha_subida")
    readonly_fields = fields
    extra = 0
    can_delete = False

    def has_add_permission(self, request, obj=None):
        return False


@admin.register(Objeto)
class ObjetoAdmin(admin.ModelAdmin):
    list_display = ("id", "nombre", "tipo", "estado", "categoria", "usuario", "fecha_publicacion")
    list_filter = ("tipo", "estado", "categoria")
    search_fields = ("nombre", "descripcion", "ubicacion")
    list_select_related = ("categoria", "usuario")
    inlines = (FotoInline,)


@admin.register(Foto)
class FotoAdmin(admin.ModelAdmin):
    list_display = ("id", "objeto", "url", "fecha_subida")
    search_fields = ("objeto__nombre", "nombre_archivo")


@admin.register(Coincidencia)
class CoincidenciaAdmin(admin.ModelAdmin):
    list_display = ("id", "objeto_perdido", "objeto_encontrado", "porcentaje", "nivel", "estado")
    list_filter = ("estado", "nivel")
    search_fields = ("objeto_perdido__nombre", "objeto_encontrado__nombre")


@admin.register(Contacto)
class ContactoAdmin(admin.ModelAdmin):
    list_display = ("id", "coincidencia", "usuario_emisor", "usuario_receptor", "estado", "fecha_contacto")
    list_filter = ("estado",)
    search_fields = ("usuario_emisor__correo", "usuario_receptor__correo")


@admin.register(Notificacion)
class NotificacionAdmin(admin.ModelAdmin):
    list_display = ("id", "usuario", "titulo", "tipo", "leida", "fecha_creacion")
    list_filter = ("leida", "tipo")
    search_fields = ("titulo", "usuario__correo")


@admin.register(Custodia)
class CustodiaAdmin(SoloConsultaAdmin):
    exclude = ("token_qr",)
    list_display = ("id", "codigo", "objeto", "estado", "recibido_por", "fecha_recepcion")
    list_filter = ("estado",)
    search_fields = ("codigo", "objeto__nombre")


@admin.register(SecuenciaCustodia)
class SecuenciaCustodiaAdmin(SoloConsultaAdmin):
    list_display = ("anio", "ultimo")


@admin.register(Reclamo)
class ReclamoAdmin(SoloConsultaAdmin):
    exclude = ("qr_retiro_token",)
    list_display = ("id", "objeto", "usuario", "estado", "fecha_reclamo", "resuelto_por", "fecha_resolucion")
    list_filter = ("estado",)
    search_fields = ("objeto__nombre", "usuario__correo")


@admin.register(Reporte)
class ReporteAdmin(admin.ModelAdmin):
    list_display = ("id", "objeto", "usuario", "motivo", "estado", "fecha_reporte", "fecha_resolucion")
    list_filter = ("estado",)
    search_fields = ("objeto__nombre", "motivo")


@admin.register(Disposicion)
class DisposicionAdmin(SoloConsultaAdmin):
    list_display = ("codigo_acta", "custodia", "destino", "responsable", "fecha_disposicion")
    search_fields = ("codigo_acta", "custodia__codigo", "custodia__objeto__nombre")
    list_filter = ("destino", "fecha_disposicion")


@admin.register(Respaldo)
class RespaldoAdmin(SoloConsultaAdmin):
    list_display = ("id", "nombre_archivo", "estado", "iniciado_en", "finalizado_en")
    search_fields = ("nombre_archivo",)
    list_filter = ("estado", "iniciado_en")


@admin.register(TokenRevocado)
class TokenRevocadoAdmin(SoloConsultaAdmin):
    list_display = ("usuario", "revocado_en", "expira_en")
    list_display_links = None
    search_fields = ("usuario__correo",)
    list_filter = ("revocado_en",)
    exclude = ("jti",)
