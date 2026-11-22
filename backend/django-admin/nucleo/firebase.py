"""H72: revocar en Firebase Auth las sesiones de una cuenta (equivale a revokeRefreshTokens).

Usa la misma cuenta de servicio que Spring, solo desde la variable de entorno
FIREBASE_CREDENCIALES_JSON. Sin ella no hace nada. Se ejecuta después de confirmar la
transacción y en segundo plano: si Google no responde, suspender o cerrar sesión no falla.
El contenido de la clave nunca se registra.
"""
import json
import logging
import threading
import time
import urllib.error
import urllib.parse
import urllib.request

import jwt
from django.conf import settings
from django.db import transaction

log = logging.getLogger(__name__)
ALCANCE = "https://www.googleapis.com/auth/cloud-platform"


def _credenciales():
    texto = getattr(settings, "FIREBASE_CREDENCIALES_JSON", "") or ""
    if not texto.strip():
        return None
    try:
        c = json.loads(texto)
        if c.get("client_email") and c.get("private_key") and (settings.FIREBASE_PROYECTO_ID or c.get("project_id")):
            return c
    except ValueError:
        pass
    log.error("FIREBASE_CREDENCIALES_JSON no es una cuenta de servicio válida; no se revoca en Firebase.")
    return None


def uid(usuario_id):
    return f"foundia-{usuario_id}"


def _token_de_acceso(c):
    ahora = int(time.time())
    asercion = jwt.encode({"iss": c["client_email"], "aud": c.get("token_uri", "https://oauth2.googleapis.com/token"),
                           "scope": ALCANCE, "iat": ahora, "exp": ahora + 3600}, c["private_key"], algorithm="RS256")
    datos = urllib.parse.urlencode({"grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
                                    "assertion": asercion}).encode()
    pedido = urllib.request.Request(c.get("token_uri", "https://oauth2.googleapis.com/token"), data=datos,
                                    headers={"Content-Type": "application/x-www-form-urlencoded"})
    with urllib.request.urlopen(pedido, timeout=15) as r:
        return json.load(r)["access_token"]


def revocar(usuario_id):
    """Revoca ya (sin hilo). Devuelve True si quedó revocado o la cuenta nunca entró a Firebase."""
    c = _credenciales()
    if c is None:
        return False
    proyecto = settings.FIREBASE_PROYECTO_ID or c.get("project_id")
    base = getattr(settings, "FIREBASE_IDENTITYTOOLKIT_URL", "https://identitytoolkit.googleapis.com").rstrip("/")
    try:
        cuerpo = json.dumps({"localId": uid(usuario_id), "validSince": str(int(time.time()))}).encode()
        pedido = urllib.request.Request(f"{base}/v1/projects/{proyecto}/accounts:update", data=cuerpo, headers={
            "Authorization": f"Bearer {_token_de_acceso(c)}", "Content-Type": "application/json"})
        with urllib.request.urlopen(pedido, timeout=15):
            return True
    except urllib.error.HTTPError as e:
        if "USER_NOT_FOUND" in e.read().decode("utf-8", "replace"):
            return True
        log.warning("Firebase respondió %s al revocar las sesiones de la cuenta %s", e.code, usuario_id)
    except Exception as e:  # noqa: BLE001 - nunca debe romper la suspensión ni el cierre de sesión
        log.warning("No se pudieron revocar las sesiones de Firebase de la cuenta %s: %s", usuario_id, type(e).__name__)
    return False


def revocar_al_confirmar(usuario_id):
    """Programa el revocado para después de la transacción actual, en un hilo aparte."""
    if _credenciales() is None:
        return
    transaction.on_commit(lambda: threading.Thread(target=revocar, args=(usuario_id,), daemon=True).start())
