"""Comprobaciones adicionales del APK final; usa la fixture QA del ejecutor principal."""
import importlib.util,json,time
spec=importlib.util.spec_from_file_location("qa",__import__("pathlib").Path(__file__).with_name("pruebas-android-amore.py"))
qa=importlib.util.module_from_spec(spec);spec.loader.exec_module(qa)
f=json.loads(qa.TEMP.read_text(encoding="utf-8"))
assert qa.adb("shell","getprop","debug.foundia.qa").decode().strip()=="amore-sprint2-20261026"
qa.RESULTADOS.extend(json.loads((qa.EVIDENCIA/"resultados.json").read_text(encoding="utf-8"))["comprobaciones"])
# Chrome se configuró sin cuenta en el emulador temporal; no hay sesión web de FoundIA.
url="http://10.0.2.2:15175/objeto/"+str(f["perdidos"][0])
qa.adb("shell","am","force-stop","com.android.chrome")
qa.adb("shell","am","start","-a","android.intent.action.VIEW","-d",url,"-p","com.android.chrome")
qa.esperar("Celular prueba Amore 0",30)
qa.comprobar("H39: URL compartida abre el objeto real en Chrome sin sesión FoundIA")
qa.foto("h39-web-publica")
# Retirada real en API QA: el mismo enlace deja de mostrar el objeto.
qa.http("/api/objetos/"+str(f["perdidos"][0]),"DELETE",token=f["token"],esperado=(200,204))
qa.adb("shell","am","force-stop","com.android.chrome")
qa.adb("shell","am","start","-a","android.intent.action.VIEW","-d",url,"-p","com.android.chrome")
qa.esperar("Objeto no encontrado",30)
qa.comprobar("H39: el mismo enlace no expone una publicación retirada")
qa.foto("h39-web-retirada")
# Acceso con error real y posterior recuperación del acceso.
qa.reiniciar();qa.pulsar("Cuenta");qa.escribir("Correo",f["correo"]);qa.escribir("Contraseña","Incorrecta123!")
qa.pulsar("Iniciar sesión");qa.esperar("incorrectos")
qa.comprobar("H21: credenciales incorrectas muestran error sin abrir la cuenta")
qa.escribir("Contraseña","Segura123!");qa.pulsar("Iniciar sesión");qa.esperar("Mis publicaciones")
qa.comprobar("H21: nuevo acceso real funciona tras corregir la contraseña")
qa.pulsar("Mis notificaciones");qa.esperar("Actualizar avisos")
qa.pulsar("Abrir coincidencia",True)
qa.esperar("Comparar coincidencia")
for _ in range(10):
    if qa.visible("de similitud") or qa.visible("El recurso no está disponible"):break
    time.sleep(.5)
qa.comprobar("H23: abrir aviso consulta la pareja o informa que fue retirada",qa.visible("de similitud") or qa.visible("El recurso no está disponible"))
qa.reiniciar();qa.esperar("Mis publicaciones");qa.pulsar("Preferencias de avisos");qa.esperar("Guardar preferencias")
# Caída efectiva de la API de QA, sin simular una respuesta satisfactoria.
pid=int((qa.RAIZ/"tmp/api-amore-s2.pid").read_text().strip())
import subprocess,shutil,os
from pathlib import Path
powershell=shutil.which("pwsh") or str(Path(os.environ["SystemRoot"])/"System32/WindowsPowerShell/v1.0/powershell.exe")
subprocess.run([powershell,"-NoProfile","-Command",f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId = {pid}'; if ($p.CommandLine -notlike '*--server.port=18082*') {{throw 'No coincide QA'}}; Stop-Process -Id {pid}"],check=True)
qa.pulsar("Recargar preferencias del servidor");qa.esperar("No hay conexión con FoundIA")
qa.comprobar("H24: caída real de API muestra error y permite reintentar")
qa.foto("h24-sin-conexion")
print("Comprobaciones acumuladas:",len(qa.RESULTADOS),flush=True)
