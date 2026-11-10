package com.proyectointegrador.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * H53: ejecución programada de la detección de objetos sin resolver.
 *
 * <p>El intervalo y el retraso inicial son configurables para poder probar
 * el flujo sin esperar un día. La tarea no borra ni modifica publicaciones;
 * solo crea notificaciones in-app y su registro de periodo.</p>
 */
@Service
@ConditionalOnProperty(name = "app.recordatorios.habilitados", havingValue = "true", matchIfMissing = true)
public class RecordatorioObjetoScheduler {

    private final RecordatorioObjetoService servicio;

    public RecordatorioObjetoScheduler(RecordatorioObjetoService servicio) {
        this.servicio = servicio;
    }

    @Scheduled(fixedDelayString = "${app.recordatorios.intervalo-ms:86400000}",
            initialDelayString = "${app.recordatorios.retraso-inicial-ms:300000}")
    public void ejecutar() {
        servicio.ejecutar();
    }
}
