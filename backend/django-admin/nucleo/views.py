import os
from datetime import datetime, timedelta, timezone as dt_timezone

import bcrypt
import jwt
from django.db import IntegrityError, transaction
from django.utils import timezone
from rest_framework import mixins, status, viewsets
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


def _solo_estado(request, permitidos):
    """H15: el panel solo cambia el estado. Devuelve (estado, error) para responder 400 en español."""
    campos = set(request.data.keys())
    if campos != {"estado"}:
        return None, "Desde el panel solo se puede cambiar el estado."
    estado = request.data.get("estado")
    if estado not in permitidos:
        return None, f"Estado inválido. Valores permitidos: {', '.join(permitidos)}."
    return estado, None


class UsuarioViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    """H15 - GET /api/admin/usuarios/ y /{id}/ · PATCH /{id}/ {"estado": "ACTIVO" | "SUSPENDIDO"}.

    No se crean ni borran cuentas, ni se cambian rol, correo o contraseña desde el panel.
    """

    queryset = Usuario.objects.all().order_by("id")
    serializer_class = UsuarioSerializer
    permission_classes = [IsAuthenticated, EsAdmin]

    def partial_update(self, request, *args, **kwargs):
        usuario = self.get_object()
        estado, error = _solo_estado(request, ("ACTIVO", "SUSPENDIDO"))
        if error:
            return Response({"detail": error}, status=status.HTTP_400_BAD_REQUEST)
        if usuario.pk == request.user.pk and estado == "SUSPENDIDO":
            return Response(
                {"detail": "No puedes suspender tu propia cuenta de administrador."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        usuario.estado = estado
        usuario.save(update_fields=["estado", "fecha_actualizacion"])
        return Response(UsuarioSerializer(usuario).data)


class CategoriaViewSet(viewsets.ModelViewSet):
    """GET/POST /api/admin/categorias/ · GET/PUT/PATCH/DELETE /api/admin/categorias/{id}/"""

    queryset = Categoria.objects.all().order_by("id")
    serializer_class = CategoriaSerializer
    permission_classes = [IsAuthenticated, EsAdmin]

    def create(self, request, *args, **kwargs):
        try:
            with transaction.atomic():
                return super().create(request, *args, **kwargs)
        except IntegrityError:
            return Response({"nombre": ["Ya existe una categoría con ese nombre."]}, status=400)

    def update(self, request, *args, **kwargs):
        try:
            with transaction.atomic():
                return super().update(request, *args, **kwargs)
        except IntegrityError:
            return Response({"nombre": ["Ya existe una categoría con ese nombre."]}, status=400)

    def destroy(self, request, *args, **kwargs):
        # Desactivar nunca rompe los objetos y filtros asociados al mismo ID.
        categoria = self.get_object()
        categoria.estado = False
        categoria.save(update_fields=["estado"])
        return Response(status=status.HTTP_204_NO_CONTENT)


class ObjetoViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    """H15 - GET /api/admin/publicaciones/ y /{id}/ · PATCH /{id}/ {"estado": "ACTIVO" | "OCULTO" | "ELIMINADO"}.

    Eliminar es lógico (estado ELIMINADO): nunca se borra la fila. Una publicación
    eliminada ya no se puede volver a mostrar desde el panel.
    """

    queryset = Objeto.objects.select_related("categoria", "usuario").order_by("id")
    serializer_class = ObjetoSerializer
    permission_classes = [IsAuthenticated, EsAdmin]

    def partial_update(self, request, *args, **kwargs):
        objeto = self.get_object()
        estado, error = _solo_estado(request, ("ACTIVO", "OCULTO", "ELIMINADO"))
        if error:
            return Response({"detail": error}, status=status.HTTP_400_BAD_REQUEST)
        if objeto.estado == "ELIMINADO" and estado != "ELIMINADO":
            return Response(
                {"detail": "La publicación ya fue eliminada y no se puede volver a mostrar."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        objeto.estado = estado
        objeto.save(update_fields=["estado", "fecha_actualizacion"])
        return Response(ObjetoSerializer(objeto).data)


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
