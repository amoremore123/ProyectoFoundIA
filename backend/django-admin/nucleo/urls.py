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
# H26/H28 son API del Sprint 2, no extras opcionales del panel nativo.
router.register("categorias", CategoriaViewSet, basename="categorias")
router.register("reportes", ReporteViewSet, basename="reportes")

urlpatterns = [
    path("auth/login/", LoginView.as_view(), name="admin-login"),
    path("dashboard/", DashboardView.as_view(), name="admin-dashboard"),
] + router.urls
