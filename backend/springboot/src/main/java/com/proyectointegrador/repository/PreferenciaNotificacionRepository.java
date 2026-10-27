package com.proyectointegrador.repository;
import com.proyectointegrador.entity.PreferenciaNotificacion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface PreferenciaNotificacionRepository extends JpaRepository<PreferenciaNotificacion,Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PreferenciaNotificacion p where p.usuarioId = :id")
    Optional<PreferenciaNotificacion> buscarParaActualizar(@Param("id") Long id);
}
