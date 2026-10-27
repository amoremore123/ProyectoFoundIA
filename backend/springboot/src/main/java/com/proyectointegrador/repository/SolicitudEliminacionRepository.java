package com.proyectointegrador.repository;
import com.proyectointegrador.entity.SolicitudEliminacion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
public interface SolicitudEliminacionRepository extends JpaRepository<SolicitudEliminacion, Long> {
    Optional<SolicitudEliminacion> findByUsuarioId(Long usuarioId);
}
