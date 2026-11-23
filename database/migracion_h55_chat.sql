-- H55 a H63: chat interno entre personas y con la biblioteca.
-- Idempotente: se puede ejecutar varias veces. Hacer un respaldo antes de aplicarla a una base con datos.
-- No usar docker compose down -v ni reimportar script.sql o seed.sql sobre datos reales.
USE objetos_perdidos_db;

CREATE TABLE IF NOT EXISTS conversaciones (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    tipo          VARCHAR(20) NOT NULL,
    objeto_id     BIGINT      NOT NULL,
    contacto_id   BIGINT      NULL,
    reclamo_id    BIGINT      NULL,
    usuario_a_id  BIGINT      NOT NULL,
    usuario_b_id  BIGINT      NULL,
    estado        VARCHAR(20) NOT NULL DEFAULT 'ABIERTA',
    creada_en     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_conversaciones_contacto UNIQUE (contacto_id),
    CONSTRAINT uq_conversaciones_reclamo UNIQUE (reclamo_id),
    CONSTRAINT fk_conversaciones_objeto FOREIGN KEY (objeto_id) REFERENCES objetos (id),
    CONSTRAINT fk_conversaciones_contacto FOREIGN KEY (contacto_id) REFERENCES contactos (id),
    CONSTRAINT fk_conversaciones_reclamo FOREIGN KEY (reclamo_id) REFERENCES reclamos (id),
    CONSTRAINT fk_conversaciones_usuario_a FOREIGN KEY (usuario_a_id) REFERENCES usuarios (id),
    CONSTRAINT fk_conversaciones_usuario_b FOREIGN KEY (usuario_b_id) REFERENCES usuarios (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS mensajes_chat (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    conversacion_id  BIGINT       NOT NULL,
    autor_id         BIGINT       NOT NULL,
    texto            TEXT         NULL,
    imagen_url       VARCHAR(500) NULL,
    enviado_en       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_mensajes_chat_conversacion (conversacion_id, id),
    KEY idx_mensajes_chat_autor (autor_id, enviado_en),
    CONSTRAINT fk_mensajes_chat_conversacion FOREIGN KEY (conversacion_id) REFERENCES conversaciones (id) ON DELETE CASCADE,
    CONSTRAINT fk_mensajes_chat_autor FOREIGN KEY (autor_id) REFERENCES usuarios (id)
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS lecturas_chat (
    id                  BIGINT   NOT NULL AUTO_INCREMENT,
    conversacion_id     BIGINT   NOT NULL,
    usuario_id          BIGINT   NOT NULL,
    ultimo_mensaje_id   BIGINT   NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_lecturas_chat UNIQUE (conversacion_id, usuario_id),
    CONSTRAINT fk_lecturas_chat_conversacion FOREIGN KEY (conversacion_id) REFERENCES conversaciones (id) ON DELETE CASCADE,
    CONSTRAINT fk_lecturas_chat_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS bloqueos_chat (
    id             BIGINT   NOT NULL AUTO_INCREMENT,
    bloqueador_id  BIGINT   NOT NULL,
    bloqueado_id   BIGINT   NOT NULL,
    creado_en      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_bloqueos_chat UNIQUE (bloqueador_id, bloqueado_id),
    CONSTRAINT fk_bloqueos_chat_bloqueador FOREIGN KEY (bloqueador_id) REFERENCES usuarios (id) ON DELETE CASCADE,
    CONSTRAINT fk_bloqueos_chat_bloqueado FOREIGN KEY (bloqueado_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE = InnoDB;

CREATE TABLE IF NOT EXISTS reportes_mensaje (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    mensaje_id  BIGINT       NOT NULL,
    usuario_id  BIGINT       NOT NULL,
    motivo      VARCHAR(255) NOT NULL,
    estado      VARCHAR(20)  NOT NULL DEFAULT 'PENDIENTE',
    creado_en   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT uq_reportes_mensaje UNIQUE (mensaje_id, usuario_id),
    CONSTRAINT fk_reportes_mensaje_mensaje FOREIGN KEY (mensaje_id) REFERENCES mensajes_chat (id) ON DELETE CASCADE,
    CONSTRAINT fk_reportes_mensaje_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id) ON DELETE CASCADE
) ENGINE = InnoDB;
