-- H28: agregar auditoría sin borrar denuncias ni inventar resoluciones anteriores.
-- Revisar respaldo y destino antes de aplicar; NO reimportar script.sql.
SET NAMES utf8mb4;
SET @existe = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reportes' AND COLUMN_NAME = 'resolucion');
SET @sql = IF(@existe = 0, 'ALTER TABLE reportes ADD COLUMN resolucion VARCHAR(1000) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
SET @existe = (SELECT COUNT(*) FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reportes' AND COLUMN_NAME = 'resuelto_por_id');
SET @sql = IF(@existe = 0, 'ALTER TABLE reportes ADD COLUMN resuelto_por_id BIGINT NULL, ADD CONSTRAINT fk_reportes_resuelto_por FOREIGN KEY (resuelto_por_id) REFERENCES usuarios(id) ON DELETE RESTRICT', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
