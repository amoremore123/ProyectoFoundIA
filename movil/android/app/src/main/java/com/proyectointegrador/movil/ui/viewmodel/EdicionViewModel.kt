package com.proyectointegrador.movil.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.proyectointegrador.movil.data.repository.CuentaRepository
import com.proyectointegrador.movil.data.repository.PublicacionesRepository
import com.proyectointegrador.movil.domain.puedeEditarPublicacion
import com.proyectointegrador.movil.domain.validarPublicacion
import com.proyectointegrador.movil.model.Categoria
import com.proyectointegrador.movil.model.Objeto
import com.proyectointegrador.movil.model.PublicacionRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class FormEdicion(
    val nombre: String = "", val descripcion: String = "", val categoriaId: Long? = null, val ubicacion: String = "",
    val fecha: String = "", val tipo: String = "PERDIDO", val latitud: Double? = null, val longitud: Double? = null,
    val errores: Map<String, String> = emptyMap(), val iniciado: Boolean = false,
)

/** H5 en móvil: editar una publicación propia. La API y las reglas son las de la web. */
class EditarPublicacionViewModel(
    private val repo: CuentaRepository,
    publicaciones: PublicacionesRepository,
    private val id: Long,
) : CargaViewModel<Objeto>({ repo.objeto(id) }) {
    private val _form = MutableStateFlow(FormEdicion())
    val form = _form.asStateFlow()
    val categorias = publicaciones.observarCategorias().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList<Categoria>())

    init {
        viewModelScope.launch {
            try { publicaciones.categorias() } catch (e: CancellationException) { throw e } catch (_: Exception) { /* queda la caché local */ }
        }
    }

    /** Copia los datos del servidor al formulario, una sola vez. */
    fun iniciar(objeto: Objeto) {
        if (_form.value.iniciado) return
        _form.value = FormEdicion(
            nombre = objeto.nombre, descripcion = objeto.descripcion.orEmpty(), categoriaId = objeto.categoria?.id,
            ubicacion = objeto.ubicacion.orEmpty(), fecha = objeto.fechaObjeto.orEmpty(), tipo = objeto.tipo ?: "PERDIDO",
            latitud = objeto.latitud, longitud = objeto.longitud, iniciado = true,
        )
    }

    val editable get() = puedeEditarPublicacion(estado.value.datos?.estado)

    fun editar(campo: String, valor: String) {
        if (accion.value.ocupada) return
        val f = _form.value
        _form.value = when (campo) {
            "nombre" -> f.copy(nombre = valor)
            "descripcion" -> f.copy(descripcion = valor)
            // Escribir otro lugar descarta las coordenadas anteriores, como en la web.
            "ubicacion" -> f.copy(ubicacion = valor, latitud = null, longitud = null)
            "fecha" -> f.copy(fecha = valor)
            else -> f
        }.copy(errores = emptyMap())
    }

    fun categoria(idCategoria: Long) {
        if (!accion.value.ocupada) _form.value = _form.value.copy(categoriaId = idCategoria, errores = emptyMap())
    }

    fun guardar() {
        val f = _form.value
        if (!editable) { rechazar("Esta publicación ya no se puede editar."); return }
        val disponible = f.categoriaId != null && categorias.value.any { it.id == f.categoriaId && it.estado }
        val errores = validarPublicacion(f.nombre, f.descripcion, disponible, f.ubicacion, f.latitud, f.longitud, f.fecha, f.tipo)
        _form.value = f.copy(errores = errores)
        if (errores.isNotEmpty()) { rechazar("Revisa los campos señalados."); return }
        ejecutar {
            repo.editar(id, PublicacionRequest(f.categoriaId!!, f.nombre.trim(), f.descripcion.trim(), f.fecha, f.tipo, f.ubicacion.trim(), f.latitud, f.longitud))
            informar("Cambios guardados.")
            emitirEvento("guardado")
        }
    }
}
