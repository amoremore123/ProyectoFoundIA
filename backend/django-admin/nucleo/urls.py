from django.conf import settings
from django.urls import path
from rest_framework.routers import DefaultRouter

from .views import (
    CategoriaViewSet,
    DashboardView,
    LoginView,
    ObjetoViewSet,
    ReporteViewSet,
    UsuarioViewSet,
)

router = DefaultRouter()
router.register("usuarios", UsuarioViewSet, basename="usuarios")
router.register("publicaciones", ObjetoViewSet, basename="publicaciones")
if settings.HABILITAR_EXTRAS:
    router.register("categorias", CategoriaViewSet, basename="categorias")
    router.register("reportes", ReporteViewSet, basename="reportes")

urlpatterns = [
    path("auth/login/", LoginView.as_view(), name="admin-login"),
    path("dashboard/", DashboardView.as_view(), name="admin-dashboard"),
] + router.urls
