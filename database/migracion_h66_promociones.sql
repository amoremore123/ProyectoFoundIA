-- H66: promociones de campañas publicadas a mano en redes sociales (texto, enlace y fecha).
-- Idempotente. Requiere migracion_h60_campanas.sql. Hacer un respaldo antes de aplicarla. No borra datos.
USE objetos_perdidos_db;

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
