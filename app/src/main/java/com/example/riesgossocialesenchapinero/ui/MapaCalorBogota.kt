package com.example.riesgossocialesenchapinero.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.riesgossocialesenchapinero.R
import com.example.riesgossocialesenchapinero.data.ApiClient
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.min

data class PuntoGeo(val lng: Double, val lat: Double)

data class LocalidadMapa(
    val codigo: Int,
    val nombre: String,
    val nivelRiesgo: String,
    val tasa100k: Double,
    val poligono: List<PuntoGeo>,
    val minLng: Double,
    val maxLng: Double,
    val minLat: Double,
    val maxLat: Double,
    val centroide: PuntoGeo
)

object GestorGeojson {
    private var cacheLocalidades: List<LocalidadMapa>? = null

    fun cargarLocalidades(context: Context, ranking: List<ApiClient.Localidad>): List<LocalidadMapa> {
        val mapaRiesgo = ranking.associateBy({ normalizar(it.nombre) }, { it })

        if (cacheLocalidades != null) {
            // Actualizar niveles con el ranking más reciente
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

                    lista.add(
                        LocalidadMapa(
                            codigo = codigo,
                            nombre = nombre,
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

    private fun normalizar(s: String): String =
        s.lowercase().replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u").replace("ñ", "n").trim()
}

/**
 * Algoritmo de Ray-Casting para determinar si un punto está dentro de un polígono.
 */
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

@Composable
fun MapaCalorBogota(
    modifier: Modifier = Modifier,
    ranking: List<ApiClient.Localidad>,
    onSeleccionarLocalidad: (String) -> Unit
) {
    val context = LocalContext.current
    var localidades by remember { mutableStateOf<List<LocalidadMapa>>(emptyList()) }
    var localidadSeleccionada by remember { mutableStateOf<LocalidadMapa?>(null) }
    var vistaCompleta by remember { mutableStateOf(false) } // false = Área urbana principal, true = Incluye Sumapaz

    LaunchedEffect(ranking) {
        localidades = GestorGeojson.cargarLocalidades(context, ranking)
    }

    // Filtrar para el área urbana si no está en vista completa
    val localidadesVisibles = remember(localidades, vistaCompleta) {
        if (vistaCompleta) localidades else localidades.filter { it.codigo != 20 } // 20 = Sumapaz (área rural sur muy extensa)
    }

    // Calcular Bounding Box del mapa
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
    val rangoLng = max(maxLng - minLng, 0.001)
    val rangoLat = max(maxLat - minLat, 0.001)

    Column(modifier = modifier.fillMaxWidth()) {
        // ENCABEZADO DEL MAPA CON LEYENDA Y TOGGLE
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🗺️ " + stringResource(R.string.mapa_calor_titulo),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    FilterChip(
                        selected = vistaCompleta,
                        onClick = { vistaCompleta = !vistaCompleta },
                        label = {
                            Text(
                                if (vistaCompleta) stringResource(R.string.mapa_ver_urbano)
                                else stringResource(R.string.mapa_ver_todo)
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // LEYENDA VISUAL
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ItemLeyenda(color = Color(0xFFE53935), texto = stringResource(R.string.riesgo_alto))
                    ItemLeyenda(color = Color(0xFFFB8C00), texto = stringResource(R.string.riesgo_medio))
                    ItemLeyenda(color = Color(0xFF43A047), texto = stringResource(R.string.riesgo_bajo))
                }
            }
        }

        // CANVAS INTERACTIVO
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.05f)
                .padding(8.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
        ) {
            var canvasWidth by remember { mutableFloatStateOf(1f) }
            var canvasHeight by remember { mutableFloatStateOf(1f) }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .pointerInput(localidadesVisibles, minLng, maxLng, minLat, maxLat) {
                        detectTapGestures { offset ->
                            val pad = 12.dp.toPx()
                            val anchoUtil = (canvasWidth - 2 * pad).coerceAtLeast(1f)
                            val altoUtil = (canvasHeight - 2 * pad).coerceAtLeast(1f)

                            val xRel = (offset.x - pad).coerceIn(0f, anchoUtil)
                            val yRel = (offset.y - pad).coerceIn(0f, altoUtil)

                            val clickLng = minLng + (xRel / anchoUtil) * rangoLng
                            val clickLat = maxLat - (yRel / altoUtil) * rangoLat
                            val ptClick = PuntoGeo(clickLng, clickLat)

                            val tocada = localidadesVisibles.firstOrNull { loc ->
                                puntoEnPoligono(ptClick, loc.poligono)
                            }
                            localidadSeleccionada = tocada
                        }
                    }
            ) {
                canvasWidth = size.width
                canvasHeight = size.height

                val pad = 12.dp.toPx()
                val anchoUtil = size.width - 2 * pad
                val altoUtil = size.height - 2 * pad

                // Función de proyección GPS -> Pantalla Canvas
                fun proyectar(p: PuntoGeo): Offset {
                    val x = pad + ((p.lng - minLng) / rangoLng * anchoUtil).toFloat()
                    val y = pad + ((maxLat - p.lat) / rangoLat * altoUtil).toFloat()
                    return Offset(x, y)
                }

                // DIBUJAR POLÍGONOS DE TODAS LAS LOCALIDADES
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
                        "alto" -> Color(0xFFE53935).copy(alpha = 0.82f)
                        "medio" -> Color(0xFFFB8C00).copy(alpha = 0.82f)
                        else -> Color(0xFF43A047).copy(alpha = 0.82f)
                    }

                    val esSeleccionada = localidadSeleccionada?.codigo == loc.codigo

                    // Relleno
                    drawPath(path, color = colorRelleno, style = Fill)

                    // Borde
                    val colorBorde = if (esSeleccionada) Color.White else Color(0xFF263238).copy(alpha = 0.6f)
                    val anchoBorde = if (esSeleccionada) 3.5.dp.toPx() else 1.2.dp.toPx()
                    drawPath(path, color = colorBorde, style = Stroke(width = anchoBorde))

                    // Centroide y punto destacado si es seleccionada
                    if (esSeleccionada) {
                        val c = proyectar(loc.centroide)
                        drawCircle(color = Color.White, radius = 6.dp.toPx(), center = c)
                        drawCircle(color = Color(0xFF1E88E5), radius = 3.5.dp.toPx(), center = c)
                    }
                }
            }

            // FICHA FLOTANTE DE IDENTIFICACIÓN AL TOCAR UNA LOCALIDAD
            androidx.compose.animation.AnimatedVisibility(
                visible = localidadSeleccionada != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp)
            ) {
                localidadSeleccionada?.let { loc ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(stringResource(R.string.btn_ver_detalle))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ItemLeyenda(color: Color, texto: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(color = color, shape = CircleShape, modifier = Modifier.size(12.dp)) {}
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = texto, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}
