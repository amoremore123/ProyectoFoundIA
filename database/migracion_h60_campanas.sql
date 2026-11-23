-- H60: campañas de difusión con enlace de origen.
-- Idempotente: se puede ejecutar varias veces. Hacer un respaldo antes de aplicarla.
-- Solo crea la tabla; no modifica ni borra datos existentes.
USE objetos_perdidos_db;

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
