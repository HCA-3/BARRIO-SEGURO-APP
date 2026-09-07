package com.example.riesgossocialesenchapinero.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Paint
import android.graphics.Typeface
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.riesgossocialesenchapinero.R
import com.example.riesgossocialesenchapinero.data.ApiClient
import com.google.android.gms.location.LocationServices
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

data class PuntoGeo(val lng: Double, val lat: Double)

data class LocalidadMapa(
    val codigo: Int,
    val nombre: String,
    val nombreCorto: String,
    val nivelRiesgo: String,
    val tasa100k: Double,
    val poligono: List<PuntoGeo>,
    val minLng: Double,
    val maxLng: Double,
    val minLat: Double,
    val maxLat: Double,
    val centroide: PuntoGeo
)

data class CuadraCalor(
    val nombre: String,
    val lng: Double,
    val lat: Double,
    val nivelRiesgo: String,
    val score: Double,
    val localidad: String = "",
    val tasa100k: Double = 0.0
)

data class CalleVial(
    val nombre: String,
    val tipo: String,
    val tramos: List<PuntoGeo>
)

object GestorGeojson {
    private var cacheLocalidades: List<LocalidadMapa>? = null
    private var cacheCuadras: List<CuadraCalor>? = null
    private var cacheCalles: List<CalleVial>? = null

    fun cargarLocalidades(context: Context, ranking: List<ApiClient.Localidad>): List<LocalidadMapa> {
        val mapaRiesgo = ranking.associateBy({ normalizar(it.nombre) }, { it })

        if (cacheLocalidades != null) {
            return cacheLocalidades!!.map { loc ->
                val r = mapaRiesgo[normalizar(loc.nombre)]
                if (r != null) {
                    loc.copy(nivelRiesgo = r.nivelRiesgo, tasa100k = r.tasaDelitos100k)
                } else loc
            }
        }

        val lista = mutableListOf<LocalidadMapa>()
        try {
            val jsonStr = context.assets.open("datos/localidades.geojson").bufferedReader().use { it.readText() }
            val root = JSONObject(jsonStr)
            val features = root.getJSONArray("features")

            for (i in 0 until features.length()) {
                val f = features.getJSONObject(i)
                val props = f.getJSONObject("properties")
                val codigo = props.optInt("codigo", i + 1)
                val nombre = props.getString("localidad")
                val geom = f.getJSONObject("geometry")
                val type = geom.getString("type")

                val puntos = mutableListOf<PuntoGeo>()
                if (type == "Polygon") {
                    val coords = geom.getJSONArray("coordinates").getJSONArray(0)
                    for (j in 0 until coords.length()) {
                        val pt = coords.getJSONArray(j)
                        puntos.add(PuntoGeo(pt.getDouble(0), pt.getDouble(1)))
                    }
                }

                if (puntos.isNotEmpty()) {
                    var minLng = Double.MAX_VALUE
                    var maxLng = -Double.MAX_VALUE
                    var minLat = Double.MAX_VALUE
                    var maxLat = -Double.MAX_VALUE
                    var sumLng = 0.0
                    var sumLat = 0.0

                    for (p in puntos) {
                        minLng = min(minLng, p.lng)
                        maxLng = max(maxLng, p.lng)
                        minLat = min(minLat, p.lat)
                        maxLat = max(maxLat, p.lat)
                        sumLng += p.lng
                        sumLat += p.lat
                    }

                    val centroide = PuntoGeo(sumLng / puntos.size, sumLat / puntos.size)
                    val r = mapaRiesgo[normalizar(nombre)]
                    val nivel = r?.nivelRiesgo ?: "medio"
                    val tasa = r?.tasaDelitos100k ?: 0.0
                    val corto = abreviarNombre(nombre)

                    lista.add(
                        LocalidadMapa(
                            codigo = codigo,
                            nombre = nombre,
                            nombreCorto = corto,
                            nivelRiesgo = nivel,
                            tasa100k = tasa,
                            poligono = puntos,
                            minLng = minLng,
                            maxLng = maxLng,
                            minLat = minLat,
                            maxLat = maxLat,
                            centroide = centroide
                        )
                    )
                }
            }
            cacheLocalidades = lista
        } catch (e: Exception) {
            android.util.Log.e("MapaCalorBogota", "Error cargando localidades.geojson", e)
        }
        return lista
    }

    fun cargarCuadrasCalor(context: Context, localidades: List<LocalidadMapa>): List<CuadraCalor> {
        if (cacheCuadras != null) return cacheCuadras!!
        val lista = mutableListOf<CuadraCalor>()

        try {
            val jsonBarrios = context.assets.open("datos/barrios.json").bufferedReader().use { it.readText() }
            val array = JSONArray(jsonBarrios)

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val nombre = obj.optString("n", "Cuadra $i").trim()
                val lat = obj.optDouble("y", 0.0)
                val lng = obj.optDouble("x", 0.0)

                if (lat != 0.0 && lng != 0.0) {
                    val pt = PuntoGeo(lng, lat)
                    val loc = localidades.firstOrNull { puntoEnPoligono(pt, it.poligono) }
                    val nivel = loc?.nivelRiesgo ?: "medio"
                    val score = when (nivel) {
                        "alto" -> 0.85 + (Math.sin(lat * 1000) * 0.12)
                        "medio" -> 0.50 + (Math.cos(lng * 1000) * 0.15)
                        else -> 0.20 + (Math.sin((lat + lng) * 500) * 0.08)
                    }.coerceIn(0.1, 1.0)

                    lista.add(
                        CuadraCalor(
                            nombre = nombre,
                            lng = lng,
                            lat = lat,
                            nivelRiesgo = nivel,
                            score = score,
                            localidad = loc?.nombre ?: "Bogotá D.C.",
                            tasa100k = loc?.tasa100k ?: 12500.0
                        )
                    )
                }
            }

            if (lista.size < 50) {
                for (loc in localidades) {
                    val pasox = (loc.maxLng - loc.minLng) / 5.0
                    val pasoy = (loc.maxLat - loc.minLat) / 5.0
                    for (ix in 1..4) {
                        for (iy in 1..4) {
                            val px = loc.minLng + ix * pasox
                            val py = loc.minLat + iy * pasoy
                            val pt = PuntoGeo(px, py)
                            if (puntoEnPoligono(pt, loc.poligono)) {
                                lista.add(
                                    CuadraCalor(
                                        nombre = "Cuadra ${loc.nombreCorto} #$ix-$iy",
                                        lng = px,
                                        lat = py,
                                        nivelRiesgo = loc.nivelRiesgo,
                                        score = if (loc.nivelRiesgo == "alto") 0.88 else if (loc.nivelRiesgo == "medio") 0.52 else 0.22,
                                        localidad = loc.nombre,
                                        tasa100k = loc.tasa100k
                                    )
                                )
                            }
                        }
                    }
                }
            }
            cacheCuadras = lista
        } catch (e: Exception) {
            android.util.Log.e("MapaCalorBogota", "Error cargando cuadras de calor", e)
        }
        return lista
    }

    fun obtenerCallesPrincipales(): List<CalleVial> {
        if (cacheCalles != null) return cacheCalles!!

        val calles = listOf(
            CalleVial(
                nombre = "Cra. 7ma (Av. Alberto Lleras)",
                tipo = "avenida",
                tramos = listOf(
                    PuntoGeo(-74.0245, 4.7650), PuntoGeo(-74.0300, 4.7200),
                    PuntoGeo(-74.0500, 4.6700), PuntoGeo(-74.0620, 4.6300),
                    PuntoGeo(-74.0720, 4.5980), PuntoGeo(-74.0780, 4.5800)
                )
            ),
            CalleVial(
                nombre = "Av. Caracas / Cra. 14",
                tipo = "troncal",
                tramos = listOf(
                    PuntoGeo(-74.0610, 4.6720), PuntoGeo(-74.0670, 4.6350),
                    PuntoGeo(-74.0740, 4.6050), PuntoGeo(-74.0950, 4.5750),
                    PuntoGeo(-74.1250, 4.5400)
                )
            ),
            CalleVial(
                nombre = "Av. NQS / Cra. 30",
                tipo = "troncal",
                tramos = listOf(
                    PuntoGeo(-74.0520, 4.7500), PuntoGeo(-74.0640, 4.6850),
                    PuntoGeo(-74.0800, 4.6400), PuntoGeo(-74.1100, 4.6000),
                    PuntoGeo(-74.1600, 4.5850)
                )
            ),
            CalleVial(
                nombre = "Av. Boyacá (Cra. 72)",
                tipo = "troncal",
                tramos = listOf(
                    PuntoGeo(-74.0480, 4.7700), PuntoGeo(-74.0680, 4.7300),
                    PuntoGeo(-74.0980, 4.6850), PuntoGeo(-74.1200, 4.6450),
                    PuntoGeo(-74.1450, 4.5950), PuntoGeo(-74.1350, 4.5300)
                )
            ),
            CalleVial(
                nombre = "Av. Ciudad de Cali (Cra. 86)",
                tipo = "avenida",
                tramos = listOf(
                    PuntoGeo(-74.0980, 4.7500), PuntoGeo(-74.1150, 4.7100),
                    PuntoGeo(-74.1350, 4.6650), PuntoGeo(-74.1650, 4.6300),
                    PuntoGeo(-74.1950, 4.6000)
                )
            ),
            CalleVial(
                nombre = "Av. Cra. 68",
                tipo = "avenida",
                tramos = listOf(
                    PuntoGeo(-74.0650, 4.6980), PuntoGeo(-74.0850, 4.6700),
                    PuntoGeo(-74.1050, 4.6400), PuntoGeo(-74.1280, 4.6050),
                    PuntoGeo(-74.1420, 4.5800)
                )
            ),
            CalleVial(
                nombre = "Av. El Dorado (Cl. 26)",
                tipo = "troncal",
                tramos = listOf(
                    PuntoGeo(-74.0650, 4.6150), PuntoGeo(-74.0850, 4.6350),
                    PuntoGeo(-74.1080, 4.6550), PuntoGeo(-74.1380, 4.6850)
                )
            ),
            CalleVial(
                nombre = "Av. Calle 80",
                tipo = "troncal",
                tramos = listOf(
                    PuntoGeo(-74.0600, 4.6700), PuntoGeo(-74.0850, 4.6900),
                    PuntoGeo(-74.1150, 4.7150), PuntoGeo(-74.1380, 4.7300)
                )
            ),
            CalleVial(
                nombre = "Av. Calle 100 / Calle 68",
                tipo = "avenida",
                tramos = listOf(
                    PuntoGeo(-74.0400, 4.6850), PuntoGeo(-74.0620, 4.6900),
                    PuntoGeo(-74.0850, 4.6780), PuntoGeo(-74.1150, 4.6700)
                )
            ),
            CalleVial(
                nombre = "Av. Calle 72",
                tipo = "calle",
                tramos = listOf(
                    PuntoGeo(-74.0550, 4.6550), PuntoGeo(-74.0750, 4.6650),
                    PuntoGeo(-74.0980, 4.6780), PuntoGeo(-74.1200, 4.6900)
                )
            ),
            CalleVial(
                nombre = "Av. Calle 13 (Av. Centenario)",
                tipo = "troncal",
                tramos = listOf(
                    PuntoGeo(-74.0750, 4.6050), PuntoGeo(-74.1050, 4.6250),
                    PuntoGeo(-74.1380, 4.6500), PuntoGeo(-74.1750, 4.6800)
                )
            ),
            CalleVial(
                nombre = "Av. Las Américas",
                tipo = "troncal",
                tramos = listOf(
                    PuntoGeo(-74.0780, 4.6150), PuntoGeo(-74.1100, 4.6250),
                    PuntoGeo(-74.1480, 4.6300), PuntoGeo(-74.1800, 4.6280)
                )
            ),
            CalleVial(
                nombre = "Av. Primero de Mayo",
                tipo = "avenida",
                tramos = listOf(
                    PuntoGeo(-74.0850, 4.5650), PuntoGeo(-74.1150, 4.5800),
                    PuntoGeo(-74.1450, 4.6050), PuntoGeo(-74.1800, 4.6200)
                )
            )
        )
        cacheCalles = calles
        return calles
    }

    private fun abreviarNombre(nombre: String): String = when (nombre.trim()) {
        "Antonio Nariño" -> "A. Nariño"
        "Barrios Unidos" -> "B. Unidos"
        "Ciudad Bolívar" -> "Cd. Bolívar"
        "Puente Aranda" -> "Pte. Aranda"
        "Rafael Uribe Uribe" -> "R. Uribe"
        "San Cristóbal" -> "S. Cristóbal"
        "Los Mártires" -> "Mártires"
        "La Candelaria", "Candelaria" -> "Candelaria"
        else -> nombre
    }

    private fun normalizar(s: String): String =
        s.lowercase().replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u").replace("ñ", "n").trim()
}

fun puntoEnPoligono(pt: PuntoGeo, poligono: List<PuntoGeo>): Boolean {
    var adentro = false
    var j = poligono.size - 1
    for (i in poligono.indices) {
        val pi = poligono[i]
        val pj = poligono[j]
        if ((pi.lat > pt.lat) != (pj.lat > pt.lat) &&
            pt.lng < (pj.lng - pi.lng) * (pt.lat - pi.lat) / (pj.lat - pi.lat) + pi.lng
        ) {
            adentro = !adentro
        }
        j = i
    }
    return adentro
}

enum class FiltroMapa {
    TODOS, ALTO, MEDIO, BAJO
}

enum class ModoVistaMapa {
    CALOR_CUADRAS, CALLES, LOCALIDADES
}

@SuppressLint("MissingPermission")
@Composable
fun MapaCalorBogota(
    modifier: Modifier = Modifier,
    ranking: List<ApiClient.Localidad>,
    onSeleccionarLocalidad: (String) -> Unit
) {
    val context = LocalContext.current
    var localidades by remember { mutableStateOf<List<LocalidadMapa>>(emptyList()) }
    var cuadrasCalor by remember { mutableStateOf<List<CuadraCalor>>(emptyList()) }
    var callesPrincipales by remember { mutableStateOf<List<CalleVial>>(emptyList()) }

    var localidadSeleccionada by remember { mutableStateOf<LocalidadMapa?>(null) }
    var cuadraSeleccionada by remember { mutableStateOf<CuadraCalor?>(null) }
    var vistaCompleta by remember { mutableStateOf(false) }
    var modoVista by remember { mutableStateOf(ModoVistaMapa.CALOR_CUADRAS) }

    // Estado de Geolocalización en tiempo real
    var ubicacionGps by remember { mutableStateOf<PuntoGeo?>(null) }
    var precisionGpsMts by remember { mutableFloatStateOf(20f) }
    var nombreZonaActual by remember { mutableStateOf("Ubicando...") }
    var nivelRiesgoActual by remember { mutableStateOf("medio") }

    // Control de gestos y zoom en el mapa
    var escalaZoom by remember { mutableFloatStateOf(1.0f) }
    var offsetPanX by remember { mutableFloatStateOf(0f) }
    var offsetPanY by remember { mutableFloatStateOf(0f) }

    // Pulso animado de radar térmico y GPS
    val infiniteTransition = rememberInfiniteTransition(label = "pulso_termico")
    val radioPulsoGps by infiniteTransition.animateFloat(
        initialValue = 4f,
        targetValue = 22f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radio_gps"
    )
    val alfaPulsoGps by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alfa_gps"
    )
    val brilloNeon by infiniteTransition.animateFloat(
        initialValue = 0.65f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "brillo_neon"
    )

    // Cargar datos espaciales y de calor
    LaunchedEffect(ranking) {
        val locs = GestorGeojson.cargarLocalidades(context, ranking)
        localidades = locs
        cuadrasCalor = GestorGeojson.cargarCuadrasCalor(context, locs)
        callesPrincipales = GestorGeojson.obtenerCallesPrincipales()
    }

    // Geolocalización automática en tiempo real
    DisposableEffect(Unit) {
        val tienePermiso = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        var locationManager: LocationManager? = null
        var locationListener: LocationListener? = null

        if (tienePermiso) {
            try {
                val fusedClient = LocationServices.getFusedLocationProviderClient(context)
                fusedClient.lastLocation.addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        val pt = PuntoGeo(loc.longitude, loc.latitude)
                        ubicacionGps = pt
                        precisionGpsMts = loc.accuracy.coerceIn(5f, 50f)
                    }
                }

                locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
                locationListener = LocationListener { loc ->
                    val pt = PuntoGeo(loc.longitude, loc.latitude)
                    ubicacionGps = pt
                    precisionGpsMts = loc.accuracy.coerceIn(5f, 50f)
                }

                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    3000L,
                    5f,
                    locationListener
                )
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    3000L,
                    5f,
                    locationListener
                )
            } catch (e: Exception) {
                android.util.Log.e("MapaCalorBogota", "Error iniciando GPS", e)
            }
        }

        if (ubicacionGps == null) {
            ubicacionGps = PuntoGeo(-74.0621, 4.6534)
        }

        onDispose {
            locationListener?.let { locationManager?.removeUpdates(it) }
        }
    }

    // Identificar zona y nivel de riesgo actual del usuario
    LaunchedEffect(ubicacionGps, localidades, cuadrasCalor) {
        val pt = ubicacionGps ?: return@LaunchedEffect
        val locActual = localidades.firstOrNull { puntoEnPoligono(pt, it.poligono) }
        val cuadraCercana = cuadrasCalor.minByOrNull {
            val dLng = it.lng - pt.lng
            val dLat = it.lat - pt.lat
            dLng * dLng + dLat * dLat
        }

        val barrio = cuadraCercana?.nombre ?: "Chapinero Central"
        val locNombre = locActual?.nombre ?: "Chapinero"
        nombreZonaActual = "$barrio, $locNombre"
        nivelRiesgoActual = locActual?.nivelRiesgo ?: "medio"
    }

    val localidadesVisibles = remember(localidades, vistaCompleta) {
        if (vistaCompleta) localidades else localidades.filter { it.codigo != 20 }
    }

    val bbox = remember(localidadesVisibles) {
        if (localidadesVisibles.isEmpty()) {
            PuntoGeo(-74.25, 4.45) to PuntoGeo(-74.00, 4.83)
        } else {
            var minX = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE
            for (loc in localidadesVisibles) {
                minX = min(minX, loc.minLng)
                maxX = max(maxX, loc.maxLng)
                minY = min(minY, loc.minLat)
                maxY = max(maxY, loc.maxLat)
            }
            PuntoGeo(minX, minY) to PuntoGeo(maxX, maxY)
        }
    }

    val minLng = bbox.first.lng
    val minLat = bbox.first.lat
    val maxLng = bbox.second.lng
    val maxLat = bbox.second.lat
    val rangoLng = max(maxLng - minLng, 0.0001)
    val rangoLat = max(maxLat - minLat, 0.0001)

    // Función de centrado en la ubicación GPS
    fun centrarEnUbicacion() {
        val pt = ubicacionGps ?: PuntoGeo(-74.0621, 4.6534)
        escalaZoom = 2.4f
        val factorCos = cos(Math.toRadians(4.65))
        val xNorm = (pt.lng - (minLng + maxLng) / 2.0) / rangoLng * factorCos
        val yNorm = (pt.lat - (minLat + maxLat) / 2.0) / rangoLat
        offsetPanX = (-xNorm * 380f * escalaZoom).toFloat()
        offsetPanY = (yNorm * 380f * escalaZoom).toFloat()
    }

    LaunchedEffect(ubicacionGps) {
        if (ubicacionGps != null && escalaZoom == 1.0f && offsetPanX == 0f && offsetPanY == 0f) {
            centrarEnUbicacion()
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // 1. BARRA SUPERIOR: ESTADO EN VIVO Y SELECTORES DE CAPA
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFF00E5FF).copy(alpha = brilloNeon),
                            shape = CircleShape,
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Mapa de Riesgo por Cuadras",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "📍 $nombreZonaActual",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    BadgeRiesgo(nivel = nivelRiesgoActual)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Selector de Capas (Calor por Cuadra, Calles, Localidades)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = modoVista == ModoVistaMapa.CALOR_CUADRAS,
                        onClick = { modoVista = ModoVistaMapa.CALOR_CUADRAS },
                        label = { Text("🔥 Calor Cuadras", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = modoVista == ModoVistaMapa.CALLES,
                        onClick = { modoVista = ModoVistaMapa.CALLES },
                        label = { Text("🛣️ Calles y Vías", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = modoVista == ModoVistaMapa.LOCALIDADES,
                        onClick = { modoVista = ModoVistaMapa.LOCALIDADES },
                        label = { Text("🏛️ Localidades", style = MaterialTheme.typography.labelSmall) }
                    )
                }
            }
        }

        // 2. CANVAS DEL MAPA INTERACTIVO CON CALLES Y CALOR
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.95f)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF141E28), Color(0xFF0C131A), Color(0xFF060A0E)),
                        radius = 1100f
                    )
                )
                .border(1.5.dp, Color(0xFF37474F), RoundedCornerShape(18.dp))
                .shadow(12.dp, RoundedCornerShape(18.dp))
        ) {
            var canvasWidth by remember { mutableFloatStateOf(1f) }
            var canvasHeight by remember { mutableFloatStateOf(1f) }

            val paintTexto = remember {
                Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 26f
                    isAntiAlias = true
                    isSubpixelText = true
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
            }
            val paintCalle = remember {
                Paint().apply {
                    color = android.graphics.Color.argb(180, 176, 190, 197)
                    textSize = 19f
                    isAntiAlias = true
                    textAlign = Paint.Align.LEFT
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
                }
            }
            val paintBadgeFondo = remember {
                Paint().apply {
                    color = android.graphics.Color.argb(220, 12, 18, 26)
                    isAntiAlias = true
                    style = Paint.Style.FILL
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(localidadesVisibles, minLng, maxLng, minLat, maxLat, escalaZoom, offsetPanX, offsetPanY) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            escalaZoom = (escalaZoom * zoom).coerceIn(0.8f, 6.0f)
                            offsetPanX += pan.x
                            offsetPanY += pan.y
                        }
                    }
                    .pointerInput(localidadesVisibles, cuadrasCalor, minLng, maxLng, minLat, maxLat, escalaZoom, offsetPanX, offsetPanY) {
                        detectTapGestures { offset ->
                            val pad = 24.dp.toPx()
                            val anchoUtil = (canvasWidth - 2 * pad).coerceAtLeast(1f)
                            val altoUtil = (canvasHeight - 2 * pad).coerceAtLeast(1f)
                            val factorCos = cos(Math.toRadians(4.65))
                            val escalaX = anchoUtil / (rangoLng * factorCos)
                            val escalaY = altoUtil / rangoLat
                            val escalaBase = min(escalaX, escalaY)

                            val anchoMapa = (rangoLng * factorCos * escalaBase).toFloat()
                            val altoMapa = (rangoLat * escalaBase).toFloat()
                            val offsetXBase = pad + (anchoUtil - anchoMapa) / 2f + offsetPanX
                            val offsetYBase = pad + (altoUtil - altoMapa) / 2f + offsetPanY

                            val xEnMapa = (offset.x - offsetXBase) / escalaZoom
                            val yEnMapa = (offset.y - offsetYBase) / escalaZoom

                            val clickLng = minLng + (xEnMapa / anchoMapa) * rangoLng
                            val clickLat = maxLat - (yEnMapa / altoMapa) * rangoLat
                            val ptClick = PuntoGeo(clickLng, clickLat)

                            val cuadraTocada = cuadrasCalor.minByOrNull {
                                val dx = (it.lng - clickLng) * factorCos
                                val dy = it.lat - clickLat
                                dx * dx + dy * dy
                            }

                            if (cuadraTocada != null) {
                                val dist = sqrt((cuadraTocada.lng - clickLng).pow(2) + (cuadraTocada.lat - clickLat).pow(2))
                                if (dist < 0.035) {
                                    cuadraSeleccionada = cuadraTocada
                                    localidadSeleccionada = null
                                    return@detectTapGestures
                                }
                            }

                            val tocada = localidadesVisibles.firstOrNull { loc ->
                                puntoEnPoligono(ptClick, loc.poligono)
                            }
                            localidadSeleccionada = tocada
                            cuadraSeleccionada = null
                        }
                    }
            ) {
                canvasWidth = size.width
                canvasHeight = size.height

                val pad = 24.dp.toPx()
                val anchoUtil = size.width - 2 * pad
                val altoUtil = size.height - 2 * pad
                val factorCos = cos(Math.toRadians(4.65))
                val escalaX = anchoUtil / (rangoLng * factorCos)
                val escalaY = altoUtil / rangoLat
                val escalaBase = min(escalaX, escalaY)

                val anchoMapa = (rangoLng * factorCos * escalaBase).toFloat()
                val altoMapa = (rangoLat * escalaBase).toFloat()
                val offsetXBase = pad + (anchoUtil - anchoMapa) / 2f + offsetPanX
                val offsetYBase = pad + (altoUtil - altoMapa) / 2f + offsetPanY

                fun proyectar(p: PuntoGeo): Offset {
                    val xRel = ((p.lng - minLng) / rangoLng * anchoMapa).toFloat()
                    val yRel = ((maxLat - p.lat) / rangoLat * altoMapa).toFloat()
                    val xFinal = offsetXBase + (xRel * escalaZoom)
                    val yFinal = offsetYBase + (yRel * escalaZoom)
                    return Offset(xFinal, yFinal)
                }

                // 1. MALLA Y POLÍGONOS BASE DE LOCALIDADES
                for (loc in localidadesVisibles) {
                    if (loc.poligono.isEmpty()) continue

                    val path = Path().apply {
                        val inicio = proyectar(loc.poligono[0])
                        moveTo(inicio.x, inicio.y)
                        for (k in 1 until loc.poligono.size) {
                            val pt = proyectar(loc.poligono[k])
                            lineTo(pt.x, pt.y)
                        }
                        close()
                    }

                    val colorRelleno = when (loc.nivelRiesgo) {
                        "alto" -> Color(0xFFFF1744).copy(alpha = if (modoVista == ModoVistaMapa.LOCALIDADES) 0.65f else 0.22f)
                        "medio" -> Color(0xFFFFAB00).copy(alpha = if (modoVista == ModoVistaMapa.LOCALIDADES) 0.65f else 0.20f)
                        else -> Color(0xFF00E676).copy(alpha = if (modoVista == ModoVistaMapa.LOCALIDADES) 0.65f else 0.18f)
                    }

                    drawPath(path, color = colorRelleno, style = Fill)

                    val colorBorde = if (localidadSeleccionada?.codigo == loc.codigo) Color(0xFF00E5FF) else Color(0xFF263238).copy(alpha = 0.60f)
                    val anchoBorde = if (localidadSeleccionada?.codigo == loc.codigo) 3.5.dp.toPx() else 1.2.dp.toPx()
                    drawPath(path, color = colorBorde, style = Stroke(width = anchoBorde, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }

                // 2. RED DE CALLES, CARRERAS Y AVENIDAS (MODO CALLES / VÍAS)
                for (calle in callesPrincipales) {
                    if (calle.tramos.size < 2) continue

                    val pathCalle = Path().apply {
                        val inicio = proyectar(calle.tramos[0])
                        moveTo(inicio.x, inicio.y)
                        for (k in 1 until calle.tramos.size) {
                            val pt = proyectar(calle.tramos[k])
                            lineTo(pt.x, pt.y)
                        }
                    }

                    val esTroncal = calle.tipo == "troncal"
                    val colorVia = when {
                        esTroncal -> Color(0xFFFF5252).copy(alpha = 0.85f)
                        calle.tipo == "avenida" -> Color(0xFFECEFF1).copy(alpha = 0.65f)
                        else -> Color(0xFF90A4AE).copy(alpha = 0.45f)
                    }
                    val anchoVia = if (esTroncal) (3.5f * escalaZoom.coerceIn(0.9f, 2.5f)) else (2.0f * escalaZoom.coerceIn(0.9f, 2.0f))

                    drawPath(pathCalle, color = colorVia, style = Stroke(width = anchoVia, cap = StrokeCap.Round, join = StrokeJoin.Round))

                    if (escalaZoom >= 1.5f && calle.tramos.isNotEmpty()) {
                        val puntoMedio = proyectar(calle.tramos[calle.tramos.size / 2])
                        if (puntoMedio.x in 0f..size.width && puntoMedio.y in 0f..size.height) {
                            drawContext.canvas.nativeCanvas.drawText(
                                calle.nombre,
                                puntoMedio.x + 8f,
                                puntoMedio.y - 6f,
                                paintCalle
                            )
                        }
                    }
                }

                // 3. MAPA DE CALOR POR CUADRA (NÚCLEOS TÉRMICOS GAUSSIANOS)
                if (modoVista == ModoVistaMapa.CALOR_CUADRAS || modoVista == ModoVistaMapa.CALLES) {
                    for (cuadra in cuadrasCalor) {
                        val centro = proyectar(PuntoGeo(cuadra.lng, cuadra.lat))
                        if (centro.x < -50 || centro.x > size.width + 50 || centro.y < -50 || centro.y > size.height + 50) continue

                        val colorCalor = when (cuadra.nivelRiesgo) {
                            "alto" -> Color(0xFFFF1744)
                            "medio" -> Color(0xFFFF9100)
                            else -> Color(0xFF00E676)
                        }

                        val radioCuadra = (16f * escalaZoom.coerceIn(1.0f, 3.0f)) * cuadra.score.toFloat()

                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(colorCalor.copy(alpha = 0.55f), colorCalor.copy(alpha = 0.0f)),
                                center = centro,
                                radius = radioCuadra * 2.2f
                            ),
                            radius = radioCuadra * 2.2f,
                            center = centro
                        )

                        drawCircle(
                            color = colorCalor.copy(alpha = 0.85f),
                            radius = radioCuadra * 0.65f,
                            center = centro
                        )
                    }
                }

                // 4. INDICADOR GPS EN TIEMPO REAL ("TÚ ESTÁS AQUÍ")
                ubicacionGps?.let { gps ->
                    val posGps = proyectar(gps)

                    val radioPrecision = (precisionGpsMts * escalaZoom * 0.4f).coerceIn(18f, 65f)
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.18f),
                        radius = radioPrecision,
                        center = posGps
                    )
                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = 0.45f),
                        radius = radioPrecision,
                        center = posGps,
                        style = Stroke(1.5f)
                    )

                    drawCircle(
                        color = Color(0xFF00E5FF).copy(alpha = alfaPulsoGps),
                        radius = radioPulsoGps * escalaZoom.coerceIn(1.0f, 2.5f) + 6f,
                        center = posGps,
                        style = Stroke(3f)
                    )

                    drawCircle(
                        color = Color(0xFFFFFFFF),
                        radius = 8.dp.toPx(),
                        center = posGps
                    )
                    drawCircle(
                        color = Color(0xFF00B0FF),
                        radius = 6.5.dp.toPx(),
                        center = posGps
                    )

                    val textoGps = "📍 Tú estás aquí"
                    paintTexto.textSize = 24f
                    val anchoTag = paintTexto.measureText(textoGps)
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        posGps.x - (anchoTag / 2f) - 10f,
                        posGps.y - 38f,
                        posGps.x + (anchoTag / 2f) + 10f,
                        posGps.y - 12f,
                        10f,
                        10f,
                        paintBadgeFondo
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        textoGps,
                        posGps.x,
                        posGps.y - 20f,
                        paintTexto
                    )
                }

                // 5. ETIQUETAS DE TEXTO DE LOCALIDADES
                if (modoVista == ModoVistaMapa.LOCALIDADES || escalaZoom < 2.0f) {
                    for (loc in localidadesVisibles) {
                        val centro = proyectar(loc.centroide)
                        if (centro.x < 0 || centro.x > size.width || centro.y < 0 || centro.y > size.height) continue

                        paintTexto.textSize = (22f * escalaZoom.coerceIn(0.9f, 2.0f)).coerceIn(20f, 40f)
                        val texto = loc.nombreCorto
                        val anchoTexto = paintTexto.measureText(texto)

                        drawContext.canvas.nativeCanvas.drawRoundRect(
                            centro.x - (anchoTexto / 2f) - 12f,
                            centro.y - 16f,
                            centro.x + (anchoTexto / 2f) + 12f,
                            centro.y + 10f,
                            12f,
                            12f,
                            paintBadgeFondo
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            texto,
                            centro.x,
                            centro.y + 2f,
                            paintTexto
                        )
                    }
                }
            }

            // BOTONES FLOTANTES DE CONTROL (ZOOM, CENTRAR EN MI UBICACIÓN)
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF00E5FF),
                    shadowElevation = 8.dp,
                    modifier = Modifier.size(44.dp)
                ) {
                    IconButton(onClick = { centrarEnUbicacion() }) {
                        Text("📍", fontSize = 20.sp)
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E2733).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color(0xFF546E7A)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(onClick = { escalaZoom = (escalaZoom * 1.35f).coerceAtMost(6.0f) }) {
                        Text("+", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 22.sp)
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E2733).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color(0xFF546E7A)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(onClick = { escalaZoom = (escalaZoom / 1.35f).coerceAtLeast(0.8f) }) {
                        Text("−", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 22.sp)
                    }
                }
            }

            // TARJETA FLOTANTE AL TOCAR UNA CUADRA O LOCALIDAD
            androidx.compose.animation.AnimatedVisibility(
                visible = cuadraSeleccionada != null || localidadSeleccionada != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
            ) {
                if (cuadraSeleccionada != null) {
                    val c = cuadraSeleccionada!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = c.nombre,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BadgeRiesgo(nivel = c.nivelRiesgo)
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Localidad: ${c.localidad} • Tasa: %,.0f del/100k".format(c.tasa100k),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Button(
                                onClick = { onSeleccionarLocalidad(c.localidad) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Ver Zona")
                            }
                        }
                    }
                } else if (localidadSeleccionada != null) {
                    val loc = localidadSeleccionada!!
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = loc.nombre,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    BadgeRiesgo(nivel = loc.nivelRiesgo)
                                }
                                if (loc.tasa100k > 0) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.tasa_formato, loc.tasa100k),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Button(
                                onClick = { onSeleccionarLocalidad(loc.nombre) },
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                            ) {
                                Text(stringResource(R.string.btn_ver_detalle))
                            }
                        }
                    }
                }
            }
        }

        // 3. CARRUSEL INFERIOR DE ACCESO RÁPIDO
        Text(
            text = "Acceso rápido por localidad:",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(localidadesVisibles, key = { it.codigo }) { loc ->
                val colorChip = when (loc.nivelRiesgo) {
                    "alto" -> Color(0xFFFFEBEE)
                    "medio" -> Color(0xFFFFF8E1)
                    else -> Color(0xFFE8F5E9)
                }
                val colorTexto = when (loc.nivelRiesgo) {
                    "alto" -> Color(0xFFC62828)
                    "medio" -> Color(0xFFEF6C00)
                    else -> Color(0xFF2E7D32)
                }
                Surface(
                    color = if (localidadSeleccionada?.codigo == loc.codigo) MaterialTheme.colorScheme.primaryContainer else colorChip,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.clickable {
                        localidadSeleccionada = loc
                        cuadraSeleccionada = null
                    }
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = loc.nombreCorto,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = colorTexto
                        )
                    }
                }
            }
        }
    }
}
