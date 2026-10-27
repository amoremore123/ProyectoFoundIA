package com.proyectointegrador.movil
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.network.ApiService
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.junit.*
import org.junit.Assert.*
class ContratoApiTest {
    private val servidor=MockWebServer()
    private lateinit var api:ApiService
    @Before fun abrir() {servidor.start();api=Retrofit.Builder().baseUrl(servidor.url("/"))
        .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)}
    @After fun cerrar() {servidor.shutdown()}
    private fun respuesta(body:String) {servidor.enqueue(MockResponse().setHeader("Content-Type","application/json").setBody(body))}
    @Test fun registroEnviaSoloLaAceptacionExplicitaYVersion()=runBlocking {
        respuesta("{\"mensaje\":\"Código enviado\"}")
        api.registrar(Registro("Ana","QA","ana@qa.invalid","Segura123!",true,"version-visible"))
        val r=servidor.takeRequest();assertEquals("/api/auth/register",r.path)
        val cuerpo=r.body.readUtf8();assertTrue(cuerpo.contains("\"aceptaTerminos\":true"));assertTrue(cuerpo.contains("version-visible"))
        assertFalse(cuerpo.contains("usuarioId"));assertFalse(cuerpo.contains("fechaAceptacion"))
    }
    @Test fun descarteYRecuperacionMantienenLaMismaPareja()=runBlocking {
        respuesta("{\"id\":\"12-34\",\"estado\":\"DESCARTADA\",\"repetido\":false}")
        api.descartar("12-34");assertEquals("/api/coincidencias/12-34/descartar",servidor.takeRequest().path)
        respuesta("{\"id\":\"12-34\",\"estado\":\"ACEPTADA\",\"objetoPerdidoId\":12,\"estadoObjetoPerdido\":\"RECUPERADO\",\"fechaRecuperacion\":\"2026-10-26T12:00:00Z\",\"repetido\":false}")
        api.recuperar("12-34",ConfirmarRecuperacion())
        val r=servidor.takeRequest();assertEquals("/api/coincidencias/12-34/confirmar",r.path)
        assertEquals("{\"confirmarRecuperacion\":true}",r.body.readUtf8())
    }
    @Test fun preferenciasEnviaLosTresValoresSinCuentaAjena()=runBlocking {
        respuesta("{\"coincidencias\":false,\"contactos\":true,\"reclamos\":false}")
        assertFalse(api.guardarPreferencias(Preferencias(false,true,false)).coincidencias)
        val r=servidor.takeRequest();assertEquals("PUT",r.method)
        assertEquals("{\"coincidencias\":false,\"contactos\":true,\"reclamos\":false}",r.body.readUtf8())
    }
    @Test fun solicitudDeEliminacionNoIncluyeIdentidadNiFechaDelCliente()=runBlocking {
        respuesta("{\"id\":1,\"estado\":\"PENDIENTE\",\"fechaSolicitud\":\"2026-10-26T12:00:00Z\",\"repetido\":false,\"informacion\":\"Pendiente de revisión\"}")
        assertEquals("PENDIENTE",api.solicitarEliminacion(ConfirmarEliminacion()).estado)
        val r=servidor.takeRequest();assertEquals("/api/perfil/solicitud-eliminacion",r.path)
        assertEquals("{\"confirmar\":true}",r.body.readUtf8())
    }
    @Test fun erroresDelServidorNuncaSeTransformanEnExito()=runBlocking {
        for(codigo in listOf(400,401,403,404,409,423,429,500)) {
            servidor.enqueue(MockResponse().setResponseCode(codigo).setBody("{}"))
            val fallo=runCatching {api.reportar(12,Denuncia("SPAM",null))}.exceptionOrNull()
            assertTrue("Código $codigo",fallo is retrofit2.HttpException)
            assertEquals(codigo,(fallo as retrofit2.HttpException).code());servidor.takeRequest()
        }
    }
}
