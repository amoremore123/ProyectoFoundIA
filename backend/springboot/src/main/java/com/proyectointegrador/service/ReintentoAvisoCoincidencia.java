package com.proyectointegrador.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name="app.coincidencias.reintentos-habilitados",havingValue="true",matchIfMissing=true)
public class ReintentoAvisoCoincidencia {
    private final AvisoCoincidenciaService avisos;
    public ReintentoAvisoCoincidencia(AvisoCoincidenciaService avisos){this.avisos=avisos;}
    @Scheduled(fixedDelayString="${app.coincidencias.reintento-ms:60000}",initialDelayString="${app.coincidencias.reintento-ms:60000}")
    public void reintentar(){avisos.reintentar();}
}
