package com.proyectointegrador.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.proyectointegrador.entity.CanalAviso;

/**
 * H68: canal de cada tipo de aviso. En el PUT cada clave es opcional (la que no llega conserva lo
 * guardado); una clave que no existe es un error, para no ignorar en silencio un nombre mal escrito.
 */
public record CanalesAvisos(CanalAviso coincidencias, CanalAviso contactos, CanalAviso reclamos,
                            CanalAviso recordatorios, CanalAviso mensajes, CanalAviso sistema) {

    /**
     * La aplicación ignora en general los campos desconocidos; aquí no, porque «chat» en vez de
     * «mensajes» dejaría el canal sin cambiar sin avisar. Jackson lo convierte en un 400.
     */
    @JsonAnySetter
    void claveDesconocida(String clave, Object valor) {
        throw new IllegalArgumentException("canales no admite la clave " + clave);
    }
}
