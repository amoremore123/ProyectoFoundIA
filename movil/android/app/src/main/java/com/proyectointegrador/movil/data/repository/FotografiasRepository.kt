package com.proyectointegrador.movil.data.repository
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.proyectointegrador.movil.domain.ErrorDatos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class FotoPreparada(val bytes:ByteArray,val tipo:String,val nombre:String)
interface FotografiasRepository {suspend fun preparar(uri:String):FotoPreparada}
class FotografiasLocales(context:Context):FotografiasRepository {
 private val resolver=context.applicationContext.contentResolver
 override suspend fun preparar(uri:String):FotoPreparada=withContext(Dispatchers.IO) {
  val origen=Uri.parse(uri)
  if(origen.scheme!="content")throw ErrorDatos("Selecciona una imagen de tu galería.")
  val bytes=resolver.openInputStream(origen)?.use {it.readBytesLimitados()}?:throw ErrorDatos("No se pudo abrir la fotografía.")
  val opciones=BitmapFactory.Options().apply {inJustDecodeBounds=true};BitmapFactory.decodeByteArray(bytes,0,bytes.size,opciones)
  val tipo=opciones.outMimeType
  if(tipo !in listOf("image/jpeg","image/png")||opciones.outWidth<1||opciones.outHeight<1||opciones.outWidth.toLong()*opciones.outHeight>40000000)throw ErrorDatos("Usa una fotografía JPEG o PNG de hasta 40 megapíxeles.")
  FotoPreparada(bytes,tipo,"foto.${if(tipo=="image/png")"png" else "jpg"}")
 }
 private fun java.io.InputStream.readBytesLimitados():ByteArray {
  val salida=java.io.ByteArrayOutputStream();val bloque=ByteArray(8192);var total=0
  while(true){val n=read(bloque);if(n<0)break;total+=n;if(total>5242880)throw ErrorDatos("La fotografía supera el máximo de 5 MiB.");salida.write(bloque,0,n)}
  return salida.toByteArray()
 }
}
