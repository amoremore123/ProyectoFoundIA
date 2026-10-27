package com.proyectointegrador.controller;
import org.springframework.test.context.*;
class PreferenciasMySqlQa extends PreferenciasIntegrationTest {
    @DynamicPropertySource static void mysql(DynamicPropertyRegistry p){MySqlQaProperties.registrar(p);}
}
