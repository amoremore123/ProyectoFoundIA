package com.proyectointegrador.movil.ui.sprint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.proyectointegrador.movil.model.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*
import com.proyectointegrador.movil.domain.urlImagen

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
            SubcomposeAsyncImage(model=urlImagen(foto.url),contentDescription="Foto de ${objeto.nombre}",
                modifier=Modifier.fillMaxWidth().height(180.dp),loading={Text("Cargando foto…")},error={Text("Foto no disponible")})
        }
    } }
}
@Composable fun PropiosScreen(volver:()->Unit,detalle:(Long)->Unit,sugerencias:(Long)->Unit,vm:PropiosViewModel=viewModel(factory=fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    Pagina("Mis publicaciones",volver) {
        Consulta(estado,vm::recargar) { objetos,recargar ->
            Button(onClick=recargar) {Text("Actualizar publicaciones")}
            if(objetos.isEmpty()) Text("Todavía no tienes publicaciones.")
            objetos.forEach { objeto ->
                Text(objeto.nombre,style=MaterialTheme.typography.titleMedium);Text("${objeto.tipo.orEmpty()} · ${objeto.estado.orEmpty()}")
                if(objeto.estado=="ACTIVO" || objeto.estado=="RECUPERADO") TextButton(onClick={detalle(objeto.id)}) {Text("Ver publicación ${objeto.id}")}
                if(objeto.tipo=="PERDIDO" && objeto.estado=="ACTIVO") Button(onClick={sugerencias(objeto.id)}) {Text("Revisar coincidencias de ${objeto.nombre}")}
                HorizontalDivider()
            }
        }
    }
}
@Composable fun SugerenciasScreen(objetoId:Long,volver:()->Unit,abrir:(String)->Unit,
    vm:SugerenciasViewModel=viewModel(key="sugerencias-$objetoId",factory=fabrica(objetoId=objetoId))) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    Pagina("Coincidencias de mi publicación",volver) {
        Consulta(estado,vm::recargar) { datos,recargar ->
            Text(datos.objeto.nombre)
            if(!vm.puedeGestionar) Text("Solo el titular del objeto perdido puede gestionar sus coincidencias.")
            else {
                if(datos.candidatas.isEmpty()) Text("No hay coincidencias sugeridas disponibles.")
                datos.candidatas.forEach { candidata ->
                    Text(candidata.nombre ?: "Objeto encontrado");Text("Similitud: ${candidata.porcentaje ?: 0.0}% · ${candidata.nivel.orEmpty()}")
                    Button(onClick={abrir(candidata.coincidenciaId ?: "$objetoId-${candidata.objetoId}")}) {Text("Comparar ambos objetos")}
                }
                Button(onClick=recargar) {Text("Actualizar coincidencias")}
            }
        }
    }
}
@Composable fun ParejaScreen(id:String,volver:()->Unit,propios:()->Unit,
    vm:ParejaViewModel=viewModel(key="pareja-$id",factory=fabrica(parejaId=id))) {
    val estado by vm.estado.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle()
    val resultado by vm.resultado.collectAsStateWithLifecycle();val confirmar by vm.confirmacion.collectAsStateWithLifecycle()
    Pagina("Comparar coincidencia",volver) {
        if(resultado!=null) {Text(resultado!!);Button(onClick=propios){Text("Volver a mis publicaciones")}}
        else Consulta(estado,vm::recargar) { datos,_ ->
            val pareja=datos.pareja
            Text("${pareja.porcentaje}% de similitud · ${pareja.nivel}",style=MaterialTheme.typography.titleLarge)
            Text("La puntuación compara características; no acredita la propiedad.")
            pareja.criterios.forEach { (criterio,puntos) ->Text("$criterio: $puntos puntos") }
            Text("Objeto perdido");ResumenObjeto(pareja.perdido);Text("Objeto encontrado");ResumenObjeto(pareja.encontrado)
            if(vm.puedeGestionar) {
                Button(onClick={vm.pedir("recuperar")},enabled=!accion.ocupada) {Text("Confirmar recuperación")}
                OutlinedButton(onClick={vm.pedir("descartar")},enabled=!accion.ocupada) {Text("Descartar coincidencia")}
            } else Text("La gestión de esta coincidencia corresponde al titular del objeto perdido activo.")
        }
    }
    confirmar?.let { tipo -> Confirmacion(if(tipo=="recuperar") "¿Recuperaste este objeto?" else "¿Descartar esta coincidencia?",
        if(tipo=="recuperar") "Se registrará la recuperación de tu objeto perdido. El hallazgo de la otra persona se conserva."
        else "Esta pareja dejará de aparecer en tus sugerencias. Las otras coincidencias se conservan.",accion,vm::cancelar,vm::confirmar) }
}
