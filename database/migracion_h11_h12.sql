-- =====================================================================
-- Migración H11 (verificación de correo) y H12 (bloqueo por intentos)
-- Ejecutar SOLO si tu base ya existía antes de este cambio
-- (por ejemplo, MySQL local). Con Docker basta: docker compose down -v
-- =====================================================================
USE objetos_perdidos_db;

ALTER TABLE usuarios
    ADD COLUMN verificado          BOOLEAN    NOT NULL DEFAULT FALSE AFTER estado,
    ADD COLUMN codigo_verificacion VARCHAR(6) NULL                   AFTER verificado,
    ADD COLUMN codigo_expira       DATETIME   NULL                   AFTER codigo_verificacion,
    ADD COLUMN intentos_fallidos   INT        NOT NULL DEFAULT 0     AFTER codigo_expira,
    ADD COLUMN bloqueado_hasta     DATETIME   NULL                   AFTER intentos_fallidos;

-- Los usuarios que ya existían se consideran verificados
UPDATE usuarios SET verificado = TRUE;
