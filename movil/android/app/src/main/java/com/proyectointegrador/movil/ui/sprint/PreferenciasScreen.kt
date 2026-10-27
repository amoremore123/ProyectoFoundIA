package com.proyectointegrador.movil.ui.sprint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.network.*

@Composable fun PreferenciasScreen(volver:()->Unit) {
    val token by Sesion.token.collectAsState()
    Pagina("Preferencias de avisos",volver) {
        Consulta(token,{RetrofitClient.api.preferencias()}) { actuales,recargar ->
            var guardadas by remember(actuales) {mutableStateOf(actuales)}
            var preferencias by remember(actuales) {mutableStateOf(actuales)}
            val accion=recordarAccion()
            Text("Los cambios afectan a los avisos nuevos; el historial se conserva.")
            listOf("Nuevas coincidencias","Solicitudes de contacto","Reclamos de objetos").forEachIndexed { indice,texto ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(texto,Modifier.weight(1f))
                    Switch(checked=when(indice){0->preferencias.coincidencias;1->preferencias.contactos;else->preferencias.reclamos},
                        enabled=!accion.ocupada,onCheckedChange={valor->preferencias=when(indice){
                            0->preferencias.copy(coincidencias=valor);1->preferencias.copy(contactos=valor);else->preferencias.copy(reclamos=valor)
                        }})
                }
            }
            EstadoAccion(accion)
            Button(enabled=!accion.ocupada,onClick={accion.ejecutar {
                preferencias=RetrofitClient.api.guardarPreferencias(preferencias);guardadas=preferencias;accion.mensaje="Preferencias guardadas."
            }}) {Text("Guardar preferencias")}
            OutlinedButton(onClick=recargar,enabled=!accion.ocupada) {Text("Recargar preferencias del servidor")}
            TextButton(onClick={preferencias=guardadas},enabled=!accion.ocupada) {Text("Cancelar cambios")}
        }
    }
}
