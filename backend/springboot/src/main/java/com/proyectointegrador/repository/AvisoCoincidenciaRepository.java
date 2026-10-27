package com.proyectointegrador.repository;

import com.proyectointegrador.entity.AvisoCoincidencia;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AvisoCoincidenciaRepository extends JpaRepository<AvisoCoincidencia, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AvisoCoincidencia a where a.objetoPerdido.id = :perdido and a.objetoEncontrado.id = :encontrado")
    Optional<AvisoCoincidencia> buscarParaActualizar(@Param("perdido") Long perdido, @Param("encontrado") Long encontrado);
}
