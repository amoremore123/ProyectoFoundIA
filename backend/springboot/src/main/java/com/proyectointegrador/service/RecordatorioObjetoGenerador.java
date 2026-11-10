package com.proyectointegrador.service;

import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Notificacion;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.RecordatorioObjeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.repository.NotificacionRepository;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.repository.RecordatorioObjetoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * H53: genera el recordatorio de un solo objeto en su propia transacción.
 *
 * <p>Se separa del orquestador para que un objeto con problemas no revierta
 * los recordatorios de los demás. Antes de escribir se vuelve a comprobar el
 * estado del objeto, porque pudo recuperarse entre la consulta y este paso.</p>
 */
@Service
public class RecordatorioObjetoGenerador {

    public static final String TIPO_AVISO = "RECORDATORIO_OBJETO";

    private final ObjetoRepository objetos;
    private final RecordatorioObjetoRepository recordatorios;
    private final NotificacionRepository notificaciones;
    private final PreferenciaNotificacionService preferencias;

    public RecordatorioObjetoGenerador(ObjetoRepository objetos,
                                       RecordatorioObjetoRepository recordatorios,
                                       NotificacionRepository notificaciones,
                                       PreferenciaNotificacionService preferencias) {
        this.objetos = objetos;
        this.recordatorios = recordatorios;
        this.notificaciones = notificaciones;
        this.preferencias = preferencias;
    }

    /**
     * @return {@code true} si se creó la notificación y su registro de periodo
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean generar(Long objetoId, String periodo, LocalDateTime limite) {
        Objeto objeto = objetos.findById(objetoId).orElse(null);
        if (objeto == null
                || objeto.getTipo() != TipoObjeto.PERDIDO
                || objeto.getEstado() != EstadoObjeto.ACTIVO
                || objeto.getFechaRecuperacion() != null
                || objeto.getFechaPublicacion() == null
                || objeto.getFechaPublicacion().isAfter(limite)) {
            return false;
        }
        if (recordatorios.existsByObjetoIdAndPeriodo(objetoId, periodo)) {
            return false;
        }
        if (!preferencias.permite(objeto.getUsuario().getId(), TIPO_AVISO)) {
            return false;
        }

        Notificacion notificacion = new Notificacion();
        notificacion.setUsuario(objeto.getUsuario());
        notificacion.setTitulo("Tu objeto sigue sin resolver");
        notificacion.setMensaje("Tu publicación \"" + objeto.getNombre()
                + "\" (#" + objeto.getId() + ") sigue sin resolverse. "
                + "Si ya lo recuperaste, actualiza su estado.");
        notificacion.setTipo(TIPO_AVISO);
        notificacion.setEnlace("/objeto/" + objeto.getId());
        notificacion.setLeida(Boolean.FALSE);
        notificacion = notificaciones.save(notificacion);

        RecordatorioObjeto registro = new RecordatorioObjeto();
        registro.setObjeto(objeto);
        registro.setPeriodo(periodo);
        registro.setNotificacion(notificacion);
        recordatorios.saveAndFlush(registro);
        return true;
    }
}
