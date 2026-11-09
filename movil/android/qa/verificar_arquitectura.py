"""Auditoría repetible de las fronteras MVVM. Solo inspecciona archivos Android."""
from pathlib import Path
import argparse,json,re,subprocess
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--base", help="Referencia opcional para verificar que el diff solo toca Android")
args=parser.parse_args()
raiz=Path(__file__).resolve().parents[1]
java=raiz/"app/src/main/java/com/proyectointegrador/movil"
fallos=[];vistas=[]
for carpeta in (java/"ui",java/"navigation"):
 for p in carpeta.rglob("*.kt"):
  if "viewmodel" in p.parts:continue
  vistas.append(str(p.relative_to(raiz)))
  texto=p.read_text(encoding="utf-8")
  if re.search(r"RetrofitClient|\.api\.|Room\.databaseBuilder|cacheDao\(|import com\.proyectointegrador\.movil\.(network|data)\.",texto):fallos.append(str(p))
fuentes="\n".join(p.read_text(encoding="utf-8") for p in java.rglob("*.kt"))
assert "allowMainThreadQueries" not in fuentes
assert "fallbackToDestructiveMigration" not in fuentes
assert not fallos,fallos
assert (raiz/"app/schemas/com.proyectointegrador.movil.data.local.FoundIADatabase/1.json").exists()
assert (raiz/"app/schemas/com.proyectointegrador.movil.data.local.FoundIADatabase/2.json").exists()
informe={"vistasRevisadas":len(vistas),"llamadasDirectasDeApiODaoEnVistas":0,"consultasEnHiloPrincipal":False,
 "migracionDestructiva":False,"esquemasRoom":[1,2]}
if args.base:
 cambios=subprocess.check_output(["git","diff","--name-only",args.base,"--"],cwd=raiz.parents[1],text=True).splitlines()
 assert all(p.startswith("movil/android/") for p in cambios),cambios
 informe["cambiosSoloAndroid"]=True
print(json.dumps(informe,ensure_ascii=False))
