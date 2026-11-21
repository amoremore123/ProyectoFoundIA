package com.proyectointegrador.movil.data.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/** Lo que la app necesita de Firebase. Sin proyecto configurado, [SinFirebase] responde sin fallar. */
interface ServicioFirebase {
    val disponible: Boolean
    suspend fun tokenPush(): String?
    fun borrarTokenPush()
    suspend fun entrarConToken(tokenPersonalizado: String, uidEsperado: String): Boolean
    fun salir()
}

object SinFirebase : ServicioFirebase {
    override val disponible = false
    override suspend fun tokenPush(): String? = null
    override fun borrarTokenPush() {}
    override suspend fun entrarConToken(tokenPersonalizado: String, uidEsperado: String) = false
    override fun salir() {}
}

/** Firebase real. Solo hay proyecto si `google-services.json` existió al compilar. */
class FirebaseReal(private val contexto: Context) : ServicioFirebase {
    override val disponible: Boolean get() = FirebaseApp.getApps(contexto).isNotEmpty()

    override suspend fun tokenPush(): String? {
        if (!disponible) return null
        return suspendCancellableCoroutine { cont ->
            FirebaseMessaging.getInstance().token.addOnCompleteListener { tarea ->
                cont.resume(if (tarea.isSuccessful) tarea.result else null)
            }
        }
    }

    override fun borrarTokenPush() {
        if (disponible) runCatching { FirebaseMessaging.getInstance().deleteToken() }
    }

    override suspend fun entrarConToken(tokenPersonalizado: String, uidEsperado: String): Boolean {
        if (!disponible) return false
        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser?.uid == uidEsperado) return true
        return suspendCancellableCoroutine { cont ->
            auth.signInWithCustomToken(tokenPersonalizado).addOnCompleteListener { tarea ->
                // El uid que devuelve Firebase debe ser el de la cuenta de FoundIA.
                cont.resume(tarea.isSuccessful && tarea.result?.user?.uid == uidEsperado)
            }
        }
    }

    override fun salir() {
        if (disponible) runCatching { FirebaseAuth.getInstance().signOut() }
    }
}

fun crearServicioFirebase(contexto: Context): ServicioFirebase =
    runCatching { FirebaseReal(contexto.applicationContext) }.getOrDefault(SinFirebase)
