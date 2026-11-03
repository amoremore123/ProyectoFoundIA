package com.proyectointegrador.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** H35: enlace de recuperación de un solo uso. Solo se guarda el hash del token. */
@Entity
@Table(name = "tokens_recuperacion_password")
@Getter @Setter @NoArgsConstructor
public class TokenRecuperacionPassword {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "expira_en", nullable = false, updatable = false)
    private LocalDateTime expiraEn;

    /** Momento en que se usó o se invalidó (por una solicitud nueva o un cambio de contraseña). */
    @Column(name = "usado_en")
    private LocalDateTime usadoEn;
}
