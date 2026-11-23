package com.proyectointegrador.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Hasta qué mensaje leyó cada persona una conversación. De ahí salen los sin leer. */
@Entity
@Table(name = "lecturas_chat", uniqueConstraints = {
        @UniqueConstraint(name = "uq_lecturas_chat", columnNames = {"conversacion_id", "usuario_id"})
})
@Getter
@Setter
@NoArgsConstructor
public class LecturaChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversacion_id", nullable = false)
    private Long conversacionId;

    @Column(name = "usuario_id", nullable = false)
    private Long usuarioId;

    @Column(name = "ultimo_mensaje_id", nullable = false)
    private Long ultimoMensajeId = 0L;
}
