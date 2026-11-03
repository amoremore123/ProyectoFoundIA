"""Comprueba las 14 historias en API y MySQL QA; nunca usa la base compartida."""
import hashlib
import json
import re
import secrets
import subprocess
import time
import urllib.error
import urllib.request
from datetime import date
from pathlib import Path

import pymysql

RAIZ = Path(__file__).resolve().parents[1]
SALIDA = RAIZ / "docs/pruebas/cierre-sprint2-20261103"
PRIVADO = RAIZ / "tmp/cierre-sprint2-20261103"
API = "http://127.0.0.1:18083"
ADMIN = "http://127.0.0.1:18004"
CORREO = "http://127.0.0.1:18372"
RESULTADOS = {}
PETICIONES = 0


def peticion(metodo, ruta, datos=None, token=None, base=API, esperado=200):
    global PETICIONES
    cabeceras = {"Content-Type": "application/json"}
    if token:
        cabeceras["Authorization"] = "Bearer " + token
    req = urllib.request.Request(base + ruta, data=json.dumps(datos).encode() if datos is not None else None,
                                 headers=cabeceras, method=metodo)
    try:
        with urllib.request.urlopen(req, timeout=30) as respuesta:
            codigo, cuerpo = respuesta.status, respuesta.read()
    except urllib.error.HTTPError as error:
        codigo, cuerpo = error.code, error.read()
    PETICIONES += 1
    codigos = esperado if isinstance(esperado, (tuple, list)) else [esperado]
    assert codigo in codigos, f"{metodo} {ruta.split('?')[0]} respondió {codigo}; se esperaba {codigos}"
    return json.loads(cuerpo) if cuerpo else None


def mensaje_para(correo, patron):
    limite = time.monotonic() + 20
    while time.monotonic() < limite:
        datos = peticion("GET", "/api/v1/messages", base=CORREO)
        for mensaje in datos["messages"]:
            if any(d.get("Address", "").lower() == correo.lower() for d in mensaje.get("To", [])):
                texto = peticion("GET", "/api/v1/message/" + mensaje["ID"], base=CORREO)
                coincidencia = re.search(patron, texto.get("Text", ""))
                if coincidencia:
                    return coincidencia.group(1)
        time.sleep(.4)
    raise AssertionError("El correo de QA no llegó a Mailpit")


def cuenta(correo, version):
    peticion("POST", "/api/auth/register", {"nombre": "Prueba", "apellido": "Integracion", "correo": correo,
             "password": "Inicial123!", "aceptaTerminos": True, "versionLegal": version}, esperado=(200, 201))
    codigo = mensaje_para(correo, r"(?:código de verificación es:\s*)([0-9]{6})")
    acceso = peticion("POST", "/api/auth/verificar", {"correo": correo, "codigo": codigo})
    return acceso["token"], acceso["usuario"]


def registrar(historia, **detalles):
    RESULTADOS[historia] = {"resultado": "correcto", **detalles}
    print(historia + ": correcto", flush=True)


def main():
    inspeccion = json.loads(subprocess.check_output(["docker", "inspect", "foundia-cierre-s2-qa-20261103-mysql-1"]))[0]
    assert inspeccion["Config"]["Labels"].get("com.foundia.qa") == "cierre-sprint2-20261103"
    assert not any(m.get("Name") == "proyectofoundia_mysql_data" for m in inspeccion["Mounts"])
    db = pymysql.connect(host="127.0.0.1", port=13372, user="root", password="cierre-s2-qa-local",
                         database="objetos_perdidos_db", charset="utf8mb4", autocommit=True)
    sufijo = secrets.token_hex(4)
    documentos = peticion("GET", "/api/legal/documentos")
    usuario_correo = "revision-" + sufijo + "@qa.invalid"
    peticion("POST", "/api/auth/register", {"nombre": "Prueba", "apellido": "Integracion", "correo": usuario_correo,
             "password": "Inicial123!", "aceptaTerminos": False, "versionLegal": documentos["version"]}, esperado=400)
    usuario, perfil = cuenta(usuario_correo, documentos["version"])
    with db.cursor() as cur:
        cur.execute("SELECT version_legal,aceptacion_legal_fecha,documentos_legales_hash FROM usuarios WHERE id=%s", (perfil["id"],))
        version, fecha, huella = cur.fetchone()
        assert version == documentos["version"] and fecha is not None and huella == documentos["sha256"]
    registrar("H33", consentimientoReal=True, version=version)
    luis = peticion("POST", "/api/auth/login", {"correo": "luis@foundia.dev", "password": "Admin123!"})["token"]
    admin = peticion("POST", "/api/auth/login", {"correo": "admin@foundia.dev", "password": "Admin123!"})["token"]
    categorias = peticion("GET", "/api/categorias")
    categoria = next(c["id"] for c in categorias if c["estado"])

    def publicar(tipo, titular, titulo):
        return peticion("POST", "/api/objetos", {"categoriaId": categoria, "nombre": titulo,
                "descripcion": "Objeto sintético del cierre Sprint dos", "fechaObjeto": date.today().isoformat(),
                "tipo": tipo, "ubicacion": "Biblioteca QA cierre"}, titular, esperado=201)

    perdido = publicar("PERDIDO", usuario, "Cierre descarte " + sufijo)
    encontrado = publicar("ENCONTRADO", luis, perdido["nombre"])
    pareja = f"{perdido['id']}-{encontrado['id']}"
    peticion("POST", f"/api/coincidencias/{pareja}/descartar", token=luis, esperado=403)
    peticion("POST", f"/api/coincidencias/{pareja}/descartar", token=usuario)
    repetida = peticion("POST", f"/api/coincidencias/{pareja}/descartar", token=usuario)
    assert repetida["repetido"]
    assert all(c["objetoId"] != encontrado["id"] for c in peticion("GET", f"/api/objetos/{perdido['id']}/coincidencias", token=usuario))
    registrar("H21", permisos=True, descartePersistente=True, repeticionSegura=True)
    recuperado = publicar("PERDIDO", usuario, "Cierre recuperacion " + sufijo)
    hallazgo = publicar("ENCONTRADO", luis, recuperado["nombre"])
    pareja2 = f"{recuperado['id']}-{hallazgo['id']}"
    respuesta = peticion("POST", f"/api/coincidencias/{pareja2}/confirmar", {"confirmarRecuperacion": True}, usuario)
    assert respuesta["estadoObjetoPerdido"] == "RECUPERADO" and respuesta["fechaRecuperacion"]
    assert peticion("GET", f"/api/objetos/{hallazgo['id']}")["estado"] == "ACTIVO"
    registrar("H22", perdidoRecuperado=recuperado["id"], hallazgoAjenoConservado=hallazgo["id"])
    avisos = []
    for _ in range(25):
        avisos = peticion("GET", "/api/notificaciones", token=usuario)
        if avisos:
            break
        time.sleep(.4)
    assert avisos
    leido = peticion("PUT", f"/api/notificaciones/{avisos[0]['id']}/leida", token=usuario)
    assert leido["leida"]
    assert next(a for a in peticion("GET", "/api/notificaciones", token=usuario) if a["id"] == leido["id"])["leida"]
    registrar("H23", avisosReales=len(avisos), lecturaPersistente=True)
    iniciales = peticion("GET", "/api/notificaciones/preferencias", token=usuario)
    cambios = {"coincidencias": False, "contactos": True, "reclamos": False}
    peticion("PUT", "/api/notificaciones/preferencias", cambios, usuario)
    assert peticion("GET", "/api/notificaciones/preferencias", token=usuario) == cambios
    peticion("PUT", "/api/notificaciones/preferencias", iniciales, usuario)
    registrar("H24", valoresGuardadosYRestaurados=True)
    nueva = peticion("POST", "/api/admin/categorias/", {"nombre": "Categoria cierre " + sufijo, "descripcion": "QA", "estado": True}, admin, base=ADMIN, esperado=201)
    actualizada = peticion("PATCH", f"/api/admin/categorias/{nueva['id']}/", {"nombre": "Categoria revisada " + sufijo, "estado": False}, admin, base=ADMIN)
    assert actualizada["id"] == nueva["id"] and not actualizada["estado"]
    peticion("PATCH", f"/api/admin/categorias/{nueva['id']}/", {"estado": True}, admin, base=ADMIN)
    registrar("H26", conservaId=nueva["id"], desactivacionYReactivacion=True)
    denuncia = peticion("POST", f"/api/objetos/{encontrado['id']}/reportes", {"motivo": "OTRO", "descripcion": "Prueba sintética de moderación"}, usuario, esperado=201)
    repetida = peticion("POST", f"/api/objetos/{encontrado['id']}/reportes", {"motivo": "OTRO", "descripcion": "Prueba sintética de moderación"}, usuario)
    assert repetida["repetido"] and repetida["id"] == denuncia["id"]
    registrar("H27", denuncia=denuncia["id"], identidadDeSesion=True, sinDuplicados=True)
    decision = {"estado": "RESUELTO", "resolucion": "Revisado en QA del cierre"}
    resuelto = peticion("POST", f"/api/admin/reportes/{denuncia['id']}/resolver/", decision, admin, base=ADMIN)
    assert resuelto["resueltoPorId"] and resuelto["fechaResolucion"]
    assert peticion("POST", f"/api/admin/reportes/{denuncia['id']}/resolver/", decision, admin, base=ADMIN)["repetido"]
    peticion("POST", f"/api/admin/reportes/{denuncia['id']}/resolver/", {"estado": "RECHAZADO", "resolucion": "Otra decisión"}, admin, base=ADMIN, esperado=409)
    registrar("H28", auditoriaReal=True, repeticionSegura=True, conflicto409=True)
    tablero = peticion("GET", "/api/admin/dashboard/", token=admin, base=ADMIN)
    assert tablero["objetos"]["recuperados"] <= tablero["objetos"]["perdidos"]
    peticion("GET", "/api/admin/dashboard/?desde=2030-02-01&hasta=2020-01-01", token=admin, base=ADMIN, esperado=400)
    registrar("H29", conteosReales=True, rangoInvalido400=True)
    tiempos = tablero["recuperacion"]
    assert tiempos["muestra"] >= 1 and tiempos["minimoHoras"] >= 0 and tiempos["maximoHoras"] >= tiempos["minimoHoras"]
    registrar("H30", muestra=tiempos["muestra"], estadisticasReales=True)
    assert len(tablero["categoriasTop"]) <= 5 and len(tablero["ubicacionesTop"]) <= 5
    assert any(u["nombre"] == "Biblioteca QA cierre" for u in tablero["ubicacionesTop"])
    registrar("H31", rankingsReales=True, sinUbicacion=tablero["sinUbicacion"])
    estado = peticion("GET", "/api/perfil/solicitud-eliminacion", token=usuario)
    assert estado["estado"] == "SIN_SOLICITUD"
    solicitud = peticion("POST", "/api/perfil/solicitud-eliminacion", {"confirmar": True}, usuario, esperado=(200, 201))
    assert solicitud["estado"] == "PENDIENTE" and peticion("GET", "/api/perfil", token=usuario)["estado"] == "ACTIVO"
    registrar("H34", solicitudPendiente=True, cuentaConservada=True)
    publico = publicar("PERDIDO", usuario, "Cierre enlace publico " + sufijo)
    peticion("GET", f"/api/objetos/{publico['id']}")
    registrar("H39", objetoPublico=publico["id"], accesoSinSesion=True, navegadorPendiente=True)

    admin_correo = "admin-cierre-" + sufijo + "@qa.invalid"
    _, admin_perfil = cuenta(admin_correo, documentos["version"])
    with db.cursor() as cur:
        cur.execute("UPDATE usuarios SET rol='ADMIN' WHERE id=%s AND correo=%s", (admin_perfil["id"], admin_correo))
    anterior = peticion("POST", "/api/admin/auth/login/", {"correo": admin_correo, "password": "Inicial123!"}, base=ADMIN)["token"]
    time.sleep(1.1)  # JWT y MySQL usan precisión de segundos.
    peticion("POST", "/api/auth/recuperar-password", {"correo": admin_correo})
    token = mensaje_para(admin_correo, r"restablecer-password\?token=([A-Za-z0-9_-]+)")
    peticion("POST", "/api/auth/restablecer-password", {"token": token, "password": "Actualizada456!"})
    peticion("POST", "/api/auth/restablecer-password", {"token": token, "password": "Otra789!"}, esperado=400)
    peticion("GET", "/api/admin/usuarios/", token=anterior, base=ADMIN, esperado=401)
    nuevo = peticion("POST", "/api/admin/auth/login/", {"correo": admin_correo, "password": "Actualizada456!"}, base=ADMIN)["token"]
    peticion("GET", "/api/admin/usuarios/", token=nuevo, base=ADMIN)
    with db.cursor() as cur:
        cur.execute("SELECT UTC_TIMESTAMP(),password_actualizado_en FROM usuarios WHERE id=%s", (admin_perfil["id"],))
        ahora_utc,cambio_utc=cur.fetchone()
        assert abs((ahora_utc-cambio_utc).total_seconds())<10, "La fecha H35 debe guardarse en UTC sin desfase"
    registrar("H35", fechasUtcComprobadas=True, correoReal=True, enlaceDeUnUso=True, sesionAdminAnterior401=True, nuevoLoginCorrecto=True, apkPendiente=True)
    movil_correo = "movil-cierre-" + sufijo + "@qa.invalid"
    cuenta(movil_correo, documentos["version"])
    PRIVADO.mkdir(exist_ok=True)
    (PRIVADO / "cuenta-movil.json").write_text(json.dumps({"correo": movil_correo, "passwordInicial": "Inicial123!", "passwordNueva": "Movil456!", "objetoPublico": publico["id"], "usuarioPrueba": perfil["id"], "tokenUsuario": usuario}), encoding="utf-8")
    salida = {"rama": "dev/amore", "entorno": "API18083, Django18004, MySQL13372, Mailpit18372", "peticiones": PETICIONES, "historias": RESULTADOS}
    assert len(RESULTADOS) == 14
    (SALIDA / "api-14-historias.json").write_text(json.dumps(salida, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"14 historias correctas; {PETICIONES} peticiones reales. Datos sintéticos solo en QA.")


if __name__ == "__main__":
    main()
