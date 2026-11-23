"""
Modelos del dominio FoundIA.

CRITICO:
- El esquema de verdad es database/script.sql (MySQL 8).
- Todos los modelos son managed = False y usan db_table exactos:
  usuarios, categorias, objetos, fotos, coincidencias, contactos,
  notificaciones, reportes.
- NO crear carpeta migrations ni ejecutar `makemigrations` para este app:
  las tablas las crea database/script.sql. Solo se usa Django para leer/escribir.
- Los nombres de campo replican los nombres de columna en snake_case
  (fecha_registro, fecha_objeto, fecha_publicacion, fecha_actualizacion,
  fecha_subida, fecha_creacion, fecha_contacto, fecha_reporte, fecha_resolucion).
"""
import datetime as _dt
from urllib.parse import urlencode

from django.db import models
from django.utils import timezone

ROL_CHOICES = [
    ("USUARIO", "USUARIO"),
    ("BIBLIOTECA", "BIBLIOTECA"),
    ("ADMIN", "ADMIN"),
]

ESTADO_USUARIO_CHOICES = [
    ("ACTIVO", "ACTIVO"),
    ("SUSPENDIDO", "SUSPENDIDO"),
]

TIPO_OBJETO_CHOICES = [
    ("PERDIDO", "PERDIDO"),
    ("ENCONTRADO", "ENCONTRADO"),
]

ESTADO_OBJETO_CHOICES = [
    ("ACTIVO", "ACTIVO"),
    ("EN_CUSTODIA", "EN_CUSTODIA"),
    ("RECUPERADO", "RECUPERADO"),
    ("OCULTO", "OCULTO"),
    ("ELIMINADO", "ELIMINADO"),
]

NIVEL_COINCIDENCIA_CHOICES = [
    ("BAJA", "BAJA"),
    ("MEDIA", "MEDIA"),
    ("ALTA", "ALTA"),
]

ESTADO_COINCIDENCIA_CHOICES = [
    ("PENDIENTE", "PENDIENTE"),
    ("REVISADA", "REVISADA"),
    ("ACEPTADA", "ACEPTADA"),
    ("DESCARTADA", "DESCARTADA"),
]

ESTADO_CONTACTO_CHOICES = [
    ("PENDIENTE", "PENDIENTE"),
    ("ACEPTADO", "ACEPTADO"),
    ("RECHAZADO", "RECHAZADO"),
    ("CERRADO", "CERRADO"),
]

ESTADO_REPORTE_CHOICES = [
    ("PENDIENTE", "PENDIENTE"),
    ("REVISADO", "REVISADO"),
    ("RESUELTO", "RESUELTO"),
    ("RECHAZADO", "RECHAZADO"),
]

ESTADO_CUSTODIA_CHOICES = [
    ("EN_CUSTODIA", "EN_CUSTODIA"),
    ("RETIRADO", "RETIRADO"),
    # H54: disposición final de un objeto no reclamado
    ("DISPUESTO", "DISPUESTO"),
]

DESTINO_DISPOSICION_CHOICES = [
    ("DONADO", "DONADO"),
    ("DESCARTADO", "DESCARTADO"),
]

ESTADO_RECLAMO_CHOICES = [
    ("PENDIENTE", "PENDIENTE"),
    ("ACEPTADO", "ACEPTADO"),
    ("RECHAZADO", "RECHAZADO"),
    ("RETIRADO", "RETIRADO"),
]


class Usuario(models.Model):
    # DRF/DRF auth necesitan request.user.is_authenticated (no es un campo BD).
    is_authenticated = True

    nombre = models.CharField(max_length=100)
    apellido = models.CharField(max_length=100)
    correo = models.CharField(max_length=150, unique=True)
    password = models.CharField(max_length=255)
    # Columnas existentes de H12, compartidas con Spring Boot.
    intentos_login = models.IntegerField(default=0, db_column="intentos_fallidos")
    bloqueado_hasta = models.DateTimeField(null=True, blank=True)
    password_actualizado_en = models.DateTimeField(null=True, blank=True)
    # H37: se incrementa al cerrar todas las sesiones; invalida los JWT anteriores.
    sesion_version = models.IntegerField(default=0, db_column="sesion_version")
    rol = models.CharField(max_length=20, choices=ROL_CHOICES, default="USUARIO")
    estado = models.CharField(
        max_length=20, choices=ESTADO_USUARIO_CHOICES, default="ACTIVO"
    )
    fecha_registro = models.DateTimeField(default=timezone.now, blank=True)
    fecha_actualizacion = models.DateTimeField(auto_now=True)

    class Meta:
        managed = False
        db_table = "usuarios"

    def __str__(self):
        return f"{self.nombre} {self.apellido} <{self.correo}>"


class TokenRevocado(models.Model):
    """H37: JWT individual revocado al cerrar sesión."""

    jti = models.CharField(max_length=64, primary_key=True)
    usuario = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="usuario_id",
        related_name="tokens_revocados",
    )
    revocado_en = models.DateTimeField(default=timezone.now)
    expira_en = models.DateTimeField()

    class Meta:
        managed = False
        db_table = "tokens_revocados"

    def __str__(self):
        return f"Token revocado {self.jti}"


class Categoria(models.Model):
    nombre = models.CharField(max_length=100, unique=True)
    descripcion = models.CharField(max_length=255, null=True, blank=True)
    estado = models.BooleanField(default=True)
    fecha_creacion = models.DateTimeField(default=timezone.now, blank=True)

    class Meta:
        managed = False
        db_table = "categorias"

    def __str__(self):
        return self.nombre


class Objeto(models.Model):
    usuario = models.ForeignKey(
        Usuario,
        on_delete=models.DO_NOTHING,
        db_column="usuario_id",
        related_name="objetos",
    )
    categoria = models.ForeignKey(
        Categoria,
        on_delete=models.DO_NOTHING,
        db_column="categoria_id",
        related_name="objetos",
    )
    nombre = models.CharField(max_length=150)
    descripcion = models.TextField()
    ubicacion = models.CharField(max_length=255, null=True, blank=True)
    latitud = models.DecimalField(
        max_digits=10, decimal_places=7, null=True, blank=True
    )
    longitud = models.DecimalField(
        max_digits=10, decimal_places=7, null=True, blank=True
    )
    fecha_objeto = models.DateField()
    tipo = models.CharField(max_length=20, choices=TIPO_OBJETO_CHOICES)
    estado = models.CharField(
        max_length=20, choices=ESTADO_OBJETO_CHOICES, default="ACTIVO"
    )
    fecha_publicacion = models.DateTimeField(default=timezone.now, blank=True)
    fecha_actualizacion = models.DateTimeField(auto_now=True)
    # H22/H30: confirmación de la recuperación en UTC (nula en publicaciones antiguas)
    fecha_recuperacion = models.DateTimeField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "objetos"

    def __str__(self):
        return self.nombre


class Foto(models.Model):
    objeto = models.ForeignKey(
        Objeto,
        on_delete=models.CASCADE,
        db_column="objeto_id",
        related_name="fotos",
    )
    url = models.CharField(max_length=500)
    nombre_archivo = models.CharField(max_length=255, null=True, blank=True)
    fecha_subida = models.DateTimeField(default=timezone.now, blank=True)

    class Meta:
        managed = False
        db_table = "fotos"

    def __str__(self):
        return f"Foto {self.id} de {self.objeto_id}"


class Coincidencia(models.Model):
    objeto_perdido = models.ForeignKey(
        Objeto,
        on_delete=models.DO_NOTHING,
        db_column="objeto_perdido_id",
        related_name="perdido_como",
    )
    objeto_encontrado = models.ForeignKey(
        Objeto,
        on_delete=models.DO_NOTHING,
        db_column="objeto_encontrado_id",
        related_name="encontrado_como",
    )
    porcentaje = models.DecimalField(max_digits=5, decimal_places=2, null=True, blank=True)
    nivel = models.CharField(
        max_length=20, choices=NIVEL_COINCIDENCIA_CHOICES, null=True, blank=True
    )
    estado = models.CharField(
        max_length=20, choices=ESTADO_COINCIDENCIA_CHOICES, default="PENDIENTE"
    )
    fecha_creacion = models.DateTimeField(default=timezone.now, blank=True)

    class Meta:
        managed = False
        db_table = "coincidencias"

    def __str__(self):
        return f"Coincidencia {self.id} ({self.porcentaje}%)"


class Contacto(models.Model):
    coincidencia = models.ForeignKey(
        Coincidencia,
        on_delete=models.DO_NOTHING,
        db_column="coincidencia_id",
        related_name="contactos",
    )
    usuario_emisor = models.ForeignKey(
        Usuario,
        on_delete=models.DO_NOTHING,
        db_column="usuario_emisor_id",
        related_name="contactos_enviados",
    )
    usuario_receptor = models.ForeignKey(
        Usuario,
        on_delete=models.DO_NOTHING,
        db_column="usuario_receptor_id",
        related_name="contactos_recibidos",
    )
    mensaje = models.TextField()
    estado = models.CharField(
        max_length=20, choices=ESTADO_CONTACTO_CHOICES, default="PENDIENTE"
    )
    fecha_contacto = models.DateTimeField(default=timezone.now, blank=True)

    class Meta:
        managed = False
        db_table = "contactos"

    def __str__(self):
        return f"Contacto {self.id}"


class Notificacion(models.Model):
    usuario = models.ForeignKey(
        Usuario,
        on_delete=models.DO_NOTHING,
        db_column="usuario_id",
        related_name="notificaciones",
    )
    titulo = models.CharField(max_length=150)
    mensaje = models.TextField()
    tipo = models.CharField(max_length=50, null=True, blank=True)
    enlace = models.CharField(max_length=500, null=True, blank=True)
    leida = models.BooleanField(default=False)
    fecha_creacion = models.DateTimeField(default=timezone.now, blank=True)
    # H69: el envío masivo que creó este aviso (null en los avisos normales)
    envio_masivo = models.ForeignKey(
        "EnvioMasivo", on_delete=models.DO_NOTHING, db_column="envio_masivo_id", null=True, blank=True,
        related_name="notificaciones",
    )

    class Meta:
        managed = False
        db_table = "notificaciones"

    def __str__(self):
        return self.titulo


class CambioRol(models.Model):
    """H36: auditoría de cada cambio de rol, con responsable y fecha."""

    usuario = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="usuario_id",
        related_name="cambios_rol",
    )
    rol_anterior = models.CharField(max_length=20, choices=ROL_CHOICES)
    rol_nuevo = models.CharField(max_length=20, choices=ROL_CHOICES)
    cambiado_por = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="cambiado_por_id",
        related_name="cambios_rol_realizados",
    )
    motivo = models.CharField(max_length=500, null=True, blank=True)
    fecha_cambio = models.DateTimeField(default=timezone.now, blank=True)

    class Meta:
        managed = False
        db_table = "cambios_rol"
        ordering = ["-fecha_cambio", "-id"]

    def __str__(self):
        return f"{self.usuario_id}: {self.rol_anterior} -> {self.rol_nuevo}"


class SecuenciaCustodia(models.Model):
    """H41: consecutivo por año para el código FND-YYYY-#### sin repetidos."""

    anio = models.IntegerField(primary_key=True)
    ultimo = models.IntegerField(default=0)

    class Meta:
        managed = False
        db_table = "secuencias_custodia"

    def __str__(self):
        return f"{self.anio}: {self.ultimo}"


class Custodia(models.Model):
    """H41: recepción de un objeto con código único, token QR firmado y constancia."""

    objeto = models.OneToOneField(
        Objeto, on_delete=models.DO_NOTHING, db_column="objeto_id",
        related_name="custodia",
    )
    codigo = models.CharField(max_length=20, unique=True)
    token_qr = models.CharField(max_length=500)
    recibido_por = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="recibido_por_id",
        related_name="custodias_recibidas",
    )
    fecha_recepcion = models.DateTimeField(default=timezone.now, blank=True)
    observaciones = models.TextField(null=True, blank=True)
    estado = models.CharField(
        max_length=20, choices=ESTADO_CUSTODIA_CHOICES, default="EN_CUSTODIA"
    )

    class Meta:
        managed = False
        db_table = "custodias"
        ordering = ["-fecha_recepcion", "-id"]

    def __str__(self):
        return f"{self.codigo} ({self.estado})"


class Disposicion(models.Model):
    """H54: acta de disposición final (donado o descartado). Una por custodia; nada se borra."""

    custodia = models.OneToOneField(
        Custodia, on_delete=models.DO_NOTHING, db_column="custodia_id",
        related_name="disposicion",
    )
    codigo_acta = models.CharField(max_length=30, unique=True)
    destino = models.CharField(max_length=20, choices=DESTINO_DISPOSICION_CHOICES)
    motivo = models.CharField(max_length=1000)
    plazo_dias = models.IntegerField()
    dias_en_custodia = models.IntegerField()
    responsable = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="responsable_id",
        related_name="disposiciones",
    )
    fecha_disposicion = models.DateTimeField(default=timezone.now, blank=True)

    class Meta:
        managed = False
        db_table = "disposiciones"
        ordering = ["-fecha_disposicion", "-id"]

    def __str__(self):
        return f"{self.codigo_acta} ({self.destino})"


class Reclamo(models.Model):
    """H43: bandeja de reclamos de custodia y QR de retiro de un solo uso."""

    objeto = models.ForeignKey(
        Objeto, on_delete=models.DO_NOTHING, db_column="objeto_id",
        related_name="reclamos",
    )
    usuario = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="usuario_id",
        related_name="reclamos",
    )
    estado = models.CharField(
        max_length=20, choices=ESTADO_RECLAMO_CHOICES, default="PENDIENTE"
    )
    fecha_reclamo = models.DateTimeField(default=timezone.now, blank=True)
    respuesta_verificacion = models.CharField(max_length=500, null=True, blank=True)
    resuelto_por = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="resuelto_por_id",
        related_name="reclamos_resueltos", null=True, blank=True,
    )
    fecha_resolucion = models.DateTimeField(null=True, blank=True)
    motivo_resolucion = models.TextField(null=True, blank=True)
    qr_retiro_token = models.CharField(max_length=64, unique=True, null=True, blank=True)
    qr_retiro_expira = models.DateTimeField(null=True, blank=True)
    qr_retiro_usado_en = models.DateTimeField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "reclamos"
        unique_together = [("objeto", "usuario")]
        ordering = ["-fecha_reclamo", "-id"]

    def __str__(self):
        return f"Reclamo {self.id} ({self.estado})"


class Reporte(models.Model):
    usuario = models.ForeignKey(
        Usuario,
        on_delete=models.DO_NOTHING,
        db_column="usuario_id",
        related_name="reportes",
    )
    objeto = models.ForeignKey(
        Objeto,
        on_delete=models.DO_NOTHING,
        db_column="objeto_id",
        related_name="reportes",
    )
    motivo = models.CharField(max_length=100)
    descripcion = models.TextField(null=True, blank=True)
    estado = models.CharField(
        max_length=20, choices=ESTADO_REPORTE_CHOICES, default="PENDIENTE"
    )
    fecha_reporte = models.DateTimeField(default=timezone.now, blank=True)
    fecha_resolucion = models.DateTimeField(null=True, blank=True)
    resolucion = models.CharField(max_length=1000, null=True, blank=True)
    resuelto_por = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="resuelto_por_id",
        related_name="reportes_resueltos", null=True, blank=True,
    )

    class Meta:
        managed = False
        db_table = "reportes"

    def __str__(self):
        return f"Reporte {self.id} ({self.estado})"


class Respaldo(models.Model):
    """H38: metadatos de cada respaldo lógico de la base de datos.

    El archivo y su huella viven en el volumen del servicio de respaldo;
    esta tabla solo permite consultar el resultado desde el panel.
    """

    TIPOS = ("BASE", "FOTOS")  # H59: base de datos o volumen de fotos

    tipo = models.CharField(max_length=10, default="BASE")
    nombre_archivo = models.CharField(max_length=255, unique=True)
    huella_sha256 = models.CharField(max_length=64, null=True, blank=True)
    tamano_bytes = models.BigIntegerField(null=True, blank=True)
    estado = models.CharField(max_length=20, default="EN_PROCESO")
    iniciado_en = models.DateTimeField()
    finalizado_en = models.DateTimeField(null=True, blank=True)
    mensaje = models.CharField(max_length=500, null=True, blank=True)
    # H70: la retención borró el archivo; la fila queda como historial.
    eliminado_en = models.DateTimeField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "respaldos"
        ordering = ["-iniciado_en", "-id"]

    def __str__(self):
        return f"Respaldo {self.nombre_archivo} ({self.estado})"


class Restauracion(models.Model):
    """H70: cada ensayo o restauración aplicada de una versión anterior.

    La escribe scripts/restaurar_version.sh; el panel solo la consulta.
    """

    version = models.CharField(max_length=20)
    modo = models.CharField(max_length=10)  # ENSAYO | APLICADA
    estado = models.CharField(max_length=12, default="EN_PROCESO")  # EN_PROCESO | COMPLETADA | FALLIDA
    respaldo_base = models.CharField(max_length=255)
    respaldo_fotos = models.CharField(max_length=255)
    respaldo_seguridad = models.CharField(max_length=20, null=True, blank=True)
    responsable = models.CharField(max_length=120, null=True, blank=True)
    iniciado_en = models.DateTimeField()
    finalizado_en = models.DateTimeField(null=True, blank=True)
    mensaje = models.CharField(max_length=1000, null=True, blank=True)

    class Meta:
        managed = False
        db_table = "restauraciones"
        ordering = ["-iniciado_en", "-id"]

    def __str__(self):
        return f"Restauración {self.version} ({self.modo}, {self.estado})"


ZONA_LIMA = _dt.timezone(_dt.timedelta(hours=-5), "America/Lima")  # Perú no usa horario de verano


def hoy_en_lima():
    return timezone.now().astimezone(ZONA_LIMA).date()


class Campana(models.Model):
    """H60: campaña de difusión con enlace de origen para medir su alcance (H67)."""

    nombre = models.CharField(max_length=120)
    descripcion = models.CharField(max_length=500, null=True, blank=True)
    origen = models.CharField(max_length=40, unique=True)
    destino = models.CharField(max_length=255, default="/")
    fecha_inicio = models.DateField()
    fecha_fin = models.DateField()
    creada_por = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="creada_por_id",
        related_name="campanas_creadas",
    )
    finalizada_en = models.DateTimeField(null=True, blank=True)
    fecha_creacion = models.DateTimeField(default=timezone.now)
    fecha_actualizacion = models.DateTimeField(default=timezone.now)

    class Meta:
        managed = False
        db_table = "campanas"
        ordering = ["-fecha_inicio", "-id"]

    def estado(self, hoy=None):
        # Las fechas de la campaña son del calendario de Lima, aunque Django trabaje en UTC
        hoy = hoy or hoy_en_lima()
        if hoy < self.fecha_inicio:
            return "PROGRAMADA"
        if self.finalizada_en is not None or hoy > self.fecha_fin:
            return "FINALIZADA"
        return "ACTIVA"

    def __str__(self):
        return f"Campaña {self.origen}"


class AlcanceCampana(models.Model):
    """H67: una visita o un registro que llegó por el enlace de una campaña. Lo escribe Spring.

    Sin datos personales: ni IP ni cuenta; las visitas solo tienen un identificador al azar de la web.
    """

    campana = models.ForeignKey(Campana, on_delete=models.DO_NOTHING, db_column="campana_id",
                                related_name="alcance")
    tipo = models.CharField(max_length=10)  # VISITA | REGISTRO
    visita_id = models.CharField(max_length=64, null=True, blank=True)
    fecha = models.DateTimeField()

    class Meta:
        managed = False
        db_table = "alcance_campanas"


class EnvioMasivo(models.Model):
    """H69: aviso enviado a muchas cuentas desde el panel, con su autor y sus destinatarios."""

    titulo = models.CharField(max_length=150)
    mensaje = models.CharField(max_length=1000)
    enlace = models.CharField(max_length=255, null=True, blank=True)
    destinatarios = models.CharField(max_length=12)  # TODOS | USUARIOS | BIBLIOTECA
    total_destinatarios = models.IntegerField()
    autor = models.ForeignKey(Usuario, on_delete=models.DO_NOTHING, db_column="autor_id",
                              related_name="envios_masivos")
    enviado_en = models.DateTimeField()

    class Meta:
        managed = False
        db_table = "envios_masivos"
        ordering = ["-enviado_en", "-id"]

    def __str__(self):
        return f"Envío masivo {self.titulo}"


class AvisoDestacado(models.Model):
    """H71: aviso que todos ven arriba en la web y en la app mientras está vigente.

    Lo administra el panel (Django); Spring lo expone sin sesión en GET /api/avisos-destacados.
    """

    titulo = models.CharField(max_length=80)
    texto = models.CharField(max_length=300)
    destino = models.CharField(max_length=255, null=True, blank=True)
    campana = models.ForeignKey(
        Campana, on_delete=models.DO_NOTHING, db_column="campana_id", null=True, blank=True,
        related_name="avisos_destacados",
    )
    fecha_inicio = models.DateField()
    fecha_fin = models.DateField()
    retirado_en = models.DateTimeField(null=True, blank=True)
    creado_por = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="creado_por_id", related_name="avisos_destacados",
    )
    fecha_creacion = models.DateTimeField(default=timezone.now)
    fecha_actualizacion = models.DateTimeField(default=timezone.now)

    ESTADOS = ("PROGRAMADO", "VIGENTE", "VENCIDO", "RETIRADO")

    class Meta:
        managed = False
        db_table = "avisos_destacados"
        ordering = ["-fecha_inicio", "-id"]

    def estado(self, hoy=None):
        # Fechas del calendario de Lima, como las campañas (H60)
        if self.retirado_en is not None:
            return "RETIRADO"
        hoy = hoy or hoy_en_lima()
        if hoy < self.fecha_inicio:
            return "PROGRAMADO"
        if hoy > self.fecha_fin:
            return "VENCIDO"
        return "VIGENTE"

    def enlace(self):
        if self.campana_id:
            return f"{self.campana.destino}?{urlencode({'origen': self.campana.origen})}"
        return self.destino or None

    def __str__(self):
        return f"Aviso destacado {self.titulo}"


class MensajeChat(models.Model):
    """H55: mensaje del chat (lo escribe Spring); Django solo lo lee para moderar (H56)."""

    conversacion_id = models.BigIntegerField()
    autor = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="autor_id", related_name="mensajes_chat",
    )
    texto = models.TextField(null=True, blank=True)
    imagen_url = models.CharField(max_length=500, null=True, blank=True)
    enviado_en = models.DateTimeField(default=timezone.now)

    class Meta:
        managed = False
        db_table = "mensajes_chat"


class ReporteMensaje(models.Model):
    """H56: reporte de un mensaje del chat; lo crea Spring y se resuelve desde el panel."""

    ACCIONES = {"DESCARTAR": "RECHAZADO", "ADVERTIR": "RESUELTO", "SUSPENDER": "RESUELTO"}

    mensaje = models.ForeignKey(
        MensajeChat, on_delete=models.DO_NOTHING, db_column="mensaje_id", related_name="reportes",
    )
    usuario = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="usuario_id", related_name="reportes_mensaje_hechos",
    )
    motivo = models.CharField(max_length=255)
    estado = models.CharField(max_length=20, default="PENDIENTE")
    creado_en = models.DateTimeField(default=timezone.now)
    accion = models.CharField(max_length=20, null=True, blank=True)
    resolucion = models.CharField(max_length=1000, null=True, blank=True)
    resuelto_por = models.ForeignKey(
        Usuario, on_delete=models.DO_NOTHING, db_column="resuelto_por_id", null=True, blank=True,
        related_name="reportes_mensaje_resueltos",
    )
    fecha_resolucion = models.DateTimeField(null=True, blank=True)

    class Meta:
        managed = False
        db_table = "reportes_mensaje"
        ordering = ["creado_en", "id"]


class Dispositivo(models.Model):
    """H57: teléfono que recibe push (lo registra Spring). Django solo lo borra al cerrar sesión o suspender."""

    usuario = models.ForeignKey(
        Usuario, on_delete=models.CASCADE, db_column="usuario_id", related_name="dispositivos",
    )
    token = models.CharField(max_length=512, unique=True)
    plataforma = models.CharField(max_length=10)
    sesion_jti = models.CharField(max_length=64, null=True, blank=True)
    fecha_registro = models.DateTimeField(default=timezone.now)
    fecha_ultimo_uso = models.DateTimeField(default=timezone.now)

    class Meta:
        managed = False
        db_table = "dispositivos"
