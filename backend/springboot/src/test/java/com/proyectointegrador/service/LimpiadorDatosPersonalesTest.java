package com.proyectointegrador.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Fase 5 - Limpieza de datos personales antes de usar la IA")
class LimpiadorDatosPersonalesTest {

    @Test
    @DisplayName("Quita correos, celulares, DNI y códigos de alumno")
    void quitaDatos() {
        String limpio = LimpiadorDatosPersonales.limpiar(
                "Soy U20261234, mi DNI es 71234567, llámame al +51 987-654-321 o a ana.perez@tecsup.edu.pe");
        assertThat(limpio).doesNotContain("U20261234", "71234567", "987-654-321", "ana.perez@tecsup.edu.pe");
        assertThat(limpio).contains(LimpiadorDatosPersonales.OMITIDO);
    }

    @Test
    @DisplayName("Conserva lo que sirve para buscar: objeto, color, aula y fecha")
    void conservaLoUtil() {
        String frase = "Perdí mi casaca azul en el aula 204 el 3 de octubre";
        assertThat(LimpiadorDatosPersonales.limpiar(frase)).isEqualTo(frase);
    }
}
