"""Carga los datos de demostración del Sprint 3 usando las APIs reales.

Crea publicaciones en lugares del campus de Tecsup, pares perdido/encontrado que
coinciden y objetos recibidos en custodia por la biblioteca. Todo pasa por Spring y
Django, así que los códigos FND, los QR firmados y las coincidencias los genera el
propio sistema. No toca la base directamente ni borra nada.

Es re-ejecutable: si una cuenta ya publicó un objeto con el mismo nombre, no lo repite,
y no registra dos veces la misma custodia.

Uso (siempre contra una base de QA, nunca contra datos reales):

    python scripts/cargar_datos_demo.py
    python scripts/cargar_datos_demo.py --spring http://127.0.0.1:18081 --django http://127.0.0.1:18000

Las fotografías salen de database/demo/fotos (o de --fotos), nombradas como la clave de
cada objeto. Son imágenes con licencia libre de Wikimedia Commons, no fotos tomadas en el
campus; sus autores y licencias están en database/demo/fotos/CREDITOS.md. Se suben con la
API de fotos (H2); los objetos sin archivo quedan sin foto (el carné, a propósito).

Cuentas: las de database/seed.sql (contraseña de desarrollo Admin123!), salvo que se
indiquen otras con --password.
"""

import argparse
import json
import mimetypes
import sys
import uuid
from pathlib import Path
from urllib import error, request

CAMPUS = ["Biblioteca", "Cafetería", "Laboratorios", "Talleres", "Aulas", "Auditorio"]

# Categorías de seed.sql: 1 Celular, 2 Laptop, 3 Mochila, 4 Documento, 5 Ropa, 6 Accesorio, 7 Llaves
OBJETOS = [
    # Pares que coinciden: misma categoría, lugar y descripción parecida. Quedan ACTIVO
    # porque las coincidencias solo cruzan publicaciones activas (no las de custodia).
    {"clave": "mochila-roja-azul-perdida", "cuenta": "luis", "categoriaId": 3, "tipo": "PERDIDO",
     "nombre": "Mochila Champion roja y azul", "ubicacion": "Laboratorios", "fechaObjeto": "2026-11-12",
     "descripcion": "Mochila Champion roja arriba y azul abajo, con cierres rojos y un cuaderno de electrónica."},
    {"clave": "mochila-roja-azul-encontrada", "cuenta": "carla", "categoriaId": 3, "tipo": "ENCONTRADO",
     "nombre": "Mochila roja y azul Champion", "ubicacion": "Laboratorios", "fechaObjeto": "2026-11-12",
     "descripcion": "Mochila Champion roja y azul con cierres rojos, encontrada junto a las mesas del laboratorio."},
    {"clave": "calculadora-perdida", "cuenta": "ana", "categoriaId": 6, "tipo": "PERDIDO",
     "nombre": "Calculadora Casio fx-991 negra", "ubicacion": "Aulas", "fechaObjeto": "2026-11-13",
     "descripcion": "Calculadora científica Casio fx-991 ClassWiz negra con su tapa."},
    {"clave": "calculadora-encontrada", "cuenta": "luis", "categoriaId": 6, "tipo": "ENCONTRADO",
     "nombre": "Calculadora científica Casio ClassWiz", "ubicacion": "Aulas", "fechaObjeto": "2026-11-13",
     "descripcion": "Calculadora Casio fx-991 ClassWiz negra encontrada en una carpeta del aula."},
    {"clave": "audifonos-perdidos", "cuenta": "carla", "categoriaId": 6, "tipo": "PERDIDO",
     "nombre": "Audífonos inalámbricos blancos", "ubicacion": "Cafetería", "fechaObjeto": "2026-11-14",
     "descripcion": "Audífonos inalámbricos blancos dentro de su estuche de carga blanco."},
    {"clave": "audifonos-encontrados", "cuenta": "ana", "categoriaId": 6, "tipo": "ENCONTRADO",
     "nombre": "Estuche de audífonos blancos", "ubicacion": "Cafetería", "fechaObjeto": "2026-11-14",
     "descripcion": "Estuche de carga blanco con los dos audífonos inalámbricos dentro."},
    # Objetos sueltos en otros lugares del campus
    {"clave": "casco-encontrado", "cuenta": "carla", "categoriaId": 8, "tipo": "ENCONTRADO",
     "nombre": "Casco de seguridad amarillo", "ubicacion": "Talleres", "fechaObjeto": "2026-11-11",
     "descripcion": "Casco de seguridad amarillo de plástico, sin nombre, encontrado en el taller.",
     "custodia": "Recibido de un docente de talleres."},
    {"clave": "carne-perdido", "cuenta": "luis", "categoriaId": 4, "tipo": "PERDIDO",
     "nombre": "Carné de estudiante", "ubicacion": "Biblioteca", "fechaObjeto": "2026-11-15",
     "descripcion": "Carné de estudiante dentro de un porta carné celeste."},
    {"clave": "casaca-encontrada", "cuenta": "ana", "categoriaId": 5, "tipo": "ENCONTRADO",
     "nombre": "Casaca de cuero negra", "ubicacion": "Auditorio", "fechaObjeto": "2026-11-16",
     "descripcion": "Casaca de cuero negra con cierres dorados, olvidada en una butaca después de la charla.",
     "custodia": "Entregada en la biblioteca por el personal del auditorio."},
]


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def llamar(self, metodo, ruta, token=None, cuerpo=None, datos=None, tipo=None):
        cabeceras = {"Accept": "application/json"}
        if token:
            cabeceras["Authorization"] = f"Bearer {token}"
        if cuerpo is not None:
            datos = json.dumps(cuerpo).encode("utf-8")
            tipo = "application/json"
        if tipo:
            cabeceras["Content-Type"] = tipo
        req = request.Request(self.base + ruta, data=datos, method=metodo, headers=cabeceras)
        try:
            with request.urlopen(req, timeout=30) as resp:
                texto = resp.read().decode("utf-8")
                return resp.status, (json.loads(texto) if texto else None)
        except error.HTTPError as e:
            texto = e.read().decode("utf-8", "replace")
            try:
                return e.code, json.loads(texto)
            except ValueError:
                return e.code, texto


def exigir(estado, cuerpo, esperado, accion):
    if estado not in esperado:
        sys.exit(f"ERROR al {accion}: HTTP {estado} {cuerpo}")
    return cuerpo


def multipart(campo, archivo):
    limite = uuid.uuid4().hex
    tipo = mimetypes.guess_type(archivo.name)[0] or "application/octet-stream"
    cuerpo = (f"--{limite}\r\nContent-Disposition: form-data; name=\"{campo}\"; "
              f"filename=\"{archivo.name}\"\r\nContent-Type: {tipo}\r\n\r\n").encode()
    cuerpo += archivo.read_bytes() + f"\r\n--{limite}--\r\n".encode()
    return cuerpo, f"multipart/form-data; boundary={limite}"


def main():
    p = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    p.add_argument("--spring", default="http://127.0.0.1:8081")
    p.add_argument("--django", default="http://127.0.0.1:8000")
    p.add_argument("--password", default="Admin123!")
    p.add_argument("--fotos", type=Path, default=Path(__file__).resolve().parent.parent / "database" / "demo" / "fotos")
    args = p.parse_args()
    if hasattr(sys.stdout, "reconfigure"):
        sys.stdout.reconfigure(encoding="utf-8")  # la consola de Windows usa cp1252 por defecto

    spring, django = Api(args.spring), Api(args.django)
    correos = {c: f"{c}@foundia.dev" for c in ("ana", "luis", "carla", "admin")}

    tokens = {}
    for cuenta in ("ana", "luis", "carla"):
        _, r = spring.llamar("POST", "/api/auth/login",
                             cuerpo={"correo": correos[cuenta], "password": args.password})
        tokens[cuenta] = exigir(_, r, {200}, f"iniciar sesión como {cuenta}")["token"]
    estado, r = django.llamar("POST", "/api/admin/auth/login/",
                              cuerpo={"correo": correos["admin"], "password": args.password})
    token_admin = exigir(estado, r, {200}, "iniciar sesión en el panel")["token"]

    resumen = {"creados": 0, "existentes": 0, "fotos": 0, "sin_foto": [], "custodias": []}
    for o in OBJETOS:
        assert o["ubicacion"] in CAMPUS
        token = tokens[o["cuenta"]]
        _, propios = spring.llamar("GET", "/api/perfil/objetos", token)
        previo = next((x for x in propios or [] if x["nombre"] == o["nombre"]), None)
        if previo:
            objeto_id = previo["id"]
            resumen["existentes"] += 1
        else:
            datos = {k: o[k] for k in ("categoriaId", "nombre", "descripcion", "fechaObjeto", "tipo", "ubicacion")}
            estado, r = spring.llamar("POST", "/api/objetos", token, cuerpo=datos)
            objeto_id = exigir(estado, r, {200, 201}, f"publicar «{o['nombre']}»")["id"]
            resumen["creados"] += 1

            foto = None
            if args.fotos:
                foto = next((args.fotos / f"{o['clave']}{ext}" for ext in (".jpg", ".jpeg", ".png")
                             if (args.fotos / f"{o['clave']}{ext}").is_file()), None)
            if foto:
                datos_foto, tipo = multipart("archivo", foto)
                estado, r = spring.llamar("POST", f"/api/objetos/{objeto_id}/fotos", token,
                                          datos=datos_foto, tipo=tipo)
                exigir(estado, r, {200, 201}, f"subir la foto de «{o['nombre']}»")
                resumen["fotos"] += 1
            else:
                resumen["sin_foto"].append(o["clave"])

        if o.get("custodia"):
            estado, r = django.llamar("POST", "/api/admin/custodias/", token_admin,
                                      cuerpo={"objetoId": objeto_id, "observaciones": o["custodia"]})
            if estado == 409 and "ya está registrada" in json.dumps(r, ensure_ascii=False):
                resumen["custodias"].append(f"{o['clave']}: ya estaba en custodia")
            else:
                r = exigir(estado, r, {200, 201}, f"registrar en custodia «{o['nombre']}»")
                resumen["custodias"].append(f"{o['clave']}: {r['codigo']}")

    print(f"Publicaciones creadas: {resumen['creados']} · ya existentes: {resumen['existentes']}")
    print(f"Fotos subidas: {resumen['fotos']}")
    if resumen["sin_foto"]:
        print("Publicadas sin foto: " + ", ".join(resumen["sin_foto"]))
    for linea in resumen["custodias"]:
        print("Custodia " + linea)


if __name__ == "__main__":
    main()
