-- =====================================================================
-- Migración H11 - Límites del servidor para el código de verificación
--
-- Agrega a usuarios:
--   intentos_codigo   -> intentos fallidos con el código vigente (máx. 5)
--   codigo_enviado_en -> último envío del código (espera de 60 s entre envíos)
--
-- Conserva todos los datos: solo agrega columnas. Hacer respaldo antes.
-- Es re-ejecutable: si una columna ya existe, no la vuelve a crear.
-- Uso (Docker):
--   docker exec -i foundia-mysql mysql -uroot -proot --default-character-set=utf8mb4 objetos_perdidos_db < database/migracion_h11_limites_codigo.sql
-- =====================================================================
SET NAMES utf8mb4;

SET @existe = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuarios' AND COLUMN_NAME = 'intentos_codigo');
SET @sql = IF(@existe = 0,
  'ALTER TABLE usuarios ADD COLUMN intentos_codigo INT NOT NULL DEFAULT 0 AFTER codigo_expira',
  'SELECT ''intentos_codigo ya existe'' AS aviso');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SET @existe = (SELECT COUNT(*) FROM information_schema.COLUMNS
               WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuarios' AND COLUMN_NAME = 'codigo_enviado_en');
SET @sql = IF(@existe = 0,
  'ALTER TABLE usuarios ADD COLUMN codigo_enviado_en DATETIME NULL AFTER intentos_codigo',
  'SELECT ''codigo_enviado_en ya existe'' AS aviso');
PREPARE s FROM @sql; EXECUTE s; DEALLOCATE PREPARE s;

SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'usuarios'
  AND COLUMN_NAME IN ('intentos_codigo', 'codigo_enviado_en');
