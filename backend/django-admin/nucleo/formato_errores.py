"""Mismo formato de error que Spring: toda respuesta de error de /api/ trae `status` y `mensaje`.

Spring responde `{"status": 400, "mensaje": "..."}`. Django respondía `{"detail": "..."}`, errores por campo
(`{"titulo": ["..."]}`) o una lista (`["..."]`), así que la web y Android tenían que leer las tres formas.
Este middleware **agrega** `status` y `mensaje` sin quitar nada: `detail` y los errores por campo siguen
igual, para no romper a quien ya los lee. Una lista se convierte en `{"detail", "mensaje", "status",
"errores"}`, porque a una lista no se le puede agregar un campo.
"""
import json
import re

# get_object_or_404 de Django responde en inglés («No Campana matches the given query.»), aunque el
# resto de la API esté en español. Se reemplaza por el mensaje de DRF.
NO_ENCONTRADO_INGLES = re.compile(r"^No \w+ matches the given query\.$")
NO_ENCONTRADO = "No encontrado."


def _texto(valor):
    """El primer mensaje legible dentro de un error de DRF (texto, lista o diccionario)."""
    if isinstance(valor, str):
        return valor
    if isinstance(valor, list):
        for elemento in valor:
            texto = _texto(elemento)
            if texto:
                return texto
        return None
    if isinstance(valor, dict):
        if "detail" in valor:
            return _texto(valor["detail"])
        for campo, contenido in valor.items():
            texto = _texto(contenido)
            if texto:
                return texto if campo in ("non_field_errors", "detail") else f"{campo}: {texto}"
    return None


def mensaje_de(datos, status):
    return _texto(datos) or f"Error {status}"


class FormatoErrorMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response

    def __call__(self, request):
        respuesta = self.get_response(request)
        if (respuesta.status_code < 400 or not request.path.startswith("/api/")
                or "application/json" not in respuesta.get("Content-Type", "")
                or getattr(respuesta, "streaming", False)):
            return respuesta
        try:
            datos = json.loads(respuesta.content.decode("utf-8"))
        except (ValueError, UnicodeDecodeError):
            return respuesta
        if isinstance(datos, list):
            texto = mensaje_de(datos, respuesta.status_code)
            datos = {"detail": texto, "mensaje": texto, "status": respuesta.status_code, "errores": datos}
        elif isinstance(datos, dict):
            if isinstance(datos.get("detail"), str) and NO_ENCONTRADO_INGLES.match(datos["detail"]):
                datos["detail"] = NO_ENCONTRADO
            if "mensaje" in datos and not isinstance(datos["mensaje"], str):
                # Un error del campo «mensaje» (por ejemplo, en H69) no se pisa: va en errores
                datos = {**datos, "errores": {"mensaje": datos["mensaje"]}}
                datos["mensaje"] = mensaje_de({"mensaje": datos["errores"]["mensaje"]}, respuesta.status_code)
            datos.setdefault("mensaje", mensaje_de(datos, respuesta.status_code))
            datos.setdefault("status", respuesta.status_code)
        else:
            return respuesta
        respuesta.content = json.dumps(datos, ensure_ascii=False).encode("utf-8")
        if respuesta.has_header("Content-Length"):
            respuesta["Content-Length"] = str(len(respuesta.content))
        return respuesta
