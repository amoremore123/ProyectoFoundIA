package com.proyectointegrador.service;

import com.proyectointegrador.exception.AsistenteNoDisponibleException;
import com.proyectointegrador.exception.LimiteAsistenteException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.client.ResourceAccessException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("Fase 6 - Voz natural de FINDI cuidando el cupo gratuito")
class VozServiceTest {

    private static final byte[] AUDIO = {9, 8, 7};
    private ClienteVoz cliente;
    private final Clock reloj = Clock.fixed(Instant.parse("2026-11-10T23:00:00Z"), ZoneOffset.UTC);

    private VozService servicio(int porMinuto, int caracteresDia) {
        return new VozService(cliente, new LimitadorAsistente(porMinuto), caracteresDia, reloj);
    }

    @BeforeEach
    void setUp() {
        cliente = mock(ClienteVoz.class);
        when(cliente.configurado()).thenReturn(true);
        when(cliente.sintetizar(anyString())).thenReturn(AUDIO);
    }

    @Test
    @DisplayName("Las frases repetidas salen de la caché y no gastan caracteres ni cupo")
    void cache() {
        VozService voz = servicio(1, 100);
        assertThat(voz.sintetizar("¡Hola! Soy FINDI.", "ip:1")).isEqualTo(AUDIO);
        assertThat(voz.sintetizar("¡Hola! Soy FINDI.", "ip:1")).isEqualTo(AUDIO);
        verify(cliente, times(1)).sintetizar(anyString());
    }

    @Test
    @DisplayName("Las solicitudes simultáneas comparten audio sin gastar dos veces el cupo")
    void cacheConcurrente() throws Exception {
        VozService voz = servicio(1, 4);
        CountDownLatch generando = new CountDownLatch(1);
        CountDownLatch terminar = new CountDownLatch(1);
        CountDownLatch segundaIniciada = new CountDownLatch(1);
        when(cliente.sintetizar("hola")).thenAnswer(invocacion -> {
            generando.countDown();
            if (!terminar.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Prueba sin liberar");
            return AUDIO;
        });
        var ejecutor = Executors.newFixedThreadPool(2);
        try {
            var primera = ejecutor.submit(() -> voz.sintetizar("hola", "ip:1"));
            assertThat(generando.await(2, TimeUnit.SECONDS)).isTrue();
            var segunda = ejecutor.submit(() -> {
                segundaIniciada.countDown();
                return voz.sintetizar("hola", "ip:1");
            });
            assertThat(segundaIniciada.await(2, TimeUnit.SECONDS)).isTrue();
            // Debe esperar el audio; una reserva duplicada agotaría los límites y fallaría.
            assertThatThrownBy(() -> segunda.get(200, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            terminar.countDown();
            assertThat(primera.get(2, TimeUnit.SECONDS)).isEqualTo(AUDIO);
            assertThat(segunda.get(2, TimeUnit.SECONDS)).isEqualTo(AUDIO);
            verify(cliente, times(1)).sintetizar("hola");
        } finally {
            terminar.countDown();
            ejecutor.shutdownNow();
        }
    }

    @Test
    @DisplayName("El tope diario de caracteres corta el uso hasta la medianoche")
    void topeDiario() {
        VozService voz = servicio(50, 20);
        voz.sintetizar("abcdefghij", "ip:1");
        assertThatThrownBy(() -> voz.sintetizar("otro texto largo", "ip:1"))
                .isInstanceOfSatisfying(LimiteAsistenteException.class,
                        e -> assertThat(e.getSegundosRestantes()).isEqualTo(3600));
    }

    @Test
    @DisplayName("Superar el límite por minuto se rechaza sin llamar a ElevenLabs")
    void limitePorMinuto() {
        VozService voz = servicio(1, 1000);
        voz.sintetizar("uno", "ip:1");
        assertThatThrownBy(() -> voz.sintetizar("dos", "ip:1")).isInstanceOf(LimiteAsistenteException.class);
        verify(cliente, times(1)).sintetizar(anyString());
    }

    @Test
    @DisplayName("Si ElevenLabs falla, responde no disponible y devuelve los caracteres reservados")
    void falla() {
        VozService voz = servicio(50, 10);
        when(cliente.sintetizar(anyString())).thenThrow(new ResourceAccessException("timeout"));
        assertThatThrownBy(() -> voz.sintetizar("abcdefghij", "ip:1"))
                .isInstanceOf(AsistenteNoDisponibleException.class)
                .hasMessageContaining("voz del navegador");

        doReturn(AUDIO).when(cliente).sintetizar(anyString());
        assertThat(voz.sintetizar("abcdefghij", "ip:1")).isEqualTo(AUDIO);
    }

    @Test
    @DisplayName("Sin clave o sin voz configurada no se llama a ElevenLabs")
    void sinConfigurar() {
        when(cliente.configurado()).thenReturn(false);
        assertThatThrownBy(() -> servicio(5, 100).sintetizar("hola", "ip:1")).isInstanceOf(AsistenteNoDisponibleException.class);
        verify(cliente, never()).sintetizar(anyString());
    }

    @Test
    @DisplayName("No se envían datos personales a ElevenLabs")
    void limpia() {
        servicio(5, 500).sintetizar("Escríbele a ana@tecsup.edu.pe o al 987654321", "ip:1");
        ArgumentCaptor<String> enviado = ArgumentCaptor.forClass(String.class);
        verify(cliente).sintetizar(enviado.capture());
        assertThat(enviado.getValue()).doesNotContain("ana@tecsup.edu.pe", "987654321");
    }
}
