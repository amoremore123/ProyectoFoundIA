from rest_framework.permissions import BasePermission


class EsAdmin(BasePermission):
    """Solo usuarios con rol ADMIN y estado ACTIVO."""

    message = "No autorizado: se requiere rol ADMIN"

    def has_permission(self, request, view):
        usuario = request.user
        return bool(
            usuario
            and usuario.is_authenticated
            and getattr(usuario, "rol", None) == "ADMIN"
            and getattr(usuario, "estado", None) == "ACTIVO"
        )
