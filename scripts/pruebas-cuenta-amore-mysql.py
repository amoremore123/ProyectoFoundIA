"""Validación H33/H34 exclusivamente en el contenedor QA creado para Amore.
No acepta destinos configurables: no puede apuntar a la BD habitual del equipo.
"""
import concurrent.futures, importlib.util, json, subprocess
from pathlib import Path
RAIZ=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location("qa_android",RAIZ/"scripts/pruebas-android-amore.py")
qa=importlib.util.module_from_spec(spec);spec.loader.exec_module(qa)
f=json.loads(qa.TEMP.read_text(encoding="utf-8"))
COMANDO=["docker","compose","-f",str(RAIZ/"scripts/qa-amore-sprint2.compose.yml"),"exec","-T","mysql","mysql","-uroot","-pqa-amore-s2","-N"]
RESULTADOS=[]
def sql(texto):
    r=subprocess.run(COMANDO,input=texto.encode(),capture_output=True,check=True)
    return r.stdout.decode().strip()
def ok(nombre,condicion):
    assert condicion,nombre
    RESULTADOS.append({"comprobacion":nombre,"resultado":"correcto"});print("OK:",nombre,flush=True)

# Una segunda base nueva, dentro del servidor QA, simula el esquema previo mínimo.
sql("CREATE DATABASE IF NOT EXISTS amore_upgrade_qa; USE amore_upgrade_qa; CREATE TABLE IF NOT EXISTS usuarios (id BIGINT PRIMARY KEY, nombre VARCHAR(100)); INSERT IGNORE INTO usuarios VALUES (1,'Dato previo QA');")
migraciones=[(RAIZ/"database"/n).read_text(encoding="utf-8") for n in ("migracion_h33_aceptacion_legal.sql","migracion_h34_solicitudes_eliminacion.sql")]
for _ in range(2):sql("USE amore_upgrade_qa;"+"\n".join(migraciones))
ok("H33/H34: migraciones reejecutables sobre esquema anterior sin perder la fila previa",sql("USE amore_upgrade_qa; SELECT CONCAT(COUNT(*),':',SUM(version_legal IS NULL AND aceptacion_legal_fecha IS NULL AND documentos_legales_hash IS NULL)) FROM usuarios;")=="1:1")
ok("H34: migración no crea solicitudes anteriores",sql("SELECT COUNT(*) FROM amore_upgrade_qa.solicitudes_eliminacion;")=="0")
for _ in range(2):sql("USE objetos_perdidos_db;"+"\n".join(migraciones))
docs=qa.http("/api/legal/documentos")
for acepta,version in ((False,docs["version"]),(True,"version-incorrecta")):
    qa.http("/api/auth/register","POST",qa.registro("rechazo@qa.invalid",version,acepta),esperado=400)
ok("H33: rechazo o versión incorrecta no crean cuenta",sql("SELECT COUNT(*) FROM objetos_perdidos_db.usuarios WHERE correo='rechazo@qa.invalid';")=="0")
# El correo de esta fixture es sintético, generado por el ejecutor, nunca entrada de usuario.
assert f["correo"].startswith("amore") and f["correo"].endswith("@qa.invalid") and "'" not in f["correo"]
valor=sql("SELECT CONCAT(version_legal,':',documentos_legales_hash,':',aceptacion_legal_fecha IS NOT NULL) FROM objetos_perdidos_db.usuarios WHERE correo='"+f["correo"]+"';")
ok("H33: versión, huella y fecha de aceptación del APK persistidas en MySQL",valor==docs["version"]+":"+docs["sha256"]+":1")
qa.http("/api/perfil/solicitud-eliminacion",esperado=401)
qa.http("/api/perfil/solicitud-eliminacion","POST",{"confirmar":True,"usuarioId":999},f["token"],400)
ok("H34: no acepta identidad enviada por el cliente",True)
antes=qa.http("/api/perfil/solicitud-eliminacion",token=f["token"])
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    respuestas=list(pool.map(lambda _:qa.http("/api/perfil/solicitud-eliminacion","POST",{"confirmar":True},f["token"],(200,201)),range(4)))
ok("H34: solicitudes simultáneas conservan el mismo trámite y fecha",len({r["id"] for r in respuestas})==1 and all(r["fechaSolicitud"]==antes["fechaSolicitud"] for r in respuestas))
ok("H34: otra cuenta conserva su propio estado",qa.http("/api/perfil/solicitud-eliminacion",token=f["otroToken"])["estado"]=="SIN_SOLICITUD")
ok("H34: solicitar no borra perfil ni publicaciones",len(qa.http("/api/perfil/objetos",token=f["token"]))==3)
qa.http("/api/perfil/solicitud-eliminacion",token="token-no-valido",esperado=401)
ok("H34: token inválido rechazado",True)
(qa.EVIDENCIA/"mysql-cuenta.json").write_text(json.dumps({"entorno":"MySQL 8 QA aislado :13369, sin modificar BD del equipo","comprobaciones":RESULTADOS},ensure_ascii=False,indent=2),encoding="utf-8")
