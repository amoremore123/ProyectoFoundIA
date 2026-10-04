-- =====================================================================
-- PLATAFORMA DE OBJETOS PERDIDOS Y ENCONTRADOS
-- script.sql
-- Crea la base de datos completa: tablas, relaciones, índices y
-- restricciones. Debe ejecutarse ANTES que seed.sql.
-- MySQL 8
-- =====================================================================

CREATE DATABASE IF NOT EXISTS objetos_perdidos_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE objetos_perdidos_db;

-- =====================================================================
-- 1. USUARIOS
-- =====================================================================
DROP TABLE IF EXISTS reportes;
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
    fecha_registro    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    fecha_actualizacion DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP
                                 ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_usuarios_correo (correo),
    CONSTRAINT chk_usuarios_rol CHECK (rol IN ('USUARIO', 'ADMIN')),
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
    PRIMARY KEY (id),
    CONSTRAINT fk_objetos_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_objetos_categoria FOREIGN KEY (categoria_id)
        REFERENCES categorias (id)
        ON DELETE RESTRICT,
    CONSTRAINT chk_objetos_tipo CHECK (tipo IN ('PERDIDO', 'ENCONTRADO')),
    CONSTRAINT chk_objetos_estado
        CHECK (estado IN ('ACTIVO', 'RECUPERADO', 'OCULTO', 'ELIMINADO')),
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
-- 7. NOTIFICACIONES
-- =====================================================================
CREATE TABLE notificaciones (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    usuario_id    BIGINT       NOT NULL,
    titulo        VARCHAR(150) NOT NULL,
    mensaje       TEXT         NOT NULL,
    tipo          VARCHAR(50)  NULL,
    leida         BOOLEAN      NOT NULL DEFAULT FALSE,
    fecha_creacion DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_notificaciones_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id)
        ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE INDEX idx_notificaciones_usuario ON notificaciones (usuario_id);

-- =====================================================================
-- 8. REPORTES
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
    PRIMARY KEY (id),
    CONSTRAINT fk_reportes_usuario FOREIGN KEY (usuario_id)
        REFERENCES usuarios (id)
        ON DELETE RESTRICT,
    CONSTRAINT fk_reportes_objeto FOREIGN KEY (objeto_id)
        REFERENCES objetos (id)
        ON DELETE CASCADE,
    CONSTRAINT chk_reportes_estado
        CHECK (estado IN ('PENDIENTE', 'REVISADO', 'RESUELTO', 'RECHAZADO'))
) ENGINE = InnoDB;

CREATE INDEX idx_reportes_usuario ON reportes (usuario_id);
CREATE INDEX idx_reportes_objeto  ON reportes (objeto_id);
CREATE INDEX idx_reportes_estado  ON reportes (estado);
