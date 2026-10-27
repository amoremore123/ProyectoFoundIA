-- H22: fecha UTC de recuperación del perdido propio.
-- Solo agrega una columna nullable. No cambia estados ni rellena datos históricos.
-- Revisar respaldo y base destino antes de aplicar; no ejecutar script.sql en bases existentes.
-- Re-ejecutable. La aplicación requiere esta columna antes de desplegar H22.
-- Validación de desarrollo: únicamente bases QA nuevas y aisladas.
SET NAMES utf8mb4;

SET @existe = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'objetos' AND COLUMN_NAME = 'fecha_recuperacion');
SET @sql = IF(@existe = 0,
  'ALTER TABLE objetos ADD COLUMN fecha_recuperacion DATETIME NULL COMMENT ''Confirmación de recuperación en UTC'' AFTER fecha_actualizacion',
  'SELECT ''fecha_recuperacion ya existe'' AS aviso');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'objetos' AND COLUMN_NAME = 'fecha_recuperacion';
