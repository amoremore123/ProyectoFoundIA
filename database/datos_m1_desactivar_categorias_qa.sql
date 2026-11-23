-- M1 (datos): desactivar las dos categorías creadas durante las pruebas del Sprint 3.
--   «Accesorios QA semanal» y «Accesorios QA React»
--
-- Se buscan por NOMBRE, no por id: cada integrante tiene su propia base y el id puede cambiar.
-- No borra nada: la categoría deja de aparecer al publicar, pero las publicaciones que ya la usan
-- la conservan. Es re-ejecutable: si ya estaban desactivadas, no cambia nada.
-- Si una base no tiene estas categorías, no hace nada.
-- Para volver atrás: UPDATE categorias SET estado = TRUE WHERE nombre IN (...).
USE objetos_perdidos_db;

SELECT id, nombre, estado AS activa_antes,
       (SELECT COUNT(*) FROM objetos o WHERE o.categoria_id = c.id) AS publicaciones_que_la_usan
  FROM categorias c
 WHERE nombre IN ('Accesorios QA semanal', 'Accesorios QA React');

UPDATE categorias
   SET estado = FALSE
 WHERE nombre IN ('Accesorios QA semanal', 'Accesorios QA React')
   AND estado = TRUE;

SELECT ROW_COUNT() AS categorias_desactivadas_ahora, UTC_TIMESTAMP() AS fecha_utc, CURRENT_USER() AS usuario_mysql;
