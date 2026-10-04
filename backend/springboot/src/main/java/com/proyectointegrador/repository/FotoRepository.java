package com.proyectointegrador.repository;

import com.proyectointegrador.entity.Foto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FotoRepository extends JpaRepository<Foto, Long> {

    List<Foto> findByObjetoIdOrderByIdAsc(Long objetoId);
}
