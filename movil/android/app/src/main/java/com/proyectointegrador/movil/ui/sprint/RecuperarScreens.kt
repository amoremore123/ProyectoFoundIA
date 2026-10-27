package com.proyectointegrador.movil.ui.sprint

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*

@Composable fun RecuperarScreen(volver:()->Unit,vm:RecuperarViewModel=viewModel(factory=fabrica())) {
    val correo by vm.correo.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle()
    Pagina("Recuperar contraseña",volver) {
        Text("Solicita el enlace de recuperación y ábrelo desde tu correo.")
        Campo("Correo",correo,vm::editar,habilitado=!accion.ocupada);EstadoAccion(accion)
        Button(enabled=!accion.ocupada,onClick=vm::solicitar) {Text("Solicitar enlace")}
    }
}
@Composable fun NuevaClaveScreen(token:String?,volver:()->Unit,entrar:()->Unit,
    vm:NuevaClaveViewModel=viewModel(key="clave-${token?.hashCode()}",factory=fabrica(tokenRecuperacion=token))) {
    val f by vm.form.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle()
    Pagina("Nueva contraseña",volver) {
        when {
            !vm.enlaceValido -> Text("El enlace de recuperación no es válido. Solicita uno nuevo desde el acceso.")
            f.terminado -> {Text("Contraseña actualizada. Inicia sesión de nuevo; se mantienen las restricciones de tu cuenta.");Button(onClick=entrar){Text("Volver al acceso")}}
            else -> {
                Text("Usa al menos 8 caracteres con mayúscula, minúscula, número y símbolo; hasta 72 bytes.")
                Campo("Nueva contraseña",f.password,vm::password,secreto=true,habilitado=!accion.ocupada)
                Campo("Confirmar contraseña",f.confirmar,vm::confirmar,secreto=true,habilitado=!accion.ocupada)
                EstadoAccion(accion);Button(enabled=!accion.ocupada,onClick=vm::guardar) {Text("Guardar nueva contraseña")}
            }
        }
    }
}
