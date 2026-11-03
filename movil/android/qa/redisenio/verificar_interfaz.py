import json,os,re,subprocess,time,xml.etree.ElementTree as ET
from pathlib import Path
ADB=Path(os.environ['LOCALAPPDATA'])/'Android/Sdk/platform-tools/adb.exe'
APP='com.proyectointegrador.movil'
OUT=Path(__file__).resolve().parent

def adb(*args):return subprocess.check_output([str(ADB),'-s','emulator-5560',*args],stderr=subprocess.STDOUT)
def ui():
 for _ in range(8):
  try:
   salida=adb('shell','uiautomator','dump','/sdcard/foundia-diseno.xml').decode('utf-8')
   if 'dumped to' not in salida:time.sleep(.5);continue
   return ET.fromstring(adb('shell','cat','/sdcard/foundia-diseno.xml').decode('utf-8'))
  except Exception:time.sleep(.5)
 raise AssertionError('No se obtuvo la interfaz accesible')
def nodos():return list(ui().iter('node'))
def esperar(texto):
 fin=time.monotonic()+35
 while time.monotonic()<fin:
  ns=nodos()
  if any(texto in n.get('text','') or texto==n.get('content-desc','') for n in ns):return ns
  time.sleep(.5)
 raise AssertionError('No aparece '+texto)
def centro(n):
 x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));return str((x1+x2)//2),str((y1+y2)//2)
def tap(texto,scroll=False):
 for _ in range(9 if scroll else 1):
  ns=nodos();opciones=[n for n in ns if n.get('text')==texto or n.get('content-desc')==texto]
  if opciones:
   adb('shell','input','tap',*centro(opciones[-1]));time.sleep(1.5);return
  w,h=map(int,re.findall(r'\d+',ns[0].get('bounds'))[-2:]);adb('shell','input','swipe',str(w//2),str(int(h*.72)),str(w//2),str(int(h*.28)),'350');time.sleep(.5)
 raise AssertionError('No se puede pulsar '+texto)
def foto(nombre):
 print('Captura:',nombre,flush=True)
 adb('shell','screencap','-p','/sdcard/foundia-diseno.png');adb('pull','/sdcard/foundia-diseno.png',str(OUT/(nombre+'.png')))
def abrir():
 adb('shell','am','force-stop',APP);adb('shell','am','start','-n',APP+'/.MainActivity');time.sleep(1)
def campos(valores):
 ns=[n for n in nodos() if n.get('class')=='android.widget.EditText']
 adb('shell','input','tap',*centro(ns[0]));time.sleep(2)
 for i,valor in enumerate(valores):
  if i:adb('shell','input','keyevent','61');time.sleep(1)
  ns=[n for n in nodos() if n.get('class')=='android.widget.EditText']
  if not any(n.get('focused')=='true' for n in ns):raise AssertionError('El campo no recibió foco')
  adb('shell','input','keyevent','123');adb('shell','input','keyevent',*(['67']*70))
  adb('shell','input','text',valor.replace(' ','%s'));time.sleep(1)
 adb('shell','input','keyevent','4');time.sleep(1)
if __name__=='__main__':
 assert adb('shell','getprop','debug.foundia.qa_room').decode().strip()=='20261027'
 resultados=[]
 esperar('Lo que perdiste');foto('01-bienvenida');tap('Comenzar')
 for i,clave in enumerate(['01 / ENCUENTRA','02 / PUBLICA','03 / CONECTA']):
  esperar(clave);foto('02-onboarding-'+str(i+1));tap('Comenzar' if i==2 else 'Siguiente',True)
 esperar('Hola, comunidad');foto('03-inicio-publico');abrir();esperar('Hola, comunidad');resultados.append('La introducción completa no vuelve a aparecer al reabrir')
 tap('Mi perfil');esperar('Inicia sesión');foto('04-acceso-claro');campos(['ana@foundia.dev','Admin123!']);tap('Iniciar sesión',True)
 esperar('Hola, Ana');foto('05-inicio-con-sesion');resultados.append('Login JWT real y saludo de la API')
 (OUT/'recorrido.json').write_text(json.dumps(resultados,ensure_ascii=False,indent=2),encoding='utf-8')
 print(json.dumps(resultados,ensure_ascii=False),flush=True)
