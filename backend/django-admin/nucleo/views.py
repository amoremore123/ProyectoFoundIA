import os
from datetime import datetime, timedelta, timezone as dt_timezone

import bcrypt
import jwt
from django.db import IntegrityError, transaction
from django.db.models import Q
from django.http import HttpResponse
from django.utils import timezone
from django.utils.dateparse import parse_date
from rest_framework import mixins, status, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import ValidationError
from rest_framework.permissions import AllowAny, IsAuthenticated
from rest_framework.response import Response
from rest_framework.views import APIView

from .custodia import constancia_pdf, etiqueta_pdf, firmar_token_custodia, verificar_token_custodia
from .models import (
    ROL_CHOICES,
    CambioRol,
    Categoria,
    Custodia,
    Objeto,
    Reporte,
    SecuenciaCustodia,
    Usuario,
)
from .authentication import get_jwt_secret, sello_credencial
from .permissions import EsAdmin, EsBibliotecaOAdmin
from .serializers import (
    CategoriaSerializer,
    CustodiaCreateSerializer,
    CustodiaObjetoSerializer,
    CustodiaSerializer,
    LoginSerializer,
    ObjetoSerializer,
    ReporteSerializer,
    UsuarioSerializer,
)

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

        # H36: BIBLIOTECA entra al panel solo para custodia y reclamos;
        # las vistas de sistema siguen exigiendo EsAdmin.
        if usuario.rol not in ("ADMIN", "BIBLIOTECA"):
            return Response(
                {"detail": "No autorizado: se requiere rol ADMIN o BIBLIOTECA"},
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
        secret = get_jwt_secret()
        emitido = datetime.now(dt_timezone.utc)
        exp = emitido + timedelta(hours=TOKEN_EXPIRE_HOURS)
        token = jwt.encode(
            {"sub": usuario.correo, "rol": usuario.rol, "iat": emitido, "exp": exp,
             "cred": sello_credencial(usuario)},
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
    """H15/H36 - GET /api/admin/usuarios/ y /{id}/.

    PATCH /{id}/ {"estado": "ACTIVO" | "SUSPENDIDO"} conserva H15.
    PATCH /{id}/ {"rol": "...", "confirmar": true, "motivo": "..."} cambia el rol
    solo si lo pide un ADMIN y deja auditoría. Nunca se quita el rol al último
    ADMIN ni al propio administrador.
    """

    queryset = Usuario.objects.all().order_by("id")
    serializer_class = UsuarioSerializer
    permission_classes = [IsAuthenticated, EsAdmin]

    def partial_update(self, request, *args, **kwargs):
        usuario = self.get_object()
        campos = set(request.data.keys())

        if "rol" in campos:
            return self._cambiar_rol(request, usuario)

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

    def _cambiar_rol(self, request, usuario):
        if set(request.data.keys()) - {"rol", "confirmar", "motivo"}:
            return Response(
                {"detail": "El cambio de rol solo admite rol, confirmar y motivo."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        if request.data.get("confirmar") is not True:
            return Response(
                {"detail": "Debes confirmar el cambio de rol con confirmar=true."},
                status=status.HTTP_400_BAD_REQUEST,
            )
        nuevo = request.data.get("rol")
        permitidos = {clave for clave, _ in ROL_CHOICES}
        if nuevo not in permitidos:
            return Response(
                {"detail": f"Rol inválido. Valores permitidos: {', '.join(sorted(permitidos))}."},
                status=status.HTTP_400_BAD_REQUEST,
            )

        with transaction.atomic():
            # El bloqueo evita que dos ADMIN degraden al mismo tiempo al último ADMIN.
            actual = Usuario.objects.select_for_update().get(pk=usuario.pk)
            if actual.rol == nuevo:
                return Response(
                    {"detail": f"La cuenta ya tiene el rol {nuevo}."},
                    status=status.HTTP_400_BAD_REQUEST,
                )
            if actual.pk == request.user.pk:
                return Response(
                    {"detail": "No puedes quitarte a ti mismo el rol de administrador."},
                    status=status.HTTP_400_BAD_REQUEST,
                )
            if actual.rol == "ADMIN" and not Usuario.objects.filter(rol="ADMIN").exclude(pk=actual.pk).exists():
                return Response(
                    {"detail": "Debe quedar al menos un ADMIN en el sistema."},
                    status=status.HTTP_400_BAD_REQUEST,
                )

            anterior = actual.rol
            actual.rol = nuevo
            actual.save(update_fields=["rol", "fecha_actualizacion"])
            cambio = CambioRol.objects.create(
                usuario=actual,
                rol_anterior=anterior,
                rol_nuevo=nuevo,
                cambiado_por=request.user,
                motivo=(request.data.get("motivo") or "").strip() or None,
            )

        datos = UsuarioSerializer(actual).data
        datos["cambioRol"] = {
            "id": cambio.id,
            "rolAnterior": anterior,
            "rolNuevo": nuevo,
            "cambiadoPor": request.user.correo,
            "fecha": cambio.fecha_cambio,
        }
        return Response(datos)

    @action(detail=True, methods=["get"], url_path="cambios-rol")
    def cambios_rol(self, request, pk=None):
        usuario = self.get_object()
        cambios = CambioRol.objects.filter(usuario=usuario).select_related("cambiado_por")[:50]
        return Response([
            {
                "id": cambio.id,
                "rolAnterior": cambio.rol_anterior,
                "rolNuevo": cambio.rol_nuevo,
                "cambiadoPor": cambio.cambiado_por.correo,
                "motivo": cambio.motivo,
                "fecha": cambio.fecha_cambio,
            }
            for cambio in cambios
        ])


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


def _siguiente_codigo_custodia(anio):
    """H41: consecutivo por año, protegido con bloqueo de fila y reintento."""
    for _ in range(5):
        try:
            with transaction.atomic():
                secuencia, _ = SecuenciaCustodia.objects.select_for_update().get_or_create(
                    anio=anio, defaults={"ultimo": 0})
                secuencia.ultimo += 1
                secuencia.save(update_fields=["ultimo"])
                return f"FND-{anio}-{secuencia.ultimo:04d}"
        except IntegrityError:
            continue
    raise IntegrityError("No se pudo generar el código único de custodia.")


class CustodiaViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, viewsets.GenericViewSet):
    """H41 - recepción de objetos en custodia.

    GET  /api/admin/custodias/            lista con filtros q, estado, categoriaId, desde, hasta
    POST /api/admin/custodias/            {"objetoId": 12, "observaciones": "..."}
    GET  /api/admin/custodias/{id}/       detalle
    GET  /api/admin/custodias/{id}/etiqueta/    PDF con QR y código (sin datos personales)
    GET  /api/admin/custodias/{id}/constancia/  PDF de recepción
    GET  /api/admin/custodias/verificar/?token=...  verifica el QR firmado

    Solo BIBLIOTECA y ADMIN. El estado del objeto pasa a EN_CUSTODIA en la
    misma transacción; nunca se registra dos veces el mismo objeto.
    """

    queryset = Custodia.objects.select_related(
        "objeto", "objeto__categoria", "recibido_por"
    ).order_by("-fecha_recepcion", "-id")
    serializer_class = CustodiaSerializer
    permission_classes = [IsAuthenticated, EsBibliotecaOAdmin]

    def get_queryset(self):
        qs = super().get_queryset()
        estado = self.request.query_params.get("estado")
        if estado:
            if estado not in ("EN_CUSTODIA", "RETIRADO"):
                raise ValidationError({"estado": "Estado inválido; usa EN_CUSTODIA o RETIRADO."})
            qs = qs.filter(estado=estado)
        q = (self.request.query_params.get("q") or "").strip()
        if q:
            qs = qs.filter(Q(codigo__icontains=q) | Q(objeto__nombre__icontains=q))
        categoria = self.request.query_params.get("categoriaId")
        if categoria:
            qs = qs.filter(objeto__categoria_id=categoria)
        desde = self.request.query_params.get("desde")
        if desde:
            fecha = parse_date(desde)
            if fecha is None:
                raise ValidationError({"desde": "Fecha inválida; usa AAAA-MM-DD."})
            qs = qs.filter(fecha_recepcion__date__gte=fecha)
        hasta = self.request.query_params.get("hasta")
        if hasta:
            fecha = parse_date(hasta)
            if fecha is None:
                raise ValidationError({"hasta": "Fecha inválida; usa AAAA-MM-DD."})
            qs = qs.filter(fecha_recepcion__date__lte=fecha)
        return qs

    def create(self, request, *args, **kwargs):
        entrada = CustodiaCreateSerializer(data=request.data)
        entrada.is_valid(raise_exception=True)
        objeto_id = entrada.validated_data["objetoId"]
        observaciones = (entrada.validated_data.get("observaciones") or "").strip() or None

        with transaction.atomic():
            objeto = Objeto.objects.select_for_update().select_related("categoria").filter(
                pk=objeto_id).first()
            if objeto is None:
                return Response({"detail": "Publicación no encontrada."},
                                status=status.HTTP_404_NOT_FOUND)
            if objeto.estado in ("ELIMINADO", "RECUPERADO"):
                return Response(
                    {"detail": "La publicación no está disponible para custodia."},
                    status=status.HTTP_400_BAD_REQUEST,
                )
            if objeto.tipo != "ENCONTRADO":
                return Response(
                    {"detail": "Solo se reciben objetos de tipo ENCONTRADO."},
                    status=status.HTTP_400_BAD_REQUEST,
                )
            if Custodia.objects.filter(objeto_id=objeto.id).exists():
                return Response(
                    {"detail": "La publicación ya está registrada en custodia."},
                    status=status.HTTP_409_CONFLICT,
                )

            custodia = Custodia(
                objeto=objeto,
                codigo=_siguiente_codigo_custodia(timezone.localdate().year),
                recibido_por=request.user,
                fecha_recepcion=timezone.now().replace(microsecond=0),
                observaciones=observaciones,
                estado="EN_CUSTODIA",
            )
            custodia.token_qr = firmar_token_custodia(custodia)
            custodia.save()
            objeto.estado = "EN_CUSTODIA"
            objeto.save(update_fields=["estado", "fecha_actualizacion"])

        return Response(CustodiaSerializer(custodia).data, status=status.HTTP_201_CREATED)

    @action(detail=True, methods=["get"], url_path="etiqueta")
    def etiqueta(self, request, pk=None):
        custodia = self.get_object()
        respuesta = HttpResponse(etiqueta_pdf(custodia), content_type="application/pdf")
        respuesta["Content-Disposition"] = (
            f'inline; filename="etiqueta-{custodia.codigo}.pdf"')
        return respuesta

    @action(detail=True, methods=["get"], url_path="constancia")
    def constancia(self, request, pk=None):
        custodia = self.get_object()
        respuesta = HttpResponse(constancia_pdf(custodia), content_type="application/pdf")
        respuesta["Content-Disposition"] = (
            f'attachment; filename="constancia-{custodia.codigo}.pdf"')
        return respuesta

    @action(detail=False, methods=["get"], url_path="verificar")
    def verificar(self, request):
        token = (request.query_params.get("token") or "").strip()
        if not token:
            return Response({"detail": "Falta el token del QR."},
                            status=status.HTTP_400_BAD_REQUEST)
        try:
            datos = verificar_token_custodia(token)
        except jwt.InvalidTokenError:
            return Response({"detail": "El token del QR no es válido."},
                            status=status.HTTP_400_BAD_REQUEST)
        custodia = Custodia.objects.select_related("objeto", "objeto__categoria").filter(
            codigo=datos["sub"], objeto_id=datos.get("objeto")).first()
        if custodia is None:
            return Response({"detail": "No existe una custodia con ese token."},
                            status=status.HTTP_404_NOT_FOUND)
        return Response({
            "valido": True,
            "codigo": custodia.codigo,
            "estado": custodia.estado,
            "objeto": CustodiaObjetoSerializer(custodia.objeto).data,
            "fechaRecepcion": custodia.fecha_recepcion.isoformat(),
        })


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
