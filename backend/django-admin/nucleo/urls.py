from django.urls import path, re_path
from rest_framework.routers import DefaultRouter

from .views import (
    CategoriaViewSet,
    CustodiaViewSet,
    DashboardExportView,
    DashboardView,
    LoginView,
    ObjetoViewSet,
    ReclamoViewSet,
    ReporteViewSet,
    UsuarioViewSet,
)

router = DefaultRouter()
router.register("usuarios", UsuarioViewSet, basename="usuarios")
router.register("publicaciones", ObjetoViewSet, basename="publicaciones")
router.register("custodias", CustodiaViewSet, basename="custodias")
router.register("reclamos", ReclamoViewSet, basename="reclamos")
# H26/H28 son API del Sprint 2, no extras opcionales del panel nativo.
router.register("categorias", CategoriaViewSet, basename="categorias")
router.register("reportes", ReporteViewSet, basename="reportes")

urlpatterns = [
    path("auth/login/", LoginView.as_view(), name="admin-login"),
    path("dashboard/", DashboardView.as_view(), name="admin-dashboard"),
    # H32: el punto forma parte de la ruta pedida por la historia.
    re_path(r"^dashboard/exportar\.(?P<formato>xlsx|pdf)$",
            DashboardExportView.as_view(), name="admin-dashboard-exportar"),
] + router.urls
