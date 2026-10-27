package com.proyectointegrador.controller;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Ejecución explícita: -Dtest=DescartarCoincidenciaMySqlQa; no entra en el verify H2. */
class DescartarCoincidenciaMySqlQa extends DescartarCoincidenciaIntegrationTest {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry properties) {
        MySqlQaProperties.registrar(properties);
    }
}
