package com.proyectointegrador.controller;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Ejecución explícita: solo servidor QA nuevo con token de guarda. */
class ReportarPublicacionMySqlQa extends ReportarPublicacionIntegrationTest {
    @DynamicPropertySource
    static void mysql(DynamicPropertyRegistry propiedades) {
        MySqlQaProperties.registrar(propiedades);
    }
}
