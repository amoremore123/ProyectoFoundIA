"""Comprueba cambios propios de Leonel sobre la entrega integrada de Amore."""
import json
from pathlib import Path
import re
import subprocess


RAIZ = Path(__file__).resolve().parents[1]
# Incluye main, las ocho historias de Leonel y la entrega de Amore sin alterarlos.
BASE = "132128927b41da7107ce3bc3811b48967803a378"
HISTORIAS = ("H21", "H22", "H23", "H24", "H26", "H27", "H28", "H29")


def git(*argumentos):
    return subprocess.check_output(["git", *argumentos], cwd=RAIZ)


def main():
    errores = []
    if git("branch", "--show-current").decode().strip() != "dev/leonel":
        errores.append("Esta verificación corresponde a la entrega de dev/leonel.")
    rutas = set(git("diff", "--name-only", "-z", BASE).decode().split("\0"))
    rutas.update(git("ls-files", "--others", "--exclude-standard", "-z").decode().split("\0"))
    rutas.discard("")
    ajenos = ("consentimiento", "documentoslegales", "recuperacionpassword", "solicitudpassword",
              "tokenrecuperacion", "solicitudeliminacion", "exportaciones", "restauracion",
              "respaldos_operaciones", "notificaciones_masivas", "continuidad", "compartir")
    protegidos = ("frontend/", "movil/", "docker-compose.yml", "database/seed.sql")
    autenticacion_fuera_de_alcance = {"AuthService.java", "AuthController.java", "RegisterRequest.java",
                                    "PerfilService.java", "JwtService.java", "UserPrincipal.java", "Usuario.java",
                                    "EmailService.java", "authentication.py"}
    for ruta in sorted(rutas):
        if not ruta.startswith(("backend/", "database/", "docs/", "scripts/")) or ruta.startswith(protegidos):
            errores.append(f"Cambio fuera de API/datos de Leonel: {ruta}")
        if any(nombre in ruta.lower() for nombre in ajenos):
            errores.append(f"Archivo de otra historia: {ruta}")
        if Path(ruta).name in autenticacion_fuera_de_alcance:
            errores.append(f"Cambio de autenticación ajeno a estas ocho historias: {ruta}")
        migracion = re.fullmatch(r"database/migracion_h(\d+)_[^/]+\.sql", ruta)
        if migracion and int(migracion.group(1)) >= 21 and int(migracion.group(1)) not in (22, 23, 24, 28):
            errores.append(f"Migración de otra historia: {Path(ruta).name}")
        archivo = RAIZ / ruta
        if not archivo.is_file() or not ruta.startswith(("backend/", "database/")):
            continue
        if archivo.suffix not in (".java", ".py", ".sql", ".yml"):
            continue
        texto = archivo.read_text(encoding="utf-8-sig")
        if re.search(r"tiempoRecuperacion|categoriasConMasPublicaciones|ubicacionesConMasPublicaciones", texto):
            errores.append(f"Indicadores de historias ajenas en {ruta}")
        if re.search(r"/api/(?:auth/(?:recuperar-password|restablecer-password)|legal/documentos|perfil/solicitud-eliminacion)", texto):
            errores.append(f"Ruta de una historia ajena en {ruta}")
        if "preferencia" in ruta.lower() and re.search(r"recordatorios|masivos", texto, re.I):
            errores.append(f"Tipos de avisos fuera de esta entrega en {ruta}")
    migraciones = []
    for archivo in (RAIZ / "database").glob("migracion_h*.sql"):
        codigo = int(re.match(r"migracion_h(\d+)", archivo.name).group(1))
        if codigo >= 21:
            if codigo in (22, 23, 24, 28):
                migraciones.append(archivo.name)
    if len(migraciones) != 5:
        errores.append("La entrega debe tener exactamente cinco migraciones incrementales.")
    if errores:
        raise SystemExit("\n".join(errores))
    print(json.dumps({"alcance_correcto": True, "base_integrada": BASE, "historias": HISTORIAS,
                      "archivos_revisados": len(rutas), "migraciones": sorted(migraciones)}, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
