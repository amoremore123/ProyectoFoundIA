package com.proyectointegrador.service;

import com.proyectointegrador.dto.InterpretacionResponse;
import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.exception.AsistenteNoDisponibleException;
import com.proyectointegrador.exception.LimiteAsistenteException;
import com.proyectointegrador.repository.CategoriaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Fase 5 - FINDI interpreta con IA sin inventar ni filtrar datos")
class InterpretadorIaCompatibleTest {

    private ClienteIa cliente;
    private CategoriaRepository categorias;
    private InterpretadorIaCompatible servicio;

    private static Categoria categoria(long id, String nombre, boolean activa) {
        Categoria c = new Categoria();
        c.setId(id);
        c.setNombre(nombre);
        c.setEstado(activa);
        return c;
    }

    @BeforeEach
    void setUp() {
        cliente = mock(ClienteIa.class);
        categorias = mock(CategoriaRepository.class);
        when(cliente.configurado()).thenReturn(true);
        when(categorias.findAllByOrderByNombreAsc()).thenReturn(List.of(
                categoria(1, "Celular", true), categoria(3, "Mochila", true), categoria(9, "Antigua", false)));
        servicio = new InterpretadorIaCompatible(cliente, new LimitadorAsistente(50), categorias);
    }

    @Test
    void contratoCompartidoUsaRespaldoSiElProveedorFalla() {
        when(cliente.completarJson(anyString(), anyString())).thenThrow(new ResourceAccessException("timeout"));
        assertThat(servicio.intentarInterpretar("celular", List.of())).isEmpty();
    }

    @Test
    void contratoCompartidoNoDuplicaElLimiteNiLaConsultaDeCategorias() {
        when(cliente.completarJson(anyString(), anyString())).thenReturn("{\"intencion\":\"saludo\",\"palabras\":[]}");
        var limitado = new InterpretadorIaCompatible(cliente, new LimitadorAsistente(1), categorias);
        assertThat(limitado.intentarInterpretar("hola", List.of())).isPresent();
        assertThat(limitado.intentarInterpretar("hola", List.of())).isPresent();
        verify(categorias, never()).findAllByOrderByNombreAsc();
    }

    @Test
    @DisplayName("Una respuesta válida se devuelve con el nombre de la categoría tomado de la base")
    void respuestaValida() {
        when(cliente.completarJson(anyString(), anyString())).thenReturn("""
                {"intencion":"buscar","tipoBuscado":"encontrado","categoriaId":1,"ubicacion":"Biblioteca",
                 "palabras":["Celular","samsung","celular"],"colores":["azul"]}""");

        InterpretacionResponse r = servicio.interpretar("se me quedó el cel en la biblio", "ip:1");

        assertThat(r.intencion()).isEqualTo("buscar");
        assertThat(r.tipoBuscado()).isEqualTo("ENCONTRADO");
        assertThat(r.categoria().id()).isEqualTo(1L);
        assertThat(r.categoria().nombre()).isEqualTo("Celular");
        assertThat(r.ubicacion()).isEqualTo("biblioteca");
        assertThat(r.palabras()).containsExactly("celular", "samsung");
        assertThat(r.colores()).containsExactly("azul");
    }

    @Test
    @DisplayName("La situación de la persona se convierte en lo que hay que buscar")
    void situacionSeConvierteEnTipoBuscado() {
        // Quien perdió algo busca entre lo ENCONTRADO; quien encontró, entre lo PERDIDO
        when(cliente.completarJson(anyString(), anyString())).thenReturn(
                "{\"intencion\":\"buscar\",\"situacion\":\"perdió\",\"palabras\":[\"mochila\"]}",
                "{\"intencion\":\"buscar\",\"situacion\":\"encontro\",\"palabras\":[\"casaca\"]}",
                "{\"intencion\":\"buscar\",\"situacion\":\"no sé\",\"tipoBuscado\":\"PERDIDO\",\"palabras\":[\"x\"]}");
        assertThat(servicio.interpretar("busco mi mochila", "ip:1").tipoBuscado()).isEqualTo("ENCONTRADO");
        assertThat(servicio.interpretar("recogí una casaca", "ip:1").tipoBuscado()).isEqualTo("PERDIDO");
        // Una situación desconocida no se reemplaza por un tipoBuscado que la IA haya inventado
        assertThat(servicio.interpretar("algo raro", "ip:1").tipoBuscado()).isEmpty();
    }

    @Test
    @DisplayName("Las intenciones nuevas de la Fase 5 son aceptadas")
    void intencionesNuevas() {
        when(cliente.completarJson(anyString(), anyString())).thenReturn("{\"intencion\":\"mis_reportes\",\"palabras\":[]}");
        assertThat(servicio.interpretar("¿cómo van mis reportes?", "ip:1").intencion()).isEqualTo("mis_reportes");
    }

    @Test
    @DisplayName("Una categoría inexistente o desactivada no se acepta")
    void categoriaNoValida() {
        when(cliente.completarJson(anyString(), anyString()))
                .thenReturn("{\"intencion\":\"buscar\",\"categoriaId\":9,\"palabras\":[\"x\"]}");
        assertThat(servicio.interpretar("algo", "ip:1").categoria()).isNull();
    }

    @Test
    @DisplayName("Una intención desconocida o un texto que no es JSON se descarta")
    void respuestaInvalida() {
        when(cliente.completarJson(anyString(), anyString())).thenReturn("{\"intencion\":\"borrar_todo\"}");
        assertThatThrownBy(() -> servicio.interpretar("hola", "ip:1")).isInstanceOf(AsistenteNoDisponibleException.class);

        when(cliente.completarJson(anyString(), anyString())).thenReturn("Claro, aquí tienes:");
        assertThatThrownBy(() -> servicio.interpretar("hola", "ip:1")).isInstanceOf(AsistenteNoDisponibleException.class);
    }

    @Test
    @DisplayName("Si el servicio falla o tarda demasiado, se responde no disponible")
    void servicioCaido() {
        when(cliente.completarJson(anyString(), anyString())).thenThrow(new ResourceAccessException("Read timed out"));
        assertThatThrownBy(() -> servicio.interpretar("mochila", "ip:1"))
                .isInstanceOf(AsistenteNoDisponibleException.class)
                .hasMessageContaining("motor local");
    }

    @Test
    @DisplayName("Sin clave configurada no se llama a la IA")
    void sinClave() {
        when(cliente.configurado()).thenReturn(false);
        assertThatThrownBy(() -> servicio.interpretar("mochila", "ip:1")).isInstanceOf(AsistenteNoDisponibleException.class);
        verify(cliente, never()).completarJson(anyString(), anyString());
    }

    @Test
    @DisplayName("No se envían correos, teléfonos ni documentos a la IA externa")
    void limpiaDatosPersonales() {
        when(cliente.completarJson(anyString(), anyString())).thenReturn("{\"intencion\":\"buscar\",\"palabras\":[\"mochila\"]}");
        servicio.interpretar("Perdí mi mochila, escríbeme a ana@tecsup.edu.pe o al 987 654 321, DNI 71234567", "ip:1");

        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        verify(cliente).completarJson(anyString(), enviado.capture());
        assertThat(enviado.getValue()).contains("mochila")
                .doesNotContain("ana@tecsup.edu.pe", "987 654 321", "71234567");
    }

    @Test
    @DisplayName("Las instrucciones solo incluyen categorías activas")
    void instruccionesSoloActivas() {
        String texto = InterpretadorIaCompatible.instrucciones(List.of(categoria(1, "Celular", true)));
        assertThat(texto).contains("1=Celular").doesNotContain("Antigua");
    }

    @Test
    @DisplayName("Superar el límite por minuto se rechaza sin llamar a la IA")
    void limite() {
        InterpretadorIaCompatible limitado = new InterpretadorIaCompatible(cliente, new LimitadorAsistente(1), categorias);
        when(cliente.completarJson(anyString(), anyString())).thenReturn("{\"intencion\":\"saludo\",\"palabras\":[]}");
        limitado.interpretar("hola", "ip:2");
        assertThatThrownBy(() -> limitado.interpretar("hola otra vez", "ip:2")).isInstanceOf(LimiteAsistenteException.class);
    }
}
