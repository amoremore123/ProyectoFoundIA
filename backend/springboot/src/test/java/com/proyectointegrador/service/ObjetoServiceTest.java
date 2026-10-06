package com.proyectointegrador.service;

import com.proyectointegrador.dto.ObjetoRequest;
import com.proyectointegrador.dto.ObjetoResponse;
import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.CategoriaRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("H1 - Creación de publicaciones")
class ObjetoServiceTest {

    @Mock
    private ObjetoRepository objetoRepository;

    @Mock
    private CategoriaRepository categoriaRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @InjectMocks
    private ObjetoService objetoService;

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNombre("Ana");
        usuario.setApellido("Pérez");
        usuario.setCorreo("ana@foundia.dev");
        return usuario;
    }

    private Categoria categoria() {
        Categoria categoria = new Categoria();
        categoria.setId(3L);
        categoria.setNombre("Mochila");
        return categoria;
    }

    private ObjetoRequest request() {
        return new ObjetoRequest(
                3L,
                "Mochila negra",
                "Mochila con libros de cálculo",
                LocalDate.of(2026, 10, 2),
                TipoObjeto.ENCONTRADO,
                "Comedor principal",
                new BigDecimal("19.4326100"),
                new BigDecimal("-99.1332000"));
    }

    private Objeto objetoDeCategoria(Long categoriaId, String nombre) {
        Categoria cat = new Categoria();
        cat.setId(categoriaId);
        cat.setNombre("Mochila");
        Objeto objeto = new Objeto();
        objeto.setId(10L);
        objeto.setNombre(nombre);
        objeto.setDescripcion("Descripción de prueba");
        objeto.setUbicacion("Comedor principal");
        objeto.setFechaObjeto(LocalDate.of(2026, 10, 2));
        objeto.setTipo(TipoObjeto.ENCONTRADO);
        objeto.setEstado(EstadoObjeto.ACTIVO);
        objeto.setFechaPublicacion(LocalDateTime.of(2026, 10, 2, 12, 0));
        objeto.setCategoria(cat);
        objeto.setUsuario(usuario());
        return objeto;
    }

    @Test
    @DisplayName("Crea la publicación con los datos del formulario")
    void crearPublicacion() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario()));
        when(categoriaRepository.findById(3L)).thenReturn(Optional.of(categoria()));

        ObjetoResponse respuesta = objetoService.crear(request(), 1L);

        ArgumentCaptor<Objeto> captor = ArgumentCaptor.forClass(Objeto.class);
        verify(objetoRepository).save(captor.capture());
        Objeto guardado = captor.getValue();

        assertThat(guardado.getNombre()).isEqualTo("Mochila negra");
        assertThat(guardado.getDescripcion()).isEqualTo("Mochila con libros de cálculo");
        assertThat(guardado.getFechaObjeto()).isEqualTo(LocalDate.of(2026, 10, 2));
        assertThat(guardado.getTipo()).isEqualTo(TipoObjeto.ENCONTRADO);
        assertThat(guardado.getEstado().name()).isEqualTo("ACTIVO");
        assertThat(guardado.getUsuario().getId()).isEqualTo(1L);
        assertThat(guardado.getCategoria().getId()).isEqualTo(3L);

        assertThat(respuesta.nombre()).isEqualTo("Mochila negra");
        assertThat(respuesta.estado()).isEqualTo("ACTIVO");
        assertThat(respuesta.categoria().nombre()).isEqualTo("Mochila");
        assertThat(respuesta.publicadoPor().nombre()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Guarda la ubicación y las coordenadas del objeto")
    void crearPublicacionConUbicacion() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario()));
        when(categoriaRepository.findById(3L)).thenReturn(Optional.of(categoria()));

        objetoService.crear(request(), 1L);

        ArgumentCaptor<Objeto> captor = ArgumentCaptor.forClass(Objeto.class);
        verify(objetoRepository).save(captor.capture());
        Objeto guardado = captor.getValue();

        assertThat(guardado.getUbicacion()).isEqualTo("Comedor principal");
        assertThat(guardado.getLatitud()).isEqualByComparingTo(new BigDecimal("19.4326100"));
        assertThat(guardado.getLongitud()).isEqualByComparingTo(new BigDecimal("-99.1332000"));
    }

    @Test
    @DisplayName("H7 - Filtra los objetos por categoría")
    void listarPorCategoria() {
        when(categoriaRepository.existsById(3L)).thenReturn(true);
        when(objetoRepository.findByCategoriaIdAndEstadoNotOrderByFechaPublicacionDesc(3L, EstadoObjeto.ELIMINADO))
                .thenReturn(List.of(objetoDeCategoria(3L, "Mochila negra")));

        List<ObjetoResponse> resultado = objetoService.porCategoria(3L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).categoria().id()).isEqualTo(3L);
        assertThat(resultado.get(0).categoria().nombre()).isEqualTo("Mochila");
        verify(objetoRepository).findByCategoriaIdAndEstadoNotOrderByFechaPublicacionDesc(3L, EstadoObjeto.ELIMINADO);
    }

    @Test
    @DisplayName("H7 - El listado con filtro de categoría usa la búsqueda del repositorio")
    void listarFiltrandoPorCategoria() {
        when(objetoRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(objetoDeCategoria(3L, "Mochila negra")));

        List<ObjetoResponse> resultado = objetoService.listar(null, null, 3L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).categoria().id()).isEqualTo(3L);
        verify(objetoRepository).findAll(any(Specification.class), any(Sort.class));
    }

    @Test
    @DisplayName("H7 - Categoría inexistente en el filtro -> no consulta los objetos")
    void porCategoriaInexistente() {
        when(categoriaRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> objetoService.porCategoria(99L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(objetoRepository, never()).findByCategoriaIdAndEstadoNotOrderByFechaPublicacionDesc(any(), any());
    }

    @Test
    @DisplayName("No crea la publicación si la categoría no existe")
    void categoriaInexistente() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.of(usuario()));
        when(categoriaRepository.findById(3L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> objetoService.crear(request(), 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(objetoRepository, never()).save(any());
    }

    @Test
    @DisplayName("No crea la publicación si el usuario no existe")
    void usuarioInexistente() {
        when(usuarioRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> objetoService.crear(request(), 1L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(objetoRepository, never()).save(any());
    }
}
