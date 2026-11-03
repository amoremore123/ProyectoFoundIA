"""Autenticacion JWT compatible con la emision de Spring Boot (HS256)."""
import os
import math

import jwt
from django.utils import timezone
from rest_framework.authentication import BaseAuthentication
from rest_framework.exceptions import AuthenticationFailed

from .models import Usuario

# Mismo default que usa Spring Boot en desarrollo; sobrescribir con JWT_SECRET.
JWT_SECRET_DEFAULT = "foundia-dev-jwt-secret-cambiar-en-produccion-123456"


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
            identidad = payload.get("sub")
            if not isinstance(identidad, str) or not identidad:
                raise ValueError("token sin sub")
            # Spring emite el correo. Se conservan los tokens Django anteriores por ID.
            if identidad.isdecimal():
                usuario = Usuario.objects.get(pk=int(identidad))
            else:
                usuario = Usuario.objects.get(correo__iexact=identidad)
        except AuthenticationFailed:
            raise
        except Exception:
            raise AuthenticationFailed("Token inválido")

        if usuario.bloqueado_hasta and usuario.bloqueado_hasta > timezone.now():
            raise AuthenticationFailed("Tu cuenta está bloqueada temporalmente")

        # H35: la recuperación invalida también los JWT del panel administrativo.
        # MySQL y los JWT de Spring guardan este límite con precisión de segundos.
        if usuario.password_actualizado_en:
            emitido = payload.get("iat")
            cambio = int(usuario.password_actualizado_en.timestamp())
            if (isinstance(emitido, bool) or not isinstance(emitido, (int, float))
                    or not math.isfinite(emitido) or emitido < cambio):
                raise AuthenticationFailed("Tu contraseña cambió. Inicia sesión de nuevo")

        # DRF asigna request.user con el primer elemento de la tupla.
        request.user = usuario
        request.auth = payload
        return (usuario, payload)

    def authenticate_header(self, request):
        # Permite que DRF devuelva 401 (y no 403) cuando no hay token.
        return self.keyword
