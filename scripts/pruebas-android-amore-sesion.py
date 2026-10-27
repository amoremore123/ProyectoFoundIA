"""Última fase QA: iniciar primero API :18082 con JWT_EXPIRATION_MS=15000.
El flujo anterior deja la pantalla de preferencias mostrando error y Reintentar.
No cambia relojes ni modifica filas para simular un vencimiento.
"""
import importlib.util,json,time
from pathlib import Path
spec=importlib.util.spec_from_file_location("qa",Path(__file__).with_name("pruebas-android-amore.py"))
qa=importlib.util.module_from_spec(spec);spec.loader.exec_module(qa)
assert qa.adb("shell","getprop","debug.foundia.qa").decode().strip()=="amore-sprint2-20261026"
f=json.loads(qa.TEMP.read_text(encoding="utf-8"))
qa.RESULTADOS.extend(json.loads((qa.EVIDENCIA/"resultados.json").read_text(encoding="utf-8"))["comprobaciones"])
for _ in range(30):
    try:qa.http("/api/legal/documentos");break
    except Exception:time.sleep(1)
qa.pulsar("Reintentar");qa.esperar("Guardar preferencias")
qa.comprobar("H24: reintento recupera la consulta tras volver la API")
qa.reiniciar();qa.esperar("Mis publicaciones");qa.pulsar("Cerrar sesión");qa.pulsar("Cuenta")
qa.escribir("Correo",f["correo"]);qa.escribir("Contraseña","Segura123!");qa.pulsar("Iniciar sesión");qa.esperar("Mis publicaciones")
time.sleep(17)
qa.pulsar("Preferencias de avisos");qa.esperar("La sesión ya no es válida.")
qa.comprobar("H21: JWT vence naturalmente y retira los datos privados del APK")
qa.foto("h21-sesion-vencida")
for i in range(5):qa.http("/api/auth/login","POST",{"correo":f["correo"],"password":"Incorrecta123!"},esperado=401 if i<4 else 423)
qa.escribir("Correo",f["correo"]);qa.escribir("Contraseña","Segura123!");qa.pulsar("Iniciar sesión");qa.esperar("bloqueada")
qa.comprobar("H21: bloqueo real 423 impide iniciar sesión aunque la clave sea correcta")
qa.foto("h21-bloqueo")
