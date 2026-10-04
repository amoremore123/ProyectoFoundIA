"""Registra los 8 modelos en el admin de Django (CRUD gratis para el equipo)."""
from django.contrib import admin

from .models import (
    Categoria,
    Coincidencia,
    Contacto,
    Foto,
    Notificacion,
    Objeto,
    Reporte,
    Usuario,
)


@admin.register(Usuario)
class UsuarioAdmin(admin.ModelAdmin):
    list_display = ("id", "nombre", "apellido", "correo", "rol", "estado", "fecha_registro")
    search_fields = ("nombre", "apellido", "correo")
    list_filter = ("rol", "estado")


@admin.register(Categoria)
class CategoriaAdmin(admin.ModelAdmin):
    list_display = ("id", "nombre", "estado", "fecha_creacion")
    list_filter = ("estado",)


@admin.register(Objeto)
class ObjetoAdmin(admin.ModelAdmin):
    list_display = ("id", "nombre", "tipo", "estado", "categoria", "usuario", "fecha_publicacion")
    list_filter = ("tipo", "estado", "categoria")
    search_fields = ("nombre", "descripcion", "ubicacion")


@admin.register(Foto)
class FotoAdmin(admin.ModelAdmin):
    list_display = ("id", "objeto", "url", "fecha_subida")


@admin.register(Coincidencia)
class CoincidenciaAdmin(admin.ModelAdmin):
    list_display = ("id", "objeto_perdido", "objeto_encontrado", "porcentaje", "nivel", "estado")
    list_filter = ("estado", "nivel")


@admin.register(Contacto)
class ContactoAdmin(admin.ModelAdmin):
    list_display = ("id", "coincidencia", "usuario_emisor", "usuario_receptor", "estado", "fecha_contacto")
    list_filter = ("estado",)


@admin.register(Notificacion)
class NotificacionAdmin(admin.ModelAdmin):
    list_display = ("id", "usuario", "titulo", "tipo", "leida", "fecha_creacion")
    list_filter = ("leida", "tipo")


@admin.register(Reporte)
class ReporteAdmin(admin.ModelAdmin):
    list_display = ("id", "objeto", "usuario", "motivo", "estado", "fecha_reporte", "fecha_resolucion")
    list_filter = ("estado",)
