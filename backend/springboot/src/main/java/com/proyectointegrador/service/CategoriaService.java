package com.proyectointegrador.service;

import com.proyectointegrador.config.CacheConfig;
import com.proyectointegrador.dto.CategoriaResponse;
import com.proyectointegrador.repository.CategoriaRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CategoriaService {

    private final CategoriaRepository categoriaRepository;

    public CategoriaService(CategoriaRepository categoriaRepository) {
        this.categoriaRepository = categoriaRepository;
    }

    // La piden casi todas las pantallas; se guarda 60 s en memoria (ver CacheConfig)
    @Cacheable(CacheConfig.CATEGORIAS)
    @Transactional(readOnly = true)
    public List<CategoriaResponse> listar() {
        return categoriaRepository.findAllByOrderByNombreAsc().stream()
                .map(CategoriaResponse::from)
                .toList();
    }
}
