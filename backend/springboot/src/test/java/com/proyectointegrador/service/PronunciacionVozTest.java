package com.proyectointegrador.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fase 6 - Pronunciación de nombres propios en la voz de FINDI")
class PronunciacionVozTest {

    @Test
    @DisplayName("Tecsup se envía como Tec-sup en cualquier combinación de mayúsculas")
    void tecsup() {
        assertThat(PronunciacionVoz.paraVoz("Objetos en Tecsup, TECSUP y tecsup."))
                .isEqualTo("Objetos en Tec-sup, Tec-sup y Tec-sup.");
    }

    @Test
    @DisplayName("No cambia palabras que solo contienen el nombre ni el resto del texto")
    void soloPalabraCompleta() {
        assertThat(PronunciacionVoz.paraVoz("tecsupino ¿En qué te ayudo hoy?")).isEqualTo("tecsupino ¿En qué te ayudo hoy?");
        assertThat(PronunciacionVoz.paraVoz(null)).isEmpty();
    }
}
