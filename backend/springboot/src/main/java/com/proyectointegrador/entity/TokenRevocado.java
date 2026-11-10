package com.proyectointegrador.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * H37: token individual revocado al cerrar sesión.
 *
 * <p>La clave es el {@code jti} del JWT y la fila se conserva hasta que el
 * token expira; después puede eliminarse sin afectar a los demás.</p>
 */
@Entity
@Table(name = "tokens_revocados")
@Getter
@Setter
@NoArgsConstructor
public class TokenRevocado {

    @Id
    @Column(name = "jti", length = 64, nullable = false)
    private String jti;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "revocado_en", nullable = false)
    private LocalDateTime revocadoEn;

    @Column(name = "expira_en", nullable = false)
    private LocalDateTime expiraEn;

    public TokenRevocado(String jti, Usuario usuario, LocalDateTime revocadoEn, LocalDateTime expiraEn) {
        this.jti = jti;
        this.usuario = usuario;
        this.revocadoEn = revocadoEn;
        this.expiraEn = expiraEn;
    }
}
