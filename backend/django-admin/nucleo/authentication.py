"""Autenticacion JWT compatible con la emision de Spring Boot (HS256)."""
import os
import math
import hashlib
import hmac
import re

import jwt
from django.utils import timezone
from rest_framework.authentication import BaseAuthentication
from rest_framework.exceptions import AuthenticationFailed

from .models import Usuario, TokenRevocado

# Mismo default que usa Spring Boot en desarrollo; sobrescribir con JWT_SECRET.
JWT_SECRET_DEFAULT = "foundia-dev-jwt-secret-cambiar-en-produccion-123456"


def get_jwt_secret() -> str:
    return os.environ.get("JWT_SECRET", JWT_SECRET_DEFAULT)


def sello_credencial(usuario) -> str:
    """HMAC compartido con Spring, sin incluir el hash BCrypt en el JWT."""
    datos = b"foundia-session-v1\0" + usuario.correo.encode("utf-8") + b"\0" + usuario.password.encode("utf-8")
    return hmac.new(get_jwt_secret().encode("utf-8"), datos, hashlib.sha256).hexdigest()


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

        # H35: el sello cambia con la credencial, incluso dentro del mismo segundo.
        if "cred" in payload:
            sello = payload["cred"]
            emitido = payload.get("iat")
            if (not isinstance(sello, str) or re.fullmatch(r"[0-9a-f]{64}", sello) is None
                    or isinstance(emitido, bool) or not isinstance(emitido, (int, float))
                    or not math.isfinite(emitido)
                    or not hmac.compare_digest(sello, sello_credencial(usuario))):
                raise AuthenticationFailed("Tu contraseña cambió. Inicia sesión de nuevo")
        elif usuario.password_actualizado_en:
            # Un sello presente pero inválido nunca se trata como token legado.
            raise AuthenticationFailed("Tu contraseña cambió. Inicia sesión de nuevo")

        # H37: versión de sesión y revocación individual.
        version = payload.get("ver", 0)
        if isinstance(version, bool) or not isinstance(version, int) or version < 0:
            raise AuthenticationFailed("Token inválido")
        if version != (usuario.sesion_version or 0):
            raise AuthenticationFailed("Tu sesión fue cerrada. Inicia sesión de nuevo")

        jti = payload.get("jti")
        if jti is not None:
            if not isinstance(jti, str) or not jti or len(jti) > 64:
                raise AuthenticationFailed("Token inválido")
            if TokenRevocado.objects.filter(jti=jti).exists():
                raise AuthenticationFailed("Tu sesión fue cerrada. Inicia sesión de nuevo")

        # DRF asigna request.user con el primer elemento de la tupla.
        request.user = usuario
        request.auth = payload
        return (usuario, payload)

    def authenticate_header(self, request):
        # Permite que DRF devuelva 401 (y no 403) cuando no hay token.
        return self.keyword
