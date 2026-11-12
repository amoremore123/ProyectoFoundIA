package com.proyectointegrador.movil.data.remote

import com.proyectointegrador.movil.model.ResultadoRetiro
import com.proyectointegrador.movil.model.RetiroCustodia
import retrofit2.http.Body
import retrofit2.http.POST

interface BibliotecaApi {
    @POST("api/admin/reclamos/retirar/")
    suspend fun retirar(@Body retiro: RetiroCustodia): ResultadoRetiro
}
