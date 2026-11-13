package com.proyectointegrador.service;

import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.CategoriaRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.repository.ReclamoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("H47 - FINDI devuelve tarjetas y acciones de un catálogo cerrado, sin ejecutarlas")
class AsistenteTarjetasTest {

    private final AsistenteMotorLocal local = mock(AsistenteMotorLocal.class);
    private final InterpretadorIaCompatible compatible = mock(InterpretadorIaCompatible.class);
    private final ClaudeAsistenteClient claude = mock(ClaudeAsistenteClient.class);
    private final CategoriaRepository categorias = mock(CategoriaRepository.class);
    private final ObjetoRepository objetos = mock(ObjetoRepository.class);
    private final ReclamoRepository reclamos = mock(ReclamoRepository.class);
    private AsistenteService servicio;

    private static InterpretacionAsistente lectura(String intencion, String tipoBuscado, Long categoriaId, String ubicacion) {
        return new InterpretacionAsistente(intencion, tipoBuscado, categoriaId, categoriaId == null ? null : "Mochila",
                ubicacion, List.of(), List.of());
    }

    private static Objeto objeto(long id, TipoObjeto tipo, EstadoObjeto estado, long autorId) {
        Usuario autor = new Usuario();
        autor.setId(autorId);
        Objeto o = new Objeto();
        o.setId(id);
        o.setNombre("Mochila azul");
        o.setTipo(tipo);
        o.setEstado(estado);
        o.setUbicacion("Biblioteca");
        o.setUsuario(autor);
        return o;
    }

    @BeforeEach
    void preparar() {
        when(categorias.findAllByOrderByNombreAsc()).thenReturn(List.of());
        when(compatible.intentarInterpretar(anyString(), any())).thenReturn(Optional.empty());
        when(claude.interpretar(anyString(), any())).thenReturn(Optional.empty());
        servicio = new AsistenteService(local, claude, mock(AsistenteIaValidator.class), categorias, objetos, reclamos, compatible);
    }

    @Test
    void publicarProponeAbrirElFormularioPrellenadoYPideConfirmacion() {
        when(local.interpretar(anyString(), any())).thenReturn(lectura("publicar", "ENCONTRADO", 3L, "biblioteca"));
        var r = servicio.interpretar("quiero publicar que perdí mi mochila en la biblioteca", null);
        assertThat(r.acciones()).hasSize(1);
        var accion = r.acciones().get(0);
        assertThat(accion.tipo()).isEqualTo(AsistenteCatalogo.ACCION_PRELLENAR_PUBLICAR);
        assertThat(accion.requiereConfirmacion()).isTrue();
        assertThat(accion.destino()).isEqualTo("/publicar");
        // Quien perdió algo publica un PERDIDO
        assertThat(accion.datos()).containsEntry("tipo", "PERDIDO").containsEntry("categoriaId", 3L).containsEntry("ubicacion", "biblioteca");
        assertThat(AsistenteCatalogo.ACCIONES).contains(accion.tipo());
    }

    @Test
    void unaReferenciaAUnObjetoVisibleDevuelveSuTarjetaSinDatosDelAutor() {
        when(local.interpretar(anyString(), any())).thenReturn(lectura("buscar", "", null, ""));
        when(objetos.findById(12L)).thenReturn(Optional.of(objeto(12, TipoObjeto.ENCONTRADO, EstadoObjeto.EN_CUSTODIA, 7)));
        var r = servicio.interpretar("¿la mochila #12 sigue en la biblioteca?", null);
        assertThat(r.tarjetas()).hasSize(1);
        var t = r.tarjetas().get(0);
        assertThat(t.tipo()).isEqualTo(AsistenteCatalogo.TARJETA_OBJETO);
        assertThat(t.enlace()).isEqualTo("/objeto/12");
        assertThat(t.datos()).containsEntry("estado", "EN_CUSTODIA").doesNotContainKeys("autor", "correo", "usuario");
        // Sin sesión no se propone reclamar
        assertThat(r.acciones()).isNull();
    }

    @Test
    void proponeReclamarSoloObjetosEncontradosAjenos() {
        when(local.interpretar(anyString(), any())).thenReturn(lectura("buscar", "", null, ""));
        when(objetos.findById(12L)).thenReturn(Optional.of(objeto(12, TipoObjeto.ENCONTRADO, EstadoObjeto.ACTIVO, 7)));
        var ajeno = servicio.interpretar("creo que la #12 es mía", 5L);
        assertThat(ajeno.acciones()).extracting(a -> a.tipo()).containsExactly(AsistenteCatalogo.ACCION_ABRIR_RECLAMO);
        assertThat(ajeno.acciones().get(0).datos()).containsEntry("objetoId", 12L);

        var propio = servicio.interpretar("creo que la #12 es mía", 7L);
        assertThat(propio.acciones()).isNull();

        when(objetos.findById(13L)).thenReturn(Optional.of(objeto(13, TipoObjeto.PERDIDO, EstadoObjeto.ACTIVO, 7)));
        assertThat(servicio.interpretar("la #13", 5L).acciones()).isNull();
    }

    @Test
    void noMuestraPublicacionesOcultasEliminadasNiInexistentes() {
        when(local.interpretar(anyString(), any())).thenReturn(lectura("buscar", "", null, ""));
        when(objetos.findById(20L)).thenReturn(Optional.of(objeto(20, TipoObjeto.ENCONTRADO, EstadoObjeto.OCULTO, 7)));
        when(objetos.findById(21L)).thenReturn(Optional.empty());
        assertThat(servicio.interpretar("ver #20", 5L).tarjetas()).isNull();
        assertThat(servicio.interpretar("ver #21", 5L).tarjetas()).isNull();
    }

    @Test
    void conSesionMuestraTarjetasDeReclamosYQrSoloComoCifras() {
        when(local.interpretar(anyString(), any())).thenReturn(lectura("ayuda", "", null, ""));
        when(reclamos.countByUsuarioId(5L)).thenReturn(3L);
        when(reclamos.countByUsuarioIdAndEstado(5L, "PENDIENTE")).thenReturn(1L);
        when(reclamos.countByUsuarioIdAndEstado(5L, "ACEPTADO")).thenReturn(2L);
        when(reclamos.countQrRetiroVigentes(eq(5L), any())).thenReturn(1L);
        var r = servicio.interpretar("¿cómo van mis reclamos y mi QR?", 5L);
        assertThat(r.tarjetas()).extracting(t -> t.tipo())
                .containsExactly(AsistenteCatalogo.TARJETA_RECLAMOS, AsistenteCatalogo.TARJETA_QR);
        assertThat(r.tarjetas().get(0).datos()).containsEntry("pendientes", 1L).containsEntry("aceptados", 2L);
        assertThat(r.tarjetas().get(1).datos()).containsEntry("vigentes", 1L);

        // Sin sesión no hay tarjetas propias
        assertThat(servicio.interpretar("¿cómo van mis reclamos?", null).tarjetas()).isNull();
        when(objetos.countByUsuarioId(anyLong())).thenReturn(0L);
    }
}
