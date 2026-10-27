"""H26/H28 siempre disponibles; /admin/ sigue siendo opcional, sin usar la BD."""
import importlib

from django.test import SimpleTestCase, override_settings
from django.urls import Resolver404, clear_url_caches, resolve

from config import urls as config_urls
from nucleo import urls as nucleo_urls


class AlcanceSprint2Tests(SimpleTestCase):
    def recargar_rutas(self):
        importlib.reload(nucleo_urls)
        importlib.reload(config_urls)
        clear_url_caches()

    def tearDown(self):
        self.recargar_rutas()

    @override_settings(HABILITAR_EXTRAS=False)
    def test_sprint2_publica_su_api_sin_habilitar_el_panel_nativo(self):
        self.recargar_rutas()
        for ruta in ("/api/admin/usuarios/", "/api/admin/publicaciones/",
                     "/api/admin/auth/login/", "/api/admin/dashboard/",
                     "/api/admin/categorias/", "/api/admin/categorias/1/",
                     "/api/admin/reportes/", "/api/admin/reportes/1/",
                     "/api/admin/reportes/1/resolver/"):
            self.assertIsNotNone(resolve(ruta))
        with self.assertRaises(Resolver404):
            resolve("/admin/")

    @override_settings(HABILITAR_EXTRAS=True)
    def test_habilitar_panel_nativo_no_retira_rutas_del_sprint2(self):
        self.recargar_rutas()
        for ruta in ("/api/admin/reportes/", "/api/admin/reportes/1/resolver/",
                     "/api/admin/categorias/", "/api/admin/dashboard/", "/admin/"):
            self.assertIsNotNone(resolve(ruta))
