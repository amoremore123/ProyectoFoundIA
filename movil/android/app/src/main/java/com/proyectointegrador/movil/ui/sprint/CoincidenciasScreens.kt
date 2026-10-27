package com.proyectointegrador.movil.ui.sprint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.network.*

@Composable fun ResumenObjeto(objeto: Objeto) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Text(objeto.nombre,style=MaterialTheme.typography.titleLarge)
        Text("${objeto.tipo.orEmpty()} · ${objeto.estado.orEmpty()}")
        Text(objeto.descripcion ?: "Sin descripción")
        Text("Categoría: ${objeto.categoria?.nombre ?: "Sin categoría"}")
        Text("Ubicación: ${objeto.ubicacion ?: "Sin ubicación"}")
        Text("Fecha: ${objeto.fechaObjeto ?: "Sin fecha"}")
        if(objeto.fotosSeguras.isEmpty()) Text("Sin foto")
        objeto.fotosSeguras.forEach { foto ->
            SubcomposeAsyncImage(model=RetrofitClient.urlAbsoluta(foto.url),contentDescription="Foto de ${objeto.nombre}",
                modifier=Modifier.fillMaxWidth().height(180.dp),loading={Text("Cargando foto…")},error={Text("Foto no disponible")})
        }
    } }
}
@Composable fun PropiosScreen(volver:()->Unit,detalle:(Long)->Unit,sugerencias:(Long)->Unit) {
    val token by Sesion.token.collectAsState()
    Pagina("Mis publicaciones",volver) {
        Consulta(token,{RetrofitClient.api.propios()}) { objetos,recargar ->
            Button(onClick=recargar) {Text("Actualizar publicaciones")}
            if(objetos.isEmpty()) Text("Todavía no tienes publicaciones.")
            objetos.forEach { objeto ->
                Text(objeto.nombre,style=MaterialTheme.typography.titleMedium)
                Text("${objeto.tipo.orEmpty()} · ${objeto.estado.orEmpty()}")
                if(objeto.estado=="ACTIVO" || objeto.estado=="RECUPERADO")
                    TextButton(onClick={detalle(objeto.id)}) {Text("Ver publicación ${objeto.id}")}
                if(objeto.tipo=="PERDIDO" && objeto.estado=="ACTIVO")
                    Button(onClick={sugerencias(objeto.id)}) {Text("Revisar coincidencias de ${objeto.nombre}")}
                HorizontalDivider()
            }
        }
    }
}
@Composable fun SugerenciasScreen(objetoId:Long,volver:()->Unit,abrir:(String)->Unit) {
    val token by Sesion.token.collectAsState()
    Pagina("Coincidencias de mi publicación",volver) {
        Consulta("$token:$objetoId",{Triple(RetrofitClient.api.objetoPorId(objetoId),RetrofitClient.api.coincidencias(objetoId),RetrofitClient.api.perfil())}) { (objeto,lista,usuario),recargar ->
            Text(objeto.nombre)
            if(objeto.publicadoPor?.id!=usuario.id || objeto.tipo!="PERDIDO") Text("Solo el titular del objeto perdido puede gestionar sus coincidencias.")
            else {
                if(lista.isEmpty()) Text("No hay coincidencias sugeridas disponibles.")
                lista.forEach { candidata ->
                    Text(candidata.nombre ?: "Objeto encontrado")
                    Text("Similitud: ${candidata.porcentaje ?: 0.0}% · ${candidata.nivel.orEmpty()}")
                    Button(onClick={abrir(candidata.coincidenciaId ?: "$objetoId-${candidata.objetoId}")}) {Text("Comparar ambos objetos")}
                }
                Button(onClick=recargar) {Text("Actualizar coincidencias")}
            }
        }
    }
}
@Composable fun ParejaScreen(id:String,volver:()->Unit,propios:()->Unit) {
    val token by Sesion.token.collectAsState()
    var resultado by remember(id,token) {mutableStateOf<String?>(null)}
    val accion=recordarAccion()
    var confirmar by remember(id,token) {mutableStateOf<String?>(null)}
    Pagina("Comparar coincidencia",volver) {
        if(resultado != null) {
            Text(resultado!!);Button(onClick=propios){Text("Volver a mis publicaciones")}
        } else Consulta("$token:$id",{RetrofitClient.api.pareja(id) to if(token!=null) RetrofitClient.api.perfil() else null}) { (pareja,usuario),_ ->
            Text("${pareja.porcentaje}% de similitud · ${pareja.nivel}",style=MaterialTheme.typography.titleLarge)
            Text("La puntuación compara características; no acredita la propiedad.")
            pareja.criterios.forEach { (criterio,puntos) ->Text("$criterio: $puntos puntos") }
            Text("Objeto perdido");ResumenObjeto(pareja.perdido)
            Text("Objeto encontrado");ResumenObjeto(pareja.encontrado)
            if(usuario != null && usuario.id == pareja.perdido.publicadoPor?.id && pareja.perdido.estado=="ACTIVO" && pareja.encontrado.estado=="ACTIVO") {
                Button(onClick={confirmar="recuperar"},enabled=!accion.ocupada) {Text("Confirmar recuperación")}
                OutlinedButton(onClick={confirmar="descartar"},enabled=!accion.ocupada) {Text("Descartar coincidencia")}
            } else Text("La gestión de esta coincidencia corresponde al titular del objeto perdido activo.")
        }
    }
    confirmar?.let { tipo -> Confirmacion(
        if(tipo=="recuperar") "¿Recuperaste este objeto?" else "¿Descartar esta coincidencia?",
        if(tipo=="recuperar") "Se registrará la recuperación de tu objeto perdido. El hallazgo de la otra persona se conserva."
        else "Esta pareja dejará de aparecer en tus sugerencias. Las otras coincidencias se conservan.",
        accion,{confirmar=null},{accion.ejecutar {
            if(tipo=="recuperar") {
                val respuesta=RetrofitClient.api.recuperar(id,ConfirmarRecuperacion())
                resultado="Recuperación confirmada. Fecha del servidor: ${respuesta.fechaRecuperacion}."
            } else {
                RetrofitClient.api.descartar(id);resultado="Coincidencia descartada. Se conservará al volver a consultar."
            }
            confirmar=null
        }}) }
}
