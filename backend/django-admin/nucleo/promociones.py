"""H66: preparar y registrar promociones de campañas en redes sociales (sin conectar las APIs de las redes)."""
import re
from datetime import timedelta
from urllib.parse import urlencode, urlsplit

from django.conf import settings
from django.utils import timezone
from rest_framework import mixins, serializers, viewsets
from rest_framework.decorators import action
from rest_framework.exceptions import ValidationError
from rest_framework.permissions import IsAuthenticated
from rest_framework.response import Response

from .models import Campana, PromocionCampana
from .permissions import EsAdmin

REDES = {
    "FACEBOOK": (2200, ("facebook.com", "fb.com", "fb.watch")),
    "INSTAGRAM": (2200, ("instagram.com",)),
    "TIKTOK": (2200, ("tiktok.com",)),
    "X": (280, ("x.com", "twitter.com")),
    "LINKEDIN": (2200, ("linkedin.com",)),
    "WHATSAPP": (2200, ("whatsapp.com", "wa.me")),
    "OTRA": (2200, None),
}
ETIQUETAS = "#FoundIA #Tecsup"
TOLERANCIA_RELOJ = timedelta(minutes=5)
CON_ZONA = re.compile(r"(Z|[+-]\d{2}:?\d{2})$", re.IGNORECASE)


def enlace_de(campana):
    # El mismo enlace con origen que muestra la campaña (H60) y que cuenta H67
    return f"{settings.FOUNDIA_WEB_URL}{campana.destino}?{urlencode({'origen': campana.origen})}"


def texto_sugerido(campana, red):
    maximo = REDES[red][0]
    enlace = enlace_de(campana)
    descripcion = (campana.descripcion or "").strip()
    fijo = f"{campana.nombre}. {{}}Más información: {enlace} {ETIQUETAS}"
    texto = fijo.format(f"{descripcion} " if descripcion else "")
    if len(texto) <= maximo:
        return texto
    # Se acorta la descripción; el enlace nunca se corta
    espacio = maximo - len(fijo.format("")) - 2  # «… »
    if espacio > 0 and descripcion:
        return fijo.format(descripcion[:espacio].rstrip() + "… ")
    return fijo.format("")[:maximo]


class FechaConZona(serializers.DateTimeField):
    def to_internal_value(self, valor):
        if not isinstance(valor, str) or not CON_ZONA.search(valor.strip()):
            raise ValidationError("Indica la zona horaria (por ejemplo, 2026-11-24T15:30:00-05:00).")
        return super().to_internal_value(valor)


class PromocionSerializer(serializers.ModelSerializer):
    campanaId = serializers.PrimaryKeyRelatedField(
        source="campana", queryset=Campana.objects.all(),
        error_messages={"does_not_exist": "No existe la campaña {pk_value}."})
    red = serializers.ChoiceField(choices=tuple(REDES), error_messages={
        "invalid_choice": "Usa FACEBOOK, INSTAGRAM, TIKTOK, X, LINKEDIN, WHATSAPP u OTRA."})
    texto = serializers.CharField(min_length=3, max_length=2200, trim_whitespace=True)
    publicadaEn = FechaConZona(source="publicada_en")
    urlPublicacion = serializers.CharField(source="url_publicacion", max_length=500, required=False,
                                           allow_blank=True, allow_null=True, trim_whitespace=True)
    campana = serializers.SerializerMethodField()
    registradaPor = serializers.SerializerMethodField()
    fechaRegistro = serializers.DateTimeField(source="fecha_registro", read_only=True)

    class Meta:
        model = PromocionCampana
        fields = ["id", "campanaId", "campana", "red", "texto", "enlace", "urlPublicacion", "publicadaEn",
                  "registradaPor", "fechaRegistro"]
        read_only_fields = ["id", "enlace"]

    def get_campana(self, obj) -> dict:
        return {"id": obj.campana_id, "nombre": obj.campana.nombre, "origen": obj.campana.origen}

    def get_registradaPor(self, obj) -> dict:
        return {"id": obj.registrada_por_id,
                "nombre": f"{obj.registrada_por.nombre} {obj.registrada_por.apellido}".strip()}

    def validate_publicadaEn(self, valor):
        if valor > timezone.now() + TOLERANCIA_RELOJ:
            raise ValidationError("No puede ser una fecha futura: registra la promoción después de publicarla.")
        return valor

    def validate(self, attrs):
        if self.instance is not None:
            if "campana" in attrs and attrs["campana"].pk != self.instance.campana_id:
                raise ValidationError({"campanaId": "No se cambia: registra otra promoción para esa campaña."})
            if "red" in attrs and attrs["red"] != self.instance.red:
                raise ValidationError({"red": "No se cambia: registra otra promoción para esa red."})
        red = attrs.get("red", getattr(self.instance, "red", None))
        maximo, dominios = REDES[red]
        texto = attrs.get("texto")
        if texto is not None and len(texto) > maximo:
            raise ValidationError({"texto": f"En {red} el texto admite hasta {maximo} caracteres; tiene {len(texto)}."})
        if "url_publicacion" in attrs:
            attrs["url_publicacion"] = self._direccion(attrs["url_publicacion"], red, dominios)
        return attrs

    @staticmethod
    def _direccion(valor, red, dominios):
        if not valor:
            return None
        partes = urlsplit(valor)
        anfitrion = (partes.hostname or "").lower()
        if partes.scheme != "https" or not anfitrion or partes.username or partes.password:
            raise ValidationError({"urlPublicacion": "Debe ser una dirección https:// de la publicación."})
        if dominios and not any(anfitrion == d or anfitrion.endswith("." + d) for d in dominios):
            raise ValidationError({"urlPublicacion": f"No es una dirección de {red} ({', '.join(dominios)})."})
        return valor


class PromocionViewSet(mixins.ListModelMixin, mixins.RetrieveModelMixin, mixins.CreateModelMixin,
                       mixins.UpdateModelMixin, viewsets.GenericViewSet):
    """H66 - Promociones en redes, solo ADMIN. No publica en las redes: prepara el texto y registra lo publicado."""

    serializer_class = PromocionSerializer
    permission_classes = [IsAuthenticated, EsAdmin]

    def get_queryset(self):
        consulta = (PromocionCampana.objects.select_related("campana", "registrada_por")
                    .order_by("-publicada_en", "-id"))
        params = self.request.query_params
        if params.get("campana"):
            if not params["campana"].isdigit():
                raise ValidationError({"campana": "Debe ser el id de una campaña."})
            consulta = consulta.filter(campana_id=int(params["campana"]))
        if params.get("red"):
            if params["red"] not in REDES:
                raise ValidationError({"red": "Red no válida."})
            consulta = consulta.filter(red=params["red"])
        return consulta

    def perform_create(self, serializer):
        campana = serializer.validated_data["campana"]
        serializer.save(enlace=enlace_de(campana), registrada_por=self.request.user, fecha_registro=timezone.now())

    @action(detail=False, methods=["get"])
    def sugerencia(self, request):
        params = request.query_params
        if not (params.get("campana") or "").isdigit():
            raise ValidationError({"campana": "Indica el id de la campaña."})
        red = params.get("red")
        if red not in REDES:
            raise ValidationError({"red": "Usa FACEBOOK, INSTAGRAM, TIKTOK, X, LINKEDIN, WHATSAPP u OTRA."})
        campana = Campana.objects.filter(pk=int(params["campana"])).first()
        if campana is None:
            raise ValidationError({"campana": "No existe esa campaña."})
        return Response({"campana": campana.id, "red": red, "texto": texto_sugerido(campana, red),
                         "enlace": enlace_de(campana), "maximo": REDES[red][0]})

