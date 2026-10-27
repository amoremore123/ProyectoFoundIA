package com.proyectointegrador.movil.data.repository

import com.google.gson.Gson
import com.proyectointegrador.movil.data.local.CachePublica
import com.proyectointegrador.movil.domain.Lectura
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.network.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

interface PublicacionesRepository {
    fun listar(tipo: String? = null): Flow<Lectura<List<Objeto>>>
    fun buscar(q: String?, tipo: String? = null): Flow<Lectura<List<Objeto>>>
    fun detalle(id: Long): Flow<Lectura<Objeto>>
    suspend fun categorias(): Lectura<List<Categoria>>
    fun observarCategorias(): Flow<List<Categoria>>
    suspend fun coincidencias(id: Long): List<CoincidenciaSugerida>
    suspend fun vigente(id: Long): Objeto
    suspend fun invalidar()
}

class ApiPublicacionesRepository(private val api: ApiService, private val cache: CachePublica) : PublicacionesRepository {
    private val mutex=Mutex()
    private var generacion=0L
    private suspend fun <T> copia(bloque:suspend()->T):T? = try {bloque()} catch(e:CancellationException){throw e} catch(_:Exception){null}
    private fun recuperable(e:Exception) = e is IOException || (e is HttpException && e.code()>=500)
    private fun <T> consultar(local:suspend()->Lectura<T>?, guardar:suspend(T)->Unit, remota:suspend()->T, retirado:suspend()->Unit = {}):Flow<Lectura<T>> = flow {
        val inicio=mutex.withLock {generacion}
        // Primero se valida con el servidor. El fallback solo existe para caída o error 5xx.
        val datos=try {remota()} catch(e:CancellationException){throw e} catch(e:Exception) {
            if(e is HttpException && e.code() in listOf(401,403,404)) mutex.withLock {copia {retirado()}}
            val respaldo=if(recuperable(e)) mutex.withLock {if(inicio==generacion)copia {local()} else null} else null
            if(respaldo!=null) {emit(respaldo.copy(desdeCache=true,aviso="Sin actualizar: mostrando una copia local. Conéctate y reintenta."));return@flow}
            throw e.comoErrorDatos()
        }
        mutex.withLock {
            if(inicio!=generacion)throw com.proyectointegrador.movil.domain.ErrorDatos("Los datos cambiaron. Actualiza la consulta.")
            copia {guardar(datos)}
        }
        emit(Lectura(datos))
    }.flowOn(Dispatchers.IO)
    private fun lista(clave:String, remota:suspend()->List<Objeto>) = consultar({cache.lista(clave)},{cache.guardarLista(clave,it)},remota,{cache.invalidarPublicaciones()})
    override fun listar(tipo:String?)=lista("lista:"+tipo.orEmpty()) {api.listarObjetos(tipo=tipo)}
    override fun buscar(q:String?,tipo:String?):Flow<Lectura<List<Objeto>>> {
        val consulta=q?.trim()?.ifBlank {null}
        val clave="buscar:"+java.security.MessageDigest.getInstance("SHA-256").digest(Gson().toJson(listOf(consulta,tipo)).toByteArray()).joinToString("") {"%02x".format(it)}
        return lista(clave) {api.buscarObjetos(q=consulta,tipo=tipo)}
    }
    override fun detalle(id:Long)=consultar({cache.detalle(id)},{cache.guardarDetalle(it)},{api.objetoPorId(id)},{cache.retirar(id)})
    override suspend fun categorias():Lectura<List<Categoria>> = consultar({cache.categorias()},{cache.guardarCategorias(it)},{api.categorias()}).first()
    override fun observarCategorias()=cache.observarCategorias()
    override suspend fun coincidencias(id:Long)=llamadaApi {api.coincidencias(id)}
    override suspend fun vigente(id:Long)=llamadaApi {api.objetoPorId(id)}
    override suspend fun invalidar() {mutex.withLock {generacion++;copia {cache.invalidarPublicaciones()}}}
}
