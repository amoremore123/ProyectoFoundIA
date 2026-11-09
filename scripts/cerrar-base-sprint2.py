"""Respaldo, restauración aislada y migraciones H33/H34/H35 de la base local compartida.

No importa script.sql/seed.sql en la base compartida ni elimina volúmenes.
Las credenciales se leen internamente del contenedor y no se imprimen.
"""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

import pymysql
import sqlparse

REPO = Path(__file__).resolve().parents[1]
COMPARTIDO = "foundia-mysql"
QA = "foundia-cierre-s2-qa-20261103-mysql-1"
BASE = "objetos_perdidos_db"
VOLUMEN = "proyectofoundia_mysql_data"


def inspeccionar(nombre):
    datos = json.loads(subprocess.check_output(["docker", "inspect", nombre]))[0]
    entorno = dict(e.split("=", 1) for e in datos["Config"]["Env"] if "=" in e)
    if nombre == COMPARTIDO:
        assert any(m.get("Name") == VOLUMEN and m["Destination"] == "/var/lib/mysql" for m in datos["Mounts"])
        assert entorno.get("MYSQL_DATABASE") == BASE
    else:
        assert nombre == QA and datos["Config"]["Labels"].get("com.foundia.qa") == "cierre-sprint2-20261103"
        assert not any(m.get("Name") == VOLUMEN for m in datos["Mounts"])
    return datos, entorno


def conectar(nombre, base=BASE):
    _, entorno = inspeccionar(nombre)
    return pymysql.connect(host="127.0.0.1", port=3306 if nombre == COMPARTIDO else 13372,
                           user="root", password=entorno["MYSQL_ROOT_PASSWORD"], database=base,
                           charset="utf8mb4", autocommit=True)


def huellas(conexion, anteriores=None):
    resultado = {}
    with conexion.cursor() as cur:
        cur.execute("START TRANSACTION WITH CONSISTENT SNAPSHOT")
        if anteriores is None:
            cur.execute("SHOW TABLES")
            tablas = [r[0] for r in cur.fetchall()]
        else:
            tablas = anteriores.keys()
        for tabla in tablas:
            assert re.fullmatch(r"[a-zA-Z0-9_]+", tabla)
            columnas = anteriores[tabla]["columnas"] if anteriores else None
            seleccion = ",".join("`" + c + "`" for c in columnas) if columnas else "*"
            cur.execute(f"SELECT {seleccion} FROM `{tabla}`")
            columnas = [d[0] for d in cur.description]
            filas = sorted(json.dumps(r, default=str, ensure_ascii=False, separators=(",", ":")) for r in cur.fetchall())
            resultado[tabla] = {"columnas": columnas, "filas": len(filas),
                                "sha256": hashlib.sha256("\n".join(filas).encode()).hexdigest()}
        conexion.commit()
    return resultado


def ejecutar_archivo(nombre, ruta, comando):
    # El password se expande dentro del contenedor y nunca aparece en los argumentos del host.
    prefijo = ["docker", "exec", "-i", nombre, "sh", "-c", 'export MYSQL_PWD="$MYSQL_ROOT_PASSWORD"; exec ' + comando]
    if comando.startswith("mysqldump"):
        with ruta.open("xb") as salida:
            subprocess.run(prefijo, stdout=salida, stderr=subprocess.PIPE, check=True)
    else:
        with ruta.open("rb") as entrada:
            subprocess.run(prefijo, stdin=entrada, stdout=subprocess.PIPE, stderr=subprocess.PIPE, check=True)


def guardar(ruta, datos):
    ruta.write_text(json.dumps(datos, ensure_ascii=False, indent=2), encoding="utf-8")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("fase", choices=["respaldar", "verificar", "migrar"])
    parser.add_argument("--directorio", required=True, type=Path)
    args = parser.parse_args()
    carpeta = args.directorio.resolve()
    raiz = (REPO.parent / "RespaldosFoundIA").resolve()
    assert raiz in carpeta.parents, "El respaldo debe quedar fuera del repositorio, en RespaldosFoundIA."
    archivo = carpeta / "objetos_perdidos_db.sql"
    manifiesto = carpeta / "manifiesto.json"
    if args.fase == "respaldar":
        carpeta.mkdir(parents=True, exist_ok=False)
        datos, _ = inspeccionar(COMPARTIDO)
        cn = conectar(COMPARTIDO)
        antes = huellas(cn)
        ejecutar_archivo(COMPARTIDO, archivo, "mysqldump --user=root --single-transaction --routines --events --triggers --hex-blob --no-tablespaces --set-gtid-purged=OFF --default-character-set=utf8mb4 " + BASE)
        assert huellas(cn) == antes, "La base cambió durante el respaldo: no se migrará con esta copia."
        estado = {"base": BASE, "contenedorId": datos["Id"], "volumen": VOLUMEN,
                  "backupSha256": hashlib.sha256(archivo.read_bytes()).hexdigest(), "tablasAntes": antes,
                  "restauracionVerificada": False}
        guardar(manifiesto, estado)
        print(json.dumps({"respaldo": str(archivo), "tablas": len(antes), "bytes": archivo.stat().st_size}))
        return
    estado = json.loads(manifiesto.read_text(encoding="utf-8"))
    assert hashlib.sha256(archivo.read_bytes()).hexdigest() == estado["backupSha256"]
    if args.fase == "verificar":
        inspeccionar(QA)
        copia = "respaldo_" + hashlib.sha256(str(carpeta).encode()).hexdigest()[:12]
        cn = conectar(QA)
        with cn.cursor() as cur:
            cur.execute(f"CREATE DATABASE `{copia}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
        ejecutar_archivo(QA, archivo, "mysql --user=root --default-character-set=utf8mb4 " + copia)
        restaurada = conectar(QA, copia)
        assert huellas(restaurada) == estado["tablasAntes"], "La copia restaurada no coincide con el origen."
        estado["restauracionVerificada"] = True
        estado["baseRestauracionQa"] = copia
        guardar(manifiesto, estado)
        print("Respaldo restaurado en QA: todas las tablas, filas y huellas coinciden.")
        return
    assert subprocess.check_output(["git", "branch", "--show-current"], cwd=REPO, text=True).strip() == "dev/amore"
    assert estado["restauracionVerificada"], "Falta verificar la restauración antes de migrar."
    datos, _ = inspeccionar(COMPARTIDO)
    assert datos["Id"] == estado["contenedorId"]
    cn = conectar(COMPARTIDO)
    with cn.cursor() as cur:
        cur.execute("SELECT GET_LOCK('foundia_cierre_sprint2',0)")
        assert cur.fetchone()[0] == 1, "Hay otra migración en curso."
        assert huellas(cn) == estado["tablasAntes"], "La base cambió: hay que generar y verificar otro respaldo."
        cur.execute("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s", (BASE,))
        presentes = set(cur.fetchall())
        requisitos = {
            "migracion_h33_aceptacion_legal.sql": {("usuarios", c) for c in ["version_legal", "aceptacion_legal_fecha", "documentos_legales_hash"]},
            "migracion_h34_solicitudes_eliminacion.sql": {("solicitudes_eliminacion", c) for c in ["id", "usuario_id", "estado", "fecha_solicitud"]},
            "migracion_h35_recuperacion_password.sql": {("usuarios", "password_actualizado_en")} | {("tokens_recuperacion_password", c) for c in ["id", "usuario_id", "token_hash", "creado_en", "expira_en", "usado_en"]},
        }
        aplicadas = []
        for nombre, requeridas in requisitos.items():
            if requeridas <= presentes:
                aplicadas.append({"archivo": nombre, "estado": "Ya aplicada; no se ejecuta otra vez"})
                continue
            sql = (REPO / "database" / nombre).read_text(encoding="utf-8-sig")
            codigo = "\n".join(l for l in sql.splitlines() if not l.lstrip().startswith("--"))
            for sentencia in sqlparse.split(codigo):
                assert not re.match(r"\s*(DROP|TRUNCATE|DELETE|UPDATE|INSERT|REPLACE|USE)\b", sentencia, re.I)
                cur.execute(sentencia)
                while cur.nextset():
                    pass
            aplicadas.append({"archivo": nombre, "estado": "Aplicada"})
        despues = huellas(cn, estado["tablasAntes"])
        assert despues == estado["tablasAntes"], "Hay diferencias en filas previas; se conserva el respaldo y no se restaura automáticamente."
        cur.execute("SELECT TABLE_NAME,COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s", (BASE,))
        finales = set(cur.fetchall())
        assert set.union(*requisitos.values()) <= finales
        for tabla, columna in finales - presentes:
            if tabla == "usuarios":
                cur.execute(f"SELECT COUNT(*) FROM usuarios WHERE `{columna}` IS NOT NULL")
                assert cur.fetchone()[0] == 0, "No debe atribuirse consentimiento ni cambios a cuentas anteriores."
        cur.execute("SELECT RELEASE_LOCK('foundia_cierre_sprint2')")
    estado.update(migraciones=aplicadas, datosPreviosConservados=True, columnasAgregadas=sorted(finales - presentes))
    guardar(manifiesto, estado)
    print(json.dumps({"migraciones": aplicadas, "datosPreviosConservados": True}, ensure_ascii=False))


if __name__ == "__main__":
    main()
