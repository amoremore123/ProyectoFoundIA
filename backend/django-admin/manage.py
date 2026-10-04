#!/usr/bin/env python
"""Punto de entrada de linea de comandos de Django."""
import os
import sys


def main():
    os.environ.setdefault("DJANGO_SETTINGS_MODULE", "config.settings")
    try:
        from django.core.management import execute_from_command_line
    except ImportError as exc:
        raise ImportError(
            "No se pudo importar Django. Asegurate de que el entorno virtual "
            "esta activo y que 'pip install -r requirements.txt' se ejecuto."
        ) from exc
    execute_from_command_line(sys.argv)


if __name__ == "__main__":
    main()
