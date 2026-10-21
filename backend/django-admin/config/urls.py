from django.contrib import admin
from django.conf import settings
from django.urls import include, path

urlpatterns = [
    path("api/admin/", include("nucleo.urls")),
]

# El CRUD general de Django se conserva, pero no se expone en la entrega H1-H20.
if settings.HABILITAR_EXTRAS:
    urlpatterns.append(path("admin/", admin.site.urls))
