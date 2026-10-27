package com.proyectointegrador.movil.ui.viewmodel

import com.proyectointegrador.movil.data.repository.*
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.domain.Lectura
import kotlinx.coroutines.flow.*
import java.text.SimpleDateFormat
import java.util.*

data class FormPublicacion(val tipo:String="PERDIDO",val nombre:String="",val descripcion:String="",val categoriaId:Long?=null,
    val ubicacion:String="",val fecha:String="",val foto:String?=null,val paso:Int=0,val publicadoId:Long?=null,val errores:Map<String,String> = emptyMap())
class PublicarViewModel(private val cuenta:CuentaRepository,publicaciones:PublicacionesRepository,private val fotos:FotografiasRepository?=null):CargaViewModel<Lectura<List<Categoria>>>({publicaciones.categorias()}) {
    private val _form=MutableStateFlow(FormPublicacion());val form=_form.asStateFlow()
    fun paso(valor:Int){if(!accion.value.ocupada)_form.value=_form.value.copy(paso=valor.coerceIn(0,5))}
    fun foto(uri:String?){if(!accion.value.ocupada&&_form.value.publicadoId==null)_form.value=_form.value.copy(foto=uri)}
    fun terminarSinFoto(){_form.value.publicadoId?.let {emitirEvento(it.toString())}}
    fun editar(campo:String,valor:String){if(accion.value.ocupada||_form.value.publicadoId!=null)return;_form.value=when(campo){
        "tipo"->_form.value.copy(tipo=valor);"nombre"->_form.value.copy(nombre=valor);"descripcion"->_form.value.copy(descripcion=valor)
        "ubicacion"->_form.value.copy(ubicacion=valor);"fecha"->_form.value.copy(fecha=valor);else->_form.value
    }.copy(errores=emptyMap())}
    fun categoria(id:Long){_form.value=_form.value.copy(categoriaId=id,errores=emptyMap())}
    fun publicar(){val f=_form.value;val errores=mutableMapOf<String,String>()
        if(f.nombre.isBlank() || f.nombre.length>150)errores["nombre"]="Ingresa un nombre de hasta 150 caracteres."
        if(f.descripcion.isBlank() || f.descripcion.length>16000)errores["descripcion"]="Ingresa una descripción válida."
        if(f.categoriaId==null || estado.value.datos?.datos?.none {it.id==f.categoriaId && it.estado}!=false)errores["categoria"]="Selecciona una categoría disponible."
        if(f.ubicacion.isBlank() || f.ubicacion.length>255)errores["ubicacion"]="Ingresa una ubicación de hasta 255 caracteres."
        val formato=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply {isLenient=false}
        val fecha=runCatching {if(!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(f.fecha))null else formato.parse(f.fecha)}.getOrNull()
        val hoy=formato.parse(formato.format(Date()))!!
        if(fecha==null || fecha.after(hoy))errores["fecha"]="Ingresa una fecha válida que no sea futura."
        if(f.tipo !in listOf("PERDIDO","ENCONTRADO"))errores["tipo"]="Selecciona el tipo de objeto."
        _form.value=f.copy(errores=errores)
        if(errores.isNotEmpty()){rechazar("Revisa los campos señalados.");return}
        ejecutar {
            // Validar y leer la imagen antes de crear evita publicaciones por un archivo inválido.
            val foto=f.foto?.let {fotos?.preparar(it)?:throw com.proyectointegrador.movil.domain.ErrorDatos("No se pudo preparar la fotografía.")}
            val id=f.publicadoId?:cuenta.publicar(PublicacionRequest(f.categoriaId!!,f.nombre.trim(),f.descripcion.trim(),f.fecha,f.tipo,f.ubicacion.trim())).id
            _form.value=_form.value.copy(publicadoId=id)
            if(foto!=null)try {cuenta.subirFoto(id,foto)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){throw com.proyectointegrador.movil.domain.ErrorDatos("La publicación $id ya está creada. No se pudo subir la foto. Reintenta la foto o continúa sin ella; no se creará otra publicación.")}
            _form.value=FormPublicacion();informar("Publicación guardada en el servidor.");emitirEvento(id.toString())
        }
    }
}
