"""Borra de la base de QA las publicaciones y los usuarios de prueba, junto con todo lo que depende de ellos.

Las publicaciones se eligen por NOMBRE (no por id): las que dicen «prueba», «QA», «H44», «H52» o terminan en un código
raro como «4b94». Los usuarios, por su correo `@qa.invalid`. Usa las claves foráneas reales de MySQL para borrar primero
a los hijos, todo dentro de una transacción.

Por seguridad solo SIMULA. Para borrar de verdad hay que pasar --aplicar. Saca antes un respaldo de la base.
Uso:  python database/datos_demo/limpiar_datos_prueba.py [--aplicar]
"""
import subprocess, sys

CONTENEDOR = "foundia-amore-s3-mysql"
BASE = "objetos_perdidos_db"
CONTENEDOR_API = "foundia-amore-s3-api"
PATRON = "prueba|QA|H44|H52|[ ][0-9a-f]{4,6}$"
OBJETOS = []
USUARIOS = []


def mysql(sql, entrada=None):
    cmd = ["docker", "exec", "-i", CONTENEDOR, "sh", "-c",
           'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --default-character-set=utf8mb4 -N -B ' + BASE]
    r = subprocess.run(cmd, input=sql.encode("utf-8"), capture_output=True)
    if r.returncode != 0:
        raise RuntimeError(r.stderr.decode("utf-8", "replace"))
    return r.stdout.decode("utf-8", "replace")


OBJETOS = [int(x) for x in mysql("select id from objetos where nombre regexp '%s' order by id;" % PATRON).split()]
USUARIOS = [int(x) for x in mysql("select id from usuarios where correo like '%@qa.invalid' order by id;").split()]
if not OBJETOS and not USUARIOS:
    print("No hay datos de prueba que borrar."); sys.exit(0)
print("Publicaciones a borrar:", OBJETOS, "| usuarios:", USUARIOS)
filas = mysql("select table_name, column_name, referenced_table_name, referenced_column_name from information_schema.key_column_usage "
              "where table_schema='%s' and referenced_table_name is not null;" % BASE).strip().splitlines()
hijos = {}
for f in filas:
    t, c, rt, rc = f.split("\t")
    hijos.setdefault(rt, []).append((t, c, rc))

sentencias = []
vistas = set()


def generar(tabla, cond, profundidad=0):
    clave = (tabla, cond)
    if clave in vistas or profundidad > 8:
        return
    vistas.add(clave)
    for hijo, col, ref in hijos.get(tabla, []):
        if hijo == tabla:
            continue
        generar(hijo, "`%s` IN (SELECT `%s` FROM `%s` WHERE %s)" % (col, ref, tabla, cond), profundidad + 1)
    sentencias.append((tabla, "DELETE FROM `%s` WHERE %s;" % (tabla, cond)))


ids_o = ",".join(str(i) for i in OBJETOS) or "0"
ids_u = ",".join(str(i) for i in USUARIOS) or "0"
fotos = [l.strip() for l in mysql("select url from fotos where objeto_id in (%s);" % ids_o).splitlines() if l.strip()]
generar("objetos", "`id` IN (%s)" % ids_o)
generar("usuarios", "`id` IN (%s) and `correo` like '%%@qa.invalid'" % ids_u)

# Cuántas filas hay por tabla antes de borrar (para el informe)
antes = {}
for tabla, sentencia in sentencias:
    consulta = sentencia.replace("DELETE FROM", "SELECT COUNT(*) FROM", 1).rstrip(";") + ";"
    antes[(tabla, sentencia)] = int(mysql(consulta).strip() or 0)

script = "START TRANSACTION;\n" + "\n".join(s for _, s in sentencias) + "\nCOMMIT;\n"
if "--aplicar" not in sys.argv:
    print("SIMULACIÓN: no se borra nada. Usa --aplicar para borrar.")
else:
    mysql(script)
resumen = {}
for (tabla, _), n in antes.items():
    resumen[tabla] = resumen.get(tabla, 0) + n
for tabla, n in resumen.items():
    if n:
        print("%-28s %d" % (tabla, n))
print("fotos a retirar del disco:", len(fotos))
open("tmp/qa-cierre/fotos-borradas.txt", "w", encoding="utf-8").write("\n".join(fotos))
