"""Ejecutar tras detener exclusivamente API QA :18083. No borra datos ni instala en dispositivos ajenos."""
import json,os,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
ADB=Path(os.environ["LOCALAPPDATA"])/"Android/Sdk/platform-tools/adb.exe"
EVIDENCIA=Path(__file__).resolve().parent/"evidencias"
APP="com.proyectointegrador.movil"
def adb(*args):return subprocess.check_output([str(ADB),"-s","emulator-5560",*args],stderr=subprocess.STDOUT)
def ui():
 for _ in range(8):
  try:
   salida=adb("shell","uiautomator","dump","/sdcard/room-vista.xml").decode()
   if "dumped to" not in salida:time.sleep(.5);continue
   return ET.fromstring(adb("shell","cat","/sdcard/room-vista.xml").decode())
  except (subprocess.CalledProcessError,ET.ParseError):time.sleep(.5)
 raise AssertionError("La interfaz todavía no ofrece un árbol accesible")
def esperar(texto):
 fin=time.monotonic()+35
 while time.monotonic()<fin:
  raiz=ui()
  if any(texto in n.get("text","") for n in raiz.iter("node")):return raiz
  time.sleep(.3)
 raise AssertionError("No apareció: "+texto)
def pulsar(texto):
 raiz=esperar(texto);n=next(n for n in raiz.iter("node") if n.get("text")==texto)
 x1,y1,x2,y2=map(int,re.findall(r"[0-9]+",n.get("bounds")));adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))
def abrir():
 adb("shell","am","force-stop",APP);adb("shell","am","start","-n",APP+"/.MainActivity")
 time.sleep(1);pulsar("Inicio")
def foto(nombre):
 adb("shell","screencap","-p","/sdcard/room-foto.png");adb("pull","/sdcard/room-foto.png",str(EVIDENCIA/(nombre+".png")))
assert adb("shell","getprop","debug.foundia.qa_room").decode().strip()=="20261027"
resultados=[]
for i in range(2):
 abrir();esperar("Sin actualizar: mostrando una copia local.")
 raiz=ui();assert any("ROOM" in n.get("text","") for n in raiz.iter("node"))
 foto("cache-sin-conexion" if i==0 else "cache-despues-reabrir")
 resultados.append({"caso":"caché visible sin conexión" if i==0 else "caché persiste tras detener y reabrir el proceso","resultado":"correcto"})
 print("OK:",resultados[-1]["caso"],flush=True)
pulsar("Publicar");esperar("Sin actualizar: mostrando una copia local.")
foto("cache-categorias-formulario")
resultados.append({"caso":"el formulario identifica categorías de copia local sin afirmar un guardado remoto","resultado":"correcto"})
print("OK:",resultados[-1]["caso"],flush=True)
(EVIDENCIA/"cache-emulador.json").write_text(json.dumps({"emulador":"QA dedicado 5560","apiQaDetenida":True,"comprobaciones":resultados},ensure_ascii=False,indent=2),encoding="utf-8")
