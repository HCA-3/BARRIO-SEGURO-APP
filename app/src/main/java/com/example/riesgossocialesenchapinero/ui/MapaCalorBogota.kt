package com.example.riesgossocialesenchapinero.ui

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.example.riesgossocialesenchapinero.R
import com.example.riesgossocialesenchapinero.data.ApiClient
import org.json.JSONObject
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min

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

object GestorGeojson {
    private var cacheLocalidades: List<LocalidadMapa>? = null

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

@Composable
fun MapaCalorBogota(
    modifier: Modifier = Modifier,
    ranking: List<ApiClient.Localidad>,
    onSeleccionarLocalidad: (String) -> Unit
) {
    val context = LocalContext.current
    var localidades by remember { mutableStateOf<List<LocalidadMapa>>(emptyList()) }
    var localidadSeleccionada by remember { mutableStateOf<LocalidadMapa?>(null) }
    var vistaCompleta by remember { mutableStateOf(false) }
    var filtroActivo by remember { mutableStateOf(FiltroMapa.TODOS) }

    val infiniteTransition = rememberInfiniteTransition(label = "pulso_termico")
    val radioPulso by infiniteTransition.animateFloat(
        initialValue = 6f,
        targetValue = 32f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radio_pulso"
    )
    val alfaPulso by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alfa_pulso"
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

    var escalaZoom by remember { mutableFloatStateOf(1.0f) }
    var offsetPanX by remember { mutableFloatStateOf(0f) }
    var offsetPanY by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(ranking) {
        localidades = GestorGeojson.cargarLocalidades(context, ranking)
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

    Column(modifier = modifier.fillMaxWidth()) {
        // ENCABEZADO Y FILTROS
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
                            color = Color(0xFFFF1744).copy(alpha = brilloNeon),
                            shape = CircleShape,
                            modifier = Modifier.size(12.dp)
                        ) {}
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.mapa_calor_titulo),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    FilterChip(
                        selected = vistaCompleta,
                        onClick = {
                            vistaCompleta = !vistaCompleta
                            escalaZoom = 1.0f
                            offsetPanX = 0f
                            offsetPanY = 0f
                        },
                        label = {
                            Text(
                                if (vistaCompleta) stringResource(R.string.mapa_ver_urbano)
                                else stringResource(R.string.mapa_ver_todo),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = filtroActivo == FiltroMapa.TODOS,
                        onClick = { filtroActivo = FiltroMapa.TODOS },
                        label = { Text("Todas (20)", style = MaterialTheme.typography.labelSmall) }
                    )
                    FilterChip(
                        selected = filtroActivo == FiltroMapa.ALTO,
                        onClick = { filtroActivo = if (filtroActivo == FiltroMapa.ALTO) FiltroMapa.TODOS else FiltroMapa.ALTO },
                        label = { Text("🔴 Alto", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFCDD2),
                            selectedLabelColor = Color(0xFFB71C1C)
                        )
                    )
                    FilterChip(
                        selected = filtroActivo == FiltroMapa.MEDIO,
                        onClick = { filtroActivo = if (filtroActivo == FiltroMapa.MEDIO) FiltroMapa.TODOS else FiltroMapa.MEDIO },
                        label = { Text("🟡 Medio", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFFFECB3),
                            selectedLabelColor = Color(0xFFE65100)
                        )
                    )
                    FilterChip(
                        selected = filtroActivo == FiltroMapa.BAJO,
                        onClick = { filtroActivo = if (filtroActivo == FiltroMapa.BAJO) FiltroMapa.TODOS else FiltroMapa.BAJO },
                        label = { Text("🟢 Seguro", style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFFC8E6C9),
                            selectedLabelColor = Color(0xFF1B5E20)
                        )
                    )
                }
            }
        }

        // CANVAS ULTRA-HD CON SOMBRAS Y BORDES SUAVES
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.95f) // Formato vertical óptimo para la forma de Bogotá
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0xFF1F2C3A), Color(0xFF0F1722), Color(0xFF070B10)),
                        radius = 1100f
                    )
                )
                .border(1.5.dp, Color(0xFF455A64), RoundedCornerShape(18.dp))
                .shadow(12.dp, RoundedCornerShape(18.dp))
        ) {
            var canvasWidth by remember { mutableFloatStateOf(1f) }
            var canvasHeight by remember { mutableFloatStateOf(1f) }

            // Configuración de texto tipográfico de ultra alta definición
            val paintTexto = remember {
                Paint().apply {
                    color = android.graphics.Color.WHITE
                    textSize = 28f
                    isAntiAlias = true
                    isSubpixelText = true
                    isLinearText = true
                    textAlign = Paint.Align.CENTER
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
            }

            val paintBadgeFondo = remember {
                Paint().apply {
                    color = android.graphics.Color.argb(215, 12, 18, 26)
                    isAntiAlias = true
                    isDither = true
                    style = Paint.Style.FILL
                }
            }

            val paintBadgeBorde = remember {
                Paint().apply {
                    color = android.graphics.Color.argb(220, 255, 255, 255)
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeWidth = 2.2f
                }
            }

            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(localidadesVisibles, minLng, maxLng, minLat, maxLat, escalaZoom, offsetPanX, offsetPanY) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            escalaZoom = (escalaZoom * zoom).coerceIn(0.8f, 5.0f)
                            offsetPanX += pan.x
                            offsetPanY += pan.y
                        }
                    }
                    .pointerInput(localidadesVisibles, minLng, maxLng, minLat, maxLat, escalaZoom, offsetPanX, offsetPanY) {
                        detectTapGestures { offset ->
                            val pad = 26.dp.toPx()
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

                            val clickLng = minLng + (xEnMapa / (anchoMapa)) * rangoLng
                            val clickLat = maxLat - (yEnMapa / (altoMapa)) * rangoLat
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

                // 1. REJILLA RADAR DISCRETA
                val centroX = size.width / 2f
                val centroY = size.height / 2f
                val colorRejilla = Color(0xFF37474F).copy(alpha = 0.30f)

                drawCircle(color = colorRejilla, radius = size.width * 0.20f, center = Offset(centroX, centroY), style = Stroke(1f))
                drawCircle(color = colorRejilla, radius = size.width * 0.38f, center = Offset(centroX, centroY), style = Stroke(1f))
                drawCircle(color = colorRejilla, radius = size.width * 0.55f, center = Offset(centroX, centroY), style = Stroke(1f))
                drawLine(color = colorRejilla, start = Offset(centroX, 0f), end = Offset(centroX, size.height), strokeWidth = 1f)
                drawLine(color = colorRejilla, start = Offset(0f, centroY), end = Offset(size.width, centroY), strokeWidth = 1f)

                val pad = 26.dp.toPx()
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

                // 2. DIBUJAR POLÍGONOS DE CADA LOCALIDAD CON ALTA DEFINICIÓN
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

                    val coincideFiltro = when (filtroActivo) {
                        FiltroMapa.TODOS -> true
                        FiltroMapa.ALTO -> loc.nivelRiesgo == "alto"
                        FiltroMapa.MEDIO -> loc.nivelRiesgo == "medio"
                        FiltroMapa.BAJO -> loc.nivelRiesgo == "bajo"
                    }

                    val alfaBase = if (coincideFiltro) 0.94f else 0.16f

                    // Gradientes y colores de alta saturación
                    val colorRelleno = when (loc.nivelRiesgo) {
                        "alto" -> Color(0xFFFF1744).copy(alpha = alfaBase)  // Rojo fuego
                        "medio" -> Color(0xFFFFAB00).copy(alpha = alfaBase) // Ámbar intenso
                        else -> Color(0xFF00E676).copy(alpha = alfaBase)    // Verde esmeralda vivo
                    }

                    val esSeleccionada = localidadSeleccionada?.codigo == loc.codigo

                    // Relleno suave con bordes redondeados
                    drawPath(path, color = colorRelleno, style = Fill)

                    // Borde de separación nítido con anti-alias
                    val colorBorde = when {
                        esSeleccionada -> Color(0xFF00E5FF)
                        coincideFiltro -> Color(0xFF0B1017)
                        else -> Color(0xFF263238).copy(alpha = 0.45f)
                    }
                    val anchoBorde = if (esSeleccionada) 4.5.dp.toPx() else 2.0.dp.toPx()
                    drawPath(
                        path = path,
                        color = colorBorde,
                        style = Stroke(
                            width = anchoBorde,
                            cap = StrokeCap.Round,
                            join = StrokeJoin.Round
                        )
                    )
                }

                // 3. ONDAS DE PULSO DE RADAR
                for (loc in localidadesVisibles) {
                    val centro = proyectar(loc.centroide)
                    if (centro.x < -60 || centro.x > size.width + 60 || centro.y < -60 || centro.y > size.height + 60) continue

                    if (loc.nivelRiesgo == "alto" && (filtroActivo == FiltroMapa.TODOS || filtroActivo == FiltroMapa.ALTO)) {
                        drawCircle(
                            color = Color(0xFFFF1744).copy(alpha = alfaPulso),
                            radius = radioPulso * escalaZoom.coerceIn(0.9f, 2.2f),
                            center = centro,
                            style = Stroke(3f)
                        )
                    }

                    val esSeleccionada = localidadSeleccionada?.codigo == loc.codigo
                    if (esSeleccionada) {
                        drawCircle(
                            color = Color(0xFF00E5FF).copy(alpha = brilloNeon),
                            radius = 18.dp.toPx(),
                            center = centro,
                            style = Stroke(3.5.dp.toPx())
                        )
                    }
                }

                // 4. ETIQUETAS DE TEXTO NÍTIDAS
                for (loc in localidadesVisibles) {
                    val centro = proyectar(loc.centroide)
                    if (centro.x < 0 || centro.x > size.width || centro.y < 0 || centro.y > size.height) continue

                    val coincideFiltro = when (filtroActivo) {
                        FiltroMapa.TODOS -> true
                        FiltroMapa.ALTO -> loc.nivelRiesgo == "alto"
                        FiltroMapa.MEDIO -> loc.nivelRiesgo == "medio"
                        FiltroMapa.BAJO -> loc.nivelRiesgo == "bajo"
                    }

                    paintTexto.textSize = (25f * escalaZoom.coerceIn(0.9f, 2.1f)).coerceIn(22f, 44f)
                    paintTexto.alpha = if (coincideFiltro) 255 else 75
                    paintBadgeFondo.alpha = if (coincideFiltro) 225 else 50
                    paintBadgeBorde.alpha = if (coincideFiltro) 240 else 40

                    val texto = loc.nombreCorto
                    val anchoTexto = paintTexto.measureText(texto)
                    val padH = 14f
                    val padV = 9f

                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        centro.x - (anchoTexto / 2f) - padH,
                        centro.y - 18f - padV,
                        centro.x + (anchoTexto / 2f) + padH,
                        centro.y + 10f + padV,
                        14f,
                        14f,
                        paintBadgeFondo
                    )
                    drawContext.canvas.nativeCanvas.drawRoundRect(
                        centro.x - (anchoTexto / 2f) - padH,
                        centro.y - 18f - padV,
                        centro.x + (anchoTexto / 2f) + padH,
                        centro.y + 10f + padV,
                        14f,
                        14f,
                        paintBadgeBorde
                    )

                    drawContext.canvas.nativeCanvas.drawText(
                        texto,
                        centro.x,
                        centro.y + 2f,
                        paintTexto
                    )
                }
            }

            // BOTONES DE ZOOM Y REINICIO
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF1E2733).copy(alpha = 0.95f),
                    border = BorderStroke(1.dp, Color(0xFF546E7A)),
                    shadowElevation = 6.dp,
                    modifier = Modifier.size(40.dp)
                ) {
                    IconButton(onClick = { escalaZoom = (escalaZoom * 1.35f).coerceAtMost(5.0f) }) {
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
                if (escalaZoom != 1.0f || offsetPanX != 0f || offsetPanY != 0f) {
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF00B0FF),
                        shadowElevation = 6.dp,
                        modifier = Modifier.size(40.dp)
                    ) {
                        IconButton(onClick = {
                            escalaZoom = 1.0f
                            offsetPanX = 0f
                            offsetPanY = 0f
                        }) {
                            Text("↺", fontWeight = FontWeight.ExtraBold, color = Color.White, fontSize = 19.sp)
                        }
                    }
                }
            }

            // TARJETA FLOTANTE AL TOCAR LOCALIDAD
            androidx.compose.animation.AnimatedVisibility(
                visible = localidadSeleccionada != null,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp)
            ) {
                localidadSeleccionada?.let { loc ->
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

        // CARROUSEL INFERIOR
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
