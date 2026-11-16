-- H42: respuesta visible solo para el personal que revisa custodia.
USE objetos_perdidos_db;

SET @sql := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE reclamos ADD COLUMN respuesta_verificacion VARCHAR(500) NULL', 'SELECT 1')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'reclamos'
      AND COLUMN_NAME = 'respuesta_verificacion');
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
