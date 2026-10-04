import os
from datetime import datetime, timedelta, timezone as dt_timezone

import bcrypt
import jwt
from django.utils import timezone
from rest_framework import status, viewsets
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .models import Categoria, Objeto, Reporte, Usuario
from .permissions import EsAdmin
from .serializers import (
    CategoriaSerializer,
    LoginSerializer,
    ObjetoSerializer,
    ReporteSerializer,
    UsuarioSerializer,
)

JWT_SECRET_DEFAULT = "foundia-jwt-secret-key-cambiar-en-produccion"
TOKEN_EXPIRE_HOURS = 12


class LoginView(APIView):
    """POST /api/admin/auth/login/"""

    authentication_classes = []
    permission_classes = [AllowAny]

    def post(self, request):
        serializer = LoginSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        correo = serializer.validated_data["correo"]
        password = serializer.validated_data["password"]

        usuario = Usuario.objects.filter(correo=correo).first()
        if usuario is None:
            return Response(
                {"detail": "Credenciales inválidas"},
                status=status.HTTP_401_UNAUTHORIZED,
            )

        try:
            password_ok = bcrypt.checkpw(
                password.encode("utf-8"), usuario.password.encode("utf-8")
            )
        except ValueError:
            password_ok = False

        if not password_ok:
            return Response(
                {"detail": "Credenciales inválidas"},
                status=status.HTTP_401_UNAUTHORIZED,
            )

        if usuario.rol != "ADMIN":
            return Response(
                {"detail": "No autorizado: se requiere rol ADMIN"},
                status=status.HTTP_403_FORBIDDEN,
            )

        if usuario.estado != "ACTIVO":
            return Response(
                {"detail": "Credenciales inválidas"},
                status=status.HTTP_401_UNAUTHORIZED,
            )

        secret = os.environ.get("JWT_SECRET", JWT_SECRET_DEFAULT)
        exp = datetime.now(dt_timezone.utc) + timedelta(hours=TOKEN_EXPIRE_HOURS)
        token = jwt.encode(
            {"sub": str(usuario.id), "rol": usuario.rol, "exp": exp},
            secret,
            "HS256",
        )

        return Response(
            {
                "token": token,
                "usuario": {
                    "id": usuario.id,
                    "nombre": usuario.nombre,
                    "apellido": usuario.apellido,
                    "correo": usuario.correo,
                    "rol": usuario.rol,
                },
            },
            status=status.HTTP_200_OK,
        )


class DashboardView(APIView):
    """GET /api/admin/dashboard/"""

    permission_classes = [IsAuthenticated, EsAdmin]

    def get(self, request):
        return Response(
            {
                "usuarios": Usuario.objects.count(),
                "objetosActivos": Objeto.objects.filter(estado="ACTIVO").count(),
                "reportesPendientes": Reporte.objects.filter(estado="PENDIENTE").count(),
                "categorias": Categoria.objects.count(),
            }
        )


class UsuarioViewSet(viewsets.ModelViewSet):
    """GET/POST /api/admin/usuarios/ · GET/PUT/PATCH/DELETE /api/admin/usuarios/{id}/"""

    queryset = Usuario.objects.all().order_by("id")
    serializer_class = UsuarioSerializer
    permission_classes = [IsAuthenticated, EsAdmin]


class CategoriaViewSet(viewsets.ModelViewSet):
    """GET/POST /api/admin/categorias/ · GET/PUT/PATCH/DELETE /api/admin/categorias/{id}/"""

    queryset = Categoria.objects.all().order_by("id")
    serializer_class = CategoriaSerializer
    permission_classes = [IsAuthenticated, EsAdmin]


class ObjetoViewSet(viewsets.ModelViewSet):
    """GET/POST /api/admin/publicaciones/ · GET/PUT/PATCH/DELETE /api/admin/publicaciones/{id}/"""

    queryset = Objeto.objects.select_related("categoria", "usuario").order_by("id")
    serializer_class = ObjetoSerializer
    permission_classes = [IsAuthenticated, EsAdmin]


class ReporteViewSet(viewsets.ModelViewSet):
    """GET/POST /api/admin/reportes/ · GET/PUT/PATCH/DELETE /api/admin/reportes/{id}/"""

    queryset = Reporte.objects.select_related("usuario", "objeto").order_by("id")
    serializer_class = ReporteSerializer
    permission_classes = [IsAuthenticated, EsAdmin]

    @staticmethod
    def _normalizar_fecha_resolucion(data):
        """Acepta tambien el alias camelCase del contrato (fechaResolucion)."""
        if "fecha_resolucion" not in data and "fechaResolucion" in data:
            data = dict(data)
            data["fecha_resolucion"] = data["fechaResolucion"]
        return data

    def update(self, request, *args, **kwargs):
        return self._actualizar(request, partial=kwargs.pop("partial", False))

    def partial_update(self, request, *args, **kwargs):
        return self._actualizar(request, partial=True)

    def _actualizar(self, request, partial):
        instance = self.get_object()
        data = self._normalizar_fecha_resolucion(request.data)
        serializer = self.get_serializer(instance, data=data, partial=partial)
        serializer.is_valid(raise_exception=True)

        if (
            serializer.validated_data.get("estado") == "RESUELTO"
            and not serializer.validated_data.get("fecha_resolucion")
        ):
            serializer.validated_data["fecha_resolucion"] = timezone.now()

        self.perform_update(serializer)
        return Response(serializer.data)
