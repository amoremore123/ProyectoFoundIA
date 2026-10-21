package com.proyectointegrador.service;

import com.proyectointegrador.dto.FotoResponse;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Foto;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.FotoRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class FotoService {

    private final ObjetoRepository objetoRepository;
    private final FotoRepository fotoRepository;
    private final AlmacenamientoImagenService almacenamiento;

    public FotoService(ObjetoRepository objetoRepository, FotoRepository fotoRepository,
                       AlmacenamientoImagenService almacenamiento) {
        this.objetoRepository = objetoRepository;
        this.fotoRepository = fotoRepository;
        this.almacenamiento = almacenamiento;
    }

    @Transactional
    public FotoResponse agregar(Long objetoId, Long usuarioId, MultipartFile archivo) {
        var objeto = objetoRepository.findById(objetoId)
                .filter(publicacion -> publicacion.getEstado() != EstadoObjeto.ELIMINADO)
                .orElseThrow(() -> new ResourceNotFoundException("Objeto no encontrado con id " + objetoId));
        if (!objeto.getUsuario().getId().equals(usuarioId)) {
            throw new AccessDeniedException("Solo el autor puede añadir fotos a esta publicación");
        }

        AlmacenamientoImagenService.ImagenGuardada imagen = almacenamiento.guardar(archivo);
        try {
            Foto foto = new Foto();
            foto.setObjeto(objeto);
            foto.setUrl(imagen.url());
            foto.setNombreArchivo(imagen.nombre());
            Foto guardada = fotoRepository.saveAndFlush(foto);
            objeto.getFotos().add(guardada);
            return new FotoResponse(guardada.getId(), guardada.getUrl());
        } catch (RuntimeException ex) {
            almacenamiento.eliminar(imagen.nombre());
            throw ex;
        }
    }
}
