"""Regresión de alcance: resolver URLs no necesita la base del equipo."""
import importlib

from django.test import SimpleTestCase, override_settings
from django.urls import Resolver404, clear_url_caches, resolve

from config import urls as config_urls
from nucleo import urls as nucleo_urls


class AlcanceSprint1Tests(SimpleTestCase):
    def recargar_rutas(self):
        importlib.reload(nucleo_urls)
        importlib.reload(config_urls)
        clear_url_caches()

    def tearDown(self):
        self.recargar_rutas()

    @override_settings(HABILITAR_EXTRAS=False)
    def test_sprint_conserva_gestion_y_oculta_urls_directas_de_extras(self):
        self.recargar_rutas()
        for ruta in ("/api/admin/usuarios/", "/api/admin/publicaciones/",
                     "/api/admin/auth/login/", "/api/admin/dashboard/"):
            self.assertIsNotNone(resolve(ruta))
        for ruta in ("/api/admin/reportes/", "/api/admin/reportes/1/",
                     "/api/admin/categorias/", "/api/admin/categorias/1/", "/admin/"):
            with self.assertRaises(Resolver404):
                resolve(ruta)

    @override_settings(HABILITAR_EXTRAS=True)
    def test_extras_se_pueden_reactivar_sin_recuperar_codigo_eliminado(self):
        self.recargar_rutas()
        for ruta in ("/api/admin/reportes/", "/api/admin/categorias/", "/admin/"):
            self.assertIsNotNone(resolve(ruta))
