-- =====================================================================
-- PLATAFORMA DE OBJETOS PERDIDOS Y ENCONTRADOS
-- script.sql
-- Crea la base de datos completa: tablas, relaciones, índices y
-- restricciones. Debe ejecutarse ANTES que seed.sql.
-- MySQL 8
-- =====================================================================

-- Mantiene la conexión que importa este archivo en UTF-8 completo.
SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS objetos_perdidos_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE objetos_perdidos_db;

-- =====================================================================
-- 1. USUARIOS
-- =====================================================================
DROP TABLE IF EXISTS cambios_rol;
DROP TABLE IF EXISTS respaldos;
DROP TABLE IF EXISTS tokens_revocados;
DROP TABLE IF EXISTS reportes;
DROP TABLE IF EXISTS preferencias_notificacion;
DROP TABLE IF EXISTS avisos_coincidencia;
DROP TABLE IF EXISTS reclamos;
DROP TABLE IF EXISTS custodias;
DROP TABLE IF EXISTS secuencias_custodia;
DROP TABLE IF EXISTS notificaciones;
DROP TABLE IF EXISTS contactos;
DROP TABLE IF EXISTS coincidencias;
DROP TABLE IF EXISTS fotos;
DROP TABLE IF EXISTS objetos;
DROP TABLE IF EXISTS categorias;
DROP TABLE IF EXISTS usuarios;

CREATE TABLE usuarios (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    nombre            VARCHAR(100) NOT NULL,
    apellido          VARCHAR(100) NOT NULL,
    correo            VARCHAR(150) NOT NULL,
    password          VARCHAR(255) NOT NULL,
    rol               VARCHAR(20)  NOT NULL DEFAULT 'USUARIO',
    estado            VARCHAR(20)  NOT NULL DEFAULT 'ACTIVO',
    -- H11: verificación de correo con código de 6 dígitos
    verificado          BOOLEAN    NOT NULL DEFAULT FALSE,
    codigo_verificacion VARCHAR(6) NULL,
    codigo_expira       DATETIME   NULL,
    -- H11: límites del servidor (intentos con el código y espera entre reenvíos)
    intentos_codigo     INT        NOT NULL DEFAULT 0,
    codigo_enviado_en   DATETIME   NULL,
    -- H12: bloqueo tras 5 intentos fallidos de inicio de sesión
    intentos_fallidos   INT        NOT NULL DEFAULT 0,
    bloqueado_hasta     DATETIME   NULL,
    fecha_registro    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP
                                 ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_usuarios_correo (correo),
    CONSTRAINT chk_usuarios_rol CHECK (rol IN ('USUARIO', 'BIBLIOTECA', 'ADMIN')),
    CONSTRAINT chk_usuarios_estado CHECK (estado IN ('ACTIVO', 'SUSPENDIDO'))
) ENGINE = InnoDB;

-- =====================================================================
-- 2. CATEGORIAS
-- =====================================================================
CREATE TABLE categorias (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    nombre        VARCHAR(100) NOT NULL,
    descripcion   VARCHAR(255) NULL,
    estado        BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_categorias_nombre (nombre)
) ENGINE = InnoDB;

-- =====================================================================
-- 3. OBJETOS
-- =====================================================================
CREATE TABLE objetos (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    usuario_id        BIGINT        NOT NULL,
    categoria_id      BIGINT        NOT NULL,
    nombre            VARCHAR(150)  NOT NULL,
    descripcion       TEXT          NOT NULL,
    ubicacion         VARCHAR(255)  NULL,
    latitud           DECIMAL(10,7) NULL,
    longitud          DECIMAL(10,7) NULL,
    fecha_objeto      DATE          NOT NULL,
    tipo              VARCHAR(20)   NOT NULL,
    estado            VARCHAR(20)   NOT NULL DEFAULT 'ACTIVO',
    fecha_publicacion DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP,
    fecha_recuperacion DATETIME     NULL COMMENT 'Confirmación de recuperación en UTC; sin fecha histórica inventada',
    PRIMARY KEY (id),
    CONSTRAINT fk_objetos_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_objetos_categoria FOREIGN KEY (categoria_id)
        REFERENCES categorias (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_objetos_tipo CHECK (tipo IN ('PERDIDO', 'ENCONTRADO')),
    CONSTRAINT chk_objetos_estado
        CHECK (estado IN ('ACTIVO', 'EN_CUSTODIA', 'RECUPERADO', 'OCULTO', 'ELIMINADO')),
    CONSTRAINT chk_objetos_coordenadas CHECK (
        (latitud  IS NULL OR (latitud  >= -90   AND latitud  <= 90)) AND
        (longitud IS NULL OR (longitud >= -180  AND longitud <= 180))
    )
) ENGINE = InnoDB;

CREATE INDEX idx_objetos_usuario    ON objetos (usuario_id);
CREATE INDEX idx_objetos_categoria  ON objetos (categoria_id);
CREATE INDEX idx_objetos_tipo       ON objetos (tipo);
CREATE INDEX idx_objetos_estado     ON objetos (estado);
CREATE INDEX idx_objetos_fecha_objeto ON objetos (fecha_objeto);

-- =====================================================================
-- 4. FOTOS
-- =====================================================================
CREATE TABLE fotos (
    id             BIGINT       NOT NULL AUTO_INCREMENT,
    objeto_id      BIGINT       NOT NULL,
    url            VARCHAR(500) NOT NULL,
    nombre_archivo VARCHAR(255) NULL,
    fecha_subida   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_fotos_objeto FOREIGN KEY (objeto_id)
        REFERENCES objetos (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_fotos_objeto ON fotos (objeto_id);

-- =====================================================================
-- 5. COINCIDENCIAS
-- =====================================================================
CREATE TABLE coincidencias (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    objeto_perdido_id    BIGINT        NOT NULL,
    objeto_encontrado_id BIGINT        NOT NULL,
    porcentaje           DECIMAL(5,2)  NULL,
    nivel                VARCHAR(20)   NULL,
    estado               VARCHAR(20)   NOT NULL DEFAULT 'PENDIENTE',
    fecha_creacion       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_coincidencias_perdido FOREIGN KEY (objeto_perdido_id)
        REFERENCES objetos (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_coincidencias_encontrado FOREIGN KEY (objeto_encontrado_id)
        REFERENCES objetos (id)
        ON DELETE CASCADE,
    CONSTRAINT uq_coincidencias_pair
        UNIQUE (objeto_perdido_id, objeto_encontrado_id),
    CONSTRAINT chk_coincidencias_distintos
        CHECK (objeto_perdido_id <> objeto_encontrado_id),
    CONSTRAINT chk_coincidencias_nivel
        CHECK (nivel IS NULL OR nivel IN ('BAJA', 'MEDIA', 'ALTA')),
    CONSTRAINT chk_coincidencias_estado
        CHECK (estado IN ('PENDIENTE', 'REVISADA', 'ACEPTADA', 'DESCARTADA')),
    CONSTRAINT chk_coincidencias_porcentaje
        CHECK (porcentaje IS NULL OR (porcentaje >= 0 AND porcentaje <= 100))
) ENGINE = InnoDB;

CREATE INDEX idx_coincidencias_perdido    ON coincidencias (objeto_perdido_id);
CREATE INDEX idx_coincidencias_encontrado ON coincidencias (objeto_encontrado_id);

-- =====================================================================
-- 6. CONTACTOS
-- =====================================================================
CREATE TABLE contactos (
    id                  BIGINT   NOT NULL AUTO_INCREMENT,
    coincidencia_id     BIGINT   NOT NULL,
    usuario_emisor_id   BIGINT   NOT NULL,
    usuario_receptor_id BIGINT   NOT NULL,
    mensaje             TEXT     NOT NULL,
    estado              VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_contacto      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_contactos_coincidencia FOREIGN KEY (coincidencia_id)
        REFERENCES coincidencias (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_contactos_emisor FOREIGN KEY (usuario_emisor_id)
        REFERENCES usuarios (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_contactos_receptor FOREIGN KEY (usuario_receptor_id)
        REFERENCES usuarios (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_contactos_distintos
        CHECK (usuario_emisor_id <> usuario_receptor_id),
    CONSTRAINT chk_contactos_estado
        CHECK (estado IN ('PENDIENTE', 'ACEPTADO', 'RECHAZADO', 'CERRADO'))
) ENGINE = InnoDB;

CREATE INDEX idx_contactos_coincidencia ON contactos (coincidencia_id);
CREATE INDEX idx_contactos_emisor       ON contactos (usuario_emisor_id);
CREATE INDEX idx_contactos_receptor     ON contactos (usuario_receptor_id);

-- =====================================================================
-- 7. RECLAMOS (H20)
-- =====================================================================
CREATE TABLE reclamos (
    id                BIGINT      NOT NULL AUTO_INCREMENT,
    objeto_id         BIGINT      NOT NULL,
    usuario_id        BIGINT      NOT NULL,
    estado            VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_reclamo     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- H43: resolución auditada y QR de retiro de un solo uso.
    resuelto_por_id   BIGINT      NULL,
    fecha_resolucion  DATETIME    NULL,
    motivo_resolucion TEXT        NULL,
    qr_retiro_token   VARCHAR(64) NULL,
    qr_retiro_expira  DATETIME    NULL,
    qr_retiro_usado_en DATETIME   NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_reclamos_objeto_usuario (objeto_id, usuario_id),
    UNIQUE KEY uq_reclamos_qr_retiro_token (qr_retiro_token),
    CONSTRAINT fk_reclamos_objeto FOREIGN KEY (objeto_id)
        REFERENCES objetos (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_reclamos_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_reclamos_resuelto_por FOREIGN KEY (resuelto_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT chk_reclamos_estado
        CHECK (estado IN ('PENDIENTE', 'ACEPTADO', 'RECHAZADO', 'RETIRADO'))
) ENGINE = InnoDB;

CREATE INDEX idx_reclamos_usuario ON reclamos (usuario_id);
CREATE INDEX idx_reclamos_estado ON reclamos (estado);

-- =====================================================================
-- 7.1 CUSTODIA (H41)
-- =====================================================================
-- Consecutivo por año para el código FND-YYYY-####.
CREATE TABLE secuencias_custodia (
    anio   INT NOT NULL PRIMARY KEY,
    ultimo INT NOT NULL DEFAULT 0
) ENGINE = InnoDB;

CREATE TABLE custodias (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    objeto_id        BIGINT       NOT NULL,
    codigo           VARCHAR(20)  NOT NULL,
    token_qr         VARCHAR(500) NOT NULL,
    recibido_por_id  BIGINT       NOT NULL,
    fecha_recepcion  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observaciones    TEXT         NULL,
    estado           VARCHAR(20)  NOT NULL DEFAULT 'EN_CUSTODIA',
    PRIMARY KEY (id),
    UNIQUE KEY uq_custodias_objeto (objeto_id),
    UNIQUE KEY uq_custodias_codigo (codigo),
    CONSTRAINT fk_custodias_objeto FOREIGN KEY (objeto_id)
        REFERENCES objetos (id) ON DELETE RESTRICT,
    CONSTRAINT fk_custodias_recibido_por FOREIGN KEY (recibido_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT chk_custodias_estado CHECK (estado IN ('EN_CUSTODIA', 'RETIRADO'))
) ENGINE = InnoDB;

CREATE INDEX idx_custodias_estado ON custodias (estado);
CREATE INDEX idx_custodias_recibido_por ON custodias (recibido_por_id);

-- =====================================================================
-- 8. NOTIFICACIONES
-- =====================================================================
CREATE TABLE notificaciones (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id    BIGINT       NOT NULL,
    titulo        VARCHAR(150) NOT NULL,
    mensaje       TEXT         NOT NULL,
    tipo          VARCHAR(50)  NULL,
    enlace        VARCHAR(500) NULL,
    leida         BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_creacion DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_notificaciones_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_notificaciones_usuario ON notificaciones (usuario_id);

-- H23: evento durable, procesado después de confirmar publicación/edición.
CREATE TABLE eventos_coincidencia (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, objeto_id BIGINT NOT NULL,
 estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE', intentos INT NOT NULL DEFAULT 0,
 detalle VARCHAR(100) NULL, fecha_creacion DATETIME NOT NULL,
 KEY idx_evento_estado_id(estado,id),
 CONSTRAINT fk_evento_objeto FOREIGN KEY(objeto_id) REFERENCES objetos(id)
) ENGINE=InnoDB;

-- H24: preferencias propias, aplicadas a eventos futuros.
CREATE TABLE preferencias_notificacion (
 usuario_id BIGINT NOT NULL PRIMARY KEY,
 coincidencias BOOLEAN NOT NULL DEFAULT TRUE, contactos BOOLEAN NOT NULL DEFAULT TRUE,
 reclamos BOOLEAN NOT NULL DEFAULT TRUE, fecha_actualizacion DATETIME NOT NULL,
 CONSTRAINT fk_preferencia_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB;

-- H23: una pareja nueva avisa una sola vez, sin escrituras en los GET.
CREATE TABLE avisos_coincidencia (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 objeto_perdido_id BIGINT NOT NULL, objeto_encontrado_id BIGINT NOT NULL, notificacion_id BIGINT NULL,
 CONSTRAINT uq_aviso_pareja UNIQUE (objeto_perdido_id, objeto_encontrado_id),
 CONSTRAINT fk_aviso_perdido FOREIGN KEY (objeto_perdido_id) REFERENCES objetos(id),
 CONSTRAINT fk_aviso_encontrado FOREIGN KEY (objeto_encontrado_id) REFERENCES objetos(id),
 CONSTRAINT fk_aviso_notificacion FOREIGN KEY (notificacion_id) REFERENCES notificaciones(id)
) ENGINE=InnoDB;

-- =====================================================================
-- 9. REPORTES
-- =====================================================================
CREATE TABLE reportes (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id        BIGINT       NOT NULL,
    objeto_id         BIGINT       NOT NULL,
    motivo            VARCHAR(100) NOT NULL,
    descripcion       TEXT         NULL,
    estado            VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    fecha_reporte     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_resolucion  DATETIME     NULL,
    resolucion        VARCHAR(1000) NULL,
    resuelto_por_id   BIGINT        NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reportes_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id)
         ON DELETE RESTRICT,
    CONSTRAINT fk_reportes_objeto FOREIGN KEY (objeto_id)
        REFERENCES objetos (id)
        ON DELETE CASCADE,
    CONSTRAINT fk_reportes_resuelto_por FOREIGN KEY (resuelto_por_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT chk_reportes_estado
        CHECK (estado IN ('PENDIENTE', 'REVISADO', 'RESUELTO', 'RECHAZADO'))
) ENGINE = InnoDB;

CREATE INDEX idx_reportes_usuario ON reportes (usuario_id);
CREATE INDEX idx_reportes_objeto  ON reportes (objeto_id);
CREATE INDEX idx_reportes_estado  ON reportes (estado);

-- H36: auditoría de cambios de rol. El último ADMIN no se puede degradar.
CREATE TABLE IF NOT EXISTS cambios_rol (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id     BIGINT       NOT NULL,
    rol_anterior   VARCHAR(20)  NOT NULL,
    rol_nuevo      VARCHAR(20)  NOT NULL,
    cambiado_por_id BIGINT      NOT NULL,
    motivo         VARCHAR(500) NULL,
    fecha_cambio   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_cambio_rol_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE RESTRICT,
    CONSTRAINT fk_cambio_rol_autor FOREIGN KEY (cambiado_por_id) REFERENCES usuarios(id) ON DELETE RESTRICT,
    CONSTRAINT chk_cambio_rol_anterior CHECK (rol_anterior IN ('USUARIO', 'BIBLIOTECA', 'ADMIN')),
    CONSTRAINT chk_cambio_rol_nuevo CHECK (rol_nuevo IN ('USUARIO', 'BIBLIOTECA', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- H33/H34: esquema de instalaciones nuevas. En bases existentes usar las dos migraciones incrementales.
ALTER TABLE usuarios ADD COLUMN version_legal VARCHAR(80) NULL,
 ADD COLUMN aceptacion_legal_fecha DATETIME NULL,
 ADD COLUMN documentos_legales_hash CHAR(64) NULL;
-- H34: trámite propio. No elimina ni actualiza cuentas o antecedentes.
CREATE TABLE IF NOT EXISTS solicitudes_eliminacion (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
    fecha_solicitud DATETIME(6) NOT NULL,
    CONSTRAINT uk_solicitud_eliminacion_usuario UNIQUE (usuario_id),
    CONSTRAINT fk_solicitud_eliminacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- H35: recuperación de contraseña. En bases existentes usar migracion_h35_recuperacion_password.sql.
ALTER TABLE usuarios ADD COLUMN password_actualizado_en DATETIME NULL;
CREATE TABLE IF NOT EXISTS tokens_recuperacion_password (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL,
    token_hash  CHAR(64)    NOT NULL,
    creado_en   DATETIME(6) NOT NULL,
    expira_en   DATETIME(6) NOT NULL,
    usado_en    DATETIME(6) NULL,
    CONSTRAINT uk_token_recuperacion_hash UNIQUE (token_hash),
    CONSTRAINT fk_token_recuperacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id),
    INDEX idx_token_recuperacion_usuario (usuario_id, creado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- H37: cierre de sesión. En bases existentes usar migracion_h37_cierre_sesion.sql.
ALTER TABLE usuarios ADD COLUMN sesion_version INT NOT NULL DEFAULT 0;
CREATE TABLE IF NOT EXISTS tokens_revocados (
    jti         VARCHAR(64) NOT NULL PRIMARY KEY,
    usuario_id  BIGINT      NOT NULL,
    revocado_en DATETIME(6) NOT NULL,
    expira_en   DATETIME(6) NOT NULL,
    CONSTRAINT fk_token_revocado_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE,
    INDEX idx_tokens_revocados_expira (expira_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- H38: registro de respaldos lógicos. En bases existentes usar migracion_h38_respaldos.sql.
-- Solo guarda metadatos; el archivo .sql.gz y su .sha256 viven en el volumen respaldos_data.
CREATE TABLE IF NOT EXISTS respaldos (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nombre_archivo VARCHAR(255) NOT NULL,
    huella_sha256  CHAR(64)     NULL,
    tamano_bytes   BIGINT       NULL,
    estado         VARCHAR(20)  NOT NULL DEFAULT 'EN_PROCESO',
    iniciado_en    DATETIME(6)  NOT NULL,
    finalizado_en  DATETIME(6)  NULL,
    mensaje        VARCHAR(500) NULL,
    CONSTRAINT uk_respaldo_archivo UNIQUE (nombre_archivo),
    CONSTRAINT chk_respaldo_estado CHECK (estado IN ('EN_PROCESO', 'COMPLETADO', 'FALLIDO')),
    INDEX idx_respaldos_iniciado (iniciado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
