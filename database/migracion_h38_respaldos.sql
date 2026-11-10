-- H38: registro de respaldos lógicos de la base de datos.
-- Idempotente: se puede ejecutar sobre una base del Sprint 2 sin borrar datos.
-- No crea ni elimina el volumen de MySQL; solo agrega la tabla de metadatos.
USE objetos_perdidos_db;

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
