package com.proyectointegrador.movil.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {

    /** En el emulador, el host de la maquina es 10.0.2.2. */
    val BASE_URL: String = com.proyectointegrador.movil.BuildConfig.API_URL

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { cadena ->
                val solicitud=cadena.request()
                val token=Sesion.actual.takeUnless { solicitud.url.encodedPath.startsWith("/api/auth/") || solicitud.url.encodedPath.startsWith("/api/legal/") }
                val respuesta=cadena.proceed(solicitud.newBuilder().apply {
                    if(token != null) header("Authorization","Bearer $token")
                }.build())
                if(respuesta.code == 401 && token != null) Sesion.cerrar(token)
                respuesta
            }
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    val api: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }

    /** Convierte rutas relativas ("/uploads/...") a URL absolutas. */
    fun urlAbsoluta(url: String?): String? {
        if (url.isNullOrBlank()) return null
        if (url.startsWith("http://") || url.startsWith("https://")) return url
        return BASE_URL.trimEnd('/') + if (url.startsWith("/")) url else "/$url"
    }
}
