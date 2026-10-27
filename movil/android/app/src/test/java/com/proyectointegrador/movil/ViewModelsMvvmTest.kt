package com.proyectointegrador.movil

import com.google.gson.Gson
import com.proyectointegrador.movil.data.repository.*
import com.proyectointegrador.movil.domain.*
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.network.ApiService
import com.proyectointegrador.movil.ui.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import okhttp3.mockwebserver.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelsMvvmTest {
    private val dispatcher=UnconfinedTestDispatcher()
    private lateinit var servidor:MockWebServer
    private lateinit var repo:CuentaRepository
    private lateinit var publicaciones:ApiPublicacionesRepository
    @Before fun preparar(){Dispatchers.setMain(dispatcher)
        servidor=MockWebServer();servidor.start()
        val api=Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        publicaciones=ApiPublicacionesRepository(api,CachePrueba());val sesion=SesionPrueba();sesion.guardar("sesion-de-prueba")
        repo=CuentaRepository(api,publicaciones,sesion)
    }
    @After fun cerrar(){servidor.shutdown();Dispatchers.resetMain()}
    private fun responder(datos:Any,codigo:Int=200){servidor.enqueue(MockResponse().setResponseCode(codigo).setBody(Gson().toJson(datos)))}
    private suspend fun <T> cargado(vm:CargaViewModel<T>)=withTimeout(5000){vm.estado.first {!it.cargando}.datos!!}
    private suspend fun terminado(vm:AccionViewModel)=withTimeout(5000){vm.accion.first {!it.ocupada}}
    @Test fun registroRequiereAceptacionYLaRecargaNoLaInventa()=runBlocking {
        responder(Documentos("v1","PROYECTO","Términos","Privacidad","huella"))
        val vm=RegistroViewModel(repo);cargado(vm)
        vm.editar("nombre","Ana");vm.editar("apellido","Prueba");vm.editar("correo","ana@qa.invalid")
        vm.editar("password","Segura123!");vm.editar("confirmar","Segura123!")
        vm.registrar();assertNotNull(vm.accion.value.error);assertEquals(1,servidor.requestCount)
        vm.aceptar(true);responder(Documentos("v2","PROYECTO","Otros términos","Privacidad","huella2"));vm.recargarDocumentos();cargado(vm)
        assertFalse(vm.form.value.acepta);vm.registrar();assertEquals(2,servidor.requestCount)
        vm.aceptar(true);responder(Mensaje("Código enviado"),201);vm.registrar()
        assertEquals("ana@qa.invalid",withTimeout(5000){vm.evento.filterNotNull().first()})
        assertEquals("",vm.form.value.password)
        servidor.takeRequest();servidor.takeRequest();val registro=servidor.takeRequest().body.readUtf8()
        assertTrue(registro.contains("\"versionLegal\":\"v2\""));assertTrue(registro.contains("\"aceptaTerminos\":true"))
    }
    @Test fun cancelarPreferenciasDespuesDeGuardarUsaUltimoValorDelServidor()=runBlocking {
        responder(Preferencias());val vm=PreferenciasViewModel(repo);cargado(vm)
        vm.editar(0,false);responder(Preferencias(false,true,true));vm.guardar();terminado(vm)
        assertFalse(vm.estado.value.datos!!.coincidencias)
        vm.editar(0,true);vm.cancelar();assertNull(vm.edicion.value);assertFalse(vm.estado.value.datos!!.coincidencias)
        assertEquals(2,servidor.requestCount)
    }
    @Test fun eliminacionCanceladaOFallidaNoSePresentaComoPendiente()=runBlocking {
        responder(Eliminacion(null,"SIN_SOLICITUD",null,false,"No borra datos"));val vm=EliminacionViewModel(repo);cargado(vm)
        vm.pedir();vm.cancelar();vm.confirmar();assertEquals(1,servidor.requestCount)
        responder(mapOf("mensaje" to "Fallo temporal"),503);vm.pedir();vm.confirmar();terminado(vm)
        assertEquals("SIN_SOLICITUD",vm.estado.value.datos!!.estado);assertNotNull(vm.accion.value.error)
        responder(Eliminacion(3,"PENDIENTE","2026-10-27T12:00:00Z",false,"Trámite pendiente"),201);vm.confirmar();terminado(vm)
        assertEquals("PENDIENTE",vm.estado.value.datos!!.estado);assertFalse(vm.confirmacion.value)
    }
    @Test fun otraCuentaNoObtieneAccionesDeDescarteORecuperacion()=runBlocking {
        responder(Pareja("1-2",Objeto(id=1,tipo="PERDIDO",estado="ACTIVO",publicadoPor=PublicadoPor(1,"Ana","P.")),
            Objeto(id=2,tipo="ENCONTRADO",estado="ACTIVO"),90.0,"ALTA",emptyMap()))
        responder(Usuario(8,"Otra","QA","otra@qa.invalid","USUARIO","ACTIVO"))
        val vm=ParejaViewModel(repo,"1-2");cargado(vm)
        assertFalse(vm.puedeGestionar);vm.pedir("recuperar");vm.confirmar();assertNull(vm.confirmacion.value);assertEquals(2,servidor.requestCount)
    }
    @Test fun denunciaOtroRequiereTextoYUnErrorNoProduceConfirmacion()=runBlocking {
        responder(Objeto(id=12,nombre="Objeto de prueba"));val vm=ReporteViewModel(repo,12);cargado(vm)
        vm.motivo("OTRO");vm.pedir();vm.confirmar();assertFalse(vm.form.value.confirmar);assertEquals(1,servidor.requestCount)
        vm.descripcion("Motivo válido de prueba");vm.pedir();responder(mapOf("mensaje" to "No disponible"),409);vm.confirmar();terminado(vm)
        assertNull(vm.form.value.resultado);assertNotNull(vm.accion.value.error)
    }
    @Test fun reenvioRespetaRetryAfterYTerminaLaEspera()=runBlocking {
        val ahora=java.util.concurrent.atomic.AtomicLong(0)
        val vm=VerificacionViewModel(repo){ahora.get()};vm.iniciar("ana@qa.invalid")
        servidor.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After","1").setBody("{\"mensaje\":\"Espera\"}"))
        vm.reenviar();terminado(vm);assertEquals(1,vm.form.value.espera)
        vm.reenviar();assertEquals(1,servidor.requestCount)
        ahora.set(1001)
        withTimeout(3000){while(vm.form.value.espera!=0){dispatcher.scheduler.advanceTimeBy(1000);dispatcher.scheduler.runCurrent();delay(10)}}
        assertEquals(0,vm.form.value.espera)
    }
    @Test fun publicarValidaAntesDeEnviarYSoloEmiteIdTrasRespuestaReal()=runBlocking {
        responder(listOf(Categoria(1,"Mochilas")));val vm=PublicarViewModel(repo,publicaciones);cargado(vm)
        vm.publicar();assertTrue(vm.form.value.errores.isNotEmpty());assertEquals(1,servidor.requestCount)
        vm.editar("nombre","Objeto QA");vm.editar("descripcion","Descripción QA");vm.editar("ubicacion","Biblioteca");vm.editar("fecha","2026-02-30");vm.categoria(1)
        vm.publicar();assertTrue(vm.form.value.errores.containsKey("fecha"));assertEquals(1,servidor.requestCount)
        vm.editar("fecha","2026-10-20");responder(Objeto(id=45,nombre="Objeto QA"),201);vm.publicar()
        assertEquals("45",withTimeout(5000){vm.evento.filterNotNull().first()});assertEquals("",vm.form.value.nombre)
    }
    @Test fun enlaceInvalidoNoIntentaCambiarLaClave() {
        val vm=NuevaClaveViewModel(repo,"corto");vm.password("Segura123!");vm.confirmar("Segura123!");vm.guardar()
        assertFalse(vm.enlaceValido);assertNotNull(vm.accion.value.error);assertEquals(0,servidor.requestCount)
    }
    @Test fun soloLaBusquedaMasRecienteActualizaLaPantalla()=runTest {
        val fake=object:PublicacionesRepository {
            override fun buscar(q:String?,tipo:String?)=flow {delay(if(q=="lenta")1000 else 10);emit(Lectura(listOf(Objeto(id=1,nombre=q.orEmpty()))))}
            override fun listar(tipo:String?)=buscar("inicio",tipo)
            override fun detalle(id:Long)=flowOf(Lectura(Objeto(id=id)))
            override suspend fun categorias()=Lectura(emptyList<Categoria>())
            override fun observarCategorias()=flowOf(emptyList<Categoria>())
            override suspend fun coincidencias(id:Long)=emptyList<CoincidenciaSugerida>()
            override suspend fun vigente(id:Long)=Objeto(id=id)
            override suspend fun invalidar(){}
        }
        val vm=ObjetosViewModel(fake);vm.editarBusqueda("lenta");advanceTimeBy(310);vm.editarBusqueda("rápida");advanceUntilIdle()
        assertEquals("rápida",vm.state.value.objetos.single().nombre);assertNull(vm.state.value.error)
    }
    @Test fun validacionesDeAccesoNoPermitenCorreoNiClaveVacios() {
        val vm=AccesoViewModel(repo);vm.correo("correo inválido");vm.password("clave");vm.entrar()
        assertNotNull(vm.accion.value.error);assertEquals(0,servidor.requestCount)
        assertTrue(correoValido("ana@qa.invalid"));assertFalse(correoValido("ana @qa.invalid"))
    }
}
