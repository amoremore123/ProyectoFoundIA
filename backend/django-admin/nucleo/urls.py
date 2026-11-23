from django.urls import path, re_path
from rest_framework.routers import DefaultRouter

from .envios_masivos import EnvioMasivoViewSet
from .views import (
    AvisoDestacadoViewSet,
    CampanaViewSet,
    CategoriaViewSet,
    CustodiaViewSet,
    DashboardExportView,
    DashboardView,
    LoginView,
    LogoutAllView,
    LogoutView,
    ObjetoViewSet,
    ReclamoViewSet,
    ReporteMensajeViewSet,
    ReporteViewSet,
    RespaldoViewSet,
    RestauracionViewSet,
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
router.register("respaldos", RespaldoViewSet, basename="respaldos")
# H70: historial de restauraciones (solo lectura; restaura scripts/restaurar_version.sh)
router.register("restauraciones", RestauracionViewSet, basename="restauraciones")
# H56: resolver los reportes de mensajes del chat
router.register("reportes-mensajes", ReporteMensajeViewSet, basename="reportes-mensajes")
# H60: campañas de difusión (sin DELETE: se finalizan)
router.register("campanas", CampanaViewSet, basename="campanas")
# H71: avisos destacados con vigencia (sin DELETE: se retiran)
router.register("avisos-destacados", AvisoDestacadoViewSet, basename="avisos-destacados")
# H69: notificaciones masivas (vista previa, confirmación y registro; no se editan ni se borran)
router.register("envios-masivos", EnvioMasivoViewSet, basename="envios-masivos")

urlpatterns = [
    path("auth/login/", LoginView.as_view(), name="admin-login"),
    path("auth/logout/", LogoutView.as_view(), name="admin-logout"),
    path("auth/logout-all/", LogoutAllView.as_view(), name="admin-logout-all"),
    path("dashboard/", DashboardView.as_view(), name="admin-dashboard"),
    # H32: el punto forma parte de la ruta pedida por la historia.
    re_path(r"^dashboard/exportar\.(?P<formato>xlsx|pdf)$",
            DashboardExportView.as_view(), name="admin-dashboard-exportar"),
] + router.urls
