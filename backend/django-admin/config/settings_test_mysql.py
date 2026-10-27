"""SOLO QA: servidor aislado con guarda; nunca usa los valores de la BD normal."""
import os
import re
from pathlib import Path

import pymysql
from django.core.exceptions import ImproperlyConfigured

from .settings_test import *  # noqa: F401,F403


def requerida(nombre):
    valor = os.environ.get(nombre)
    if not valor:
        raise ImproperlyConfigured(f"Falta {nombre}; no se permite usar la base normal.")
    return valor


nombre = requerida("FOUNDIA_QA_MYSQL_DATABASE")
puerto = int(requerida("FOUNDIA_QA_MYSQL_PORT"))
password = requerida("FOUNDIA_QA_MYSQL_PASSWORD")
token = requerida("FOUNDIA_QA_MYSQL_TOKEN")
usuario = requerida("FOUNDIA_QA_MYSQL_USER")
directorio = Path(requerida("FOUNDIA_QA_MYSQL_DATADIR")).resolve()
if not re.fullmatch(r"foundia_s2_qa_[a-f0-9]{12}", nombre) or not 1024 < puerto <= 65535 or puerto == 3306:
    raise ImproperlyConfigured("Se requiere un nombre QA y un puerto aislado.")
with pymysql.connect(host="127.0.0.1", port=puerto, user=usuario, password=password, database=nombre) as conexion:
    with conexion.cursor() as cursor:
        cursor.execute("SELECT token FROM foundia_qa_guard")
        if cursor.fetchall() != ((token,),):
            raise ImproperlyConfigured("La base no pertenece a esta ejecución QA.")
        cursor.execute("SELECT @@datadir")
        if Path(cursor.fetchone()[0]).resolve() != directorio:
            raise ImproperlyConfigured("El servidor no usa el directorio QA autorizado.")

DATABASES = {"default": {
    "ENGINE": "django.db.backends.mysql", "NAME": nombre, "USER": usuario,
    "PASSWORD": password, "HOST": "127.0.0.1", "PORT": puerto,
    "TEST": {"NAME": "test_" + nombre},
    "OPTIONS": {"charset": "utf8mb4", "init_command": "SET time_zone='+00:00', sql_mode='STRICT_TRANS_TABLES'"},
}}
