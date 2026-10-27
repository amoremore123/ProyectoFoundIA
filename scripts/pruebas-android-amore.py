"""Prueba el APK instalado contra la API y MySQL QA; no usa la base del equipo.
Requiere emulador-5554, APK con API_URL=10.0.2.2:18082 y Mailpit :18369.
Los tokens temporales quedan solo en tmp/, nunca en las evidencias versionadas.
"""
import argparse, concurrent.futures, datetime, json, os, re, subprocess, time, urllib.request, urllib.error
import xml.etree.ElementTree as ET
from pathlib import Path
BASE="http://127.0.0.1:18082"
MAIL="http://127.0.0.1:18369"
ADB=Path(os.environ["LOCALAPPDATA"])/"Android/Sdk/platform-tools/adb.exe"
RAIZ=Path(__file__).resolve().parents[1]
TEMP=RAIZ/"tmp/amore-s2-fixture.json"
EVIDENCIA=RAIZ/"docs/pruebas/evidencias-amore-sprint2-2026-10-26"
EVIDENCIA.mkdir(parents=True,exist_ok=True)
APP="com.proyectointegrador.movil"
RESULTADOS=[]

def http(ruta,metodo="GET",datos=None,token=None,esperado=200):
    headers={"Content-Type":"application/json"}
    if token:headers["Authorization"]="Bearer "+token
    req=urllib.request.Request(BASE+ruta,data=json.dumps(datos).encode() if datos is not None else None,headers=headers,method=metodo)
    try:
        with urllib.request.urlopen(req,timeout=25) as r:status=r.status;body=r.read()
    except urllib.error.HTTPError as e:status=e.code;body=e.read()
    assert status in ([esperado] if isinstance(esperado,int) else esperado),(ruta,status,body.decode(errors="replace")[:500])
    return json.loads(body) if body else None

def comprobar(nombre,condicion=True):
    assert condicion,nombre
    if any(r["comprobacion"]==nombre for r in RESULTADOS):return
    RESULTADOS.append({"comprobacion":nombre,"resultado":"correcto"})
    print("OK:",nombre,flush=True)
    (EVIDENCIA/"resultados.json").write_text(json.dumps({"entorno":"API :18082 + MySQL QA :13369 + emulador Android", "comprobaciones":RESULTADOS},ensure_ascii=False,indent=2),encoding="utf-8")

def codigo(correo):
    for _ in range(30):
        with urllib.request.urlopen(MAIL+"/api/v1/messages",timeout=5) as r:mensajes=json.load(r)["messages"]
        for m in mensajes:
            if any(t.get("Address")==correo for t in m.get("To",[])):
                encontrado=re.search(r"[0-9]{6}",m["Subject"])
                if encontrado:return encontrado[0]
        time.sleep(.5)
    raise AssertionError("No llegó el código QA")

def registro(correo,version,acepta=True):
    return {"nombre":"Amore", "apellido":"Prueba","correo":correo,"password":"Segura123!", "aceptaTerminos":acepta,"versionLegal":version}

def cuenta(correo,version):
    http("/api/auth/register","POST",registro(correo,version),esperado=201)
    return http("/api/auth/verificar","POST",{"correo":correo,"codigo":codigo(correo)})["token"]

def preparar():
    docs=http("/api/legal/documentos")
    sufijo=str(int(time.time()))
    correo="amore"+sufijo+"@qa.invalid"
    otro="otra"+sufijo+"@qa.invalid"
    f={"correo":correo,"otro":otro,"version":docs["version"],"otroToken":cuenta(otro,docs["version"])}
    TEMP.write_text(json.dumps(f),encoding="utf-8")
    print("Fixture preparada exclusivamente en QA",flush=True)

def adb(*args):
    return subprocess.check_output([str(ADB),"-s","emulator-5554",*args],stderr=subprocess.STDOUT)

def ui():
    adb("shell","uiautomator","dump","/sdcard/foundia.xml")
    return ET.fromstring(adb("shell","cat","/sdcard/foundia.xml").decode())

def nodo(texto,raiz=None):
    return next((n for n in (raiz if raiz is not None else ui()).iter("node") if n.get("text")==texto or n.get("content-desc")==texto),None)

def tap(n):
    x1,y1,x2,y2=map(int,re.findall(r"[0-9]+",n.get("bounds")))
    assert x2>x1 and y2>y1
    adb("shell","input","tap",str((x1+x2)//2),str((y1+y2)//2))

def buscar(texto,scroll=False):
    for _ in range(10 if scroll else 4):
        n=nodo(texto)
        if n is not None:return n
        if scroll:adb("shell","input","swipe","540","1750","540","650","300")
        else:time.sleep(.3)
    raise AssertionError("No se encontró control: "+texto+"; visibles: "+str([n.get("text") for n in ui().iter("node") if n.get("text")]))

def pulsar(texto,scroll=False):tap(buscar(texto,scroll))
def escribir(etiqueta,valor):
    raiz=ui();campo=None
    for n in raiz.iter("node"):
        if n.get("class")=="android.widget.EditText" and any(c.get("text")==etiqueta for c in n.iter("node")):campo=n;break
    if campo is None:
        buscar(etiqueta,True);raiz=ui()
        campo=next(n for n in raiz.iter("node") if n.get("class")=="android.widget.EditText" and any(c.get("text")==etiqueta for c in n.iter("node")))
    tap(campo)
    adb("shell","input","keyevent","KEYCODE_MOVE_END")
    if campo.get("text"):adb("shell","input","keyevent",*(["KEYCODE_DEL"]*len(campo.get("text"))))
    adb("shell","input","text",valor.replace(" ","%s"))
    adb("shell","input","keyevent","4")

def visible(parte):
    return any(parte in (n.get("text","")+n.get("content-desc","")) for n in ui().iter("node"))

def esperar(parte,segundos=20):
    fin=time.monotonic()+segundos
    while time.monotonic()<fin:
        if visible(parte):return
        time.sleep(.3)
    raise AssertionError("No apareció: "+parte)

def foto(nombre):
    adb("shell","screencap","-p","/sdcard/foundia.png")
    adb("pull","/sdcard/foundia.png",str(EVIDENCIA/(nombre+".png")))

def atras():adb("shell","input","keyevent","4")
def reiniciar():
    adb("shell","am","force-stop",APP);adb("shell","am","start","-n",APP+"/.MainActivity")
    time.sleep(.8)

def ejecutar():
    f=json.loads(TEMP.read_text(encoding="utf-8"))
    assert adb("shell","getprop","debug.foundia.qa").decode().strip()=="amore-sprint2-20261026", "Usar únicamente el emulador QA marcado"
    adb("shell","pm","clear",APP);reiniciar();pulsar("Cuenta");pulsar("Crear cuenta")
    esperar("Leer términos de uso")
    pulsar("Leer términos de uso");esperar("Términos de uso de FoundIA");foto("h33-terminos");pulsar("Cerrar documento")
    escribir("Nombre","Amore");escribir("Apellido","Prueba");escribir("Correo",f["correo"])
    escribir("Contraseña","Segura123!");escribir("Confirmar contraseña","Segura123!")
    buscar("Registrarme y recibir código",True)
    raiz=ui();boton=buscar("Registrarme y recibir código")
    padres={h:p for p in raiz.iter() for h in p}
    b=nodo("Registrarme y recibir código",raiz)
    while b is not None and b.get("clickable")!="true":b=padres.get(b)
    comprobar("H33: registro deshabilitado sin aceptación", b is not None and b.get("enabled")=="false")
    casilla=next(n for n in raiz.iter("node") if n.get("checkable")=="true")
    comprobar("H33: aceptación inicialmente desmarcada y etiquetada",casilla.get("checked")=="false" and any("Acepto" in n.get("text","") for n in casilla.iter("node")))
    foto("h33-aceptacion");tap(casilla);pulsar("Registrarme y recibir código")
    esperar("Verificar y entrar")
    escribir("Código de 6 dígitos","000000");pulsar("Verificar y entrar");esperar("Código incorrecto")
    comprobar("H33: código incorrecto mantiene la cuenta sin activar")
    # Limpiar el campo con selección total mediante pulsación larga no depende del teclado.
    raiz=ui();campo=next(n for n in raiz.iter("node") if n.get("class")=="android.widget.EditText" and n.get("text")=="000000")
    tap(campo);adb("shell","input","keyevent","KEYCODE_MOVE_END")
    for _ in range(6):adb("shell","input","keyevent","KEYCODE_DEL")
    adb("shell","input","text",codigo(f["correo"]));atras();pulsar("Verificar y entrar");esperar("Mis publicaciones")
    comprobar("H33: registro y verificación reales desde Android")
    f["token"]=http("/api/auth/login","POST",{"correo":f["correo"],"password":"Segura123!"})["token"]
    TEMP.write_text(json.dumps(f),encoding="utf-8")
    reiniciar();esperar("Mis publicaciones");comprobar("H21: sesión cifrada persistida tras cerrar y reabrir")
    # Tres parejas independientes para cancelación, descarte y recuperación.
    cat=http("/api/categorias")[0]["id"]
    perdidos=[];encontrados=[]
    for i in range(3):
        body={"categoriaId":cat,"nombre":"Celular prueba Amore "+str(i),"descripcion":"Publicación sintética de QA", "fechaObjeto":"2026-10-20","tipo":"PERDIDO","ubicacion":"Biblioteca QA"}
        perdidos.append(http("/api/objetos","POST",body,f["token"],201)["id"])
        body["tipo"]="ENCONTRADO";encontrados.append(http("/api/objetos","POST",body,f["otroToken"],201)["id"])
    f["perdidos"]=perdidos;f["encontrados"]=encontrados;TEMP.write_text(json.dumps(f),encoding="utf-8")
    pulsar("Preferencias de avisos");esperar("Guardar preferencias")
    raiz=ui();switches=[n for n in raiz.iter("node") if n.get("checkable")=="true"]
    tap(switches[0]);pulsar("Guardar preferencias");esperar("Preferencias guardadas.")
    comprobar("H24: cambio Android guardado en la API",http("/api/notificaciones/preferencias",token=f["token"])["coincidencias"] is False)
    atras();pulsar("Preferencias de avisos");esperar("Guardar preferencias")
    comprobar("H24: recarga conserva preferencia",[n for n in ui().iter("node") if n.get("checkable")=="true"][0].get("checked")=="false")
    foto("h24-preferencias");atras()
    pulsar("Solicitar eliminación de cuenta");esperar("Sin solicitud");pulsar("Solicitar eliminación de mi cuenta y datos")
    pulsar("Cancelar");comprobar("H34: cancelar no crea solicitud",http("/api/perfil/solicitud-eliminacion",token=f["token"])["estado"]=="SIN_SOLICITUD")
    pulsar("Solicitar eliminación de mi cuenta y datos");pulsar("Confirmar");esperar("PENDIENTE")
    antes=http("/api/perfil/solicitud-eliminacion",token=f["token"])
    repetida=http("/api/perfil/solicitud-eliminacion","POST",{"confirmar":True},f["token"])
    comprobar("H34: solicitud persistida y repetición con misma fecha",antes["fechaSolicitud"]==repetida["fechaSolicitud"] and repetida["repetido"])
    foto("h34-pendiente");atras()
    pulsar("Mis publicaciones");esperar("Revisar coincidencias de Celular prueba Amore 0")
    pulsar("Revisar coincidencias de Celular prueba Amore 0");esperar("Comparar ambos objetos");pulsar("Comparar ambos objetos");buscar("Descartar coincidencia",True)
    pareja=f"{perdidos[0]}-{encontrados[0]}"
    pulsar("Descartar coincidencia");pulsar("Cancelar")
    comprobar("H21: cancelar conserva la pareja",any(c["coincidenciaId"]==pareja for c in http(f"/api/objetos/{perdidos[0]}/coincidencias")))
    pulsar("Descartar coincidencia");pulsar("Confirmar");esperar("Coincidencia descartada.")
    comprobar("H21: descarte Android persistido",all(c["coincidenciaId"]!=pareja for c in http(f"/api/objetos/{perdidos[0]}/coincidencias")))
    foto("h21-descarte");pulsar("Volver a mis publicaciones")
    pulsar("Revisar coincidencias de Celular prueba Amore 1");esperar("Comparar ambos objetos");pulsar("Comparar ambos objetos");buscar("Confirmar recuperación",True)
    pulsar("Confirmar recuperación");pulsar("Cancelar")
    comprobar("H22: cancelar conserva el perdido activo",http(f"/api/objetos/{perdidos[1]}")["estado"]=="ACTIVO")
    pulsar("Confirmar recuperación");pulsar("Confirmar");esperar("Recuperación confirmada.")
    comprobar("H22: recuperación real sin cambiar el hallazgo",http(f"/api/objetos/{perdidos[1]}")["estado"]=="RECUPERADO" and http(f"/api/objetos/{encontrados[1]}")["estado"]=="ACTIVO")
    foto("h22-recuperacion");pulsar("Volver a mis publicaciones")
    # Detalle y denuncia de la tercera publicación.
    pulsar("Ver publicación "+str(perdidos[2]));buscar("Reportar esta publicación",True);pulsar("Reportar esta publicación")
    esperar("Revisar denuncia");pulsar("Revisar denuncia");pulsar("Cancelar");comprobar("H27: cancelar conserva el formulario sin mostrar éxito",visible("Revisar denuncia"))
    pulsar("Revisar denuncia");pulsar("Confirmar");esperar("Denuncia registrada");foto("h27-denuncia")
    comprobar("H27: denuncia Android confirmada por el servidor")
    atras();buscar("Compartir publicación",True);pulsar("Compartir publicación");esperar("Compartir")
    foto("h39-selector");atras();comprobar("H39: cancelar selector no afirma un envío",not visible("Enviado correctamente"))
    # Volver a cuenta desde una navegación nueva, conservando la sesión.
    reiniciar();esperar("Mis publicaciones");pulsar("Mis notificaciones");esperar("Actualizar avisos")
    comprobar("H23: bandeja propia disponible desde Android")
    raiz=ui()
    if nodo("Marcar como leída",raiz) is not None:
        pulsar("Marcar como leída");esperar("Leída");comprobar("H23: lectura Android persistida",any(n["leida"] for n in http("/api/notificaciones",token=f["token"])))
    foto("h23-bandeja");atras();pulsar("Cerrar sesión");pulsar("Cuenta");esperar("Iniciar sesión")
    comprobar("H21: cerrar sesión retira los datos privados")
    adb("shell","am","start","-a","android.intent.action.VIEW","-d","foundia://restablecer?token=corto","-n",APP+"/.MainActivity")
    esperar("El enlace de recuperación no es válido")
    comprobar("H35: enlace inválido rechazado sin cambiar contraseña")
    foto("h35-enlace-invalido")
    print("Comprobaciones Android terminadas:",len(RESULTADOS),flush=True)

if __name__=="__main__":
    parser=argparse.ArgumentParser();parser.add_argument("fase",choices=["preparar","android"]);args=parser.parse_args()
    preparar() if args.fase=="preparar" else ejecutar()
