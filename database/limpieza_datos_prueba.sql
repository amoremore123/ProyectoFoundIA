-- =====================================================================================================
-- Limpieza de datos de prueba (solo bases de desarrollo / QA)
--
-- Borra las cuentas que crearon las pruebas automáticas y todo lo que depende de ellas:
--   * correos que terminan en @prueba.invalid   (pruebas e2e de Playwright)
--   * correos prueba.*@foundia.dev               (cuentas sueltas para probar correos)
-- Las cuentas reales (admin, ana, luis, carla, etc.) y sus publicaciones no se tocan.
--
-- Cómo funciona
--   * Por defecto es una VISTA PREVIA: borra dentro de una transacción, muestra cuántas filas saldrían
--     de cada tabla y deshace todo (ROLLBACK). No cambia nada.
--   * Para borrar de verdad hay que anteponer  SET @confirmar = 1;  (ver abajo).
--   * Si algo falla, o si al final quedara alguna cuenta de prueba o faltara algún dato real,
--     la transacción se deshace entera.
--
-- Uso (Git Bash, desde la raíz del repositorio; SAQUE UN RESPALDO ANTES DE BORRAR):
--   Vista previa:
--     docker exec -i foundia-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" objetos_perdidos_db' < database/limpieza_datos_prueba.sql
--   Borrar de verdad:
--     (echo "SET @confirmar = 1;"; cat database/limpieza_datos_prueba.sql) | docker exec -i foundia-mysql sh -c 'mysql -uroot -p"$MYSQL_ROOT_PASSWORD" objetos_perdidos_db'
--
-- Las fotos de esos objetos quedan como archivos en el volumen de uploads; el script lista cuántas son.
-- No cambia el esquema ni la secuencia de códigos de custodia.
-- =====================================================================================================

SET @confirmar = IFNULL(@confirmar, 0);

-- ---------- 1. Qué se va a borrar (tablas auxiliares; se quitan al final) ----------
DROP TABLE IF EXISTS _lp_usuarios, _lp_objetos, _lp_coincidencias, _lp_contactos, _lp_reclamos,
                     _lp_pertenencias, _lp_custodias, _lp_conversaciones, _lp_mensajes, _lp_notificaciones;

CREATE TABLE _lp_usuarios (id BIGINT PRIMARY KEY)
    SELECT id FROM usuarios WHERE correo LIKE '%@prueba.invalid' OR correo LIKE 'prueba.%@foundia.dev';
CREATE TABLE _lp_objetos (id BIGINT PRIMARY KEY)
    SELECT id FROM objetos WHERE usuario_id IN (SELECT id FROM _lp_usuarios);
CREATE TABLE _lp_coincidencias (id BIGINT PRIMARY KEY)
    SELECT id FROM coincidencias
    WHERE objeto_perdido_id IN (SELECT id FROM _lp_objetos) OR objeto_encontrado_id IN (SELECT id FROM _lp_objetos);
CREATE TABLE _lp_contactos (id BIGINT PRIMARY KEY)
    SELECT id FROM contactos
    WHERE coincidencia_id IN (SELECT id FROM _lp_coincidencias)
       OR usuario_emisor_id IN (SELECT id FROM _lp_usuarios) OR usuario_receptor_id IN (SELECT id FROM _lp_usuarios);
CREATE TABLE _lp_reclamos (id BIGINT PRIMARY KEY)
    SELECT id FROM reclamos WHERE objeto_id IN (SELECT id FROM _lp_objetos) OR usuario_id IN (SELECT id FROM _lp_usuarios);
CREATE TABLE _lp_pertenencias (id BIGINT PRIMARY KEY)
    SELECT id FROM pertenencias WHERE usuario_id IN (SELECT id FROM _lp_usuarios);
CREATE TABLE _lp_custodias (id BIGINT PRIMARY KEY)
    SELECT id FROM custodias WHERE objeto_id IN (SELECT id FROM _lp_objetos);
CREATE TABLE _lp_conversaciones (id BIGINT PRIMARY KEY)
    SELECT id FROM conversaciones
    WHERE usuario_a_id IN (SELECT id FROM _lp_usuarios) OR usuario_b_id IN (SELECT id FROM _lp_usuarios)
       OR objeto_id IN (SELECT id FROM _lp_objetos) OR contacto_id IN (SELECT id FROM _lp_contactos)
       OR reclamo_id IN (SELECT id FROM _lp_reclamos) OR pertenencia_id IN (SELECT id FROM _lp_pertenencias);
CREATE TABLE _lp_mensajes (id BIGINT PRIMARY KEY)
    SELECT id FROM mensajes_chat
    WHERE conversacion_id IN (SELECT id FROM _lp_conversaciones) OR autor_id IN (SELECT id FROM _lp_usuarios);
CREATE TABLE _lp_notificaciones (id BIGINT PRIMARY KEY)
    SELECT id FROM notificaciones WHERE usuario_id IN (SELECT id FROM _lp_usuarios);

-- Lo que debe seguir igual después
SET @usuarios_reales = (SELECT COUNT(*) FROM usuarios WHERE id NOT IN (SELECT id FROM _lp_usuarios));
SET @objetos_reales  = (SELECT COUNT(*) FROM objetos  WHERE id NOT IN (SELECT id FROM _lp_objetos));
SET @fotos_en_disco  = (SELECT COUNT(*) FROM fotos    WHERE objeto_id IN (SELECT id FROM _lp_objetos));

-- ---------- 2. Borrado en orden (lo que depende primero) dentro de una transacción ----------
DROP PROCEDURE IF EXISTS _lp_limpiar;
DELIMITER //
CREATE PROCEDURE _lp_limpiar(IN confirmar INT)
BEGIN
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET @resultado = 'ERROR: se deshizo todo, no se borró nada';
        RESIGNAL;
    END;

    SET @detalle = '';
    START TRANSACTION;

    DELETE FROM reportes_mensaje WHERE mensaje_id IN (SELECT id FROM _lp_mensajes) OR usuario_id IN (SELECT id FROM _lp_usuarios);
    SET @detalle = CONCAT(@detalle, 'reportes_mensaje: ', ROW_COUNT(), '\n');
    DELETE FROM lecturas_chat WHERE conversacion_id IN (SELECT id FROM _lp_conversaciones) OR usuario_id IN (SELECT id FROM _lp_usuarios);
    SET @detalle = CONCAT(@detalle, 'lecturas_chat: ', ROW_COUNT(), '\n');
    DELETE FROM mensajes_chat WHERE id IN (SELECT id FROM _lp_mensajes);
    SET @detalle = CONCAT(@detalle, 'mensajes_chat: ', ROW_COUNT(), '\n');
    DELETE FROM conversaciones WHERE id IN (SELECT id FROM _lp_conversaciones);
    SET @detalle = CONCAT(@detalle, 'conversaciones: ', ROW_COUNT(), '\n');
    DELETE FROM bloqueos_chat WHERE bloqueador_id IN (SELECT id FROM _lp_usuarios) OR bloqueado_id IN (SELECT id FROM _lp_usuarios);
    SET @detalle = CONCAT(@detalle, 'bloqueos_chat: ', ROW_COUNT(), '\n');
    DELETE FROM contactos WHERE id IN (SELECT id FROM _lp_contactos);
    SET @detalle = CONCAT(@detalle, 'contactos: ', ROW_COUNT(), '\n');
    DELETE FROM avisos_coincidencia
    WHERE objeto_perdido_id IN (SELECT id FROM _lp_objetos) OR objeto_encontrado_id IN (SELECT id FROM _lp_objetos)
       OR notificacion_id IN (SELECT id FROM _lp_notificaciones);
    SET @detalle = CONCAT(@detalle, 'avisos_coincidencia: ', ROW_COUNT(), '\n');
    DELETE FROM recordatorios_objeto WHERE objeto_id IN (SELECT id FROM _lp_objetos) OR notificacion_id IN (SELECT id FROM _lp_notificaciones);
    SET @detalle = CONCAT(@detalle, 'recordatorios_objeto: ', ROW_COUNT(), '\n');
    DELETE FROM coincidencias WHERE id IN (SELECT id FROM _lp_coincidencias);
    SET @detalle = CONCAT(@detalle, 'coincidencias: ', ROW_COUNT(), '\n');
    DELETE FROM disposiciones WHERE custodia_id IN (SELECT id FROM _lp_custodias);
    SET @detalle = CONCAT(@detalle, 'disposiciones: ', ROW_COUNT(), '\n');
    DELETE FROM custodias WHERE id IN (SELECT id FROM _lp_custodias);
    SET @detalle = CONCAT(@detalle, 'custodias: ', ROW_COUNT(), '\n');
    DELETE FROM reclamos WHERE id IN (SELECT id FROM _lp_reclamos);
    SET @detalle = CONCAT(@detalle, 'reclamos: ', ROW_COUNT(), '\n');
    DELETE FROM reportes WHERE objeto_id IN (SELECT id FROM _lp_objetos) OR usuario_id IN (SELECT id FROM _lp_usuarios);
    SET @detalle = CONCAT(@detalle, 'reportes: ', ROW_COUNT(), '\n');
    DELETE FROM eventos_coincidencia WHERE objeto_id IN (SELECT id FROM _lp_objetos);
    SET @detalle = CONCAT(@detalle, 'eventos_coincidencia: ', ROW_COUNT(), '\n');
    DELETE FROM fotos WHERE objeto_id IN (SELECT id FROM _lp_objetos);
    SET @detalle = CONCAT(@detalle, 'fotos: ', ROW_COUNT(), '\n');
    DELETE FROM objetos WHERE id IN (SELECT id FROM _lp_objetos);
    SET @detalle = CONCAT(@detalle, 'objetos: ', ROW_COUNT(), '\n');
    DELETE FROM notificaciones WHERE id IN (SELECT id FROM _lp_notificaciones);
    SET @detalle = CONCAT(@detalle, 'notificaciones: ', ROW_COUNT(), '\n');
    DELETE FROM pertenencias WHERE id IN (SELECT id FROM _lp_pertenencias);
    SET @detalle = CONCAT(@detalle, 'pertenencias: ', ROW_COUNT(), '\n');
    DELETE FROM dispositivos WHERE usuario_id IN (SELECT id FROM _lp_usuarios);
    SET @detalle = CONCAT(@detalle, 'dispositivos: ', ROW_COUNT(), '\n');
    DELETE FROM preferencias_notificacion WHERE usuario_id IN (SELECT id FROM _lp_usuarios);
    SET @detalle = CONCAT(@detalle, 'preferencias_notificacion: ', ROW_COUNT(), '\n');
    DELETE FROM tokens_revocados WHERE usuario_id IN (SELECT id FROM _lp_usuarios);
    SET @detalle = CONCAT(@detalle, 'tokens_revocados: ', ROW_COUNT(), '\n');
    DELETE FROM tokens_recuperacion_password WHERE usuario_id IN (SELECT id FROM _lp_usuarios);
    SET @detalle = CONCAT(@detalle, 'tokens_recuperacion_password: ', ROW_COUNT(), '\n');
    DELETE FROM solicitudes_eliminacion WHERE usuario_id IN (SELECT id FROM _lp_usuarios);
    SET @detalle = CONCAT(@detalle, 'solicitudes_eliminacion: ', ROW_COUNT(), '\n');
    DELETE FROM invitaciones WHERE usuario_id IN (SELECT id FROM _lp_usuarios);
    SET @detalle = CONCAT(@detalle, 'invitaciones: ', ROW_COUNT(), '\n');
    DELETE FROM cambios_rol WHERE usuario_id IN (SELECT id FROM _lp_usuarios);
    SET @detalle = CONCAT(@detalle, 'cambios_rol: ', ROW_COUNT(), '\n');
    DELETE FROM usuarios WHERE id IN (SELECT id FROM _lp_usuarios);
    SET @detalle = CONCAT(@detalle, 'usuarios: ', ROW_COUNT(), '\n');

    -- Comprobaciones: no queda ninguna cuenta de prueba y lo real sigue completo
    IF (SELECT COUNT(*) FROM usuarios WHERE correo LIKE '%@prueba.invalid' OR correo LIKE 'prueba.%@foundia.dev') <> 0
       OR (SELECT COUNT(*) FROM usuarios) <> @usuarios_reales
       OR (SELECT COUNT(*) FROM objetos) <> @objetos_reales THEN
        ROLLBACK;
        SET @resultado = 'ALGO NO CUADRA: se deshizo todo, no se borró nada';
    ELSEIF confirmar = 1 THEN
        COMMIT;
        SET @resultado = 'BORRADO CONFIRMADO';
    ELSE
        ROLLBACK;
        SET @resultado = 'VISTA PREVIA: no se borró nada (para borrar, anteponer SET @confirmar = 1;)';
    END IF;
END //
DELIMITER ;

CALL _lp_limpiar(@confirmar);

-- ---------- 3. Resultado ----------
SELECT @resultado AS resultado;
SELECT @detalle AS filas_por_tabla\G
SELECT @usuarios_reales AS usuarios_que_quedan, @objetos_reales AS objetos_que_quedan,
       @fotos_en_disco AS archivos_de_foto_que_quedan_huerfanos;
SELECT correo, rol FROM usuarios WHERE id NOT IN (SELECT id FROM _lp_usuarios) ORDER BY id;

-- ---------- 4. Quitar lo auxiliar ----------
DROP PROCEDURE IF EXISTS _lp_limpiar;
DROP TABLE IF EXISTS _lp_usuarios, _lp_objetos, _lp_coincidencias, _lp_contactos, _lp_reclamos,
                     _lp_pertenencias, _lp_custodias, _lp_conversaciones, _lp_mensajes, _lp_notificaciones;
