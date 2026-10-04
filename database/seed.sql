-- =====================================================================
-- PLATAFORMA DE OBJETOS PERDIDOS Y ENCONTRADOS
-- seed.sql
-- Datos iniciales de desarrollo. Ejecutar DESPUÉS de script.sql.
-- Es re-ejecutable: limpia primero los datos de negocio.
--
-- Contraseñas: hash BCrypt (compatibles con Spring Security).
--   admin@foundia.dev   / Admin123!
--   ana@foundia.dev     / Admin123!
--   luis@foundia.dev    / Admin123!
--   carla@foundia.dev   / Admin123!
-- Documentadas únicamente con fines de desarrollo.
-- =====================================================================

USE objetos_perdidos_db;

-- Limpieza en orden inverso a las dependencias (permite re-ejecutar)
DELETE FROM reportes;
DELETE FROM notificaciones;
DELETE FROM contactos;
DELETE FROM coincidencias;
DELETE FROM fotos;
DELETE FROM objetos;
DELETE FROM usuarios;
DELETE FROM categorias;

ALTER TABLE usuarios    AUTO_INCREMENT = 1;
ALTER TABLE categorias  AUTO_INCREMENT = 1;
ALTER TABLE objetos     AUTO_INCREMENT = 1;

-- =====================================================================
-- CATEGORÍAS
-- =====================================================================
INSERT INTO categorias (nombre, descripcion, estado) VALUES
    ('Celular',   'Teléfonos inteligentes y celulares',        TRUE),
    ('Laptop',    'Computadoras portátiles y accesorios',      TRUE),
    ('Mochila',   'Mochilas y bolsos',                         TRUE),
    ('Documento', 'Cédulas, carnés, títulos y papeles',        TRUE),
    ('Ropa',      'Prendas de vestir',                         TRUE),
    ('Accesorio', 'Relojes, gafas, joyas y otros accesorios',  TRUE),
    ('Llaves',    'Llaves y llaveros',                         TRUE),
    ('Otros',     'Objetos que no entran en otras categorías', TRUE);

-- =====================================================================
-- USUARIOS (hash BCrypt de "Admin123!")
-- =====================================================================
INSERT INTO usuarios (nombre, apellido, correo, password, rol, estado) VALUES
    ('Admin',    'Sistema', 'admin@foundia.dev',  '$2a$10$os2A3cVrJ17vne137tk8neTtNo3FVsye4LGvyXZunlYSjrcyVce8G', 'ADMIN',  'ACTIVO'),
    ('Ana',      'Pérez',   'ana@foundia.dev',    '$2a$10$os2A3cVrJ17vne137tk8neTtNo3FVsye4LGvyXZunlYSjrcyVce8G', 'USUARIO','ACTIVO'),
    ('Luis',     'García',  'luis@foundia.dev',   '$2a$10$os2A3cVrJ17vne137tk8neTtNo3FVsye4LGvyXZunlYSjrcyVce8G', 'USUARIO','ACTIVO'),
    ('Carla',    'López',   'carla@foundia.dev',  '$2a$10$os2A3cVrJ17vne137tk8neTtNo3FVsye4LGvyXZunlYSjrcyVce8G', 'USUARIO','ACTIVO');

-- =====================================================================
-- OBJETOS DE PRUEBA
--   usuario_id:  1=Ana, 2=Luis, 3=Carla
--   categoria_id: 1=Celular, 2=Laptop, 3=Mochila, 4=Documento,
--                 5=Ropa, 6=Accesorio, 7=Llaves, 8=Otros
-- =====================================================================
INSERT INTO objetos
    (usuario_id, categoria_id, nombre, descripcion, ubicacion, latitud, longitud,
     fecha_objeto, tipo, estado, fecha_publicacion)
VALUES
    (1, 3, 'Mochila negra con libros',
     'Mochila negra con una pulsera roja en la cremallera. Contiene libros de cálculo.',
     'Comedor principal', 19.4326100, -99.1332000, '2026-10-02', 'ENCONTRADO', 'ACTIVO', '2026-10-02 12:30:00'),

    (2, 1, 'Celular Samsung Galaxy A54',
     'Celular color azul oscuro, con funda transparente y rayón en la esquina inferior.',
     'Aula 204', 19.4331000, -99.1345000, '2026-10-01', 'PERDIDO', 'ACTIVO', '2026-10-01 18:05:00'),

    (3, 7, 'Llavero con llaves de casa',
     'Llavero metálico con tres llaves y un llavero de tela azul.',
     'Biblioteca', 19.4318000, -99.1351000, '2026-10-03', 'ENCONTRADO', 'ACTIVO', '2026-10-03 09:15:00'),

    (1, 4, 'Cédula de identidad',
     'Cédula de identidad dentro de una cartera negra. Los datos serán verificados por privacidad.',
     'Cancha deportiva', 19.4340000, -99.1320000, '2026-09-30', 'PERDIDO', 'ACTIVO', '2026-09-30 16:40:00'),

    (2, 2, 'Laptop Dell Inspiron',
     'Laptop gris con calcomanía de la universidad. Se perdió junto con su cargador.',
     'Laboratorio de cómputo', 19.4322000, -99.1339000, '2026-09-28', 'PERDIDO', 'ACTIVO', '2026-09-28 20:10:00'),

    (3, 6, 'Gafas de sol',
     'Gafas de sol negras en estuche rígido color café.',
     'Entrada principal', 19.4345000, -99.1328000, '2026-10-03', 'ENCONTRADO', 'ACTIVO', '2026-10-03 14:20:00');

-- =====================================================================
-- FOTOS DE EJEMPLO
-- =====================================================================
INSERT INTO fotos (objeto_id, url, nombre_archivo) VALUES
    (1, '/uploads/objetos/mochila-negra.jpg',  'mochila-negra.jpg'),
    (2, '/uploads/objetos/celular-samsung.jpg','celular-samsung.jpg'),
    (3, '/uploads/objetos/llavero.jpg',        'llavero.jpg');

-- =====================================================================
-- NOTIFICACIONES DE EJEMPLO
-- =====================================================================
INSERT INTO notificaciones (usuario_id, titulo, mensaje, tipo, leida) VALUES
    (1, 'Nuevo objeto encontrado', 'Se publicó un objeto encontrado cerca de tu última búsqueda.', 'SISTEMA', FALSE),
    (2, 'Bienvenido',             'Tu cuenta fue creada correctamente. ¡Publica tu primer objeto!', 'SISTEMA', TRUE);
