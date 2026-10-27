package com.proyectointegrador.repository;

import com.proyectointegrador.entity.EventoCoincidencia;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface EventoCoincidenciaRepository extends JpaRepository<EventoCoincidencia,Long> {
    interface Pendiente {Long getId();Long getObjetoId();}
    @Transactional(readOnly=true,propagation=Propagation.REQUIRES_NEW)
    @Query("select e.id as id,e.objeto.id as objetoId from EventoCoincidencia e where e.estado='PENDIENTE' and (:objeto is null or e.objeto.id=:objeto) order by e.id")
    List<Pendiente> pendientes(@Param("objeto") Long objeto,Pageable limite);

    @Modifying @Transactional(propagation=Propagation.REQUIRES_NEW)
    @Query("update EventoCoincidencia e set e.estado='COMPLETADO',e.intentos=e.intentos+1,e.detalle=null where e.id=:id")
    void completar(@Param("id") Long id);

    @Modifying @Transactional(propagation=Propagation.REQUIRES_NEW)
    @Query("update EventoCoincidencia e set e.intentos=e.intentos+1,e.detalle=:detalle where e.id=:id and e.estado='PENDIENTE'")
    void fallido(@Param("id") Long id,@Param("detalle") String detalle);
}
