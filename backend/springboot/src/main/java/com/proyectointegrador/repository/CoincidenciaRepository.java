package com.proyectointegrador.repository;

import com.proyectointegrador.entity.Coincidencia;
import com.proyectointegrador.entity.EstadoCoincidencia;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CoincidenciaRepository extends JpaRepository<Coincidencia, Long> {

    boolean existsByObjetoPerdidoIdAndObjetoEncontradoId(Long objetoPerdidoId, Long objetoEncontradoId);

    Optional<Coincidencia> findByObjetoPerdidoIdAndObjetoEncontradoId(Long objetoPerdidoId, Long objetoEncontradoId);

    // Lectura actual también en MySQL REPEATABLE READ, después de bloquear los objetos.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Coincidencia c where c.objetoPerdido.id = :perdidoId and c.objetoEncontrado.id = :encontradoId")
    Optional<Coincidencia> buscarParejaParaActualizar(@Param("perdidoId") Long perdidoId,
                                                    @Param("encontradoId") Long encontradoId);

    List<Coincidencia> findByObjetoPerdidoId(Long objetoPerdidoId);

    List<Coincidencia> findByObjetoEncontradoId(Long objetoEncontradoId);

    boolean existsByObjetoPerdidoIdAndObjetoEncontradoIdAndEstado(Long perdidoId, Long encontradoId,
                                                               EstadoCoincidencia estado);

    @Query("select c.objetoEncontrado.id from Coincidencia c where c.objetoPerdido.id = :id and c.estado = :estado")
    List<Long> encontrarDestinosPorEstado(@Param("id") Long id, @Param("estado") EstadoCoincidencia estado);

    @Query("select c.objetoPerdido.id from Coincidencia c where c.objetoEncontrado.id = :id and c.estado = :estado")
    List<Long> encontrarOrigenesPorEstado(@Param("id") Long id, @Param("estado") EstadoCoincidencia estado);

    interface ParejaIds {
        Long getPerdidoId();
        Long getEncontradoId();
    }

    // Proyectar IDs evita cargar objetos antes de bloquearlos en una escritura.
    @Query("select c.objetoPerdido.id as perdidoId, c.objetoEncontrado.id as encontradoId from Coincidencia c where c.id = :id")
    Optional<ParejaIds> identificarPareja(@Param("id") Long id);
}
