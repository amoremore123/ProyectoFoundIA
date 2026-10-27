package com.proyectointegrador.movil.network

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Solo conserva la sesión cifrada; no almacena contraseñas ni una base de datos local. */
object Sesion {
    private const val ALIAS = "foundia_sesion"
    private lateinit var preferencias: android.content.SharedPreferences
    private val _token = MutableStateFlow<String?>(null)
    val token = _token.asStateFlow()
    val actual: String? get() = _token.value

    @Synchronized fun iniciar(contexto: Context) {
        if (::preferencias.isInitialized) return
        preferencias = contexto.applicationContext.getSharedPreferences("sesion", Context.MODE_PRIVATE)
        _token.value = runCatching {
            preferencias.getString("acceso", null)?.let { valor ->
                val partes=valor.split(":")
                val cifrador=Cipher.getInstance("AES/GCM/NoPadding")
                cifrador.init(Cipher.DECRYPT_MODE, llave(), GCMParameterSpec(128,Base64.decode(partes[0],Base64.NO_WRAP)))
                String(cifrador.doFinal(Base64.decode(partes[1],Base64.NO_WRAP)),Charsets.UTF_8)
            }
        }.getOrElse { preferencias.edit().clear().commit(); null }
    }
    @Synchronized fun guardar(token: String) {
        val cifrador=Cipher.getInstance("AES/GCM/NoPadding");cifrador.init(Cipher.ENCRYPT_MODE,llave())
        val valor=Base64.encodeToString(cifrador.iv,Base64.NO_WRAP)+":"+
            Base64.encodeToString(cifrador.doFinal(token.toByteArray(Charsets.UTF_8)),Base64.NO_WRAP)
        check(preferencias.edit().putString("acceso",valor).commit()) { "No se pudo guardar la sesión" }
        _token.value=token
    }
    @Synchronized fun cerrar(esperado: String? = actual) {
        if (actual != esperado) return
        preferencias.edit().clear().commit();_token.value=null
    }
    private fun llave(): SecretKey {
        val almacen=KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (almacen.getKey(ALIAS,null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
}
