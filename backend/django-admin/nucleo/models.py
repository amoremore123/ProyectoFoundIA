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
from django.db import models
from django.utils import timezone

ROL_CHOICES = [
    ("USUARIO", "USUARIO"),
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


class Usuario(models.Model):
    # DRF/DRF auth necesitan request.user.is_authenticated (no es un campo BD).
    is_authenticated = True

    nombre = models.CharField(max_length=100)
    apellido = models.CharField(max_length=100)
    correo = models.CharField(max_length=150, unique=True)
    password = models.CharField(max_length=255)
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
    leida = models.BooleanField(default=False)
    fecha_creacion = models.DateTimeField(default=timezone.now, blank=True)

    class Meta:
        managed = False
        db_table = "notificaciones"

    def __str__(self):
        return self.titulo


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

    class Meta:
        managed = False
        db_table = "reportes"

    def __str__(self):
        return f"Reporte {self.id} ({self.estado})"
