-- H67: visitas y registros por origen de campaña, sin datos personales (ni IP ni cuenta).
-- Idempotente. Requiere migracion_h60_campanas.sql. Hacer un respaldo antes de aplicarla. No borra datos.
USE objetos_perdidos_db;

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
