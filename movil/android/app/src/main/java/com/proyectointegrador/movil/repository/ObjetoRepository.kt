package com.proyectointegrador.movil.repository

import com.proyectointegrador.movil.model.Categoria
import com.proyectointegrador.movil.model.CoincidenciaSugerida
import com.proyectointegrador.movil.model.Objeto
import com.proyectointegrador.movil.network.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ObjetoRepository {

    private val api = RetrofitClient.api

    suspend fun listarObjetos(tipo: String? = null): Result<List<Objeto>> =
        runCatchingApi { api.listarObjetos(tipo = tipo) }

    suspend fun buscar(
        q: String?,
        tipo: String? = null
    ): Result<List<Objeto>> =
        runCatchingApi { api.buscarObjetos(q = q, tipo = tipo) }

    suspend fun detalle(id: Long): Result<Objeto> =
        runCatchingApi { api.objetoPorId(id) }

    suspend fun categorias(): Result<List<Categoria>> =
        runCatchingApi { api.categorias() }

    suspend fun coincidencias(id: Long): Result<List<CoincidenciaSugerida>> =
        runCatchingApi { api.coincidencias(id) }

    private suspend fun <T> runCatchingApi(block: suspend () -> T): Result<T> =
        try {
            Result.success(withContext(Dispatchers.IO) { block() })
        } catch (error: Throwable) {
            Result.failure(error)
        }
}
