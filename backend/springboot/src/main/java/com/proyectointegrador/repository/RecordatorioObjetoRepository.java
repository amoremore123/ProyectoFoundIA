package com.proyectointegrador.repository;

import com.proyectointegrador.entity.RecordatorioObjeto;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordatorioObjetoRepository extends JpaRepository<RecordatorioObjeto, Long> {

    boolean existsByObjetoIdAndPeriodo(Long objetoId, String periodo);
}
