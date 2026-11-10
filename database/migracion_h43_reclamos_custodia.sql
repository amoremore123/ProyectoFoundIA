-- H43: resolución auditada de reclamos y QR de retiro de un solo uso.
-- Idempotente: se puede ejecutar sobre una base del Sprint 2 sin borrar datos.
USE objetos_perdidos_db;

-- 1. Columnas nuevas (solo si no existen).
SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE reclamos ADD COLUMN resuelto_por_id BIGINT NULL', 'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reclamos'
      AND COLUMN_NAME = 'resuelto_por_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE reclamos ADD COLUMN fecha_resolucion DATETIME NULL', 'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reclamos'
      AND COLUMN_NAME = 'fecha_resolucion');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE reclamos ADD COLUMN motivo_resolucion TEXT NULL', 'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reclamos'
      AND COLUMN_NAME = 'motivo_resolucion');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE reclamos ADD COLUMN qr_retiro_token VARCHAR(64) NULL', 'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reclamos'
      AND COLUMN_NAME = 'qr_retiro_token');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE reclamos ADD COLUMN qr_retiro_expira DATETIME NULL', 'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reclamos'
      AND COLUMN_NAME = 'qr_retiro_expira');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE reclamos ADD COLUMN qr_retiro_usado_en DATETIME NULL', 'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reclamos'
      AND COLUMN_NAME = 'qr_retiro_usado_en');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 2. Llave foránea del responsable de la resolución.
SET @existe_fk := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'reclamos'
      AND CONSTRAINT_NAME = 'fk_reclamos_resuelto_por');
SET @sql := IF(@existe_fk = 0,
    'ALTER TABLE reclamos ADD CONSTRAINT fk_reclamos_resuelto_por FOREIGN KEY (resuelto_por_id) REFERENCES usuarios (id) ON DELETE RESTRICT',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. Token de retiro único.
SET @existe_indice := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reclamos'
      AND INDEX_NAME = 'uq_reclamos_qr_retiro_token');
SET @sql := IF(@existe_indice = 0,
    'ALTER TABLE reclamos ADD UNIQUE KEY uq_reclamos_qr_retiro_token (qr_retiro_token)',
    'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 4. Índice de estado para la bandeja.
SET @existe_indice := (SELECT COUNT(*) FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reclamos'
      AND INDEX_NAME = 'idx_reclamos_estado');
SET @sql := IF(@existe_indice = 0,
    'ALTER TABLE reclamos ADD INDEX idx_reclamos_estado (estado)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 5. El estado RETIRADO se agrega sin perder los reclamos existentes.
SET @existe_check := (SELECT COUNT(*) FROM information_schema.TABLE_CONSTRAINTS
    WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'reclamos'
      AND CONSTRAINT_NAME = 'chk_reclamos_estado');
SET @sql := IF(@existe_check > 0,
    'ALTER TABLE reclamos DROP CHECK chk_reclamos_estado', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

ALTER TABLE reclamos
    ADD CONSTRAINT chk_reclamos_estado
    CHECK (estado IN ('PENDIENTE', 'ACEPTADO', 'RECHAZADO', 'RETIRADO'));
