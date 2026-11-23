-- H71: avisos destacados con vigencia, visibles arriba en la web y en la app.
-- Idempotente. Requiere migracion_h60_campanas.sql. Hacer un respaldo antes de aplicarla. No borra datos.
USE objetos_perdidos_db;

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
