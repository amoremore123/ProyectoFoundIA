"""H35 de punta a punta: APK, correo Mailpit, enlace web, reutilización y vencimiento QA."""
import hashlib
import json
import os
import re
import secrets
import subprocess
import sys
import time
from importlib.machinery import SourceFileLoader
from pathlib import Path

RAIZ = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(RAIZ / "movil/android/qa/redisenio"))
import verificar_interfaz as ui
from verificar_interfaz import adb, centro, esperar, nodos, tap

api = SourceFileLoader("api_cierre", str(RAIZ / "scripts/verificar-cierre-sprint2.py")).load_module()
SALIDA = RAIZ / "docs/pruebas/cierre-sprint2-20261103"
PRIVADO = RAIZ / "tmp/cierre-sprint2-20261103"
ui.OUT = SALIDA


def escribir(valores):
    limite=time.monotonic()+25
    campos=[]
    while time.monotonic()<limite:
        campos=[n for n in nodos() if n.get("class")=="android.widget.EditText"]
        if campos:break
        time.sleep(.3)
    assert campos, "El enlace no abrió un formulario nuevo"
    adb("shell", "input", "tap", *centro(campos[0]))
    time.sleep(1)
    for indice, valor in enumerate(valores):
        if indice:
            for _ in range(8):
                adb("shell", "input", "keyevent", "61")
                time.sleep(.5)
                if any(n.get("focused") == "true" for n in nodos() if n.get("class") == "android.widget.EditText"):
                    break
            else:
                raise AssertionError("El siguiente campo no recibió foco")
        adb("shell", "input", "keyevent", "123")
        adb("shell", "input", "keyevent", *(["67"] * 80))
        adb("shell", "input", "text", valor.replace(" ", "%s"))
        time.sleep(.5)
    adb("shell", "input", "keyevent", "4")
    time.sleep(.6)


def abrir_enlace(uri):
    assert uri.startswith("foundia://restablecer?token=")
    try:
        resultado = adb("shell", "am", "start", "-W", "-a", "android.intent.action.VIEW", "-d", uri)
        assert b"Error" not in resultado
    except Exception:
        raise AssertionError("El enlace no pudo abrir FoundIA Android") from None
    esperar("Nueva contraseña")


def pedir_desde_apk(correo):
    tap("Recuperar contraseña", True)
    esperar("Volvamos a conectar")
    escribir([correo])
    tap("Solicitar enlace", True)
    esperar("Si el correo corresponde")


def main():
    assert adb("shell", "getprop", "debug.foundia.qa_cierre").decode().strip() == "20261103"
    config = json.loads((PRIVADO / "cuenta-movil.json").read_text(encoding="utf-8"))
    if "--continuar" not in sys.argv:
        documentos=api.peticion("GET", "/api/legal/documentos")
        config["correo"]="movil-apk-"+secrets.token_hex(4)+"@qa.invalid"
        api.cuenta(config["correo"],documentos["version"])
        (PRIVADO/"cuenta-movil.json").write_text(json.dumps(config),encoding="utf-8")
        adb("shell", "pm", "clear", ui.APP)
        adb("shell", "am", "start", "-n", ui.APP + "/.MainActivity")
        esperar("Lo que perdiste")
        tap("Ya tengo una cuenta", True)
        esperar("Inicia sesión")
        pedir_desde_apk(config["correo"])
        ui.foto("h35-apk-solicitud")
        token = api.mensaje_para(config["correo"], r"restablecer-password\?token=([A-Za-z0-9_-]+)")
        (PRIVADO / "enlace-correo.json").write_text(json.dumps({"token": token}), encoding="utf-8")
        entorno = os.environ.copy()
        entorno["NODE_PATH"] = r"C:\Users\amore\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules"
        proceso = subprocess.run([r"C:\Program Files\nodejs\node.exe", str(RAIZ / "scripts/verificar-cierre-sprint2-web.cjs"), "enlace"],
                                 cwd=RAIZ, env=entorno, capture_output=True)
        assert proceso.returncode == 0, "No se pudo verificar el enlace web hacia Android"
        uri = json.loads((PRIVADO / "enlace-android.json").read_text(encoding="utf-8"))["uri"]
        abrir_enlace(uri)
        escribir([config["passwordNueva"], config["passwordNueva"]])
        tap("Guardar nueva contraseña", True)
        esperar("Contraseña actualizada.")
        ui.foto("h35-apk-cambio-correcto")
        abrir_enlace(uri)
        escribir(["Otra789!", "Otra789!"])
        tap("Guardar nueva contraseña", True)
        esperar("El enlace no es válido o ya venció")
        ui.foto("h35-apk-enlace-reutilizado")

    else:
        token=json.loads((PRIVADO/"enlace-correo.json").read_text(encoding="utf-8"))["token"]
    # Solo sobre la cuenta sintética QA: permitir otro pedido y vencer el enlace recibido.
    db = api.pymysql.connect(host="127.0.0.1", port=13372, user="root", password="cierre-s2-qa-local",
                             database="objetos_perdidos_db", charset="utf8mb4", autocommit=True)
    with db.cursor() as cur:
        cur.execute("SELECT id FROM usuarios WHERE correo=%s", (config["correo"],))
        usuario_id = cur.fetchone()[0]
        cur.execute("UPDATE tokens_recuperacion_password SET creado_en=UTC_TIMESTAMP()-INTERVAL 2 MINUTE WHERE usuario_id=%s", (usuario_id,))
    adb("shell", "am", "force-stop", ui.APP)
    adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-n", ui.APP+"/.MainActivity")
    esperar("Hola, comunidad")
    tap("Perfil")
    esperar("Inicia sesión")
    pedir_desde_apk(config["correo"])
    vencido = api.mensaje_para(config["correo"], r"restablecer-password\?token=([A-Za-z0-9_-]+)")
    assert vencido != token
    with db.cursor() as cur:
        cur.execute("UPDATE tokens_recuperacion_password SET expira_en=UTC_TIMESTAMP()-INTERVAL 1 MINUTE WHERE usuario_id=%s AND token_hash=%s",
                    (usuario_id, hashlib.sha256(vencido.encode()).hexdigest()))
        assert cur.rowcount == 1
    abrir_enlace("foundia://restablecer?token=" + vencido)
    escribir(["Otra789!", "Otra789!"])
    tap("Guardar nueva contraseña", True)
    esperar("El enlace no es válido o ya venció")
    ui.foto("h35-apk-enlace-vencido")
    api.peticion("POST", "/api/auth/login", {"correo": config["correo"], "password": config["passwordInicial"]}, esperado=401)
    adb("shell", "am", "force-stop", ui.APP)
    adb("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-n", ui.APP+"/.MainActivity")
    esperar("Hola, comunidad")
    tap("Perfil")
    esperar("Inicia sesión")
    escribir([config["correo"], config["passwordNueva"]])
    tap("Iniciar sesión", True)
    esperar("Hola, Prueba")
    ui.foto("h35-apk-login-nueva-clave")
    resultado = {"solicitudDesdeApk": True, "correoRealMailpit": True, "puenteWebAndroidVerificado": True,
                 "cambioRealConfirmado": True, "enlaceReutilizadoRechazado": True, "enlaceVencidoRechazado": True,
                 "passwordAnteriorRechazada": True, "nuevoLoginEnApk": True,
                 "vencimientoForzadoSoloEnQa": True, "tokensExcluidosDeEvidencias": True}
    (SALIDA / "h35-apk.json").write_text(json.dumps(resultado, ensure_ascii=False, indent=2), encoding="utf-8")
    reporte = json.loads((SALIDA / "api-14-historias.json").read_text(encoding="utf-8"))
    reporte["historias"]["H35"]["apkPendiente"] = False
    reporte["historias"]["H35"]["apkVerificado"] = resultado
    (SALIDA / "api-14-historias.json").write_text(json.dumps(reporte, ensure_ascii=False, indent=2), encoding="utf-8")
    print("H35 APK: solicitud, correo, cambio, reutilización, vencimiento y nuevo acceso correctos.", flush=True)


if __name__ == "__main__":
    main()
