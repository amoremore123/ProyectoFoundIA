"""Pruebas MySQL exclusivas de las ocho historias de Leonel; nunca usa una BD existente.

Ejecutar con el Python virtual de Django (requirements.txt instalado).
Requiere MySQL portátil ya instalado y un directorio NUEVO fuera del repositorio.
No descarga nada, no borra carpetas y detiene únicamente el proceso que crea.
"""
import argparse
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import socket
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

import pymysql
import sqlparse
from pymysql.constants import CLIENT


def crear_tablas(cursor, texto):
    texto = "\n".join(linea for linea in texto.splitlines() if not linea.lstrip().startswith("--"))
    for sentencia in sqlparse.split(texto):
        sentencia = sentencia.strip()
        if not sentencia:
            continue
        if re.match(r"^(CREATE TABLE|CREATE (?:UNIQUE )?INDEX|SET NAMES)\b", sentencia, re.I):
            cursor.execute(sentencia)
        elif not re.match(r"^(DROP TABLE|CREATE DATABASE|USE)\b", sentencia, re.I):
            raise RuntimeError("El esquema contiene una sentencia no autorizada para QA.")


def probar_migraciones(admin, cursor, repo, nombre):
    destino = nombre + "_migraciones"
    cursor.execute(f"CREATE DATABASE `{destino}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
    admin.select_db(destino)
    base = subprocess.check_output(["git", "show", "191271096b3ac4f9e84c28a6cb8bf31a37188e1a:database/script.sql"], cwd=repo)
    crear_tablas(cursor, base.decode("utf-8-sig"))
    cursor.execute("INSERT INTO usuarios(nombre,apellido,correo,password) VALUES ('QA','Histórico','qa@prueba.invalid','no-se-usa')")
    cursor.execute("INSERT INTO categorias(nombre) VALUES ('QA')")
    cursor.execute("INSERT INTO objetos(usuario_id,categoria_id,nombre,descripcion,fecha_objeto,tipo,estado) VALUES (1,1,'QA','Histórico','2026-01-01','PERDIDO','RECUPERADO')")
    cursor.execute("INSERT INTO reportes(usuario_id,objeto_id,motivo) VALUES (1,1,'Histórico')")
    archivos = sorted(f for f in (repo / "database").glob("migracion_h*.sql")
                      if f.stem.split("_")[1] in {"h22", "h23", "h24", "h28"})
    assert len(archivos) == 5
    for _ in range(2):
        for archivo in archivos:
            sql = archivo.read_text(encoding="utf-8-sig")
            codigo = "\n".join(linea for linea in sql.splitlines() if not linea.lstrip().startswith("--"))
            if re.search(r"(?im)^\s*(USE|DROP|TRUNCATE|DELETE|UPDATE)\b", codigo):
                raise RuntimeError("Migración no permitida por el ejecutor de QA.")
            cursor.execute(sql)
            while cursor.nextset():
                pass
    cursor.execute("SELECT id,estado,fecha_recuperacion FROM objetos")
    assert cursor.fetchall() == ((1, "RECUPERADO", None),)
    cursor.execute("SELECT id,estado,resolucion,resuelto_por_id,fecha_resolucion FROM reportes")
    assert cursor.fetchall() == ((1, "PENDIENTE", None, None, None),)
    cursor.execute("SELECT id,nombre,correo FROM usuarios")
    assert cursor.fetchall() == ((1, "QA", "qa@prueba.invalid"),)
    columnas = {}
    for esquema in (nombre, destino):
        cursor.execute("SELECT TABLE_NAME,COLUMN_NAME,COLUMN_TYPE,IS_NULLABLE FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=%s ORDER BY TABLE_NAME,COLUMN_NAME", (esquema,))
        columnas[esquema] = cursor.fetchall()
    assert columnas[nombre] == columnas[destino], "El esquema migrado difiere del esquema nuevo"
    admin.select_db(nombre)
    return {"archivos": len(archivos), "pasadas": 2, "antecedentes_conservados": True, "esquemas_equivalentes": True}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mysql-basedir", required=True, type=Path)
    parser.add_argument("--workdir", required=True, type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    work = args.workdir.resolve()
    base = args.mysql_basedir.resolve()
    servidor = base / "bin" / ("mysqld.exe" if os.name == "nt" else "mysqld")
    if not servidor.is_file() or work.exists() or work == repo or repo in work.parents:
        raise SystemExit("Se requiere mysqld instalado y un directorio QA nuevo fuera del proyecto.")
    maven = shutil.which("mvn")
    if not maven:
        raise SystemExit("Maven no está disponible.")
    work.mkdir(parents=True)
    data = work / "datos"
    data.mkdir()
    with (work / "inicializacion.log").open("wb") as log:
        subprocess.run([str(servidor), "--no-defaults", "--initialize-insecure",
                        f"--basedir={base}", f"--datadir={data}"], stdout=log, stderr=log,
                       check=True, timeout=180)
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port = sock.getsockname()[1]
    assert port > 1024 and port != 3306
    comando = [str(servidor), "--no-defaults", f"--basedir={base}", f"--datadir={data}",
               "--bind-address=127.0.0.1", f"--port={port}", "--mysqlx=OFF", "--skip-log-bin",
               "--default-time-zone=+00:00", "--character-set-server=utf8mb4",
               "--collation-server=utf8mb4_unicode_ci", f"--log-error={work / 'servidor.log'}",
               f"--pid-file={work / 'servidor.pid'}"]
    admin = None
    resumen = {"mysql_nuevo": True, "puerto": port, "datadir": str(data),
               "base_real_modificada": False, "servidor_detenido": False}
    with (work / "consola.log").open("wb") as consola:
        proceso = subprocess.Popen(comando, stdout=consola, stderr=consola)
        try:
            for _ in range(90):
                if proceso.poll() is not None:
                    raise RuntimeError("MySQL QA terminó; consultar servidor.log.")
                try:
                    admin = pymysql.connect(host="127.0.0.1", port=port, user="root", connect_timeout=2,
                                            autocommit=True, client_flag=CLIENT.MULTI_STATEMENTS)
                    break
                except pymysql.err.OperationalError:
                    time.sleep(1)
            if admin is None:
                raise RuntimeError("MySQL QA no estuvo listo a tiempo.")
            nombre = "foundia_s2_qa_" + secrets.token_hex(6)
            password, token = secrets.token_hex(24), secrets.token_hex(24)
            with admin.cursor() as cursor:
                cursor.execute("SELECT @@datadir, @@port, VERSION(), @@transaction_isolation")
                ruta, puerto, version, aislamiento = cursor.fetchone()
                assert Path(ruta).resolve() == data.resolve() and puerto == port
                resumen.update(mysql_version=version, aislamiento=aislamiento, base=nombre)
                cursor.execute("ALTER USER 'root'@'localhost' IDENTIFIED BY %s", (secrets.token_hex(24),))
                cursor.execute("SELECT COUNT(*) FROM information_schema.SCHEMATA WHERE SCHEMA_NAME=%s", (nombre,))
                assert cursor.fetchone()[0] == 0
                cursor.execute(f"CREATE DATABASE `{nombre}` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci")
                admin.select_db(nombre)
                # Solo CREATE TABLE/INDEX sobre la base nueva. Nunca ejecutar DROP/USE
                # ni reimportar script.sql/seed.sql sobre una base de la aplicación.
                texto = (repo / "database/script.sql").read_text(encoding="utf-8-sig")
                crear_tablas(cursor, texto)
                resumen["migraciones"] = probar_migraciones(admin, cursor, repo, nombre)
                cursor.execute("CREATE TABLE foundia_qa_guard (token VARCHAR(64) NOT NULL)")
                cursor.execute("INSERT INTO foundia_qa_guard VALUES (%s)", (token,))
                for host in ("localhost", "127.0.0.1"):
                    cursor.execute("CREATE USER %s@%s IDENTIFIED BY %s", ("foundia_qa", host, password))
                    for esquema in (nombre, "test_" + nombre):
                        cursor.execute(f"GRANT ALL PRIVILEGES ON `{esquema}`.* TO %s@%s", ("foundia_qa", host))
            entorno = dict(os.environ, FOUNDIA_QA_MYSQL_PORT=str(port), FOUNDIA_QA_MYSQL_DATABASE=nombre,
                           FOUNDIA_QA_MYSQL_PASSWORD=password, FOUNDIA_QA_MYSQL_TOKEN=token,
                           FOUNDIA_QA_MYSQL_USER="foundia_qa", FOUNDIA_QA_MYSQL_DATADIR=str(data.resolve()))
            orden_maven = [maven, "-q", "-Dtest=*MySqlQa", "test"]
            if os.name == "nt":
                orden_maven = ["cmd.exe", "/d", "/c", *orden_maven]
            with (work / "spring-mysql.log").open("wb") as log:
                subprocess.run(orden_maven, cwd=repo / "backend/springboot", env=entorno,
                               stdout=log, stderr=log, check=True, timeout=900)
            reportes = repo / "backend/springboot/target/surefire-reports"
            suites = [ET.parse(f).getroot() for f in reportes.glob("TEST-*MySqlQa.xml")]
            resumen["spring_mysql"] = {k: sum(int(s.attrib.get(k, 0)) for s in suites)
                                        for k in ("tests", "failures", "errors", "skipped")}
            assert suites and resumen["spring_mysql"]["tests"] > 0
            shutil.copytree(reportes, work / "surefire", dirs_exist_ok=True)
            with (work / "django-mysql.log").open("wb") as log:
                subprocess.run([sys.executable, "manage.py", "test", "nucleo", "--noinput",
                                "--settings=config.settings_test_mysql"], cwd=repo / "backend/django-admin",
                               env=entorno, stdout=log, stderr=log, check=True, timeout=180)
            resultado = (work / "django-mysql.log").read_text(encoding="utf-8", errors="replace")
            resumen["django_mysql"] = {"tests": int(re.search(r"Ran (\d+) tests?", resultado).group(1)), "ok": True}
        finally:
            if admin is not None:
                try:
                    with admin.cursor() as cursor:
                        cursor.execute("SHUTDOWN")
                except pymysql.MySQLError:
                    pass
                admin.close()
            try:
                proceso.wait(timeout=30)
            except subprocess.TimeoutExpired:
                proceso.terminate()
                proceso.wait(timeout=15)
            resumen["servidor_detenido"] = proceso.poll() is not None
            (work / "resultado.json").write_text(json.dumps(resumen, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(resumen, ensure_ascii=True, indent=2))


if __name__ == "__main__":
    main()
