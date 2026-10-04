package com.proyectointegrador.service;

import com.proyectointegrador.dto.CategoriaResponse;
import com.proyectointegrador.repository.CategoriaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar() {
        return categoriaRepository.findAllByOrderByIdAsc().stream()
                .map(CategoriaResponse::from)
                .toList();
    }
}
