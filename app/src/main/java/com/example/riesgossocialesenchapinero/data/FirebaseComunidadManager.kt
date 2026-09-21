package com.example.riesgossocialesenchapinero.data

import android.util.Log
import com.example.riesgossocialesenchapinero.util.FiltroGroserias
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Gestor de mensajes y alertas comunitarias en tiempo real utilizando Firebase Firestore.
 *
 * Ventajas:
 * 1. 100% gratuito con la cuota de Firebase (Spark Plan).
 * 2. Tiempo real: Todos los celulares con la app reciben los reportes instantáneamente sin recargar.
 * 3. Persistencia Offline: Si no hay señal o internet, guarda el mensaje localmente y lo sincroniza al reconectar.
 * 4. Sin servidor en PC: Funciona 24/7 sin necesidad de encender backend local.
 */
object FirebaseComunidadManager {
    private const val TAG = "FirebaseComunidad"
    private const val COLECCION_MENSAJES = "mensajes_comunidad"

    private var db: FirebaseFirestore? = null

    private fun obtenerDb(): FirebaseFirestore? {
        if (db == null) {
            try {
                db = FirebaseFirestore.getInstance()
            } catch (e: Exception) {
                Log.w(TAG, "Firebase Firestore no inicializado (usando fallback local): ${e.message}")
            }
        }
        return db
    }

    /**
     * Escucha mensajes en tiempo real desde Firestore.
     * Devuelve una [ListenerRegistration] para desuscribirse cuando la pantalla se destruya.
     */
    fun escucharMensajes(
        miAlias: String,
        onMensajesActualizados: (List<ApiClient.MensajeComunidad>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration? {
        val firestore = obtenerDb() ?: return null

        return try {
            firestore.collection(COLECCION_MENSAJES)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limitToLast(100)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error escuchando mensajes de Firestore", error)
                        onError(error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val lista = snapshot.documents.mapNotNull { doc ->
                            try {
                                val alias = doc.getString("alias_anonimo") ?: "Vecino Anónimo"
                                ApiClient.MensajeComunidad(
                                    id = doc.id,
                                    aliasAnonimo = alias,
                                    avatarColor = doc.getString("avatar_color") ?: "#00E5FF",
                                    texto = doc.getString("texto") ?: "",
                                    imagenBase64 = doc.getString("imagen_base64"),
                                    localidad = doc.getString("localidad") ?: "Bogotá",
                                    timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                                    esAlerta = doc.getBoolean("es_alerta") ?: false,
                                    esMio = alias == miAlias
                                )
                            } catch (e: Exception) {
                                null
                            }
                        }
                        onMensajesActualizados(lista)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo registrar el listener de Firestore: ${e.message}")
            onError(e)
            null
        }
    }

    /**
     * Publica un mensaje o alerta comunitaria en Firebase Firestore.
     */
    suspend fun enviarMensaje(
        texto: String,
        imagenBase64: String?,
        aliasAnonimo: String,
        localidad: String = "Bogotá",
        esAlerta: Boolean = false
    ): ApiClient.MensajeComunidad {
        val firestore = obtenerDb()
        val textoCensurado = FiltroGroserias.censurar(texto.trim())
        val aliasCensurado = FiltroGroserias.censurar(aliasAnonimo.trim()).ifBlank { "Vecino Anónimo" }

        val colores = listOf("#00E5FF", "#FFAB00", "#00E676", "#FF1744", "#D500F9", "#FF6D00", "#2979FF")
        val idxColor = kotlin.math.abs(aliasCensurado.hashCode()) % colores.size
        val avatarColor = colores[idxColor]

        val timestampActual = System.currentTimeMillis()

        val data = hashMapOf(
            "alias_anonimo" to aliasCensurado,
            "avatar_color" to avatarColor,
            "texto" to textoCensurado,
            "imagen_base64" to imagenBase64,
            "localidad" to localidad,
            "timestamp" to timestampActual,
            "es_alerta" to esAlerta
        )

        if (firestore != null) {
            try {
                val docRef = firestore.collection(COLECCION_MENSAJES).add(data).await()
                return ApiClient.MensajeComunidad(
                    id = docRef.id,
                    aliasAnonimo = aliasCensurado,
                    avatarColor = avatarColor,
                    texto = textoCensurado,
                    imagenBase64 = imagenBase64,
                    localidad = localidad,
                    timestamp = timestampActual,
                    esAlerta = esAlerta,
                    esMio = true
                )
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al enviar a Firestore, usando fallback local: ${e.message}")
            }
        }

        // Fallback local en caso de que Firestore aún no esté configurado con credenciales
        return ApiClient.enviarMensajeComunidad(
            texto = textoCensurado,
            imagenBase64 = imagenBase64,
            aliasAnonimo = aliasCensurado,
            localidad = localidad,
            esAlerta = esAlerta
        )
    }

    private const val COLECCION_CUADRAS = "calificaciones_cuadras"

    /**
     * Escucha las calificaciones y justificaciones de cuadras en tiempo real.
     */
    fun escucharCalificacionesCuadras(
        onActualizado: (List<ReporteCuadra>) -> Unit,
        onError: (Exception) -> Unit = {}
    ): ListenerRegistration? {
        val firestore = obtenerDb() ?: return null

        return try {
            firestore.collection(COLECCION_CUADRAS)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(200)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Error escuchando calificaciones de cuadras", error)
                        onError(error)
                        return@addSnapshotListener
                    }

                    if (snapshot != null) {
                        val reportes = snapshot.documents.mapNotNull { doc ->
                            try {
                                val data = doc.data ?: return@mapNotNull null
                                ReporteCuadra.fromMap(doc.id, data)
                            } catch (e: Exception) {
                                null
                            }
                        }
                        onActualizado(reportes)
                    }
                }
        } catch (e: Exception) {
            Log.e(TAG, "No se pudo registrar listener de cuadras: ${e.message}")
            onError(e)
            null
        }
    }

    /**
     * Publica una nueva calificación de riesgo y justificación para una cuadra.
     */
    suspend fun guardarCalificacionCuadra(reporte: ReporteCuadra): Boolean {
        val firestore = obtenerDb() ?: return false
        return try {
            val justificacionLimpia = FiltroGroserias.censurar(reporte.justificacion.trim())
            val aliasLimpio = FiltroGroserias.censurar(reporte.usuarioAlias.trim()).ifBlank { "Vecino anónimo" }
            val reporteAGuardar = reporte.copy(
                justificacion = justificacionLimpia,
                usuarioAlias = aliasLimpio
            )
            firestore.collection(COLECCION_CUADRAS)
                .add(reporteAGuardar.toMap())
                .await()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error guardando calificacion de cuadra en Firestore", e)
            false
        }
    }

    /**
     * Incrementa el voto de apoyo o confirmación comunitaria de una cuadra.
     */
    suspend fun apoyarCalificacionCuadra(reporteId: String) {
        val firestore = obtenerDb() ?: return
        try {
            val docRef = firestore.collection(COLECCION_CUADRAS).document(reporteId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                val votos = snapshot.getLong("votosApoyo") ?: 0
                transaction.update(docRef, "votosApoyo", votos + 1)
            }.await()
        } catch (e: Exception) {
            Log.e(TAG, "Error apoyando reporte: ${e.message}")
        }
    }
}

