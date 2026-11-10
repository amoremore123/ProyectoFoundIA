package com.proyectointegrador.repository;

import com.proyectointegrador.entity.Reclamo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ReclamoRepository extends JpaRepository<Reclamo, Long> {

    Optional<Reclamo> findByObjetoIdAndUsuarioId(Long objetoId, Long usuarioId);

    long countByUsuarioId(Long usuarioId);

    long countByUsuarioIdAndEstado(Long usuarioId, String estado);

    @Query("""
            select count(r) from Reclamo r
            where r.usuario.id = :usuarioId
              and r.estado = 'ACEPTADO'
              and r.qrRetiroUsadoEn is null
              and r.qrRetiroExpira > :ahora
            """)
    long countQrRetiroVigentes(@Param("usuarioId") Long usuarioId, @Param("ahora") LocalDateTime ahora);
}
