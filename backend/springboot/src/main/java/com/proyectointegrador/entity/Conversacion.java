package com.proyectointegrador.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/** H55: una conversación pertenece a un contacto o a un reclamo y solo la ven sus participantes. */
@Entity
@Table(name = "conversaciones", uniqueConstraints = {
        @UniqueConstraint(name = "uq_conversaciones_contacto", columnNames = "contacto_id"),
        @UniqueConstraint(name = "uq_conversaciones_reclamo", columnNames = "reclamo_id")
})
@Getter
@Setter
@NoArgsConstructor
public class Conversacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoConversacion tipo;

    @ManyToOne(optional = false)
    @JoinColumn(name = "objeto_id", nullable = false)
    private Objeto objeto;

    @ManyToOne
    @JoinColumn(name = "contacto_id")
    private Contacto contacto;

    @ManyToOne
    @JoinColumn(name = "reclamo_id")
    private Reclamo reclamo;

    /** Quien inició: el dueño del objeto perdido o quien reclama. */
    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_a_id", nullable = false)
    private Usuario usuarioA;

    /** Quien encontró el objeto. Es null en las conversaciones con la biblioteca. */
    @ManyToOne
    @JoinColumn(name = "usuario_b_id")
    private Usuario usuarioB;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoConversacion estado = EstadoConversacion.ABIERTA;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @PrePersist
    void prePersist() {
        if (creadaEn == null) {
            creadaEn = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        }
    }
}
