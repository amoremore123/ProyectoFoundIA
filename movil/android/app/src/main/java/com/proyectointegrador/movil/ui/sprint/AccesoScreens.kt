package com.proyectointegrador.movil.ui.sprint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*

@Composable fun AccesoScreen(registro:()->Unit,verificar:(String)->Unit,recuperar:()->Unit,
    vm:AccesoViewModel=viewModel(factory=fabrica()),sesionVm:SesionViewModel=viewModel(factory=fabrica())) {
    val f by vm.form.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle()
    val sesion by sesionVm.estado.collectAsStateWithLifecycle()
    Pagina("Tu cuenta") {
        sesion.aviso?.let {Text(it)}
        Text("Inicia sesión para administrar tus publicaciones y avisos.")
        Campo("Correo",f.correo,vm::correo);Campo("Contraseña",f.password,vm::password,secreto=true)
        EstadoAccion(accion)
        Button(enabled=!accion.ocupada,onClick=vm::entrar) {Text("Iniciar sesión")}
        TextButton(onClick=registro,enabled=!accion.ocupada) {Text("Crear cuenta")}
        TextButton(onClick={verificar(f.correo.trim())},enabled=!accion.ocupada) {Text("Verificar mi correo")}
        TextButton(onClick=recuperar,enabled=!accion.ocupada) {Text("Olvidé mi contraseña")}
    }
}
@Composable fun RegistroScreen(volver:()->Unit,verificar:(String)->Unit,vm:RegistroViewModel=viewModel(factory=fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle();val f by vm.form.collectAsStateWithLifecycle()
    val accion by vm.accion.collectAsStateWithLifecycle();val evento by vm.evento.collectAsStateWithLifecycle()
    LaunchedEffect(evento){evento?.let {vm.consumirEvento();verificar(it)}}
    Pagina("Crear cuenta",volver) {
        Consulta(estado,vm::recargarDocumentos) { docs,_ ->
            var leer by remember {mutableStateOf<String?>(null)}
            Text("Términos y privacidad del proyecto. Pendientes de revisión institucional.")
            Text("Versión: ${docs.version}")
            TextButton(onClick={leer=docs.terminos}) {Text("Leer términos de uso")}
            TextButton(onClick={leer=docs.privacidad}) {Text("Leer política de privacidad")}
            leer?.let { texto -> AlertDialog(onDismissRequest={leer=null},title={Text("Documento presentado")},
                text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {Text(texto)}},
                confirmButton={TextButton(onClick={leer=null}) {Text("Cerrar documento")}}) }
            Campo("Nombre",f.nombre,{vm.editar("nombre",it)});Campo("Apellido",f.apellido,{vm.editar("apellido",it)})
            Campo("Correo",f.correo,{vm.editar("correo",it)})
            Text("Contraseña: 8 caracteres como mínimo, mayúscula, minúscula, número y símbolo; hasta 72 bytes.")
            Campo("Contraseña",f.password,{vm.editar("password",it)},secreto=true)
            Campo("Confirmar contraseña",f.confirmar,{vm.editar("confirmar",it)},secreto=true)
            Row(Modifier.toggleable(value=f.acepta,role=Role.Checkbox,enabled=!accion.ocupada,onValueChange=vm::aceptar)) {
                Checkbox(checked=f.acepta,onCheckedChange=null,enabled=!accion.ocupada)
                Text("Acepto los términos y la política de privacidad de la versión presentada",Modifier.weight(1f))}
            EstadoAccion(accion)
            Button(enabled=f.acepta && !accion.ocupada,onClick=vm::registrar) {Text("Registrarme y recibir código")}
            TextButton(onClick=vm::recargarDocumentos,enabled=!accion.ocupada) {Text("Volver a cargar documentos")}
        }
    }
}
@Composable fun VerificarScreen(correoInicial:String,volver:()->Unit,vm:VerificacionViewModel=viewModel(factory=fabrica())) {
    val f by vm.form.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle()
    LaunchedEffect(correoInicial){vm.iniciar(correoInicial)}
    Pagina("Verificar correo",volver) {
        Text("Ingresa el código recibido por correo. La cuenta no se activa hasta verificarlo.")
        Campo("Correo",f.correo,vm::correo);Campo("Código de 6 dígitos",f.codigo,vm::codigo)
        EstadoAccion(accion)
        Button(enabled=!accion.ocupada,onClick=vm::verificar) {Text("Verificar y entrar")}
        TextButton(enabled=!accion.ocupada && f.espera==0,onClick=vm::reenviar) {Text(if(f.espera>0) "Reenviar en ${f.espera} s" else "Reenviar código")}
    }
}
@Composable fun CuentaScreen(abrir:(String)->Unit,vm:PerfilViewModel=viewModel(factory=fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle()
    Pagina("Mi cuenta") {
        Consulta(estado,vm::recargar) { usuario,_ ->
            Text("${usuario.nombre} ${usuario.apellido}",style=MaterialTheme.typography.headlineSmall);Text(usuario.correo)
            Button(onClick={abrir("propios")}) {Text("Mis publicaciones")}
            Button(onClick={abrir("avisos")}) {Text("Mis notificaciones")}
            Button(onClick={abrir("preferencias")}) {Text("Preferencias de avisos")}
            OutlinedButton(onClick={abrir("eliminacion")}) {Text("Solicitar eliminación de cuenta")}
        }
        EstadoAccion(accion)
        OutlinedButton(onClick=vm::salir,enabled=!accion.ocupada) {Text("Cerrar sesión")}
    }
}
