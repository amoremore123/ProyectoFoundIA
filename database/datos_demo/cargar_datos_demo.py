"""Datos de demostración con sentido, creados por la API real (no por SQL) para que queden coherentes.

Crea dos recorridos: (1) una mochila en custodia con reclamo aceptado, QR de retiro sin usar y chat con la biblioteca;
(2) una casaca perdida y otra encontrada que coinciden, con chat entre las dos personas.
Hace falta que existan las cuentas ana@, luis@, carla@ (BIBLIOTECA) y admin@foundia.dev del seed.
La contraseña de esas cuentas de prueba se pasa por la variable de entorno FOUNDIA_QA_PASSWORD (no se guarda en Git).
Uso:  FOUNDIA_QA_PASSWORD=... python database/datos_demo/cargar_datos_demo.py
"""
import json, os, sys, time, urllib.request, urllib.error
from datetime import date

CLAVE = os.environ.get("FOUNDIA_QA_PASSWORD")
if not CLAVE:
    sys.exit("Define la variable de entorno FOUNDIA_QA_PASSWORD con la contraseña de las cuentas de prueba del seed.")
SPRING = os.environ.get("FOUNDIA_SPRING_URL", "http://127.0.0.1:8081")
DJANGO = os.environ.get("FOUNDIA_DJANGO_URL", "http://127.0.0.1:8000")
HOY = str(date.today())


def llamar(metodo, ruta, cuerpo=None, token=None, base=SPRING, esperado=(200, 201, 204)):
    cab = {"Content-Type": "application/json"}
    if token:
        cab["Authorization"] = "Bearer " + token
    datos = json.dumps(cuerpo).encode("utf-8") if cuerpo is not None else None
    req = urllib.request.Request(base + ruta, data=datos, headers=cab, method=metodo)
    try:
        with urllib.request.urlopen(req, timeout=40) as r:
            t = r.read().decode("utf-8")
            codigo = r.status
    except urllib.error.HTTPError as e:
        t = e.read().decode("utf-8"); codigo = e.code
    resultado = json.loads(t) if t.startswith(("{", "[")) else t
    if codigo not in esperado:
        raise AssertionError("%s %s -> %s %s" % (metodo, ruta, codigo, str(resultado)[:200]))
    return resultado


def entrar(correo, base=SPRING):
    ruta = "/api/auth/login" if base == SPRING else "/api/admin/auth/login/"
    return llamar("POST", ruta, {"correo": correo, "password": CLAVE}, base=base)["token"]


ana, luis, carla = entrar("ana@foundia.dev"), entrar("luis@foundia.dev"), entrar("carla@foundia.dev")
admin = entrar("admin@foundia.dev", DJANGO)
categorias = {c["nombre"]: c["id"] for c in llamar("GET", "/api/categorias") if c["estado"]}


def publicar(token, tipo, nombre, descripcion, ubicacion, categoria):
    return llamar("POST", "/api/objetos", {"categoriaId": categorias[categoria], "nombre": nombre, "descripcion": descripcion,
                                           "ubicacion": ubicacion, "fechaObjeto": HOY, "tipo": tipo}, token)["id"]


# --- Flujo 1: objeto en custodia, reclamo aceptado, QR pendiente y chat con la biblioteca ---
mochila = publicar(luis, "ENCONTRADO", "Mochila azul Jansport",
                   "Mochila azul con un llavero de panda y cuadernos de Matemática. La encontré en la biblioteca.", "Biblioteca", "Mochila")
custodia = llamar("POST", "/api/admin/custodias/", {"objetoId": mochila, "observaciones": "Entregada en la mesa de la biblioteca el mismo día."}, admin, DJANGO)
reclamo = llamar("POST", "/api/objetos/%d/reclamos/custodia" % mochila, {"respuesta": "Tiene un llavero de panda y un cuaderno de Matemática"}, ana)
llamar("POST", "/api/admin/reclamos/%d/resolver/" % reclamo["id"], {"decision": "ACEPTADO", "motivo": "La descripción coincide con la mochila."}, admin, DJANGO)
conv1 = llamar("POST", "/api/conversaciones", {"reclamoId": reclamo["id"]}, ana)["id"]
for token, texto in ((ana, "Hola, mi reclamo ya fue aceptado. ¿Hasta qué hora puedo pasar a recogerla hoy?"),
                     (carla, "Hola Ana, puedes pasar hoy hasta las 6 pm con tu DNI y el código QR de retiro."),
                     (ana, "Perfecto, gracias. Voy saliendo de clases.")):
    llamar("POST", "/api/conversaciones/%d/mensajes" % conv1, {"texto": texto}, token)
print("Flujo 1: objeto", mochila, "custodia", custodia["id"], "reclamo", reclamo["id"], "conversación", conv1)

# --- Flujo 2: coincidencia entre una publicación perdida y una encontrada, con chat entre personas ---
perdida = publicar(ana, "PERDIDO", "Casaca negra Adidas",
                   "Casaca negra de la marca Adidas con capucha, talla M. La perdí en la cafetería.", "Cafetería", "Ropa")
encontrada = publicar(luis, "ENCONTRADO", "Casaca negra Adidas",
                      "Casaca negra con capucha de la marca Adidas, talla M. La dejaron olvidada en una mesa de la cafetería.", "Cafetería", "Ropa")
contacto = None
for _ in range(10):
    try:
        contacto = llamar("POST", "/api/contactos", {"objetoPerdidoId": perdida, "objetoEncontradoId": encontrada}, ana)
        break
    except AssertionError as e:
        time.sleep(2)
        ultimo = str(e)
if contacto is None:
    raise SystemExit("No se pudo crear el contacto: " + ultimo)
conv2 = llamar("POST", "/api/conversaciones", {"contactoId": contacto["id"]}, ana)["id"]
for token, texto in ((ana, "Hola Luis, creo que la casaca que encontraste es la mía."),
                     (luis, "Hola Ana, ¡qué bueno! ¿Qué marca es y de qué color?"),
                     (ana, "Es una Adidas negra con capucha, talla M."),
                     (luis, "Esa es. Puedes recogerla mañana en la biblioteca.")):
    llamar("POST", "/api/conversaciones/%d/mensajes" % conv2, {"texto": texto}, token)
print("Flujo 2: perdida", perdida, "encontrada", encontrada, "contacto", contacto["id"], "conversación", conv2)

# Se dejan los mensajes como leídos por quien los envió y sin avisos viejos de las pruebas.
print("Listo.")
