"""Configuración SOLO para pruebas automáticas del panel (H15).

Usa una base SQLite en memoria: nunca toca la base MySQL del equipo.
Ejecutar:  python manage.py test nucleo --settings=config.settings_test
"""
from .settings import *  # noqa: F401,F403

DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.sqlite3",
        "NAME": ":memory:",
    }
}

# Los modelos del dominio son managed=False (las tablas las crea database/script.sql).
# Este corredor los vuelve "managed" solo durante las pruebas para crear sus tablas.
TEST_RUNNER = "nucleo.test_runner.ModelosNoGestionadosTestRunner"
