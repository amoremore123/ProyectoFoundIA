package com.proyectointegrador.repository;

import com.proyectointegrador.entity.Contacto;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactoRepository extends JpaRepository<Contacto, Long> {

    List<Contacto> findByUsuarioEmisorId(Long usuarioId);

    List<Contacto> findByUsuarioReceptorId(Long usuarioId);
}
