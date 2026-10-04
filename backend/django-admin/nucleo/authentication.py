"""Autenticacion JWT compatible con la emision de Spring Boot (HS256)."""
import os

import jwt
from rest_framework.authentication import BaseAuthentication
from rest_framework.exceptions import AuthenticationFailed

from .models import Usuario

# Mismo default que usa Spring Boot en desarrollo; sobrescribir con JWT_SECRET.
JWT_SECRET_DEFAULT = "foundia-jwt-secret-key-cambiar-en-produccion"


def get_jwt_secret() -> str:
    return os.environ.get("JWT_SECRET", JWT_SECRET_DEFAULT)


class JwtAuthentication(BaseAuthentication):
    """Header: Authorization: Bearer <token>."""

    keyword = "Bearer"

    def authenticate(self, request):
        auth_header = request.headers.get("Authorization", "")
        if not auth_header:
            return None

        parts = auth_header.split()
        if len(parts) != 2 or parts[0].lower() != self.keyword.lower():
            return None

        token = parts[1]
        try:
            payload = jwt.decode(
                token,
                get_jwt_secret(),
                algorithms=["HS256"],
            )
            usuario_id = payload.get("sub")
            if usuario_id is None:
                raise ValueError("token sin sub")
            usuario = Usuario.objects.get(pk=int(usuario_id))
        except AuthenticationFailed:
            raise
        except Exception:
            raise AuthenticationFailed("Token inválido")

        # DRF asigna request.user con el primer elemento de la tupla.
        request.user = usuario
        request.auth = payload
        return (usuario, payload)

    def authenticate_header(self, request):
        # Permite que DRF devuelva 401 (y no 403) cuando no hay token.
        return self.keyword
