package com.proyectointegrador.movil
import com.proyectointegrador.movil.data.repository.*
import com.proyectointegrador.movil.ui.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
@OptIn(ExperimentalCoroutinesApi::class)
class ExperienciaTest {
 private class Preferencias:PreferenciasExperiencia {
  var valor=Experiencia(true);var fallo=false
  override suspend fun leer()=valor
  override suspend fun guardarIntroduccion(){if(fallo)throw IllegalStateException("Sin guardar");valor=valor.copy(introduccionCompleta=true)}
  override suspend fun guardarTema(tema:String){valor=valor.copy(tema=tema)}
 }
 @Before fun preparar(){Dispatchers.setMain(UnconfinedTestDispatcher())}
 @After fun cerrar(){Dispatchers.resetMain()}
 @Test fun bienvenidaSeConservaAlRecrearRepositorio()=runTest {
  val disco=Preferencias();val repo=ExperienciaRepository(disco);repo.cargar();assertFalse(repo.estado.value.introduccionCompleta)
  repo.completar();val reabierto=ExperienciaRepository(disco);reabierto.cargar();assertTrue(reabierto.estado.value.introduccionCompleta)
 }
 @Test fun temaOscuroPersisteYNoReiniciaBienvenida()=runTest {
  val disco=Preferencias();val repo=ExperienciaRepository(disco);repo.cargar();repo.completar();repo.tema("oscuro")
  val reabierto=ExperienciaRepository(disco);reabierto.cargar();assertEquals("oscuro",reabierto.estado.value.tema);assertTrue(reabierto.estado.value.introduccionCompleta)
 }
 @Test fun completarSoloNavegaSiSeGuardoLaPreferencia()=runTest {
  val disco=Preferencias();val vm=ExperienciaViewModel(ExperienciaRepository(disco));disco.fallo=true;vm.completar("perfil")
  assertNull(vm.evento.value);assertNotNull(vm.accion.value.error)
  disco.fallo=false;vm.completar("perfil");assertEquals("perfil",vm.evento.value);assertTrue(vm.estado.value.introduccionCompleta)
 }
}
