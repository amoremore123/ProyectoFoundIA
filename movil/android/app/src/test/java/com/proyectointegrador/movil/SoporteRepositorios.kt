package com.proyectointegrador.movil

import com.proyectointegrador.movil.data.local.CachePublica
import com.proyectointegrador.movil.data.repository.*
import com.proyectointegrador.movil.domain.*
import com.proyectointegrador.movil.model.*
import kotlinx.coroutines.flow.*

class CachePrueba:CachePublica {
    val listas=mutableMapOf<String,Lectura<List<Objeto>>>()
    val detalles=mutableMapOf<Long,Lectura<Objeto>>()
    private val catalogo=MutableStateFlow<List<Categoria>>(emptyList())
    var invalidaciones=0
    override suspend fun lista(clave:String)=listas[clave]
    override suspend fun guardarLista(clave:String,datos:List<Objeto>){listas[clave]=Lectura(datos,true);datos.forEach {guardarDetalle(it)}}
    override suspend fun detalle(id:Long)=detalles[id]
    override suspend fun guardarDetalle(dato:Objeto){detalles[dato.id]=Lectura(dato,true)}
    override suspend fun categorias()=Lectura(catalogo.value,true)
    override suspend fun guardarCategorias(datos:List<Categoria>){catalogo.value=datos}
    override fun observarCategorias()=catalogo.asStateFlow()
    override suspend fun invalidarPublicaciones(){invalidaciones++;listas.clear();detalles.clear()}
    override suspend fun retirar(id:Long){detalles.remove(id);listas.clear()}
}
class SesionPrueba:AlmacenSesion {
    private val estado=MutableStateFlow(EstadoSesion(false))
    override val cambios:Flow<EstadoSesion> get()=estado
    override val revision get()=estado.value.revision
    override val autenticado get()=estado.value.autenticado
    override val aviso get()=estado.value.aviso
    override fun guardar(token:String){estado.value=EstadoSesion(true,revision=revision+1)}
    override fun cerrar(){estado.value=EstadoSesion(false,revision=revision+1)}
}
