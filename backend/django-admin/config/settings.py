"""
Configuracion del proyecto Django Admin (FoundIA).

Base de datos: las tablas las crea database/script.sql (esquema de verdad).
Este proyecto NO genera migraciones (nucleo/models.py usa managed=False).

Para preparar la BD en local:
    1) Ejecutar database/script.sql contra MySQL 8.
    2) py manage.py migrate   (solo crea tablas de django_content_type/auth/sessions)
    3) py manage.py createsuperuser  (opcional, para /admin/ de Django)
"""
import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent

SECRET_KEY = os.environ.get(
    "DJANGO_SECRET_KEY",
    "django-insecure-clave-desarrollo-foundia-cambiar-en-produccion",
)

DEBUG = os.environ.get("DJANGO_DEBUG", "True").lower() in ("1", "true", "yes", "on")

ALLOWED_HOSTS = ["*"] if DEBUG else [
    h.strip() for h in os.environ.get("DJANGO_HOSTS", "").split(",") if h.strip()
]

INSTALLED_APPS = [
    "django.contrib.admin",
    "django.contrib.auth",
    "django.contrib.contenttypes",
    "django.contrib.sessions",
    "django.contrib.messages",
    "django.contrib.staticfiles",
    "rest_framework",
    "corsheaders",
    "nucleo",
]

MIDDLEWARE = [
    "corsheaders.middleware.CorsMiddleware",
    "django.middleware.security.SecurityMiddleware",
    "django.contrib.sessions.middleware.SessionMiddleware",
    "django.middleware.common.CommonMiddleware",
    "django.middleware.csrf.CsrfViewMiddleware",
    "django.contrib.auth.middleware.AuthenticationMiddleware",
    "django.contrib.messages.middleware.MessageMiddleware",
    "django.middleware.clickjacking.XFrameOptionsMiddleware",
]

ROOT_URLCONF = "config.urls"

TEMPLATES = [
    {
        "BACKEND": "django.template.backends.django.DjangoTemplates",
        "DIRS": [],
        "APP_DIRS": True,
        "OPTIONS": {
            "context_processors": [
                "django.template.context_processors.request",
                "django.contrib.auth.context_processors.auth",
                "django.contrib.messages.context_processors.messages",
            ],
        },
    },
]

WSGI_APPLICATION = "config.wsgi.application"

# Las tablas reales las crea database/script.sql. `py manage.py migrate`
# solo crea las tablas internas de Django (contenttypes, auth, sessions).
DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.mysql",
        "NAME": os.environ.get("DB_NAME", "objetos_perdidos_db"),
        "USER": os.environ.get("DB_USERNAME", "root"),
        # XAMPP usa root sin contraseña; en Docker/.env se define DB_PASSWORD.
        "PASSWORD": os.environ.get("DB_PASSWORD", ""),
        "HOST": os.environ.get("DB_HOST", "localhost"),
        "PORT": os.environ.get("DB_PORT", "3306"),
        "OPTIONS": {
            "charset": "utf8mb4",
            "init_command": "SET sql_mode='STRICT_TRANS_TABLES'",
        },
    }
}

# Compatibilidad con XAMPP local: MariaDB 10.4 no llega al 10.5 que exige
# Django 5.1+, pero en Docker usamos MySQL 8.0 (siempre se omite el requisito
# SOLO cuando se define DB_SKIP_MARIADB_VERSION_CHECK=1 en el entorno).
if os.environ.get("DB_SKIP_MARIADB_VERSION_CHECK") == "1":
    from django.db.backends.mysql.base import DatabaseWrapper

    DatabaseWrapper.check_database_version_supported = lambda self: None

AUTH_PASSWORD_VALIDATORS = [
    {"NAME": "django.contrib.auth.password_validation.UserAttributeSimilarityValidator"},
    {"NAME": "django.contrib.auth.password_validation.MinimumLengthValidator"},
    {"NAME": "django.contrib.auth.password_validation.CommonPasswordValidator"},
    {"NAME": "django.contrib.auth.password_validation.NumericPasswordValidator"},
]

LANGUAGE_CODE = "es"

TIME_ZONE = "UTC"

USE_I18N = True

USE_TZ = True

STATIC_URL = "static/"

DEFAULT_AUTO_FIELD = "django.db.models.BigAutoField"

REST_FRAMEWORK = {
    "DEFAULT_AUTHENTICATION_CLASSES": [
        "nucleo.authentication.JwtAuthentication",
    ],
    "DEFAULT_PERMISSION_CLASSES": [
        "rest_framework.permissions.IsAuthenticated",
    ],
    "DEFAULT_PAGINATION_CLASS": "rest_framework.pagination.PageNumberPagination",
    "PAGE_SIZE": 10,
}

# CORS: permite que el panel React admin (:5174) llame a esta API
CORS_ALLOWED_ORIGINS = [
    o.strip()
    for o in os.environ.get("CORS_ALLOWED_ORIGINS", "http://localhost:5174").split(",")
    if o.strip()
]
