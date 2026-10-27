package com.proyectointegrador.service;

import com.proyectointegrador.repository.EventoCoincidenciaRepository;
import com.proyectointegrador.exception.ResourceNotFoundException;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
public class AvisoCoincidenciaService {
    private final CoincidenciaService motor;
    private final EventoCoincidenciaRepository eventos;
    private final RegistroAvisoCoincidenciaService registro;

    public AvisoCoincidenciaService(CoincidenciaService motor,EventoCoincidenciaRepository eventos,RegistroAvisoCoincidenciaService registro) {
        this.motor=motor;this.eventos=eventos;this.registro=registro;
    }

    @TransactionalEventListener(phase=TransactionPhase.AFTER_COMMIT)
    @Async("avisosCoincidenciaExecutor")
    public void alGuardar(ObjetoGuardado evento) {
        try{procesar(evento.id());}
        catch(RuntimeException error){
            LoggerFactory.getLogger(AvisoCoincidenciaService.class).warn("El evento durable queda para reintento ({})",error.getClass().getSimpleName());
        }
    }

    public void reintentar(){procesar(null);}

    private void procesar(Long objetoId) {
        for(var evento:eventos.pendientes(objetoId,PageRequest.of(0,100))) {
            try {
                try {
                    for(var sugerencia:motor.listar(evento.getObjetoId())) {
                        String[] ids=sugerencia.coincidenciaId().split("-");
                        registro.registrar(Long.parseLong(ids[0]),Long.parseLong(ids[1]));
                    }
                } catch(ResourceNotFoundException retirado) {
                    // Una publicación retirada no concede acceso ni genera un aviso nuevo.
                }
                eventos.completar(evento.getId());
            } catch(RuntimeException error) {
                eventos.fallido(evento.getId(),error.getClass().getSimpleName());
                LoggerFactory.getLogger(AvisoCoincidenciaService.class).warn("Aviso pendiente de reintento, evento {} ({})",evento.getId(),error.getClass().getSimpleName());
            }
        }
    }
}
