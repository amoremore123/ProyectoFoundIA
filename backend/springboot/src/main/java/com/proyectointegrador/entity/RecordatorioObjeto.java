package com.proyectointegrador.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
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

/**
 * H53: registro de un recordatorio ya generado para un objeto y un periodo.
 *
 * <p>La restricción única {@code (objeto_id, periodo)} es la garantía final
 * contra recordatorios duplicados cuando dos ejecuciones coinciden.</p>
 */
@Entity
@Table(name = "recordatorios_objeto",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_recordatorio_objeto_periodo",
                columnNames = {"objeto_id", "periodo"}))
@Getter
@Setter
@NoArgsConstructor
public class RecordatorioObjeto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "objeto_id", nullable = false)
    private Objeto objeto;

    /** Periodo mensual en UTC con formato {@code yyyy-MM}. */
    @Column(name = "periodo", nullable = false, length = 7)
    private String periodo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notificacion_id")
    private Notificacion notificacion;

    @Column(name = "fecha_creacion", nullable = false, updatable = false)
    private LocalDateTime fechaCreacion;

    @PrePersist
    void prePersist() {
        if (fechaCreacion == null) {
            fechaCreacion = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        }
    }
}
