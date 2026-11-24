"""H34: después de restaurar una versión anterior (H70), vuelve a anonimizar las cuentas ya eliminadas."""
from django.core.management.base import BaseCommand

from nucleo.eliminacion import reaplicar_eliminaciones


class Command(BaseCommand):
    help = "Vuelve a anonimizar las cuentas de solicitudes ATENDIDA que una restauración trajo de vuelta."

    def handle(self, *args, **opciones):
        vueltas = reaplicar_eliminaciones()
        self.stdout.write(f"Cuentas anonimizadas de nuevo: {vueltas}")
