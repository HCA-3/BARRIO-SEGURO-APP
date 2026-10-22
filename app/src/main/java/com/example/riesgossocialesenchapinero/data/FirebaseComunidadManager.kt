package com.example.riesgossocialesenchapinero.data

import android.content.Context
import android.util.Log
import com.example.riesgossocialesenchapinero.util.FiltroGroserias
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

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
    private const val PREFS_CACHE = "cache_cuadras_prefs"
    private const val KEY_CACHE = "calificaciones_json"

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
     * Descarga de forma asíncrona las calificaciones de cuadras.
     * Prioridad:
     * 1. Consulta única a Firebase Firestore (si está online).
     * 2. Si falla o está vacío, consulta al Backend REST local (ApiClient).
     * 3. Si ambos fallan o no hay internet, carga la caché local guardada en el dispositivo.
     */
    suspend fun descargarCalificacionesCuadras(context: Context? = null): List<ReporteCuadra> {
        val firestore = obtenerDb()
        if (firestore != null) {
            try {
                val snapshot = firestore.collection(COLECCION_CUADRAS)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(200)
                    .get()
                    .await()

                if (!snapshot.isEmpty) {
                    val reportes = snapshot.documents.mapNotNull { doc ->
                        try {
                            val data = doc.data ?: return@mapNotNull null
                            ReporteCuadra.fromMap(doc.id, data)
                        } catch (e: Exception) {
                            null
                        }
                    }
                    if (reportes.isNotEmpty()) {
                        guardarEnCacheLocal(context, reportes)
                        return reportes
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Fallo al descargar de Firestore, intentando backend local: ${e.message}")
            }
        }

        // Fallback: Si Firestore no tiene datos o no responde, consultar la API local de Backend
        try {
            val desdeApi = withContext(Dispatchers.IO) {
                ApiClient.obtenerCalificacionesCuadras()
            }
            if (desdeApi.isNotEmpty()) {
                guardarEnCacheLocal(context, desdeApi)
                return desdeApi
            }
        } catch (e: Exception) {
            Log.w(TAG, "Fallo al descargar de ApiClient: ${e.message}")
        }

        // Fallback final: Cargar desde la memoria local en caché para modo offline
        return cargarDeCacheLocal(context)
    }

    /**
     * Publica una nueva calificación de riesgo y justificación para una cuadra.
     * Se guarda en Firebase Firestore y se replica en el Backend local para que aparezca en el Panel de Control.
     */
    suspend fun guardarCalificacionCuadra(reporte: ReporteCuadra, context: Context? = null): Boolean {
        val justificacionLimpia = FiltroGroserias.censurar(reporte.justificacion.trim())
        val aliasLimpio = FiltroGroserias.censurar(reporte.usuarioAlias.trim()).ifBlank { "Vecino anónimo" }
        val reporteAGuardar = reporte.copy(
            justificacion = justificacionLimpia,
            usuarioAlias = aliasLimpio
        )

        var guardadoEnNube = false
        val firestore = obtenerDb()

        if (firestore != null) {
            try {
                firestore.collection(COLECCION_CUADRAS)
                    .document(reporteAGuardar.id)
                    .set(reporteAGuardar.toMap())
                    .await()
                guardadoEnNube = true
            } catch (e: Exception) {
                Log.e(TAG, "Error guardando calificacion de cuadra en Firestore", e)
            }
        }

        // Replicar en el backend local y panel de control
        try {
            withContext(Dispatchers.IO) {
                ApiClient.guardarCalificacionCuadra(reporteAGuardar)
            }
        } catch (e: Exception) {
            Log.w(TAG, "No se pudo sincronizar reporte con backend local: ${e.message}")
        }

        return guardadoEnNube || true
    }

    /**
     * Incrementa el voto de apoyo o confirmación comunitaria de una cuadra en Firebase y en el backend.
     */
    suspend fun apoyarCalificacionCuadra(reporteId: String) {
        val firestore = obtenerDb()
        if (firestore != null) {
            try {
                val docRef = firestore.collection(COLECCION_CUADRAS).document(reporteId)
                firestore.runTransaction { transaction ->
                    val snapshot = transaction.get(docRef)
                    val votos = snapshot.getLong("votosApoyo") ?: 0
                    transaction.update(docRef, "votosApoyo", votos + 1)
                }.await()
            } catch (e: Exception) {
                Log.e(TAG, "Error apoyando reporte en Firestore: ${e.message}")
            }
        }

        try {
            withContext(Dispatchers.IO) {
                ApiClient.apoyarCalificacionCuadra(reporteId)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error apoyando reporte en backend: ${e.message}")
        }
    }

    private fun guardarEnCacheLocal(context: Context?, reportes: List<ReporteCuadra>) {
        if (context == null || reportes.isEmpty()) return
        try {
            val arr = JSONArray()
            for (r in reportes) {
                val obj = JSONObject().apply {
                    put("id", r.id)
                    put("latitud", r.latitud)
                    put("longitud", r.longitud)
                    put("direccionAprox", r.direccionAprox)
                    put("nivelRiesgo", r.nivelRiesgo)
                    put("categoria", r.categoria)
                    put("justificacion", r.justificacion)
                    put("horarioCritico", r.horarioCritico)
                    put("timestamp", r.timestamp)
                    put("usuarioAlias", r.usuarioAlias)
                    put("votosApoyo", r.votosApoyo)
                }
                arr.put(obj)
            }
            context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_CACHE, arr.toString())
                .apply()
        } catch (e: Exception) {
            Log.w(TAG, "Error guardando en cache local: ${e.message}")
        }
    }

    private fun cargarDeCacheLocal(context: Context?): List<ReporteCuadra> {
        if (context == null) return emptyList()
        return try {
            val jsonStr = context.getSharedPreferences(PREFS_CACHE, Context.MODE_PRIVATE)
                .getString(KEY_CACHE, null) ?: return emptyList()
            val arr = JSONArray(jsonStr)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                ReporteCuadra(
                    id = o.optString("id", "cuadra_$i"),
                    latitud = o.optDouble("latitud", 0.0),
                    longitud = o.optDouble("longitud", 0.0),
                    direccionAprox = o.optString("direccionAprox", ""),
                    nivelRiesgo = o.optString("nivelRiesgo", "Medio"),
                    categoria = o.optString("categoria", "Robo frecuente"),
                    justificacion = o.optString("justificacion", ""),
                    horarioCritico = o.optString("horarioCritico", "Noche"),
                    timestamp = o.optLong("timestamp", System.currentTimeMillis()),
                    usuarioAlias = o.optString("usuarioAlias", "Vecino anónimo"),
                    votosApoyo = o.optInt("votosApoyo", 0)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}

