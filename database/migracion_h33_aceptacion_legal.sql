-- H33: aceptación explícita; NULL en cuentas anteriores. No atribuye consentimientos históricos.
SET NAMES utf8mb4;

SET @existe = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='usuarios' AND COLUMN_NAME='version_legal');
SET @sql = IF(@existe=0, 'ALTER TABLE usuarios ADD COLUMN version_legal VARCHAR(80) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @existe = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='usuarios' AND COLUMN_NAME='aceptacion_legal_fecha');
SET @sql = IF(@existe=0, 'ALTER TABLE usuarios ADD COLUMN aceptacion_legal_fecha DATETIME NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @existe = (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='usuarios' AND COLUMN_NAME='documentos_legales_hash');
SET @sql = IF(@existe=0, 'ALTER TABLE usuarios ADD COLUMN documentos_legales_hash CHAR(64) NULL', 'SELECT 1');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;
