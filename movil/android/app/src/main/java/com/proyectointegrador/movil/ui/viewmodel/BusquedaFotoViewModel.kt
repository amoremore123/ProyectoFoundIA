package com.proyectointegrador.movil.ui.viewmodel

import com.proyectointegrador.movil.data.repository.AsistenteRepository
import com.proyectointegrador.movil.data.repository.FotografiasRepository
import com.proyectointegrador.movil.domain.ErrorDatos
import com.proyectointegrador.movil.domain.ResultadoFoto
import com.proyectointegrador.movil.domain.textoDeBusquedaPorFoto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Buscar con una foto: la IA describe el objeto y con eso se busca entre las publicaciones reales. La foto no se guarda. */
class BusquedaFotoViewModel(
    private val asistente: AsistenteRepository,
    private val fotos: FotografiasRepository,
) : AccionViewModel() {
    private val _resultado = MutableStateFlow<ResultadoFoto?>(null)
    val resultado = _resultado.asStateFlow()

    fun buscar(uri: String) {
        ejecutar {
            val imagen = fotos.prepararParaBusqueda(uri)
            val r = try { asistente.buscarPorFoto(imagen) } catch (e: ErrorDatos) {
                throw if (e.estadoHttp == 503) ErrorDatos("La búsqueda por foto no está disponible ahora. Describe el objeto con palabras.", 503)
                else if (e.estadoHttp == 429) ErrorDatos("Hiciste muchas búsquedas por foto seguidas. Espera un momento o escribe lo que buscas.", 429)
                else e
            }
            val texto = textoDeBusquedaPorFoto(r)
            if (texto == null) {
                informar("No se reconoció un objeto en la foto. Prueba con otra foto o escribe lo que buscas.")
            } else {
                informar("Buscamos «$texto» según tu foto.")
                _resultado.value = ResultadoFoto(texto, r.categoria?.id, r.descripcion.orEmpty())
            }
        }
    }

    fun consumirResultado() { _resultado.value = null }
}
