import os
from datetime import datetime, timedelta, timezone as dt_timezone

import bcrypt
import jwt
from django.db import IntegrityError, transaction
from django.utils import timezone
from rest_framework import mixins, status, viewsets
from rest_framework.decorators import action
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

    @transaction.atomic
    def post(self, request):
        serializer = LoginSerializer(data=request.data)
        serializer.is_valid(raise_exception=True)

        correo = serializer.validated_data["correo"].strip().lower()
        password = serializer.validated_data["password"]

        usuario = Usuario.objects.select_for_update().filter(correo=correo).first()
        if usuario is None:
            return Response(
                {"detail": "Credenciales inválidas"},
                status=status.HTTP_401_UNAUTHORIZED,
            )

        ahora = timezone.now()
        if usuario.bloqueado_hasta and usuario.bloqueado_hasta > ahora:
            return Response({"detail": "Tu cuenta está bloqueada temporalmente. Intenta de nuevo más tarde."}, status=423)
        if usuario.bloqueado_hasta:
            usuario.intentos_login = 0
            usuario.bloqueado_hasta = None

        try:
            password_ok = bcrypt.checkpw(
                password.encode("utf-8"), usuario.password.encode("utf-8")
            )
        except ValueError:
            password_ok = False

        if not password_ok:
            usuario.intentos_login += 1
            if usuario.intentos_login >= 5:
                usuario.bloqueado_hasta = ahora + timedelta(minutes=15)
            usuario.save(update_fields=["intentos_login", "bloqueado_hasta"])
            if usuario.bloqueado_hasta:
                return Response({"detail": "Tu cuenta está bloqueada por 15 minutos tras cinco intentos fallidos."}, status=423)
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

        usuario.intentos_login = 0
        usuario.bloqueado_hasta = None
        usuario.save(update_fields=["intentos_login", "bloqueado_hasta"])
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
        from .metricas import metricas_dashboard

        datos = {
            **metricas_dashboard(request.query_params),
            "usuarios": Usuario.objects.count(),
            "objetosActivos": Objeto.objects.filter(estado="ACTIVO").count(),
            "reportesPendientes": Reporte.objects.filter(estado="PENDIENTE").count(),
            "categorias": Categoria.objects.count(),
        }
        return Response(datos)


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


class ReporteViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    """H28: consultar y resolver; no crear, reasignar ni borrar denuncias."""

    queryset = Reporte.objects.select_related("usuario", "objeto").order_by("id")
    serializer_class = ReporteSerializer
    permission_classes = [IsAuthenticated, EsAdmin]

    def partial_update(self, request, *args, **kwargs):
        from .resolucion_reportes import resolver_reporte
        return resolver_reporte(request, kwargs["pk"])

    @action(detail=True, methods=["post"])
    def resolver(self, request, pk=None):
        from .resolucion_reportes import resolver_reporte
        return resolver_reporte(request, pk)
