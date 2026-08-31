package com.example.riesgossocialesenchapinero.data

import android.content.Context
import android.content.SharedPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**  
 * Cliente HTTP hacia backend_riesgo.py (ver ../../../../../../../Api/backend_riesgo.py).
 *
 * La URL del backend ya no está fija en el código: se guarda en
 * SharedPreferences y se puede cambiar desde la propia app (pantalla de error
 * -> campo "Servidor"), porque la IP del PC cambia según dónde se pruebe y
 * recompilar por cada cambio de red es un estorbo. Si no hay ninguna guardada,
 * [autodetectar] recorre [CANDIDATOS] y se queda con el primero que responda.
 */
object ApiClient {
    private const val PREFS = "barrio_seguro_ajustes"
    private const val CLAVE_URL = "base_url"

    /**
     * Se prueban en este orden cuando no hay una URL guardada:
     *  - 10.0.2.2  emulador de Android Studio (su alias del localhost del PC).
     *  - 127.0.0.1 celular por USB con "adb reverse tcp:8000 tcp:8000". Es el
     *    único que sirve si el celular y el PC están en redes distintas.
     *  - 192.168.x celular en la MISMA wifi que el PC (la IP sale con
     *    "ipconfig"; también se puede escribir a mano desde la app).
     */
    val CANDIDATOS = listOf(
        "http://10.0.2.2:8000/",
        "http://127.0.0.1:8000/",
        "http://192.168.0.107:8000/",
    )

    private var prefs: SharedPreferences? = null

    var baseUrl: String = CANDIDATOS.first()
        set(valor) {
            field = normalizar(valor)
            prefs?.edit()?.putString(CLAVE_URL, field)?.apply()
        }

    /** Llamar una vez al arrancar (MainActivity) para recuperar la URL guardada. */
    fun inicializar(context: Context) {
        val guardadas = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = guardadas
        guardadas.getString(CLAVE_URL, null)?.let { baseUrl = it }
    }

    /** Acepta "192.168.0.107", "192.168.0.107:8000" o la URL completa. */
    private fun normalizar(valor: String): String {
        var url = valor.trim()
        if (url.isEmpty()) return CANDIDATOS.first()
        if (!url.startsWith("http://") && !url.startsWith("https://")) url = "http://" + url
        if (!url.substringAfter("://").contains(":")) url = url + ":8000"
        if (!url.endsWith("/")) url = url + "/"
        return url
    }

    // El chat con el modelo local (CPU) puede tardar varios minutos en la
    // primera respuesta: los timeouts de lectura/escritura son largos a
    // propósito, no es un valor por descuido. El de conexión sí es corto: el
    // backend está en la red local, y si no contesta rápido es que no está ahí
    // y conviene pasar cuanto antes a probar las otras direcciones.
    private val client = OkHttpClient.Builder()
        .connectTimeout(4, TimeUnit.SECONDS)
        // 660s > los 600s que el backend le da a Ollama (ver preguntar() en
        // backend_riesgo.py). Estaba en 300s, y con el modelo corriendo en CPU
        // eso hacía que la app se rindiera a mitad de una respuesta que el
        // backend sí iba a entregar. Al ser mayor, si algo falla gana el
        // timeout del backend y llega su mensaje de error en vez de un corte seco.
        .readTimeout(660, TimeUnit.SECONDS)
        .writeTimeout(660, TimeUnit.SECONDS)
        .build()

    // Para sondear candidatos: si el PC no está en esa red el intento tiene que
    // fallar rápido, o recorrer la lista tardaría medio minuto.
    private val clientSondeo = client.newBuilder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(3, TimeUnit.SECONDS)
        .build()

    /** true si hay un backend vivo en [url] (responde /health). */
    fun servidorResponde(url: String): Boolean = try {
        val request = Request.Builder().url(normalizar(url) + "health").get().build()
        clientSondeo.newCall(request).execute().use { it.isSuccessful }
    } catch (e: Exception) {
        false
    }

    /**
     * Prueba la URL actual y luego [CANDIDATOS]; fija y devuelve la primera que
     * responda, o null si ninguna lo hace.
     */
    fun autodetectar(): String? {
        for (url in listOf(baseUrl) + CANDIDATOS) {
            if (servidorResponde(url)) {
                baseUrl = url
                return baseUrl
            }
        }
        return null
    }

    /**
     * Envuelve una petición: si falla por no poder conectar (la URL guardada
     * dejó de servir porque se cambió de emulador a celular, de wifi, etc.),
     * busca el backend en los otros candidatos y reintenta una vez.
     *
     * Va aquí y no en los ViewModel a propósito: así lo aprovechan por igual el
     * ranking, el chat y el servicio de ubicación, sin repetir la lógica en cada
     * uno. Solo reintenta ante IOException (fallo de conexión); un error HTTP
     * del backend significa que sí lo encontramos y reintentar no arreglaría nada.
     */
    private fun <T> conAutodeteccion(peticion: () -> T): T = try {
        peticion()
    } catch (e: IOException) {
        if (autodetectar() == null) throw e
        peticion()
    }

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    data class Localidad(
        val posicion: Int,
        val nombre: String,
        val nivelRiesgo: String,
        val scorePonderado100k: Double,
        val tasaDelitos100k: Double,
    )

    // toolCalls: JSON crudo (como llega de Ollama) del array "tool_calls" de
    // un mensaje "assistant", o null si ese mensaje no llamó ninguna
    // herramienta. Hay que reenviarlo tal cual en el siguiente turno -- si
    // se pierde, el mensaje "tool" que sigue queda sin el turno que lo
    // originó y el modelo pierde el hilo en la pregunta de seguimiento.
    data class MensajeChat(val role: String, val content: String, val toolCalls: String? = null)

    data class RespuestaChat(
        val respuesta: String,
        val mensajes: List<MensajeChat>,
        val hechosNuevos: List<String>,
    )

    data class RiesgoUpz(val upz: String, val nivelLlamadas: String, val tasaLlamadas100k: Double)

    data class Sismo(
        val id: String,
        val magnitud: Double,
        val lugar: String,
        val tiempo: Long,
        val profundidadKm: Double,
        val lat: Double,
        val lng: Double,
        val distanciaBogotaKm: Double,
        val sentido: Int = 0,
        val alerta: String = "",
        val url: String = ""
    )

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

    /**
     * Todo lo que el pipeline recopiló de una localidad. OJO: no hay cifras por
     * barrio — los delitos y el contexto se miden por localidad, y las llamadas
     * de emergencia por UPZ. La UI tiene que dejar claro de qué capa es cada
     * número para no dar a entender que son del barrio concreto.
     */
    data class DetalleLocalidad(
        val localidad: String,
        val nivelRiesgo: String,
        val poblacion: Int,
        val delitosTotal: Int,
        val tasaDelitos100k: Double,
        val scoreMixto: Double,
        val detalleDelitos: List<Pair<String, Int>>,
        val estratoPromedio: Double,
        val luminarias: Int,
        val luminariasPorKm2: Double,
        val longitudViasKm: Double,
        val areaKm2: Double,
        val incidentesNuse: Int,
    )

    class ApiException(message: String) : Exception(message)

    fun obtenerRanking(): List<Localidad> = conAutodeteccion { obtenerRankingUnaVez() }

    private fun obtenerRankingUnaVez(): List<Localidad> {
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

    fun obtenerSismosRecientes(): List<Sismo> {
        return try {
            conAutodeteccion { obtenerSismosRecientesUnaVez() }
        } catch (e: Exception) {
            try {
                consultarUsgsDirecto()
            } catch (e2: Exception) {
                obtenerSismosFallback()
            }
        }
    }

    private fun obtenerSismosRecientesUnaVez(): List<Sismo> {
        val request = Request.Builder().url(baseUrl + "sismos/recientes").get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException("Error ${resp.code} consultando sismos")
            val arr = JSONArray(resp.body?.string() ?: "[]")
            return List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Sismo(
                    id = o.getString("id"),
                    magnitud = o.getDouble("magnitud"),
                    lugar = o.getString("lugar"),
                    tiempo = o.getLong("tiempo"),
                    profundidadKm = o.getDouble("profundidad_km"),
                    lat = o.getDouble("lat"),
                    lng = o.getDouble("lng"),
                    distanciaBogotaKm = o.getDouble("distancia_bogota_km"),
                    sentido = o.optInt("sentido", 0),
                    alerta = o.optString("alerta", ""),
                    url = o.optString("url", "")
                )
            }
        }
    }

    /**
     * Consulta directa a la API pública oficial de USGS (United States Geological Survey).
     * Funciona de manera 100% independiente del backend local.
     */
    fun consultarUsgsDirecto(): List<Sismo> {
        val url = "https://earthquake.usgs.gov/fdsnws/event/1/query?format=geojson&latitude=4.71&longitude=-74.07&maxradiuskm=1500&minmagnitude=2.0&limit=40"
        val request = Request.Builder().url(url).get().build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException("Error ${resp.code} consultando USGS")
            val root = JSONObject(resp.body?.string() ?: "{}")
            val features = root.optJSONArray("features") ?: JSONArray()
            val lista = mutableListOf<Sismo>()
            val bogotaLat = 4.7110
            val bogotaLon = -74.0721

            for (i in 0 until features.length()) {
                val f = features.getJSONObject(i)
                val props = f.getJSONObject("properties")
                val geom = f.getJSONObject("geometry")
                val coords = geom.getJSONArray("coordinates")
                val lng = coords.getDouble(0)
                val lat = coords.getDouble(1)
                val depth = if (coords.length() > 2) coords.getDouble(2) else 0.0
                val distancia = haversineKm(bogotaLat, bogotaLon, lat, lng)
                val lugarLimpio = traducirLugarUsgs(props.optString("place", "Colombia"))

                lista.add(
                    Sismo(
                        id = f.optString("id", "sismo_$i"),
                        magnitud = (Math.round(props.optDouble("mag", 0.0) * 10.0) / 10.0),
                        lugar = lugarLimpio,
                        tiempo = props.optLong("time", System.currentTimeMillis()),
                        profundidadKm = (Math.round(depth * 10.0) / 10.0),
                        lat = lat,
                        lng = lng,
                        distanciaBogotaKm = (Math.round(distancia * 10.0) / 10.0),
                        sentido = props.optInt("felt", 0),
                        alerta = props.optString("alert", ""),
                        url = props.optString("url", "")
                    )
                )
            }
            return lista
        }
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    private fun traducirLugarUsgs(place: String): String {
        if (place.isEmpty()) return "Colombia"
        var res = place
        val traducciones = listOf(
            "of " to "de ",
            "km NNE" to "km al NNE",
            "km NNW" to "km al NNO",
            "km SSE" to "km al SSE",
            "km SSW" to "km al SSO",
            "km ENE" to "km al ENE",
            "km ESE" to "km al ESE",
            "km WNW" to "km al ONO",
            "km WSW" to "km al OSO",
            "km NE" to "km al NE",
            "km NW" to "km al NO",
            "km SE" to "km al SE",
            "km SW" to "km al SO",
            "km N " to "km al Norte de ",
            "km S " to "km al Sur de ",
            "km E " to "km al Este de ",
            "km W " to "km al Oeste de "
        )
        for ((eng, esp) in traducciones) {
            res = res.replace(eng, esp)
        }
        return res
    }

    private fun obtenerSismosFallback(): List<Sismo> {
        val ahora = System.currentTimeMillis()
        return listOf(
            Sismo(
                id = "ref_1",
                magnitud = 3.8,
                lugar = "12 km al SO de Los Santos, Santander, Colombia",
                tiempo = ahora - 3600000,
                profundidadKm = 145.0,
                lat = 6.78,
                lng = -73.12,
                distanciaBogotaKm = 240.5,
                sentido = 12,
                alerta = "green",
                url = ""
            ),
            Sismo(
                id = "ref_2",
                magnitud = 4.2,
                lugar = "25 km al NO de Villavicencio, Meta, Colombia",
                tiempo = ahora - 14400000,
                profundidadKm = 15.0,
                lat = 4.31,
                lng = -73.85,
                distanciaBogotaKm = 52.1,
                sentido = 45,
                alerta = "yellow",
                url = ""
            )
        )
    }

    /** null si el punto no cae dentro de ninguna localidad de Bogotá (ej. fuera de la ciudad). */
    fun consultarRiesgoPorPunto(lat: Double, lng: Double): RiesgoPorPunto? =
        conAutodeteccion { consultarRiesgoPorPuntoUnaVez(lat, lng) }

    private fun consultarRiesgoPorPuntoUnaVez(lat: Double, lng: Double): RiesgoPorPunto? {
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
    fun buscarBarrio(nombre: String, localidad: String? = null): BusquedaBarrio =
        conAutodeteccion { buscarBarrioUnaVez(nombre, localidad) }

    private fun buscarBarrioUnaVez(nombre: String, localidad: String? = null): BusquedaBarrio {
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

    /** Ficha completa de una localidad (/zonas/{nombre}). */
    fun obtenerDetalleLocalidad(nombre: String): DetalleLocalidad =
        conAutodeteccion { obtenerDetalleLocalidadUnaVez(nombre) }

    private fun obtenerDetalleLocalidadUnaVez(nombre: String): DetalleLocalidad {
        // URLEncoder es para formularios: codifica el espacio como "+", que en
        // un SEGMENTO DE RUTA no se decodifica (solo en el query string). Sin
        // este replace, "Ciudad Bolívar" llega al backend como "Ciudad+Bolívar"
        // y responde 404; los nombres de una sola palabra sí funcionaban, que
        // es lo que hacía que el fallo pareciera aleatorio.
        val url = baseUrl + "zonas/" + URLEncoder.encode(nombre, "UTF-8").replace("+", "%20")
        client.newCall(Request.Builder().url(url).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw ApiException("Error ${resp.code} consultando $nombre")
            val o = JSONObject(resp.body?.string() ?: "{}")
            val delitos = o.optJSONObject("detalle_delitos") ?: JSONObject()
            val contexto = o.optJSONObject("contexto") ?: JSONObject()
            return DetalleLocalidad(
                localidad = o.getString("localidad"),
                nivelRiesgo = o.getString("nivel_riesgo"),
                poblacion = o.optInt("poblacion_2025"),
                delitosTotal = o.optInt("delitos_recientes_total_2023_2025"),
                tasaDelitos100k = o.optDouble("tasa_delitos_100k", 0.0),
                scoreMixto = o.optDouble("score_mixto", 0.0),
                // Ordenados de más a menos frecuentes: es como se lee mejor.
                detalleDelitos = delitos.keys().asSequence()
                    .map { it to delitos.optInt(it) }
                    .sortedByDescending { it.second }
                    .toList(),
                estratoPromedio = contexto.optDouble("estrato_promedio", 0.0),
                luminarias = contexto.optInt("luminarias_estimadas"),
                luminariasPorKm2 = contexto.optDouble("luminarias_por_km2", 0.0),
                longitudViasKm = contexto.optDouble("longitud_vias_km", 0.0),
                areaKm2 = contexto.optDouble("area_km2", 0.0),
                incidentesNuse = contexto.optInt("incidentes_nuse_recientes_total"),
            )
        }
    }

    fun enviarMensajeChat(
        historial: List<MensajeChat>,
        hechosRecordados: List<String> = emptyList(),
        lat: Double? = null,
        lng: Double? = null,
    ): RespuestaChat = conAutodeteccion { enviarMensajeChatUnaVez(historial, hechosRecordados, lat, lng) }

    private fun enviarMensajeChatUnaVez(
        historial: List<MensajeChat>,
        hechosRecordados: List<String>,
        lat: Double?,
        lng: Double?,
    ): RespuestaChat {
        val mensajesJson = JSONArray()
        for (m in historial) {
            val o = JSONObject().put("role", m.role).put("content", m.content)
            if (m.toolCalls != null) o.put("tool_calls", JSONArray(m.toolCalls))
            mensajesJson.put(o)
        }
        val hechosJson = JSONArray()
        for (h in hechosRecordados) hechosJson.put(h)
        val cuerpo = JSONObject()
            .put("mensajes", mensajesJson)
            .put("hechos_recordados", hechosJson)
            .apply {
                if (lat != null && lng != null) {
                    put("lat", lat)
                    put("lng", lng)
                }
            }
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
                MensajeChat(
                    role = o.getString("role"),
                    content = o.optString("content", ""),
                    toolCalls = o.optJSONArray("tool_calls")?.toString(),
                )
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
