-- H64: foto de perfil privada. Guarda la ruta interna (perfiles/<uuid>.jpg), nunca una URL pública.
-- Idempotente. Hacer un respaldo antes de aplicarla y aplicarla ANTES de desplegar Spring:
-- con ddl-auto=none, sin esta columna fallan las consultas de usuarios.
USE objetos_perdidos_db;

SET @existe := (SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'usuarios' AND column_name = 'foto_perfil_url');
SET @sql := IF(@existe = 0,
    'ALTER TABLE usuarios ADD COLUMN foto_perfil_url VARCHAR(80) NULL',
    'SELECT ''usuarios.foto_perfil_url ya existe''');
PREPARE paso FROM @sql; EXECUTE paso; DEALLOCATE PREPARE paso;
