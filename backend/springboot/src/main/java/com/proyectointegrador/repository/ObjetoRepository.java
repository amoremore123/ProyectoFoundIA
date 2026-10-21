package com.proyectointegrador.repository;

import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ObjetoRepository extends JpaRepository<Objeto, Long>, JpaSpecificationExecutor<Objeto> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Objeto o where o.id = :id")
    Optional<Objeto> findByIdForUpdate(@Param("id") Long id);

    List<Objeto> findByCategoriaIdAndEstadoInOrderByFechaPublicacionDescIdDesc(Long categoriaId, List<EstadoObjeto> estados);

    List<Objeto> findByUbicacionContainingIgnoreCaseAndEstadoInOrderByFechaPublicacionDescIdDesc(String ubicacion, List<EstadoObjeto> estados);

    List<Objeto> findByUsuarioIdOrderByFechaPublicacionDesc(Long usuarioId);

    @EntityGraph(attributePaths = {"usuario", "categoria", "fotos"})
    List<Objeto> findByTipoAndCategoriaIdAndEstado(TipoObjeto tipo, Long categoriaId, EstadoObjeto estado);
}
