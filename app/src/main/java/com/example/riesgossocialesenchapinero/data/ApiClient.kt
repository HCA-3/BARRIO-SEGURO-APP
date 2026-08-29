package com.example.riesgossocialesenchapinero.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Cliente HTTP hacia backend_riesgo.py (ver ../../../../../../../Api/backend_riesgo.py).
 *
 * BASE_URL por defecto apunta al emulador de Android Studio (10.0.2.2 es la
 * IP especial que el emulador usa para llegar al "localhost" de la máquina
 * host). Para un celular físico en la misma wifi que el PC, cambiar por la
 * IP local del PC, ej. "http://192.168.1.23:8000/" (se ve con "ipconfig").
 */
object ApiClient {
    var baseUrl: String = "http://10.0.2.2:8000/"

    // El chat con el modelo local (CPU) puede tardar varios minutos en la
    // primera respuesta: timeouts largos a propósito, no es un valor por
    // descuido.
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class Localidad(
        val posicion: Int,
        val nombre: String,
        val nivelRiesgo: String,
        val scorePonderado100k: Double,
        val tasaDelitos100k: Double,
    )

    data class MensajeChat(val role: String, val content: String)

    data class RespuestaChat(val respuesta: String, val mensajes: List<MensajeChat>)

    data class RiesgoPorPunto(val localidad: String, val nivelRiesgo: String, val scoreMixto: Double)

    class ApiException(message: String) : Exception(message)

    fun obtenerRanking(): List<Localidad> {
        val request = Request.Builder().url(baseUrl + "zonas/ranking").get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException("Error ${resp.code} consultando el ranking")
            val arr = JSONArray(resp.body?.string() ?: "[]")
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Localidad(
                    posicion = o.getInt("posicion"),
                    nombre = o.getString("localidad"),
                    nivelRiesgo = o.getString("nivel_riesgo"),
                    scorePonderado100k = o.getDouble("score_ponderado_100k"),
                    tasaDelitos100k = o.getDouble("tasa_delitos_100k"),
                )
            }
        }
    }

    /** null si el punto no cae dentro de ninguna localidad de Bogotá (ej. fuera de la ciudad). */
    fun consultarRiesgoPorPunto(lat: Double, lng: Double): RiesgoPorPunto? {
        val request = Request.Builder().url(baseUrl + "riesgo?lat=$lat&lng=$lng").get().build()
        client.newCall(request).execute().use { resp ->
            if (resp.code == 404) return null
            if (!resp.isSuccessful) throw ApiException("Error ${resp.code} consultando el riesgo por ubicación")
            val o = JSONObject(resp.body?.string() ?: "{}")
            return RiesgoPorPunto(
                localidad = o.getString("localidad"),
                nivelRiesgo = o.getString("nivel_riesgo"),
                scoreMixto = o.getDouble("score_mixto"),
            )
        }
    }

    fun enviarMensajeChat(historial: List<MensajeChat>): RespuestaChat {
        val mensajesJson = JSONArray()
        for (m in historial) {
            mensajesJson.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        val cuerpo = JSONObject().put("mensajes", mensajesJson).toString()
        val request = Request.Builder()
            .url(baseUrl + "chat")
            .post(cuerpo.toRequestBody(jsonMediaType))
            .build()

        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException("Error ${resp.code} hablando con el agente")
            val json = JSONObject(resp.body?.string() ?: "{}")
            val mensajesResp = json.getJSONArray("mensajes")
            val historialActualizado = List(mensajesResp.length()) { i ->
                val o = mensajesResp.getJSONObject(i)
                MensajeChat(role = o.getString("role"), content = o.optString("content", ""))
            }
            return RespuestaChat(respuesta = json.getString("respuesta"), mensajes = historialActualizado)
        }
    }
}
