package com.proyectointegrador.movil.ui.sprint

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.network.*

@Composable fun RecuperarScreen(volver:()->Unit) {
    var correo by remember {mutableStateOf("")};val accion=recordarAccion()
    Pagina("Recuperar contraseña",volver) {
        Text("Solicita el enlace de recuperación y ábrelo desde tu correo.")
        Campo("Correo",correo,{correo=it},habilitado=!accion.ocupada)
        EstadoAccion(accion)
        Button(enabled=!accion.ocupada && correo.isNotBlank(),onClick={accion.ejecutar {
            RetrofitClient.api.solicitarClave(Correo(correo.trim()))
            accion.mensaje="Si el correo corresponde a una cuenta habilitada, recibirás un enlace de recuperación."
        }}) {Text("Solicitar enlace")}
    }
}
@Composable fun NuevaClaveScreen(token:String?,volver:()->Unit,entrar:()->Unit) {
    var clave by remember(token) {mutableStateOf("")};var confirmacion by remember(token) {mutableStateOf("")}
    var terminado by remember(token) {mutableStateOf(false)}
    val accion=recordarAccion()
    Pagina("Nueva contraseña",volver) {
        when {
            token==null -> Text("El enlace de recuperación no es válido. Solicita uno nuevo desde el acceso.")
            terminado -> {Text("Contraseña actualizada. Inicia sesión de nuevo; se mantienen las restricciones de tu cuenta.");Button(onClick=entrar){Text("Volver al acceso")}}
            else -> {
                Text("Usa al menos 8 caracteres con mayúscula, minúscula, número y símbolo; hasta 72 bytes.")
                Campo("Nueva contraseña",clave,{clave=it},secreto=true,habilitado=!accion.ocupada)
                Campo("Confirmar contraseña",confirmacion,{confirmacion=it},secreto=true,habilitado=!accion.ocupada)
                EstadoAccion(accion)
                Button(enabled=!accion.ocupada && clave==confirmacion && clave.length>=8,onClick={accion.ejecutar {
                    RetrofitClient.api.restablecer(Restablecer(token,clave));Sesion.cerrar();clave="";confirmacion="";terminado=true
                }}) {Text("Guardar nueva contraseña")}
            }
        }
    }
}
