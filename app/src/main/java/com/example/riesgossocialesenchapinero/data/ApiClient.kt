package com.example.riesgossocialesenchapinero.data

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
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

    data class RespuestaChat(
        val respuesta: String,
        val mensajes: List<MensajeChat>,
        val hechosNuevos: List<String>,
    )

    data class RiesgoUpz(val upz: String, val nivelLlamadas: String, val tasaLlamadas100k: Double)

    data class RiesgoPorPunto(
        val localidad: String,
        val nivelRiesgo: String,
        val scoreMixto: Double,
        val upz: RiesgoUpz?,
    )

    data class ResultadoBarrio(
        val barrio: String,
        val localidad: String,
        val nivelRiesgo: String,
        val scoreMixto: Double,
        val upz: RiesgoUpz?,
    )

    sealed interface BusquedaBarrio {
        data class Encontrado(val resultado: ResultadoBarrio) : BusquedaBarrio
        data class Ambiguo(val opciones: List<ResultadoBarrio>) : BusquedaBarrio
        data class NoEncontrado(val mensaje: String) : BusquedaBarrio
    }

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
            val upzJson = o.optJSONObject("upz")
            return RiesgoPorPunto(
                localidad = o.getString("localidad"),
                nivelRiesgo = o.getString("nivel_riesgo"),
                scoreMixto = o.getDouble("score_mixto"),
                upz = upzJson?.let {
                    RiesgoUpz(
                        upz = it.getString("upz"),
                        nivelLlamadas = it.getString("nivel_llamadas"),
                        tasaLlamadas100k = it.getDouble("tasa_llamadas_100k"),
                    )
                },
            )
        }
    }

    /**
     * Búsqueda directa de barrio -> localidad/riesgo, SIN pasar por el chat
     * conversacional (determinístico, mismo tool_buscar_barrio del backend).
     */
    fun buscarBarrio(nombre: String, localidad: String? = null): BusquedaBarrio {
        val url = buildString {
            append(baseUrl)
            append("barrios/buscar?nombre=")
            append(URLEncoder.encode(nombre, "UTF-8"))
            if (!localidad.isNullOrBlank()) {
                append("&localidad=")
                append(URLEncoder.encode(localidad, "UTF-8"))
            }
        }
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException("Error ${resp.code} buscando el barrio")
            val o = JSONObject(resp.body?.string() ?: "{}")
            if (o.has("opciones")) {
                val arr = o.getJSONArray("opciones")
                val opciones = List(arr.length()) { i -> parseResultadoBarrio(arr.getJSONObject(i)) }
                return BusquedaBarrio.Ambiguo(opciones)
            }
            if (o.has("error")) {
                return BusquedaBarrio.NoEncontrado(o.getString("error"))
            }
            return BusquedaBarrio.Encontrado(parseResultadoBarrio(o))
        }
    }

    private fun parseResultadoBarrio(o: JSONObject): ResultadoBarrio {
        val upzJson = o.optJSONObject("upz")
        return ResultadoBarrio(
            barrio = o.getString("barrio"),
            localidad = o.getString("localidad"),
            nivelRiesgo = o.getString("nivel_riesgo"),
            scoreMixto = o.getDouble("score_mixto"),
            upz = upzJson?.let {
                RiesgoUpz(
                    upz = it.getString("upz"),
                    nivelLlamadas = it.getString("nivel_llamadas"),
                    tasaLlamadas100k = it.getDouble("tasa_llamadas_100k"),
                )
            },
        )
    }

    fun enviarMensajeChat(historial: List<MensajeChat>, hechosRecordados: List<String> = emptyList()): RespuestaChat {
        val mensajesJson = JSONArray()
        for (m in historial) {
            mensajesJson.put(JSONObject().put("role", m.role).put("content", m.content))
        }
        val hechosJson = JSONArray()
        for (h in hechosRecordados) hechosJson.put(h)
        val cuerpo = JSONObject()
            .put("mensajes", mensajesJson)
            .put("hechos_recordados", hechosJson)
            .toString()
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
            val hechosResp = json.optJSONArray("hechos_nuevos") ?: JSONArray()
            val hechosNuevos = List(hechosResp.length()) { i -> hechosResp.getString(i) }
            return RespuestaChat(
                respuesta = json.getString("respuesta"),
                mensajes = historialActualizado,
                hechosNuevos = hechosNuevos,
            )
        }
    }
}
