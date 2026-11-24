package com.proyectointegrador.repository;

import com.proyectointegrador.entity.Conversacion;
import com.proyectointegrador.entity.TipoConversacion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversacionRepository extends JpaRepository<Conversacion, Long> {

    Optional<Conversacion> findByContactoId(Long contactoId);

    Optional<Conversacion> findByReclamoId(Long reclamoId);

    @Query("select c from Conversacion c where c.usuarioA.id = :usuarioId or c.usuarioB.id = :usuarioId order by c.id desc")
    List<Conversacion> delUsuario(@Param("usuarioId") Long usuarioId);

    List<Conversacion> findTop100ByTipoOrderByIdDesc(TipoConversacion tipo);

    /** Etiqueta QR: la conversación que ya abrió esta persona con el dueño de esa pertenencia. */
    Optional<Conversacion> findFirstByPertenenciaIdAndUsuarioAId(Long pertenenciaId, Long usuarioAId);

    long countByTipoAndUsuarioAIdAndCreadaEnAfter(TipoConversacion tipo, Long usuarioAId, java.time.LocalDateTime desde);
}
