"""Regresión H35 con JWT reales de ambos emisores y MySQL/Mailpit QA aislados.

No modifica relojes, fechas SQL ni datos compartidos. Requiere los servicios de
qa-cierre-sprint2.compose.yml y ambos backends en los puertos del verificador.
"""
import argparse
import base64
from datetime import datetime, timezone
import importlib.util
import json
import math
from pathlib import Path
import secrets
import subprocess
import time

import pymysql


def main(salida):
    raiz = Path(__file__).resolve().parents[1]
    contenedor = json.loads(subprocess.check_output([
        "docker", "inspect", "foundia-cierre-s2-qa-20261103-mysql-1"]))[0]
    assert contenedor["Config"]["Labels"].get("com.foundia.qa") == "cierre-sprint2-20261103"
    assert contenedor["NetworkSettings"]["Ports"]["3306/tcp"] == [{"HostIp": "127.0.0.1", "HostPort": "13372"}]
    assert not any(m.get("Name") == "proyectofoundia_mysql_data" for m in contenedor["Mounts"])
    spec = importlib.util.spec_from_file_location("verificador_cierre", raiz / "scripts/verificar-cierre-sprint2.py")
    api = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(api)
    db = pymysql.connect(host="127.0.0.1", port=13372, user="root", password="cierre-s2-qa-local",
                         database="objetos_perdidos_db", charset="utf8mb4", autocommit=True)
    intentos = []

    def comprobar_ambos(tokens, esperado):
        for token in tokens.values():
            api.peticion("GET", "/api/perfil", token=token, esperado=esperado)
            api.peticion("GET", "/api/admin/usuarios/", token=token, base=api.ADMIN, esperado=esperado)

    def emitido(token):
        return json.loads(base64.urlsafe_b64decode(token.split(".")[1] + "=="))["iat"]

    try:
        version = api.peticion("GET", "/api/legal/documentos")["version"]
        # Un caso por emisor reduce llamadas dentro de la ventana de un segundo.
        for emisor, ruta_login, base_login in (("spring", "/api/auth/login", api.API),
                                               ("django", "/api/admin/auth/login/", api.ADMIN)):
            for intento in range(1, 9):
                correo = "revocacion-h35-" + secrets.token_hex(6) + "@qa.invalid"
                _, usuario = api.cuenta(correo, version)
                with db.cursor() as cur:
                    cur.execute("UPDATE usuarios SET rol='ADMIN' WHERE id=%s AND correo=%s", (usuario["id"], correo))
                    assert cur.rowcount == 1
                api.peticion("POST", "/api/auth/recuperar-password", {"correo": correo})
                enlace = api.mensaje_para(correo, r"restablecer-password\?token=([A-Za-z0-9_-]+)")
                # Esperar solo ANTES de emitir. No se espera entre login y reset.
                frontera = math.ceil(time.time()) + .005
                time.sleep(max(0, frontera - time.time()))
                inicio = time.time()
                anteriores = {emisor: api.peticion("POST", ruta_login,
                    {"correo": correo, "password": "Inicial123!"}, base=base_login)["token"]}
                comprobar_ambos(anteriores, 200)
                api.peticion("POST", "/api/auth/restablecer-password", {"token": enlace, "password": "Revocada456!"})
                fin_reset = time.time()
                with db.cursor() as cur:
                    cur.execute("SELECT password_actualizado_en FROM usuarios WHERE id=%s", (usuario["id"],))
                    cambio = int(cur.fetchone()[0].replace(tzinfo=timezone.utc).timestamp())
                comprobar_ambos(anteriores, 401)
                nuevos = {
                    "spring": api.peticion("POST", "/api/auth/login", {"correo": correo, "password": "Revocada456!"})["token"],
                    "django": api.peticion("POST", "/api/admin/auth/login/", {"correo": correo, "password": "Revocada456!"}, base=api.ADMIN)["token"],
                }
                comprobar_ambos(nuevos, 200)
                mismo_segundo = emitido(anteriores[emisor]) == cambio
                fila = {"emisor": emisor, "intento": intento, "usuario_sintetico_id": usuario["id"],
                        "iat": emitido(anteriores[emisor]), "cambio_segundo": cambio,
                        "mismo_segundo": mismo_segundo, "login_a_reset_ms": round((fin_reset - inicio) * 1000, 2),
                        "anteriores_antes_reset": [200] * 2, "anteriores_despues_reset": [401] * 2,
                        "nuevos_login_inmediato": [200] * 4, "espera_entre_reset_y_login": False}
                intentos.append(fila)
                if mismo_segundo:
                    time.sleep(2.1)
                    comprobar_ambos(anteriores, 401)
                    fila["anteriores_tras_dos_segundos"] = [401] * 2
                    break
        concluyente = all(any(f["emisor"] == emisor and f["mismo_segundo"] for f in intentos)
                          for emisor in ("spring", "django"))
        resultado = {"fecha": datetime.now(timezone.utc).isoformat(),
                     "codigo": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=raiz, text=True).strip(),
                     "codigo_sin_confirmar": bool(subprocess.check_output(["git", "diff", "--name-only"], cwd=raiz, text=True).strip()),
                     "resultado": "correcto" if concluyente else "no_concluyente", "intentos": intentos, "peticiones_reales": api.PETICIONES,
                     "relojes_o_fechas_sql_modificados": False, "solo_cuentas_sinteticas_qa": True,
                     "tokens_o_passwords_registrados": False}
        salida.parent.mkdir(parents=True, exist_ok=True)
        salida.write_text(json.dumps(resultado, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps(resultado, ensure_ascii=False, indent=2))
        assert concluyente, "Sin caso concluyente del mismo segundo para cada emisor; no afirmar éxito"
    finally:
        db.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--salida", required=True, type=Path, help="JSON de evidencia fuera de los antecedentes históricos")
    main(parser.parse_args().salida)
