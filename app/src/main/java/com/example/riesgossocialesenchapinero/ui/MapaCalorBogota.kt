package com.example.riesgossocialesenchapinero.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import com.example.riesgossocialesenchapinero.data.ApiClient
import com.example.riesgossocialesenchapinero.data.FirebaseComunidadManager
import com.example.riesgossocialesenchapinero.data.ReporteCuadra
import com.example.riesgossocialesenchapinero.ui.theme.ColoresDatos
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PuntoGeo(val lng: Double, val lat: Double)

data class LocalidadMapa(
    val codigo: Int,
    val nombre: String,
    val nombreCorto: String,
    val nivelRiesgo: String,
    val tasa100k: Double,
    val poligono: List<PuntoGeo>,
    val centroide: PuntoGeo
)

enum class FiltroCuadras(val label: String) {
    TODOS("Todos los reportes"),
    CRITICOS("🔴 Alto / Crítico"),
    PRECAUCION("🟡 Precaución"),
    SEGUROS("🟢 Seguros"),
    LOCALIDADES("🗺️ Capa Localidades")
}

@Composable
fun MapaCalorBogota(
    modifier: Modifier = Modifier,
    ranking: List<ApiClient.Localidad> = emptyList(),
    onSeleccionarLocalidad: ((String) -> Unit)? = null
) {
    MapaCalorBogotaScreen(
        ranking = ranking,
        modifier = modifier,
        onSeleccionarLocalidad = onSeleccionarLocalidad
    )
}

@Composable
fun MapaCalorBogotaScreen(
    ranking: List<ApiClient.Localidad>,
    modifier: Modifier = Modifier,
    onSeleccionarLocalidad: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var reportesCuadras by remember { mutableStateOf<List<ReporteCuadra>>(emptyList()) }
    var localidades by remember { mutableStateOf<List<LocalidadMapa>>(emptyList()) }
    var reporteSeleccionado by remember { mutableStateOf<ReporteCuadra?>(null) }
    var puntoParaCalificar by remember { mutableStateOf<GeoPoint?>(null) }
    var filtroActivo by remember { mutableStateOf(FiltroCuadras.TODOS) }
    var mostrarCapasLocalidad by remember { mutableStateOf(false) }

    var miUbicacion by remember { mutableStateOf<GeoPoint?>(null) }
    var mapaRef by remember { mutableStateOf<MapView?>(null) }
    var modoSeleccionarPunto by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Solicitar y obtener ubicación actual
    val lanzadorPermisos = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permisos ->
        if (permisos[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permisos[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        ) {
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc: Location? ->
                        if (loc != null) {
                            val gp = GeoPoint(loc.latitude, loc.longitude)
                            miUbicacion = gp
                            mapaRef?.controller?.animateTo(gp, 15.5, 800L)
                        }
                    }
            } catch (_: SecurityException) {}
        }
    }

    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc: Location? ->
                        if (loc != null) {
                            miUbicacion = GeoPoint(loc.latitude, loc.longitude)
                        }
                    }
            } catch (_: SecurityException) {}
        } else {
            lanzadorPermisos.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    // Cargar polígonos de localidades
    LaunchedEffect(ranking) {
        withContext(Dispatchers.IO) {
            val lista = cargarLocalidadesGeoJson(context, ranking)
            localidades = lista
        }
    }

    // Escuchar reportes comunitarios de cuadras en tiempo real desde Firebase
    DisposableEffect(Unit) {
        val listener = FirebaseComunidadManager.escucharCalificacionesCuadras(
            onActualizado = { reportes ->
                reportesCuadras = reportes
            }
        )
        onDispose {
            listener?.remove()
        }
    }

    val reportesFiltrados = remember(reportesCuadras, filtroActivo) {
        when (filtroActivo) {
            FiltroCuadras.TODOS -> reportesCuadras
            FiltroCuadras.CRITICOS -> reportesCuadras.filter { it.nivelRiesgo.equals("Critico", ignoreCase = true) || it.nivelRiesgo.equals("Alto", ignoreCase = true) }
            FiltroCuadras.PRECAUCION -> reportesCuadras.filter { it.nivelRiesgo.equals("Medio", ignoreCase = true) }
            FiltroCuadras.SEGUROS -> reportesCuadras.filter { it.nivelRiesgo.equals("Bajo", ignoreCase = true) }
            FiltroCuadras.LOCALIDADES -> reportesCuadras
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Mapa Nativo OSMDroid ultra-rápido a 60 FPS
        AndroidView(
            factory = { ctx ->
                Configuration.getInstance().apply {
                    userAgentValue = ctx.packageName
                    osmdroidTileCache = File(ctx.cacheDir, "osmdroid")
                }

                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(14.2)
                    // Centrar en Chapinero / Bogotá
                    val centroBogota = GeoPoint(4.6534, -74.0620)
                    controller.setCenter(centroBogota)

                    // Capa de ubicación en vivo
                    val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this)
                    locationOverlay.enableMyLocation()
                    overlays.add(locationOverlay)

                    // Eventos de toque en el mapa (para seleccionar y calificar cuadras)
                    val receiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                if (modoSeleccionarPunto) {
                                    puntoParaCalificar = p
                                    modoSeleccionarPunto = false
                                } else {
                                    // Si no hay reporte seleccionado, tocar un espacio libre deselecciona
                                    reporteSeleccionado = null
                                }
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                puntoParaCalificar = p
                            }
                            return true
                        }
                    }
                    overlays.add(MapEventsOverlay(receiver))

                    mapaRef = this
                }
            },
            update = { mapView ->
                // Actualizar marcadores de cuadras y polígonos
                actualizarCapasMapa(
                    context = context,
                    mapView = mapView,
                    reportes = reportesFiltrados,
                    localidades = if (mostrarCapasLocalidad || filtroActivo == FiltroCuadras.LOCALIDADES) localidades else emptyList(),
                    onReporteClick = { rep ->
                        reporteSeleccionado = rep
                    },
                    onLocalidadClick = { locNombre ->
                        onSeleccionarLocalidad?.invoke(locNombre)
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        // Barra superior con Filtros
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mapa de Riesgo por Cuadras",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "${reportesCuadras.size} cuadras calificadas por vecinos",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (modoSeleccionarPunto) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Toca una cuadra 📍",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(FiltroCuadras.values()) { filtro ->
                    val seleccionado = filtroActivo == filtro
                    FilterChip(
                        selected = seleccionado,
                        onClick = {
                            filtroActivo = filtro
                            if (filtro == FiltroCuadras.LOCALIDADES) {
                                mostrarCapasLocalidad = !mostrarCapasLocalidad
                            }
                        },
                        label = { Text(filtro.label, fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }
        }

        // Botón Flotante Principal: "+ Calificar Cuadra"
        FloatingActionButtonGroup(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = if (reporteSeleccionado != null) 240.dp else 24.dp),
            onCalificarClick = {
                modoSeleccionarPunto = true
                Toast.makeText(context, "Toca cualquier cuadra o calle en el mapa para calificarla", Toast.LENGTH_LONG).show()
            },
            onRecentrarClick = {
                if (miUbicacion != null) {
                    mapaRef?.controller?.animateTo(miUbicacion, 16.0, 600L)
                } else {
                    lanzadorPermisos.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
            },
            onZoomInClick = {
                mapaRef?.controller?.zoomIn()
            },
            onZoomOutClick = {
                mapaRef?.controller?.zoomOut()
            },
            onGoogleMapsClick = {
                val centro = mapaRef?.mapCenter ?: miUbicacion ?: GeoPoint(4.6534, -74.0620)
                val uri = Uri.parse("geo:${centro.latitude},${centro.longitude}?q=${centro.latitude},${centro.longitude}(Barrio Seguro)")
                val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                    setPackage("com.google.android.apps.maps")
                }
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                } else {
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                }
            }
        )

        // Ficha Inferior con Detalle del Reporte Seleccionado
        AnimatedVisibility(
            visible = reporteSeleccionado != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val rep = reporteSeleccionado
            if (rep != null) {
                CardDetalleReporteCuadra(
                    reporte = rep,
                    onCerrar = { reporteSeleccionado = null },
                    onApoyar = {
                        scope.launch {
                            FirebaseComunidadManager.apoyarCalificacionCuadra(rep.id)
                            reporteSeleccionado = rep.copy(votosApoyo = rep.votosApoyo + 1)
                            Toast.makeText(context, "¡Apoyaste este reporte vecinal!", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }

        // Diálogo para Calificar y Justificar una Cuadra
        if (puntoParaCalificar != null) {
            DialogoCalificarCuadra(
                punto = puntoParaCalificar!!,
                onDismiss = { puntoParaCalificar = null },
                onGuardar = { reporteNuevo ->
                    scope.launch {
                        val exito = FirebaseComunidadManager.guardarCalificacionCuadra(reporteNuevo)
                        puntoParaCalificar = null
                        if (exito) {
                            Toast.makeText(context, "¡Cuadra calificada y compartida con la comunidad!", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Reporte guardado localmente", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun FloatingActionButtonGroup(
    modifier: Modifier = Modifier,
    onCalificarClick: () -> Unit,
    onRecentrarClick: () -> Unit,
    onZoomInClick: () -> Unit,
    onZoomOutClick: () -> Unit,
    onGoogleMapsClick: () -> Unit
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Botón destacado: + Calificar Cuadra
        Button(
            onClick = onCalificarClick,
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(24.dp),
            elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp),
            modifier = Modifier.height(48.dp)
        ) {
            Text("➕ Calificar Cuadra", fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        // Recentrar GPS
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.size(44.dp)
        ) {
            IconButton(onClick = onRecentrarClick) {
                Text("🎯", fontSize = 18.sp)
            }
        }

        // Abrir Google Maps
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.size(44.dp)
        ) {
            IconButton(onClick = onGoogleMapsClick) {
                Text("🗺️", fontSize = 18.sp)
            }
        }

        // Píldora de Zoom (+ / -)
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 6.dp,
            modifier = Modifier.width(44.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onZoomInClick, modifier = Modifier.size(40.dp)) {
                    Text("➕", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                HorizontalDivider(modifier = Modifier.width(28.dp), color = MaterialTheme.colorScheme.outlineVariant)
                IconButton(onClick = onZoomOutClick, modifier = Modifier.size(40.dp)) {
                    Text("➖", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CardDetalleReporteCuadra(
    reporte: ReporteCuadra,
    onCerrar: () -> Unit,
    onApoyar: () -> Unit
) {
    val colorNivel = when (reporte.nivelRiesgo.lowercase()) {
        "critico", "crítico" -> Color(0xFFD50000)
        "alto" -> Color(0xFFFF6D00)
        "medio" -> Color(0xFFFFD600)
        else -> Color(0xFF00C853)
    }

    val fechaStr = remember(reporte.timestamp) {
        val sdf = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
        sdf.format(Date(reporte.timestamp))
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(colorNivel)
                    )
                    Text(
                        text = "Riesgo ${reporte.nivelRiesgo.uppercase()}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorNivel
                    )
                }
                IconButton(onClick = onCerrar, modifier = Modifier.size(28.dp)) {
                    Text("✕", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (reporte.direccionAprox.isNotBlank()) {
                Text(
                    text = "📍 ${reporte.direccionAprox}",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "⚠️ ${reporte.categoria}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = "⏰ Horario: ${reporte.horarioCritico}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Justificación del vecino:",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Text(
                text = if (reporte.justificacion.isNotBlank()) "\"${reporte.justificacion}\"" else "\"Sin justificación adicional\"",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Por ${reporte.usuarioAlias} • $fechaStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onApoyar,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "👍 Validar (${reporte.votosApoyo})",
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun DialogoCalificarCuadra(
    punto: GeoPoint,
    onDismiss: () -> Unit,
    onGuardar: (ReporteCuadra) -> Unit
) {
    var nivelRiesgo by remember { mutableStateOf("Alto") }
    var categoria by remember { mutableStateOf("Robo frecuente") }
    var horario by remember { mutableStateOf("Noche") }
    var direccion by remember { mutableStateOf("Sector Lat ${String.format(Locale.US, "%.4f", punto.latitude)}, Lng ${String.format(Locale.US, "%.4f", punto.longitude)}") }
    var justificacion by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("Vecino de Chapinero") }

    val niveles = listOf("Bajo", "Medio", "Alto", "Critico")
    val categorias = listOf(
        "Mala iluminación",
        "Robo frecuente / Atraco",
        "Calle solitaria / Callejón",
        "Consumo en vía pública",
        "Acoso callejero",
        "Riñas / Inseguridad",
        "Hurto de vehículos",
        "Otro motivo"
    )
    val horarios = listOf("Día", "Noche", "Madrugada", "Todo el día")

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🛡️ Calificar Cuadra / Calle",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Text("✕")
                    }
                }

                Text(
                    text = "Advierte a la comunidad sobre el nivel de peligro y describe por qué.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                // Dirección o referencia
                OutlinedTextField(
                    value = direccion,
                    onValueChange = { direccion = it },
                    label = { Text("Nombre de la calle o dirección de referencia") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Nivel de Riesgo
                Text(
                    text = "Nivel de Peligrosidad:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    items(niveles) { n ->
                        val sel = nivelRiesgo == n
                        val colorPildora = when (n) {
                            "Critico" -> Color(0xFFD50000)
                            "Alto" -> Color(0xFFFF6D00)
                            "Medio" -> Color(0xFFFFD600)
                            else -> Color(0xFF00C853)
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (sel) colorPildora else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { nivelRiesgo = n }
                        ) {
                            Text(
                                text = when (n) {
                                    "Critico" -> "🔴 Crítico"
                                    "Alto" -> "🟠 Alto"
                                    "Medio" -> "🟡 Medio"
                                    else -> "🟢 Bajo / Seguro"
                                },
                                color = if (sel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Motivo principal
                Text(
                    text = "Motivo de Riesgo:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    items(categorias) { cat ->
                        val sel = categoria == cat
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { categoria = cat }
                        ) {
                            Text(
                                text = cat,
                                color = if (sel) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Horario
                Text(
                    text = "Horario más crítico:",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(vertical = 6.dp)
                ) {
                    items(horarios) { h ->
                        val sel = horario == h
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (sel) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable { horario = h }
                        ) {
                            Text(
                                text = h,
                                color = if (sel) MaterialTheme.colorScheme.onSecondary else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Justificación escrita
                Text(
                    text = "¿Por qué es peligrosa? (Justificación):",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
                OutlinedTextField(
                    value = justificacion,
                    onValueChange = { justificacion = it },
                    placeholder = { Text("Ej: Poca iluminación a partir de las 8pm, motos sospechosas cerca del parque...") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = alias,
                    onValueChange = { alias = it },
                    label = { Text("Tu alias o nombre de vecino") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val reporte = ReporteCuadra(
                                latitud = punto.latitude,
                                longitud = punto.longitude,
                                direccionAprox = direccion.trim(),
                                nivelRiesgo = nivelRiesgo,
                                categoria = categoria,
                                justificacion = justificacion.trim(),
                                horarioCritico = horario,
                                usuarioAlias = alias.trim().ifBlank { "Vecino anónimo" },
                                timestamp = System.currentTimeMillis()
                            )
                            onGuardar(reporte)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Text("Publicar Reporte")
                    }
                }
            }
        }
    }
}

/**
 * Actualiza los marcadores interactivos de OsmDroid y polígonos sobre el mapa.
 */
private fun actualizarCapasMapa(
    context: Context,
    mapView: MapView,
    reportes: List<ReporteCuadra>,
    localidades: List<LocalidadMapa>,
    onReporteClick: (ReporteCuadra) -> Unit,
    onLocalidadClick: (String) -> Unit = {}
) {
    // Conservar solo el overlay de ubicación y el overlay de eventos
    val overlaysConservados = mapView.overlays.filter { it is MyLocationNewOverlay || it is MapEventsOverlay }
    mapView.overlays.clear()
    mapView.overlays.addAll(overlaysConservados)

    // 1. Polígonos de Localidades (si están activos)
    for (loc in localidades) {
        if (loc.poligono.size < 3) continue
        val polygon = Polygon(mapView).apply {
            val geoPoints = loc.poligono.map { GeoPoint(it.lat, it.lng) }
            points = geoPoints
            val colorBase = ColoresDatos.relleno(loc.nivelRiesgo)
            fillPaint.color = colorBase.copy(alpha = 0.22f).toArgb()
            outlinePaint.color = Color(0xFF1A73E8).copy(alpha = 0.60f).toArgb()
            outlinePaint.strokeWidth = 3f
            title = loc.nombre
            setOnClickListener { _, _, _ ->
                onLocalidadClick(loc.nombre)
                true
            }
        }
        mapView.overlays.add(polygon)
    }

    // 2. Marcadores de Cuadras Calificadas
    for (rep in reportes) {
        val marker = Marker(mapView).apply {
            position = GeoPoint(rep.latitud, rep.longitud)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = "Cuadra ${rep.nivelRiesgo}: ${rep.categoria}"
            snippet = rep.justificacion
            icon = crearIconoMarcadorRiesgo(context, rep.nivelRiesgo)
            setOnMarkerClickListener { _, _ ->
                onReporteClick(rep)
                true
            }
        }
        mapView.overlays.add(marker)
    }

    mapView.invalidate()
}

/**
 * Genera dinámicamente un icono nítido de marcador según el nivel de riesgo.
 */
private fun crearIconoMarcadorRiesgo(context: Context, nivelRiesgo: String): Drawable {
    val sizePx = 72
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val color = when (nivelRiesgo.lowercase()) {
        "critico", "crítico" -> android.graphics.Color.rgb(213, 0, 0)
        "alto" -> android.graphics.Color.rgb(255, 109, 0)
        "medio" -> android.graphics.Color.rgb(255, 214, 0)
        else -> android.graphics.Color.rgb(0, 200, 83)
    }

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        style = Paint.Style.FILL
    }

    val paintBorde = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    val radio = sizePx / 2.6f
    val cx = sizePx / 2f
    val cy = sizePx / 2.6f

    // Sombra suave
    val paintSombra = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.argb(80, 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy + 4f, radio, paintSombra)

    // Círculo principal
    canvas.drawCircle(cx, cy, radio, paint)
    canvas.drawCircle(cx, cy, radio, paintBorde)

    // Punto central blanco
    val paintCentro = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = android.graphics.Color.WHITE
        style = Paint.Style.FILL
    }
    canvas.drawCircle(cx, cy, radio / 3f, paintCentro)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Carga polígonos de localidades desde assets.
 */
private fun cargarLocalidadesGeoJson(context: Context, ranking: List<ApiClient.Localidad>): List<LocalidadMapa> {
    val mapaRiesgo = ranking.associateBy({ normalizar(it.nombre) }, { it })
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
                val sumLng = puntos.map { it.lng }.average()
                val sumLat = puntos.map { it.lat }.average()
                val r = mapaRiesgo[normalizar(nombre)]
                lista.add(
                    LocalidadMapa(
                        codigo = codigo,
                        nombre = nombre,
                        nombreCorto = nombre.replace("Santa Fe", "Sta. Fe").replace("La Candelaria", "Candelaria"),
                        nivelRiesgo = r?.nivelRiesgo ?: "Medio",
                        tasa100k = r?.tasaDelitos100k ?: 0.0,
                        poligono = puntos,
                        centroide = PuntoGeo(sumLng, sumLat)
                    )
                )
            }
        }
    } catch (_: Exception) {}
    return lista
}

private fun normalizar(s: String): String {
    return s.lowercase()
        .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
        .replace("ñ", "n").trim()
}
