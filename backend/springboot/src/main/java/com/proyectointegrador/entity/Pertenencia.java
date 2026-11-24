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

/**
 * Etiqueta QR preventiva: algo mío que todavía no perdí (calculadora, casaca, USB). El código del
 * QR es aleatorio y no revela al dueño; al desactivarla, el QR deja de funcionar.
 */
@Entity
@Table(name = "pertenencias")
@Getter
@Setter
@NoArgsConstructor
public class Pertenencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "nombre", nullable = false, length = 80)
    private String nombre;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    @Column(name = "codigo", nullable = false, unique = true, length = 32)
    private String codigo;

    @Column(name = "activa", nullable = false)
    private boolean activa = true;

    @Column(name = "creada_en", nullable = false, updatable = false)
    private LocalDateTime creadaEn;

    @PrePersist
    void prePersist() {
        if (creadaEn == null) {
            creadaEn = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        }
    }
}
