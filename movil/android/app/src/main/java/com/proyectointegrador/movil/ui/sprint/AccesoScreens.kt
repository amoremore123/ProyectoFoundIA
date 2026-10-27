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
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.network.*
import kotlinx.coroutines.delay

@Composable fun AccesoScreen(registro:()->Unit,verificar:(String)->Unit,recuperar:()->Unit) {
    var correo by remember {mutableStateOf("")};var clave by remember {mutableStateOf("")}
    val accion=recordarAccion()
    val aviso by Sesion.aviso.collectAsState()
    Pagina("Tu cuenta") {
        aviso?.let {Text(it)}
        Text("Inicia sesión para administrar tus publicaciones y avisos.")
        Campo("Correo",correo,{correo=it});Campo("Contraseña",clave,{clave=it},secreto=true)
        EstadoAccion(accion)
        Button(enabled=!accion.ocupada && correo.isNotBlank() && clave.isNotBlank(),onClick={accion.ejecutar {
            val respuesta=RetrofitClient.api.login(Login(correo.trim(),clave));Sesion.guardar(respuesta.token)
        }}) {Text("Iniciar sesión")}
        TextButton(onClick=registro,enabled=!accion.ocupada) {Text("Crear cuenta")}
        TextButton(onClick={verificar(correo.trim())},enabled=!accion.ocupada) {Text("Verificar mi correo")}
        TextButton(onClick=recuperar,enabled=!accion.ocupada) {Text("Olvidé mi contraseña")}
    }
}
@Composable fun RegistroScreen(volver:()->Unit,verificar:(String)->Unit) {
    Pagina("Crear cuenta",volver) {
        Consulta("documentos",{RetrofitClient.api.documentos()}) { docs,recargar ->
            var nombre by remember {mutableStateOf("")};var apellido by remember {mutableStateOf("")}
            var correo by remember {mutableStateOf("")};var clave by remember {mutableStateOf("")}
            var confirmar by remember {mutableStateOf("")}
            var acepta by remember(docs.version,docs.sha256) {mutableStateOf(false)}
            var leer by remember {mutableStateOf<String?>(null)}
            val accion=recordarAccion()
            Text("Términos y privacidad del proyecto. Pendientes de revisión institucional.")
            Text("Versión: ${docs.version}")
            TextButton(onClick={leer=docs.terminos}) {Text("Leer términos de uso")}
            TextButton(onClick={leer=docs.privacidad}) {Text("Leer política de privacidad")}
            leer?.let { texto -> AlertDialog(onDismissRequest={leer=null},title={Text("Documento presentado")},
                text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())) {Text(texto)}},
                confirmButton={TextButton(onClick={leer=null}) {Text("Cerrar documento")}}) }
            Campo("Nombre",nombre,{nombre=it});Campo("Apellido",apellido,{apellido=it});Campo("Correo",correo,{correo=it})
            Text("Contraseña: 8 caracteres como mínimo, mayúscula, minúscula, número y símbolo; hasta 72 bytes.")
            Campo("Contraseña",clave,{clave=it},secreto=true);Campo("Confirmar contraseña",confirmar,{confirmar=it},secreto=true)
            Row(Modifier.toggleable(value=acepta,role=Role.Checkbox,enabled=!accion.ocupada,onValueChange={acepta=it})) {Checkbox(checked=acepta,onCheckedChange=null,enabled=!accion.ocupada);Text("Acepto los términos y la política de privacidad de la versión presentada",Modifier.weight(1f))}
            EstadoAccion(accion)
            Button(enabled=acepta && !accion.ocupada && nombre.isNotBlank() && apellido.isNotBlank() && correo.isNotBlank() && clave==confirmar && clave.isNotBlank(),onClick={accion.ejecutar {
                RetrofitClient.api.registrar(Registro(nombre.trim(),apellido.trim(),correo.trim(),clave,true,docs.version))
                verificar(correo.trim())
            }}) {Text("Registrarme y recibir código")}
            TextButton(onClick=recargar,enabled=!accion.ocupada) {Text("Volver a cargar documentos")}
        }
    }
}
@Composable fun VerificarScreen(correoInicial:String,volver:()->Unit) {
    var correo by remember {mutableStateOf(correoInicial)};var codigo by remember {mutableStateOf("")}
    var espera by remember {mutableIntStateOf(0)}
    val accion=recordarAccion()
    LaunchedEffect(espera) {if(espera>0) {delay(1000);espera--}}
    Pagina("Verificar correo",volver) {
        Text("Ingresa el código recibido por correo. La cuenta no se activa hasta verificarlo.")
        Campo("Correo",correo,{correo=it});Campo("Código de 6 dígitos",codigo,{codigo=it.filter(Char::isDigit).take(6)})
        EstadoAccion(accion)
        Button(enabled=!accion.ocupada && codigo.length==6 && correo.isNotBlank(),onClick={accion.ejecutar {
            val respuesta=RetrofitClient.api.verificar(Verificacion(correo.trim(),codigo));Sesion.guardar(respuesta.token)
        }}) {Text("Verificar y entrar")}
        TextButton(enabled=!accion.ocupada && espera==0 && correo.isNotBlank(),onClick={accion.ejecutar {
            try {
                val respuesta=RetrofitClient.api.reenviar(Correo(correo.trim()));espera=60;accion.mensaje=respuesta.mensaje
            } catch(e:retrofit2.HttpException) {
                if(e.code()==429) espera=e.response()?.headers()?.get("Retry-After")?.toIntOrNull()?.coerceIn(1,3600) ?: 60
                throw e
            }
        }}) {Text(if(espera>0) "Reenviar en $espera s" else "Reenviar código")}
    }
}
@Composable fun CuentaScreen(abrir:(String)->Unit) {
    val token by Sesion.token.collectAsState()
    Pagina("Mi cuenta") {
        Consulta(token,{RetrofitClient.api.perfil()}) { usuario,_ ->
            Text("${usuario.nombre} ${usuario.apellido}",style=MaterialTheme.typography.headlineSmall)
            Text(usuario.correo)
            Button(onClick={abrir("propios")}) {Text("Mis publicaciones")}
            Button(onClick={abrir("avisos")}) {Text("Mis notificaciones")}
            Button(onClick={abrir("preferencias")}) {Text("Preferencias de avisos")}
            OutlinedButton(onClick={abrir("eliminacion")}) {Text("Solicitar eliminación de cuenta")}
        }
        OutlinedButton(onClick={Sesion.cerrar()}) {Text("Cerrar sesión")}
    }
}
