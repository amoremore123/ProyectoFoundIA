from django.apps import apps
from django.test.runner import DiscoverRunner


class ModelosNoGestionadosTestRunner(DiscoverRunner):
    """Crea temporalmente las tablas de los modelos managed=False en la base de pruebas."""

    def setup_test_environment(self, *args, **kwargs):
        self._no_gestionados = [m for m in apps.get_app_config("nucleo").get_models() if not m._meta.managed]
        for modelo in self._no_gestionados:
            modelo._meta.managed = True
        super().setup_test_environment(*args, **kwargs)

    def teardown_test_environment(self, *args, **kwargs):
        super().teardown_test_environment(*args, **kwargs)
        for modelo in self._no_gestionados:
            modelo._meta.managed = False
