import sys
sys.path.insert(0,'qa/redisenio');from verificar_interfaz import *
assert adb('shell','getprop','debug.foundia.qa_room').decode().strip()=='20261027'
resultados=[]
try:
 adb('shell','wm','size','900x1600');adb('shell','wm','density','450');adb('shell','settings','put','system','font_scale','1.3');abrir();esperar('Hola, Ana');foto('14-inicio-320dp-fuente-grande')
 ns=nodos()
 for etiqueta in ['Inicio','Buscar','Publicar','Avisos','Perfil']:
  n=next(n for n in ns if n.get('text')==etiqueta or n.get('content-desc')==etiqueta);x1,y1,x2,y2=map(int,re.findall(r'\d+',n.get('bounds')));assert 0<=x1<x2<=900 and 0<=y1<y2<=1600
 resultados.append('Cinco destinos accesibles a 320dp y fuente 130%')
 tap('Publicar');esperar('PASO 1 DE 6');foto('15-publicar-320dp');tap('Siguiente',True);esperar('PASO 2 DE 6');tap('Siguiente',True);esperar('Ingresa un nombre');foto('16-validacion-por-paso');resultados.append('La publicación no avanza sin información obligatoria')
 ns=[n for n in nodos() if n.get('class')=='android.widget.EditText']
 if not ns:
  adb('shell','input','swipe','450','550','450','1100','350');ns=[n for n in nodos() if n.get('class')=='android.widget.EditText']
 adb('shell','input','tap',*centro(ns[0]));time.sleep(2);foto('17-formulario-con-teclado');adb('shell','input','keyevent','4');resultados.append('Formulario desplazable con teclado y fuente ampliada')
finally:
 adb('shell','wm','size','1080x1920');adb('shell','wm','density','420');adb('shell','settings','put','system','font_scale','1.0')
(OUT/'pantalla-pequena.json').write_text(json.dumps(resultados,ensure_ascii=False,indent=2),encoding='utf-8');print(json.dumps(resultados,ensure_ascii=False),flush=True)
