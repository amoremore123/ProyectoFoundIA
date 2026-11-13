package com.proyectointegrador.repository;

import com.proyectointegrador.entity.Notificacion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NotificacionRepository extends JpaRepository<Notificacion, Long> {

    List<Notificacion> findByUsuarioIdOrderByFechaCreacionDesc(Long usuarioId);

    Optional<Notificacion> findByIdAndUsuarioId(Long id, Long usuarioId);

    // H25: historial paginado de la cuenta propia, con o sin filtro de lectura
    Page<Notificacion> findByUsuarioId(Long usuarioId, Pageable pageable);

    Page<Notificacion> findByUsuarioIdAndLeida(Long usuarioId, Boolean leida, Pageable pageable);

    long countByUsuarioIdAndLeidaFalse(Long usuarioId);

    // H25: una sola sentencia y siempre filtrada por la cuenta del token
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Notificacion n SET n.leida = true WHERE n.usuario.id = :usuarioId AND n.leida = false")
    int marcarTodasLeidas(@Param("usuarioId") Long usuarioId);
}
