package com.proyectointegrador.repository;

import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;

public interface ObjetoRepository extends JpaRepository<Objeto, Long>, JpaSpecificationExecutor<Objeto> {

    List<Objeto> findByCategoriaIdAndEstadoInOrderByFechaPublicacionDescIdDesc(Long categoriaId, List<EstadoObjeto> estados);

    List<Objeto> findByUbicacionContainingIgnoreCaseAndEstadoInOrderByFechaPublicacionDescIdDesc(String ubicacion, List<EstadoObjeto> estados);

    List<Objeto> findByUsuarioIdOrderByFechaPublicacionDesc(Long usuarioId);

    List<Objeto> findByTipoAndCategoriaIdAndEstado(TipoObjeto tipo, Long categoriaId, EstadoObjeto estado);
}
