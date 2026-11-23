package com.proyectointegrador.service;

import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.Optional;

/**
 * H64: foto de perfil privada. El archivo vive en {@code perfiles/} y solo se entrega con
 * sesión a la propia cuenta. Al reemplazar o borrar, el archivo anterior se elimina solo
 * después de confirmar la base; si la transacción falla, se borra el archivo nuevo.
 */
@Service
public class FotoPerfilService {

    private final UsuarioRepository usuarios;
    private final AlmacenamientoImagenService almacenamiento;

    public FotoPerfilService(UsuarioRepository usuarios, AlmacenamientoImagenService almacenamiento) {
        this.usuarios = usuarios;
        this.almacenamiento = almacenamiento;
    }

    @Transactional
    public String subir(Long usuarioId, MultipartFile archivo) {
        Usuario usuario = bloquear(usuarioId);
        String nueva = almacenamiento.guardarPerfil(archivo);
        String anterior = usuario.getFotoPerfilUrl();
        usuario.setFotoPerfilUrl(nueva);
        usuarios.saveAndFlush(usuario);
        alTerminar(anterior, nueva);
        return com.proyectointegrador.dto.UsuarioResponse.urlFotoPerfil(nueva);
    }

    @Transactional
    public void eliminar(Long usuarioId) {
        Usuario usuario = bloquear(usuarioId);
        String anterior = usuario.getFotoPerfilUrl();
        if (anterior == null) {
            return;
        }
        usuario.setFotoPerfilUrl(null);
        usuarios.saveAndFlush(usuario);
        alTerminar(anterior, null);
    }

    @Transactional(readOnly = true)
    public Optional<Path> archivo(Long usuarioId) {
        return usuarios.findById(usuarioId)
                .map(Usuario::getFotoPerfilUrl)
                .flatMap(almacenamiento::archivoPerfil);
    }

    private Usuario bloquear(Long usuarioId) {
        return usuarios.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    /** Borra el archivo que sobra según termine la transacción: el anterior si confirma, el nuevo si no. */
    private void alTerminar(String anterior, String nueva) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                if (estado == STATUS_COMMITTED) {
                    almacenamiento.eliminarPerfil(anterior);
                } else {
                    almacenamiento.eliminarPerfil(nueva);
                }
            }
        });
    }
}
