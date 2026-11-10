"""Pruebas reales Sprint 3. Solo cuentas seed; no imprime ni persiste tokens.

Ejecutar desde la raíz con Docker ya migrado y un respaldo verificado.
Genera dos publicaciones identificadas QA; no elimina datos existentes.
"""
import hashlib
import json
import sys
from pathlib import Path
import urllib.error
import urllib.request
from datetime import date

SPRING = "http://localhost:8081"
DJANGO = "http://localhost:8000"
OUT = Path("tmp/sprint3-real")
OUT.mkdir(parents=True, exist_ok=True)
evidencia = []


def pedir(metodo, ruta, datos=None, token=None, base=SPRING, esperado=200, archivo=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base + ruta, method=metodo, headers=headers,
                                 data=json.dumps(datos).encode() if datos is not None else None)
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            status, body = r.status, r.read()
    except urllib.error.HTTPError as e:
        status, body = e.code, e.read()
    evidencia.append({"metodo": metodo, "ruta": ruta.split("?")[0], "status": status,
                      "esperado": esperado, "ok": status == esperado, "api": base})
    (OUT / "peticiones.json").write_text(json.dumps(evidencia, indent=2), encoding="utf-8")
    assert status == esperado, f"{metodo} {ruta.split('?')[0]}: {status}, esperado {esperado}: {body[:350]!r}"
    if archivo:
        assert body.startswith(b"%PDF") if archivo.endswith("pdf") else body.startswith(b"PK")
        (OUT / archivo).write_bytes(body)
        evidencia[-1].update(bytes=len(body), sha256=hashlib.sha256(body).hexdigest())
        return body
    return json.loads(body) if body else None


def login(correo, base=SPRING):
    ruta = "/api/auth/login" if base == SPRING else "/api/admin/auth/login/"
    return pedir("POST", ruta, {"correo": correo, "password": "Admin123!"}, base=base)


def main():
    ana = login("ana@foundia.dev")
    luis = login("luis@foundia.dev")
    admin = login("admin@foundia.dev", DJANGO)["token"]
    a, l = ana["token"], luis["token"]
    pedir("GET", "/api/admin/dashboard/", token=a, base=DJANGO, esperado=403)
    cats = pedir("GET", "/api/categorias")
    categoria = next(c["id"] for c in cats if c.get("estado", True))
    objetos = []
    for nombre in ["QA Sprint3 entrega", "QA Sprint3 disposición"]:
        o = pedir("POST", "/api/objetos", {"nombre": nombre, "descripcion": "Mochila azul de prueba Sprint 3, sin datos personales.",
                  "categoriaId": categoria, "ubicacion": "Biblioteca QA", "fechaObjeto": str(date.today()), "tipo": "ENCONTRADO"}, token=a, esperado=201)
        objetos.append(o["id"])
    (OUT / "objetos.json").write_text(json.dumps(objetos))
    for texto in [f"#{objetos[0]}", "quiero publicar una mochila perdida en biblioteca"]:
        respuesta = pedir("POST", "/api/asistente/interpretar", {"mensaje": texto}, token=l)
        if texto.startswith("#"):
            assert any(t["tipo"] == "OBJETO" for t in respuesta.get("tarjetas", []))
        accion = "ABRIR_RECLAMO" if texto.startswith("#") else "PRELLENAR_PUBLICAR"
        assert any(t["tipo"] == accion for t in respuesta.get("acciones", []))
        (OUT / ("findi-objeto.json" if texto.startswith("#") else "findi-publicar.json")).write_text(json.dumps(respuesta, indent=2), encoding="utf-8")
    sugerencia = pedir("POST", "/api/asistente/sugerir-publicacion", {"nombre": "Mochila azul", "descripcion": "Encontré una mochila azul en biblioteca", "tipo": "ENCONTRADO"}, token=a)
    (OUT / "sugerencia.json").write_text(json.dumps(sugerencia, indent=2), encoding="utf-8")
    custodias = []
    for objeto in objetos:
        c = pedir("POST", "/api/admin/custodias/", {"objetoId": objeto, "observaciones": "Prueba controlada Sprint 3 Yajaira"}, token=admin, base=DJANGO, esperado=201)
        custodias.append(c["id"])
    (OUT / "custodias.json").write_text(json.dumps(custodias))
    for nombre in ["etiqueta", "constancia"]:
        pedir("GET", f"/api/admin/custodias/{custodias[0]}/{nombre}/", token=admin, base=DJANGO, archivo=nombre+".pdf")
    reclamo = pedir("POST", f"/api/objetos/{objetos[0]}/reclamos", {}, token=l, esperado=201)
    rid = reclamo["id"]
    pedir("POST", f"/api/admin/reclamos/{rid}/resolver/", {"decision": "ACEPTADO", "motivo": "Verificación presencial de prueba QA"}, token=admin, base=DJANGO)
    pedir("GET", f"/api/admin/reclamos/{rid}/qr-retiro/", token=admin, base=DJANGO, archivo="qr-retiro.pdf")
    # El token de retiro se consulta únicamente para este reclamo QA y nunca se imprime.
    import subprocess
    sql = f"SELECT qr_retiro_token FROM reclamos WHERE id={int(rid)};"
    qr = subprocess.check_output(["docker", "compose", "exec", "-T", "mysql", "sh", "-c", 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -N -B -uroot objetos_perdidos_db'], input=sql.encode()).decode().strip()
    pedir("POST", "/api/admin/reclamos/retirar/", {"token": qr}, token=admin, base=DJANGO)
    pedir("POST", "/api/admin/reclamos/retirar/", {"token": qr}, token=admin, base=DJANGO, esperado=409)
    for estado in ["TODAS", "NO_LEIDAS", "LEIDAS"]:
        r = pedir("GET", f"/api/notificaciones/historial?estado={estado}&pagina=0&tamano=1", token=l)
        assert r["pagina"] == 0 and r["tamano"] == 1
    pedir("GET", "/api/notificaciones/historial?pagina=1&tamano=1", token=l)
    pedir("GET", "/api/notificaciones/historial?estado=INVALIDO", token=l, esperado=400)
    pedir("PUT", "/api/notificaciones/leidas", {}, token=l)
    assert pedir("PUT", "/api/notificaciones/leidas", {}, token=l)["actualizadas"] == 0
    for formato in ["pdf", "xlsx"]:
        pedir("GET", "/api/admin/dashboard/exportar."+formato, token=admin, base=DJANGO, archivo="estadisticas."+formato)
    tablero = pedir("GET", "/api/admin/dashboard/", token=admin, base=DJANGO)
    (OUT / "dashboard.json").write_text(json.dumps(tablero, indent=2), encoding="utf-8")
    # Revocación individual y global en ambos emisores, con dos sesiones.
    for base, correo, prefijo, protegido in [(SPRING, "ana@foundia.dev", "/api/auth/", "/api/notificaciones/historial"), (DJANGO, "admin@foundia.dev", "/api/admin/auth/", "/api/admin/dashboard/")]:
        sufijo = "/" if base == DJANGO else ""
        t1, t2 = login(correo, base)["token"], login(correo, base)["token"]
        pedir("POST", prefijo+"logout"+sufijo, {}, token=t1, base=base)
        pedir("GET", protegido, token=t1, base=base, esperado=401)
        pedir("GET", protegido, token=t2, base=base)
        t3 = login(correo, base)["token"]
        pedir("POST", prefijo+"logout-all"+sufijo, {}, token=t2, base=base)
        for t in [t2, t3]:
            pedir("GET", protegido, token=t, base=base, esperado=401)
    print(f"OK: {len(evidencia)} peticiones; objetos QA {objetos}; custodias {custodias}")
    (OUT / "peticiones.json").write_text(json.dumps(evidencia, indent=2), encoding="utf-8")


def complemento():
    """Tras main y con CUSTODIA_PLAZO_DIAS=1, solo envejece la custodia QA creada."""
    import subprocess
    admin = login("admin@foundia.dev", DJANGO)["token"]
    cid = json.loads((OUT / "custodias.json").read_text())[1]
    sql = f"UPDATE custodias SET fecha_recepcion=UTC_TIMESTAMP()-INTERVAL 2 DAY WHERE id={int(cid)} AND observaciones='Prueba controlada Sprint 3 Yajaira';"
    subprocess.run(["docker", "compose", "exec", "-T", "mysql", "sh", "-c", 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" exec mysql -uroot objetos_perdidos_db'], input=sql.encode(), check=True)
    vencidas = pedir("GET", "/api/admin/custodias/vencidas/", token=admin, base=DJANGO)
    assert vencidas["plazoDias"] == 1 and any(c["id"] == cid for c in vencidas["resultados"])
    pedir("POST", f"/api/admin/custodias/{cid}/disponer/", {"destino": "DONADO", "motivo": "Prueba QA de disposición tras plazo cumplido", "confirmar": True}, token=admin, base=DJANGO, esperado=201)
    pedir("GET", f"/api/admin/custodias/{cid}/acta/", token=admin, base=DJANGO, archivo="acta.pdf")
    pedir("POST", f"/api/admin/custodias/{cid}/disponer/", {"destino": "DONADO", "motivo": "Prueba de repetición QA", "confirmar": True}, token=admin, base=DJANGO, esperado=409)
    respaldo = pedir("GET", "/api/admin/respaldos/ultimo/", token=admin, base=DJANGO)
    (OUT / "ultimo-respaldo.json").write_text(json.dumps(respaldo, indent=2), encoding="utf-8")
    luis = login("luis@foundia.dev")
    uid = luis["usuario"]["id"]
    original = luis["usuario"]["rol"]
    try:
        pedir("PATCH", f"/api/admin/usuarios/{uid}/", {"rol": "BIBLIOTECA", "confirmar": True, "motivo": "Prueba temporal QA H36"}, token=admin, base=DJANGO)
        b = login("luis@foundia.dev", DJANGO)["token"]
        for ruta in ["custodias/", "reclamos/", "custodias/vencidas/"]:
            pedir("GET", "/api/admin/"+ruta, token=b, base=DJANGO)
        for ruta in ["dashboard/", "usuarios/", "categorias/", "reportes/"]:
            pedir("GET", "/api/admin/"+ruta, token=b, base=DJANGO, esperado=403)
    finally:
        pedir("PATCH", f"/api/admin/usuarios/{uid}/", {"rol": original, "confirmar": True, "motivo": "Restaurar rol tras prueba QA H36"}, token=admin, base=DJANGO)
    (OUT / "complemento.json").write_text(json.dumps(evidencia, indent=2), encoding="utf-8")
    print(f"Complemento OK: {len(evidencia)} peticiones, rol restaurado")


if __name__ == "__main__":
    complemento() if "--complemento" in sys.argv else main()
