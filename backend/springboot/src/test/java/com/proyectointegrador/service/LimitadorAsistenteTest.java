package com.proyectointegrador.service;

import com.proyectointegrador.exception.LimiteAsistenteException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("Fase 5 - Límite de consultas a FINDI")
class LimitadorAsistenteTest {

    @Test
    @DisplayName("Permite N consultas por minuto, informa cuánto esperar y se libera al pasar la ventana")
    void ventanaDeUnMinuto() {
        AtomicLong ahora = new AtomicLong(1_000_000);
        LimitadorAsistente limitador = new LimitadorAsistente(2, ahora::get);

        limitador.verificar("ip:1");
        ahora.addAndGet(10_000);
        limitador.verificar("ip:1");

        assertThatThrownBy(() -> limitador.verificar("ip:1"))
                .isInstanceOfSatisfying(LimiteAsistenteException.class,
                        e -> assertThat(e.getSegundosRestantes()).isEqualTo(50));
        // Otra persona tiene su propio cupo
        assertThatCode(() -> limitador.verificar("ip:2")).doesNotThrowAnyException();

        ahora.addAndGet(50_000);
        assertThatCode(() -> limitador.verificar("ip:1")).doesNotThrowAnyException();
    }
}
