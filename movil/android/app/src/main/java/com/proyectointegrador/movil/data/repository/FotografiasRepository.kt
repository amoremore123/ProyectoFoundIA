package com.proyectointegrador.movil.data.repository
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.proyectointegrador.movil.domain.ErrorDatos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class FotoPreparada(val bytes:ByteArray,val tipo:String,val nombre:String)
interface FotografiasRepository {
 suspend fun preparar(uri:String):FotoPreparada
 /** Borra la captura propia que ya no se usará. Las fotos de la galería nunca se tocan. */
 fun descartar(uri:String) {}
 /** Reduce la foto a JPEG de máximo 1024 px y 2 MiB y la devuelve como data URL. */
 suspend fun prepararParaBusqueda(uri:String):String = throw UnsupportedOperationException("No disponible")
}
class FotografiasLocales(context:Context):FotografiasRepository {
 private val resolver=context.applicationContext.contentResolver
 private val autoridadCapturas=context.packageName+".capturas"
 private val carpetaCapturas=File(context.cacheDir,"capturas")
 override suspend fun prepararParaBusqueda(uri:String):String=withContext(Dispatchers.IO) {
  val origen=Uri.parse(uri)
  if(origen.scheme!="content")throw ErrorDatos("Selecciona una imagen de tu galería.")
  val limites=BitmapFactory.Options().apply {inJustDecodeBounds=true}
  resolver.openInputStream(origen)?.use {BitmapFactory.decodeStream(it,null,limites)}?:throw ErrorDatos("No se pudo abrir la fotografía.")
  if(limites.outMimeType !in listOf("image/jpeg","image/png")||limites.outWidth<1||limites.outHeight<1)throw ErrorDatos("Usa una fotografía JPEG o PNG.")
  val (ancho,alto)=com.proyectointegrador.movil.domain.ladoReducido(limites.outWidth,limites.outHeight)
  var muestreo=1
  while(limites.outWidth/(muestreo*2)>=ancho&&limites.outHeight/(muestreo*2)>=alto)muestreo*=2
  val opciones=BitmapFactory.Options().apply {inSampleSize=muestreo}
  val grande=resolver.openInputStream(origen)?.use {BitmapFactory.decodeStream(it,null,opciones)}?:throw ErrorDatos("No se pudo leer la fotografía.")
  val reducida=if(grande.width==ancho&&grande.height==alto)grande else android.graphics.Bitmap.createScaledBitmap(grande,ancho,alto,true)
  var calidad=85
  val salida=java.io.ByteArrayOutputStream()
  do {
   salida.reset();reducida.compress(android.graphics.Bitmap.CompressFormat.JPEG,calidad,salida);calidad-=10
  } while(salida.size()>com.proyectointegrador.movil.domain.BYTES_MAXIMOS_FOTO_BUSQUEDA&&calidad>=25)
  if(salida.size()>com.proyectointegrador.movil.domain.BYTES_MAXIMOS_FOTO_BUSQUEDA)throw ErrorDatos("La fotografía es demasiado grande para buscar. Usa otra.")
  "data:image/jpeg;base64,"+android.util.Base64.encodeToString(salida.toByteArray(),android.util.Base64.NO_WRAP)
 }
 override fun descartar(uri:String) {
  val origen=Uri.parse(uri)
  if(origen.scheme!="content"||origen.authority!=autoridadCapturas)return
  val nombre=origen.lastPathSegment?:return
  val archivo=File(carpetaCapturas,nombre)
  if(archivo.canonicalFile.parentFile==carpetaCapturas.canonicalFile)archivo.delete()
 }
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
