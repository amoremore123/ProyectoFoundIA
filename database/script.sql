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
DROP TABLE IF EXISTS dispositivos;
DROP TABLE IF EXISTS reportes_mensaje;
DROP TABLE IF EXISTS lecturas_chat;
DROP TABLE IF EXISTS mensajes_chat;
DROP TABLE IF EXISTS conversaciones;
DROP TABLE IF EXISTS bloqueos_chat;
DROP TABLE IF EXISTS promociones_campana;
DROP TABLE IF EXISTS alcance_campanas;
DROP TABLE IF EXISTS avisos_destacados;
DROP TABLE IF EXISTS campanas;
DROP TABLE IF EXISTS invitaciones;
DROP TABLE IF EXISTS archivos_por_borrar;
DROP TABLE IF EXISTS solicitudes_eliminacion;
DROP TABLE IF EXISTS tokens_recuperacion_password;
DROP TABLE IF EXISTS eventos_coincidencia;
DROP TABLE IF EXISTS cambios_rol;
DROP TABLE IF EXISTS restauraciones;
DROP TABLE IF EXISTS respaldos;
DROP TABLE IF EXISTS tokens_revocados;
DROP TABLE IF EXISTS reportes;
DROP TABLE IF EXISTS preferencias_notificacion;
DROP TABLE IF EXISTS avisos_coincidencia;
DROP TABLE IF EXISTS recordatorios_objeto;
DROP TABLE IF EXISTS reclamos;
DROP TABLE IF EXISTS disposiciones;
DROP TABLE IF EXISTS custodias;
DROP TABLE IF EXISTS secuencias_custodia;
DROP TABLE IF EXISTS notificaciones;
DROP TABLE IF EXISTS envios_masivos;
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
    CONSTRAINT chk_usuarios_estado CHECK (estado IN ('ACTIVO', 'SUSPENDIDO', 'ELIMINADO'))
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
    respuesta_verificacion VARCHAR(500) NULL,
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
    CONSTRAINT chk_custodias_estado CHECK (estado IN ('EN_CUSTODIA', 'RETIRADO', 'DISPUESTO'))
) ENGINE = InnoDB;

CREATE INDEX idx_custodias_estado ON custodias (estado);
CREATE INDEX idx_custodias_recibido_por ON custodias (recibido_por_id);

-- H54: acta de disposición final (donado o descartado) de un objeto no reclamado
CREATE TABLE disposiciones (
    id                BIGINT        NOT NULL AUTO_INCREMENT,
    custodia_id       BIGINT        NOT NULL,
    codigo_acta       VARCHAR(30)   NOT NULL,
    destino           VARCHAR(20)   NOT NULL,
    motivo            VARCHAR(1000) NOT NULL,
    plazo_dias        INT           NOT NULL,
    dias_en_custodia  INT           NOT NULL,
    responsable_id    BIGINT        NOT NULL,
    fecha_disposicion DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uq_disposiciones_custodia (custodia_id),
    UNIQUE KEY uq_disposiciones_acta (codigo_acta),
    CONSTRAINT fk_disposiciones_custodia FOREIGN KEY (custodia_id)
        REFERENCES custodias (id) ON DELETE RESTRICT,
    CONSTRAINT fk_disposiciones_responsable FOREIGN KEY (responsable_id)
        REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT chk_disposiciones_destino CHECK (destino IN ('DONADO', 'DESCARTADO'))
) ENGINE = InnoDB;

CREATE INDEX idx_disposiciones_fecha ON disposiciones (fecha_disposicion);

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
 reclamos BOOLEAN NOT NULL DEFAULT TRUE, recordatorios BOOLEAN NOT NULL DEFAULT TRUE,
 fecha_actualizacion DATETIME NOT NULL,
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

-- H53: un recordatorio por objeto y mes UTC; evita repetir el aviso en el periodo.
CREATE TABLE recordatorios_objeto (
    id              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    objeto_id       BIGINT      NOT NULL,
    periodo         CHAR(7)     NOT NULL,
    notificacion_id BIGINT      NULL,
    fecha_creacion  DATETIME(6) NOT NULL,
    CONSTRAINT uk_recordatorio_objeto_periodo UNIQUE (objeto_id, periodo),
    CONSTRAINT fk_recordatorio_objeto FOREIGN KEY (objeto_id) REFERENCES objetos(id)
        ON DELETE CASCADE,
    CONSTRAINT fk_recordatorio_notificacion FOREIGN KEY (notificacion_id) REFERENCES notificaciones(id)
        ON DELETE SET NULL,
    INDEX idx_recordatorios_periodo (periodo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

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
-- H59: tipo BASE (MySQL) o FOTOS (volumen uploads_data). Bases existentes: migracion_h59_respaldo_fotos.sql.
CREATE TABLE IF NOT EXISTS respaldos (
    id             BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tipo           VARCHAR(10)  NOT NULL DEFAULT 'BASE',
    nombre_archivo VARCHAR(255) NOT NULL,
    huella_sha256  CHAR(64)     NULL,
    tamano_bytes   BIGINT       NULL,
    estado         VARCHAR(20)  NOT NULL DEFAULT 'EN_PROCESO',
    iniciado_en    DATETIME(6)  NOT NULL,
    finalizado_en  DATETIME(6)  NULL,
    mensaje        VARCHAR(500) NULL,
    CONSTRAINT uk_respaldo_archivo UNIQUE (nombre_archivo),
    CONSTRAINT chk_respaldo_estado CHECK (estado IN ('EN_PROCESO', 'COMPLETADO', 'FALLIDO')),
    CONSTRAINT chk_respaldo_tipo CHECK (tipo IN ('BASE', 'FOTOS')),
    INDEX idx_respaldos_iniciado (iniciado_en),
    INDEX idx_respaldos_tipo_iniciado (tipo, iniciado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- H40: invitaciones sin identidad en el enlace.
CREATE TABLE IF NOT EXISTS invitaciones (
    codigo_hash CHAR(64) NOT NULL PRIMARY KEY,
    usuario_id BIGINT NOT NULL,
    usos INT NOT NULL DEFAULT 0,
    creada_en DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_invitacion_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios(id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- H60: campañas de difusión con enlace de origen. Bases existentes: migracion_h60_campanas.sql.
CREATE TABLE IF NOT EXISTS campanas (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    nombre              VARCHAR(120) NOT NULL,
    descripcion         VARCHAR(500) NULL,
    origen              VARCHAR(40)  NOT NULL,
    destino             VARCHAR(255) NOT NULL DEFAULT '/',
    fecha_inicio        DATE         NOT NULL,
    fecha_fin           DATE         NOT NULL,
    creada_por_id       BIGINT       NOT NULL,
    finalizada_en       DATETIME(6)  NULL,
    fecha_creacion      DATETIME(6)  NOT NULL,
    fecha_actualizacion DATETIME(6)  NOT NULL,
    CONSTRAINT uk_campanas_origen UNIQUE (origen),
    CONSTRAINT fk_campanas_creada_por FOREIGN KEY (creada_por_id) REFERENCES usuarios (id) ON DELETE RESTRICT,
    CONSTRAINT chk_campanas_fechas CHECK (fecha_fin >= fecha_inicio),
    INDEX idx_campanas_fechas (fecha_inicio, fecha_fin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- H64: foto de perfil privada (ruta interna perfiles/<uuid>.jpg). Bases existentes: migracion_h64_foto_perfil.sql.
ALTER TABLE usuarios ADD COLUMN foto_perfil_url VARCHAR(80) NULL;

-- H55 a H63: chat interno (ver database/migracion_h55_chat.sql).
CREATE TABLE IF NOT EXISTS conversaciones (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tipo          VARCHAR(20) NOT NULL,
    objeto_id     BIGINT      NOT NULL,
    contacto_id   BIGINT      NULL,
    reclamo_id    BIGINT      NULL,
    usuario_a_id  BIGINT      NOT NULL,
    usuario_b_id  BIGINT      NULL,
    estado        VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
    creada_en     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_conversaciones_contacto UNIQUE (contacto_id),
    CONSTRAINT uq_conversaciones_reclamo UNIQUE (reclamo_id),
    CONSTRAINT fk_conversaciones_objeto FOREIGN KEY (objeto_id) REFERENCES objetos (id),
    CONSTRAINT fk_conversaciones_contacto FOREIGN KEY (contacto_id) REFERENCES contactos (id),
    CONSTRAINT fk_conversaciones_reclamo FOREIGN KEY (reclamo_id) REFERENCES reclamos (id),
    CONSTRAINT fk_conversaciones_usuario_a FOREIGN KEY (usuario_a_id) REFERENCES usuarios (id),
    CONSTRAINT fk_conversaciones_usuario_b FOREIGN KEY (usuario_b_id) REFERENCES usuarios (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS mensajes_chat (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    conversacion_id  BIGINT       NOT NULL,
    autor_id         BIGINT       NOT NULL,
    texto            TEXT         NULL,
    imagen_url       VARCHAR(500) NULL,
    enviado_en       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mensajes_chat_conversacion (conversacion_id, id),
    KEY idx_mensajes_chat_autor (autor_id, enviado_en),
    CONSTRAINT fk_mensajes_chat_conversacion FOREIGN KEY (conversacion_id) REFERENCES conversaciones (id) ON DELETE CASCADE,
    CONSTRAINT fk_mensajes_chat_autor FOREIGN KEY (autor_id) REFERENCES usuarios (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS lecturas_chat (
    id                  BIGINT   NOT NULL AUTO_INCREMENT,
    conversacion_id     BIGINT   NOT NULL,
    usuario_id          BIGINT   NOT NULL,
    ultimo_mensaje_id   BIGINT   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_lecturas_chat UNIQUE (conversacion_id, usuario_id),
    CONSTRAINT fk_lecturas_chat_conversacion FOREIGN KEY (conversacion_id) REFERENCES conversaciones (id) ON DELETE CASCADE,
    CONSTRAINT fk_lecturas_chat_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS bloqueos_chat (
    id             BIGINT   NOT NULL AUTO_INCREMENT,
    bloqueador_id  BIGINT   NOT NULL,
    bloqueado_id   BIGINT   NOT NULL,
    creado_en      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_bloqueos_chat UNIQUE (bloqueador_id, bloqueado_id),
    CONSTRAINT fk_bloqueos_chat_bloqueador FOREIGN KEY (bloqueador_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_bloqueos_chat_bloqueado FOREIGN KEY (bloqueado_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS reportes_mensaje (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    mensaje_id  BIGINT       NOT NULL,
    usuario_id  BIGINT       NOT NULL,
    motivo      VARCHAR(255) NOT NULL,
    estado      VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    creado_en   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    -- H56: resolución auditada desde el panel (migracion_h56_resolver_reportes_mensaje.sql)
    accion           VARCHAR(20)   NULL,
    resolucion       VARCHAR(1000) NULL,
    resuelto_por_id  BIGINT        NULL,
    fecha_resolucion DATETIME      NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_reportes_mensaje UNIQUE (mensaje_id, usuario_id),
    CONSTRAINT fk_reportes_mensaje_resuelto_por FOREIGN KEY (resuelto_por_id) REFERENCES usuarios (id),
    CONSTRAINT fk_reportes_mensaje_mensaje FOREIGN KEY (mensaje_id) REFERENCES mensajes_chat (id) ON DELETE CASCADE,
    CONSTRAINT fk_reportes_mensaje_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE = InnoDB;

-- H57: dispositivos para el push con Firebase y marca de envío. Bases existentes: migracion_h57_dispositivos.sql.
CREATE TABLE IF NOT EXISTS dispositivos (
    id               BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    usuario_id       BIGINT       NOT NULL,
    token            VARCHAR(512) NOT NULL,
    plataforma       VARCHAR(10)  NOT NULL,
    sesion_jti       VARCHAR(64)  NULL,
    fecha_registro   DATETIME     NOT NULL,
    fecha_ultimo_uso DATETIME     NOT NULL,
    CONSTRAINT uk_dispositivo_token UNIQUE (token),
    CONSTRAINT fk_dispositivo_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT chk_dispositivo_plataforma CHECK (plataforma IN ('ANDROID')),
    INDEX idx_dispositivos_usuario (usuario_id, fecha_ultimo_uso),
    INDEX idx_dispositivos_sesion (sesion_jti)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE notificaciones ADD COLUMN push_enviado_en DATETIME NULL;
CREATE INDEX idx_notificaciones_push ON notificaciones (push_enviado_en, fecha_creacion);

-- H65: Idempotency-Key para que un reintento sin conexión no duplique. Bases existentes: migracion_h65_idempotencia.sql.
ALTER TABLE mensajes_chat ADD COLUMN id_cliente VARCHAR(64) NULL;
ALTER TABLE mensajes_chat ADD CONSTRAINT uq_mensajes_chat_cliente UNIQUE (autor_id, id_cliente);
ALTER TABLE objetos ADD COLUMN id_cliente VARCHAR(64) NULL;
ALTER TABLE objetos ADD CONSTRAINT uq_objetos_cliente UNIQUE (usuario_id, id_cliente);

-- H70: restaurar una versión anterior. Bases existentes: migracion_h70_restauraciones.sql.
ALTER TABLE respaldos ADD COLUMN eliminado_en DATETIME(6) NULL;

CREATE TABLE IF NOT EXISTS restauraciones (
    id                 BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    version            VARCHAR(20)   NOT NULL,
    modo               VARCHAR(10)   NOT NULL,
    estado             VARCHAR(12)   NOT NULL DEFAULT 'EN_PROCESO',
    respaldo_base      VARCHAR(255)  NOT NULL,
    respaldo_fotos     VARCHAR(255)  NOT NULL,
    respaldo_seguridad VARCHAR(20)   NULL,
    responsable        VARCHAR(120)  NULL,
    iniciado_en        DATETIME(6)   NOT NULL,
    finalizado_en      DATETIME(6)   NULL,
    mensaje            VARCHAR(1000) NULL,
    CONSTRAINT chk_restauracion_modo CHECK (modo IN ('ENSAYO', 'APLICADA')),
    CONSTRAINT chk_restauracion_estado CHECK (estado IN ('EN_PROCESO', 'COMPLETADA', 'FALLIDA')),
    INDEX idx_restauraciones_iniciado (iniciado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- H68: canal de cada tipo de aviso. Bases existentes: migracion_h68_canal_avisos.sql.
ALTER TABLE preferencias_notificacion ADD COLUMN canal_coincidencias VARCHAR(8) NOT NULL DEFAULT 'PUSH', ADD CONSTRAINT chk_preferencia_canal_coincidencias CHECK (canal_coincidencias IN ('PUSH', 'CORREO', 'AMBOS', 'NINGUNO'));
ALTER TABLE preferencias_notificacion ADD COLUMN canal_contactos VARCHAR(8) NOT NULL DEFAULT 'PUSH', ADD CONSTRAINT chk_preferencia_canal_contactos CHECK (canal_contactos IN ('PUSH', 'CORREO', 'AMBOS', 'NINGUNO'));
ALTER TABLE preferencias_notificacion ADD COLUMN canal_reclamos VARCHAR(8) NOT NULL DEFAULT 'PUSH', ADD CONSTRAINT chk_preferencia_canal_reclamos CHECK (canal_reclamos IN ('PUSH', 'CORREO', 'AMBOS', 'NINGUNO'));
ALTER TABLE preferencias_notificacion ADD COLUMN canal_recordatorios VARCHAR(8) NOT NULL DEFAULT 'PUSH', ADD CONSTRAINT chk_preferencia_canal_recordatorios CHECK (canal_recordatorios IN ('PUSH', 'CORREO', 'AMBOS', 'NINGUNO'));
ALTER TABLE preferencias_notificacion ADD COLUMN canal_mensajes VARCHAR(8) NOT NULL DEFAULT 'PUSH', ADD CONSTRAINT chk_preferencia_canal_mensajes CHECK (canal_mensajes IN ('PUSH', 'CORREO', 'AMBOS', 'NINGUNO'));
ALTER TABLE preferencias_notificacion ADD COLUMN canal_sistema VARCHAR(8) NOT NULL DEFAULT 'PUSH', ADD CONSTRAINT chk_preferencia_canal_sistema CHECK (canal_sistema IN ('PUSH', 'CORREO', 'AMBOS', 'NINGUNO'));
ALTER TABLE notificaciones ADD COLUMN correo_enviado_en DATETIME NULL;
ALTER TABLE notificaciones ADD COLUMN correo_resultado VARCHAR(10) NULL;
CREATE INDEX idx_notificaciones_correo ON notificaciones (correo_enviado_en, fecha_creacion);

-- H71: avisos destacados con vigencia. Bases existentes: migracion_h71_avisos_destacados.sql.
CREATE TABLE IF NOT EXISTS avisos_destacados (
    id                  BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    titulo              VARCHAR(80)  NOT NULL,
    texto               VARCHAR(300) NOT NULL,
    destino             VARCHAR(255) NULL,
    campana_id          BIGINT       NULL,
    fecha_inicio        DATE         NOT NULL,
    fecha_fin           DATE         NOT NULL,
    retirado_en         DATETIME(6)  NULL,
    creado_por_id       BIGINT       NOT NULL,
    fecha_creacion      DATETIME(6)  NOT NULL,
    fecha_actualizacion DATETIME(6)  NOT NULL,
    CONSTRAINT fk_aviso_destacado_campana FOREIGN KEY (campana_id) REFERENCES campanas (id),
    CONSTRAINT fk_aviso_destacado_autor FOREIGN KEY (creado_por_id) REFERENCES usuarios (id),
    CONSTRAINT chk_aviso_destacado_fechas CHECK (fecha_fin >= fecha_inicio),
    INDEX idx_avisos_destacados_vigencia (fecha_inicio, fecha_fin)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- H69: notificaciones masivas. Bases existentes: migracion_h69_envios_masivos.sql.
CREATE TABLE IF NOT EXISTS envios_masivos (
    id                  BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    titulo              VARCHAR(150)  NOT NULL,
    mensaje             VARCHAR(1000) NOT NULL,
    enlace              VARCHAR(255)  NULL,
    destinatarios       VARCHAR(12)   NOT NULL,
    total_destinatarios INT           NOT NULL,
    autor_id            BIGINT        NOT NULL,
    enviado_en          DATETIME(6)   NOT NULL,
    CONSTRAINT fk_envio_masivo_autor FOREIGN KEY (autor_id) REFERENCES usuarios (id),
    CONSTRAINT chk_envio_masivo_destinatarios CHECK (destinatarios IN ('TODOS', 'USUARIOS', 'BIBLIOTECA')),
    INDEX idx_envios_masivos_enviado (enviado_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
ALTER TABLE notificaciones ADD COLUMN envio_masivo_id BIGINT NULL,
    ADD CONSTRAINT fk_notificacion_envio_masivo FOREIGN KEY (envio_masivo_id) REFERENCES envios_masivos (id);

-- H67: alcance de las campañas. Bases existentes: migracion_h67_alcance_campanas.sql.
CREATE TABLE IF NOT EXISTS alcance_campanas (
    id         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campana_id BIGINT      NOT NULL,
    tipo       VARCHAR(10) NOT NULL,
    visita_id  VARCHAR(64) NULL,
    fecha      DATETIME(6) NOT NULL,
    CONSTRAINT fk_alcance_campana FOREIGN KEY (campana_id) REFERENCES campanas (id),
    CONSTRAINT uq_alcance_visita UNIQUE (campana_id, visita_id),
    CONSTRAINT chk_alcance_tipo CHECK (tipo IN ('VISITA', 'REGISTRO')),
    CONSTRAINT chk_alcance_visita CHECK ((tipo = 'VISITA') = (visita_id IS NOT NULL)),
    INDEX idx_alcance_campana_fecha (campana_id, fecha)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- H66: promociones en redes. Bases existentes: migracion_h66_promociones.sql.
CREATE TABLE IF NOT EXISTS promociones_campana (
    id                BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    campana_id        BIGINT        NOT NULL,
    red               VARCHAR(10)   NOT NULL,
    texto             VARCHAR(2200) NOT NULL,
    enlace            VARCHAR(400)  NOT NULL,
    url_publicacion   VARCHAR(500)  NULL,
    publicada_en      DATETIME(6)   NOT NULL,
    registrada_por_id BIGINT        NOT NULL,
    fecha_registro    DATETIME(6)   NOT NULL,
    CONSTRAINT fk_promocion_campana FOREIGN KEY (campana_id) REFERENCES campanas (id),
    CONSTRAINT fk_promocion_autor FOREIGN KEY (registrada_por_id) REFERENCES usuarios (id),
    CONSTRAINT chk_promocion_red CHECK (red IN ('FACEBOOK', 'INSTAGRAM', 'TIKTOK', 'X', 'LINKEDIN', 'WHATSAPP', 'OTRA')),
    INDEX idx_promociones_campana (campana_id, publicada_en)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- H34: eliminación real de la cuenta. Bases existentes: migracion_h34_eliminacion_real.sql.
ALTER TABLE solicitudes_eliminacion
    ADD COLUMN atendida_por_id BIGINT NULL,
    ADD COLUMN fecha_atencion DATETIME(6) NULL,
    ADD COLUMN motivo_rechazo VARCHAR(500) NULL,
    ADD CONSTRAINT fk_solicitud_eliminacion_atendida_por FOREIGN KEY (atendida_por_id) REFERENCES usuarios (id),
    ADD CONSTRAINT chk_solicitud_eliminacion_estado CHECK (estado IN ('PENDIENTE', 'ATENDIDA', 'RECHAZADA'));

CREATE TABLE IF NOT EXISTS archivos_por_borrar (
    id         BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    ruta       VARCHAR(255) NOT NULL,
    anotado_en DATETIME(6)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
