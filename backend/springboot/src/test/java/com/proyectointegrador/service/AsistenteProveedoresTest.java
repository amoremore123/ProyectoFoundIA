package com.proyectointegrador.service;

import com.proyectointegrador.repository.CategoriaRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.repository.ReclamoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class AsistenteProveedoresTest {
    private final AsistenteMotorLocal local = mock(AsistenteMotorLocal.class);
    private final ClaudeAsistenteClient claude = mock(ClaudeAsistenteClient.class);
    private final AsistenteIaValidator validador = mock(AsistenteIaValidator.class);
    private final InterpretadorIaCompatible compatible = mock(InterpretadorIaCompatible.class);
    private final CategoriaRepository categorias = mock(CategoriaRepository.class);
    private final InterpretacionAsistente interpretacion = new InterpretacionAsistente(
            "buscar", "ENCONTRADO", null, null, "biblioteca", List.of("celular"), List.of());
    private AsistenteService servicio;

    @BeforeEach
    void preparar() {
        when(categorias.findAllByOrderByNombreAsc()).thenReturn(List.of());
        servicio = new AsistenteService(local, claude, validador, categorias,
                mock(ObjetoRepository.class), mock(ReclamoRepository.class), compatible);
    }

    @Test
    void conservaProveedorCompatibleSinLlamarAClaude() {
        when(compatible.intentarInterpretar("celular", List.of())).thenReturn(Optional.of(interpretacion));
        var r = servicio.interpretar("celular", null);
        assertThat(r.fuente()).isEqualTo("ia");
        assertThat(r.intencion()).isEqualTo("buscar");
        assertThat(r.datosPropios()).isNull();
        verifyNoInteractions(claude, local);
    }

    @Test
    void conservaClaudeCuandoNoHayInterpretacionCompatible() {
        var cruda = new IaInterpretacion("buscar", "ENCONTRADO", null, "", List.of("celular"), List.of());
        when(compatible.intentarInterpretar("celular", List.of())).thenReturn(Optional.empty());
        when(claude.interpretar("celular", List.of())).thenReturn(Optional.of(cruda));
        when(validador.validar(cruda, List.of())).thenReturn(Optional.of(interpretacion));
        assertThat(servicio.interpretar("celular", null).fuente()).isEqualTo("claude");
        verifyNoInteractions(local);
    }

    @Test
    void vuelveAlMotorLocalCuandoAmbosProveedoresNoResponden() {
        when(compatible.intentarInterpretar("celular", List.of())).thenReturn(Optional.empty());
        when(claude.interpretar("celular", List.of())).thenReturn(Optional.empty());
        when(local.interpretar("celular", List.of())).thenReturn(interpretacion);
        assertThat(servicio.interpretar("celular", null).fuente()).isEqualTo("local");
    }
}
