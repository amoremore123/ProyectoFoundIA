package com.proyectointegrador.movil

import android.content.ContentValues
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.proyectointegrador.movil.data.repository.*
import com.proyectointegrador.movil.di.Inyeccion
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.ui.viewmodel.PublicarViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class RedisenioApiTest {
 @Test fun fotoPerfilYContactoUsanServidorReal()=runBlocking {
  Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("probarApiReal")=="true")
  assertEquals("http://10.0.2.2:18083/",BuildConfig.API_URL)
  val instrumentation=InstrumentationRegistry.getInstrumentation();val contexto=instrumentation.targetContext
  val c=Inyeccion.contenedor;val cuenta=c.cuenta
  cuenta.login("ana@foundia.dev","Admin123!")
  val categoria=c.publicaciones.categorias().datos.first().id
  val foto=BitmapFactory.decodeResource(contexto.resources,R.drawable.tecsup_campus)
  val valores=ContentValues().apply {put(MediaStore.Images.Media.DISPLAY_NAME,"foundia-prueba-visual.png");put(MediaStore.Images.Media.MIME_TYPE,"image/png");put(MediaStore.Images.Media.RELATIVE_PATH,"Pictures/FoundiaQA")}
  val uri=contexto.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,valores)!!
  var original:Usuario?=null
  try {
   contexto.contentResolver.openOutputStream(uri)!!.use {assertTrue(foto.compress(Bitmap.CompressFormat.PNG,100,it))};foto.recycle()
   val preparado=c.fotografias.preparar(uri.toString());assertEquals("image/png",preparado.tipo);assertTrue(preparado.bytes.isNotEmpty())
   lateinit var vm:PublicarViewModel
   instrumentation.runOnMainSync {vm=PublicarViewModel(cuenta,c.publicaciones,c.fotografias)}
   withTimeout(20000){vm.estado.first {!it.cargando}}
   instrumentation.runOnMainSync {
    vm.editar("nombre","Objeto visual QA perdido");vm.editar("descripcion","Prueba visual: mochila azul. Fotografía institucional usada solo como archivo sintético de QA.")
    vm.editar("ubicacion","Biblioteca QA");vm.editar("fecha","2026-10-20");vm.categoria(categoria);vm.foto(uri.toString());vm.publicar()
   }
   val perdido=withTimeout(20000){vm.evento.filterNotNull().first()}.toLong()
   val publicado=c.publicaciones.vigente(perdido);assertEquals(1,publicado.fotosSeguras.size)
   original=cuenta.perfil();val editado=cuenta.actualizarPerfil(PerfilRequest(original.nombre,"Prueba visual"))
   assertEquals("Prueba visual",editado.apellido);assertEquals(original.correo,editado.correo)
   cuenta.actualizarPerfil(PerfilRequest(original.nombre,original.apellido));original=null
   cuenta.login("luis@foundia.dev","Admin123!")
   val encontrado=cuenta.publicar(PublicacionRequest(categoria,"Objeto visual QA encontrado",publicado.descripcion!!,"2026-10-20","ENCONTRADO","Biblioteca QA")).id
   cuenta.subirFoto(encontrado,preparado)
   cuenta.login("ana@foundia.dev","Admin123!")
   val pareja=cuenta.pareja("$perdido-$encontrado").pareja
   assertTrue(pareja.porcentaje>0);assertEquals(1,pareja.encontrado.fotosSeguras.size)
   val contacto=cuenta.contactar(ContactoRequest(perdido,encontrado));assertTrue(contacto.id>0)
   val repetido=cuenta.contactar(ContactoRequest(perdido,encontrado));assertEquals(contacto.id,repetido.id);assertTrue(repetido.repetido)
   File(contexto.filesDir,"redisenio-api-qa.json").writeText(Gson().toJson(mapOf("perdido" to perdido,"encontrado" to encontrado,"pareja" to pareja.id,"porcentaje" to pareja.porcentaje,"foto" to publicado.fotosSeguras.first().url,"contacto" to contacto.id,"perfilRestaurado" to true,"imagenSinteticaQa" to true)))
  } finally {
   original?.let {cuenta.actualizarPerfil(PerfilRequest(it.nombre,it.apellido))}
   contexto.contentResolver.delete(uri,null,null)
  }
 }
}
