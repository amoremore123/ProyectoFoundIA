package com.proyectointegrador.movil
import com.google.gson.Gson
import com.proyectointegrador.movil.data.repository.*
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
class RedisenioViewModelsTest {
 private lateinit var servidor:MockWebServer;private lateinit var cuenta:CuentaRepository;private lateinit var publicaciones:ApiPublicacionesRepository
 @Before fun preparar(){Dispatchers.setMain(UnconfinedTestDispatcher());servidor=MockWebServer();servidor.start()
  val api=Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
  publicaciones=ApiPublicacionesRepository(api,CachePrueba());val sesion=SesionPrueba();sesion.guardar("prueba");cuenta=CuentaRepository(api,publicaciones,sesion)
 }
 @After fun cerrar(){servidor.shutdown();Dispatchers.resetMain()}
 private fun responder(dato:Any,codigo:Int=200){servidor.enqueue(MockResponse().setResponseCode(codigo).setBody(Gson().toJson(dato)))}
 private suspend fun <T> cargar(vm:CargaViewModel<T>)=withTimeout(5000){vm.estado.first {!it.cargando}.datos!!}
 private suspend fun terminar(vm:AccionViewModel)=withTimeout(5000){vm.accion.first {!it.ocupada}}
 private suspend fun publicador():PublicarViewModel {
  responder(listOf(Categoria(1,"Documentos",null,true)))
  val fotos=object:FotografiasRepository{override suspend fun preparar(uri:String)=FotoPreparada(byteArrayOf(1,2),"image/png","foto.png")}
  return PublicarViewModel(cuenta,publicaciones,fotos).also {cargar(it);it.editar("nombre","Carné");it.editar("descripcion","Objeto de prueba");it.editar("ubicacion","Biblioteca");it.editar("fecha","2026-10-20");it.categoria(1);it.foto("content://qa/foto")}
 }
 @Test fun fotoFallidaNoInventaExitoNiDuplicaPublicacion()=runBlocking {
  val vm=publicador();responder(Objeto(id=91),201);responder(mapOf("mensaje" to "Sin foto"),500);vm.publicar();terminar(vm)
  assertEquals(91L,vm.form.value.publicadoId);assertNull(vm.evento.value);assertNotNull(vm.accion.value.error)
  responder(ObjetoFoto(1,"/uploads/foto.png"),201);vm.publicar();terminar(vm)
  assertEquals("91",vm.evento.value);assertEquals(4,servidor.requestCount)
  assertEquals("/api/categorias",servidor.takeRequest().path);assertEquals("/api/objetos",servidor.takeRequest().path)
  assertEquals("/api/objetos/91/fotos",servidor.takeRequest().path);assertEquals("/api/objetos/91/fotos",servidor.takeRequest().path)
 }
 @Test fun continuarSinFotoSoloAbreUnObjetoQueElServidorYaCreo()=runBlocking {
  val vm=publicador();vm.terminarSinFoto();assertNull(vm.evento.value)
  responder(Objeto(id=92),201);responder(mapOf("mensaje" to "Falló"),500);vm.publicar();terminar(vm)
  vm.terminarSinFoto();assertEquals("92",vm.evento.value);assertEquals(3,servidor.requestCount)
 }
 @Test fun editarPerfilUsaPutRealYConservaCorreoDelServidor()=runBlocking {
  val original=Usuario(1,"Ana","Prueba","ana@qa.invalid","USUARIO","ACTIVO");responder(original)
  val vm=EditarPerfilViewModel(cuenta);cargar(vm);vm.editar("nombre","Amore");responder(original.copy(nombre="Amore"));vm.guardar(false);terminar(vm)
  assertEquals("Amore",vm.estado.value.datos?.nombre);assertEquals(original.correo,vm.estado.value.datos?.correo)
  servidor.takeRequest();val solicitud=servidor.takeRequest();assertEquals("PUT",solicitud.method);assertEquals("/api/perfil",solicitud.path)
  assertFalse(solicitud.body.readUtf8().contains("passwordActual"))
 }
 @Test fun cambiarClaveRequiereLaActualYConfirmacion()=runBlocking {
  responder(Usuario(1,"Ana","Prueba","ana@qa.invalid","USUARIO","ACTIVO"));val vm=EditarPerfilViewModel(cuenta);cargar(vm)
  vm.editar("password","Segura123!");vm.editar("confirmar","Segura123!");vm.guardar(true);assertNotNull(vm.accion.value.error);assertEquals(1,servidor.requestCount)
 }
 @Test fun contadorDeAvisosSoloCambiaTrasRespuestaDelServidor()=runBlocking {
  val aviso=Aviso(4,"Nueva coincidencia","Mensaje QA","COINCIDENCIA",false,null,"/coincidencias/1-2")
  responder(listOf(aviso));cuenta.avisos();assertEquals(1,cuenta.sinLeer.value)
  responder(mapOf("mensaje" to "Sin servicio"),500);assertTrue(runCatching {cuenta.leer(4)}.isFailure);assertEquals(1,cuenta.sinLeer.value)
  responder(aviso.copy(leida=true));cuenta.leer(4);assertEquals(0,cuenta.sinLeer.value)
 }
 @Test fun formularioPorPasosNoAvanzaSiFaltanCampos()=runBlocking {
  responder(listOf(Categoria(1,"Documentos")));val vm=PublicarViewModel(cuenta,publicaciones);cargar(vm)
  vm.siguiente();assertEquals(1,vm.form.value.paso);vm.siguiente();assertEquals(1,vm.form.value.paso);assertTrue(vm.form.value.errores.containsKey("nombre"));assertEquals(1,servidor.requestCount)
 }

}
