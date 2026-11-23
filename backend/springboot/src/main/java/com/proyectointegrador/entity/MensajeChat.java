package com.proyectointegrador.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Entity
@Table(name = "mensajes_chat", uniqueConstraints = @jakarta.persistence.UniqueConstraint(
        name = "uq_mensajes_chat_cliente", columnNames = {"autor_id", "id_cliente"}))
@Getter
@Setter
@NoArgsConstructor
public class MensajeChat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "conversacion_id", nullable = false)
    private Conversacion conversacion;

    @ManyToOne(optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Column(name = "texto", columnDefinition = "TEXT")
    private String texto;

    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    /** H65: Idempotency-Key del envío; un reintento con la misma clave devuelve este mensaje. */
    @Column(name = "id_cliente", length = 64)
    private String idCliente;

    @Column(name = "enviado_en", nullable = false, updatable = false)
    private LocalDateTime enviadoEn;

    @PrePersist
    void prePersist() {
        if (enviadoEn == null) {
            enviadoEn = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        }
    }
}
