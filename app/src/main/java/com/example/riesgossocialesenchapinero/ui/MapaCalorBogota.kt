package com.example.riesgossocialesenchapinero.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Geocoder
import android.location.Location
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.style.TextOverflow
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
import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
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

data class BarrioBogota(
    val nombre: String,
    val lat: Double,
    val lng: Double
)

enum class FiltroCuadras(val label: String) {
    TODOS("Todos"),
    CRITICOS("🔴 Críticos"),
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

    // Estados principales
    var reportesFirebase by remember { mutableStateOf<List<ReporteCuadra>>(emptyList()) }
    var localidades by remember { mutableStateOf<List<LocalidadMapa>>(emptyList()) }
    var barriosBogota by remember { mutableStateOf<List<BarrioBogota>>(emptyList()) }

    var reporteSeleccionado by remember { mutableStateOf<ReporteCuadra?>(null) }
    var localidadSeleccionadaInfo by remember { mutableStateOf<LocalidadMapa?>(null) }
    var puntoParaCalificar by remember { mutableStateOf<GeoPoint?>(null) }
    var direccionAutoCalculada by remember { mutableStateOf("") }
    var buscandoDireccion by remember { mutableStateOf(false) }

    var filtroActivo by remember { mutableStateOf(FiltroCuadras.TODOS) }
    var mostrarCapasLocalidad by remember { mutableStateOf(false) }
    var mostrarLeyenda by remember { mutableStateOf(false) }

    var miUbicacion by remember { mutableStateOf<GeoPoint?>(null) }
    var mapaRef by remember { mutableStateOf<MapView?>(null) }
    var modoSeleccionarPunto by remember { mutableStateOf(false) }

    // Barra de Búsqueda
    var textoBusqueda by remember { mutableStateOf("") }
    var resultadosBusqueda by remember { mutableStateOf<List<BarrioBogota>>(emptyList()) }
    var mostrarResultadosBusqueda by remember { mutableStateOf(false) }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Solicitar y obtener ubicación actual del usuario
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

    // Cargar datos estáticos en segundo plano (Localidades GeoJSON y Barrios JSON)
    LaunchedEffect(ranking) {
        withContext(Dispatchers.IO) {
            val listaLoc = cargarLocalidadesGeoJson(context, ranking)
            localidades = listaLoc
            val listaBar = cargarBarriosJson(context)
            barriosBogota = listaBar
        }
    }

    // Escuchar reportes comunitarios de cuadras en tiempo real desde Firebase Firestore
    DisposableEffect(Unit) {
        val listener = FirebaseComunidadManager.escucharCalificacionesCuadras(
            onActualizado = { reportes ->
                reportesFirebase = reportes
            }
        )
        onDispose {
            listener?.remove()
        }
    }

    // Lista combinada: Si Firebase está vacío (o sin conexión), usar reportes de ejemplo representativos
    val todosLosReportes = remember(reportesFirebase) {
        if (reportesFirebase.isNotEmpty()) {
            // Combinar con los iniciales asegurando que no se dupliquen IDs
            val idsFb = reportesFirebase.map { it.id }.toSet()
            val combinados = reportesFirebase.toMutableList()
            for (rep in reportesInicialesEjemplo()) {
                if (!idsFb.contains(rep.id)) {
                    combinados.add(rep)
                }
            }
            combinados
        } else {
            reportesInicialesEjemplo()
        }
    }

    val reportesFiltrados = remember(todosLosReportes, filtroActivo) {
        when (filtroActivo) {
            FiltroCuadras.TODOS -> todosLosReportes
            FiltroCuadras.CRITICOS -> todosLosReportes.filter { it.nivelRiesgo.equals("Critico", ignoreCase = true) || it.nivelRiesgo.equals("Alto", ignoreCase = true) }
            FiltroCuadras.PRECAUCION -> todosLosReportes.filter { it.nivelRiesgo.equals("Medio", ignoreCase = true) }
            FiltroCuadras.SEGUROS -> todosLosReportes.filter { it.nivelRiesgo.equals("Bajo", ignoreCase = true) }
            FiltroCuadras.LOCALIDADES -> todosLosReportes
        }
    }

    // Función auxiliar para iniciar calificación de un punto
    fun iniciarCalificacionPunto(punto: GeoPoint) {
        puntoParaCalificar = punto
        buscandoDireccion = true
        scope.launch {
            val dir = obtenerDireccionAproximada(context, barriosBogota, punto.latitude, punto.longitude)
            direccionAutoCalculada = dir
            buscandoDireccion = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                mapaRef?.onPause()
                mapaRef?.onDetach()
            } catch (_: Exception) {}
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 🗺️ Mapa Nativo OSMDroid ultra fluido a 60 FPS
        AndroidView(
            factory = { ctx ->
                try {
                    Configuration.getInstance().load(ctx, ctx.getSharedPreferences("osmdroid_prefs", Context.MODE_PRIVATE))
                    Configuration.getInstance().userAgentValue = "BarrioSeguroApp/${ctx.packageName}"
                    Configuration.getInstance().osmdroidTileCache = File(ctx.cacheDir, "osmdroid")
                } catch (_: Exception) {}

                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    isTilesScaledToDpi = true
                    minZoomLevel = 11.0
                    maxZoomLevel = 19.5
                    zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
                    controller.setZoom(14.5)

                    // Centrar en Chapinero / Bogotá
                    val centroBogota = GeoPoint(4.6534, -74.0620)
                    controller.setCenter(centroBogota)

                    // Limitar desplazamiento al área metropolitana de Bogotá
                    val bogotaBounds = BoundingBox(4.88, -73.90, 4.35, -74.30)
                    setScrollableAreaLimitDouble(bogotaBounds)

                    // Capa de ubicación en vivo
                    try {
                        val locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(ctx), this).apply {
                            enableMyLocation()
                            enableFollowLocation()
                        }
                        overlays.add(locationOverlay)
                    } catch (_: Exception) {}

                    // Eventos táctiles en el mapa
                    val receiver = object : MapEventsReceiver {
                        override fun singleTapConfirmedHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                if (modoSeleccionarPunto) {
                                    modoSeleccionarPunto = false
                                    iniciarCalificacionPunto(p)
                                } else {
                                    reporteSeleccionado = null
                                    localidadSeleccionadaInfo = null
                                    mostrarResultadosBusqueda = false
                                }
                            }
                            return true
                        }

                        override fun longPressHelper(p: GeoPoint?): Boolean {
                            if (p != null) {
                                modoSeleccionarPunto = false
                                iniciarCalificacionPunto(p)
                            }
                            return true
                        }
                    }
                    overlays.add(MapEventsOverlay(receiver))

                    onResume()
                    mapaRef = this
                }
            },
            update = { mapView ->
                mapView.onResume()
                actualizarCapasMapa(
                    context = context,
                    mapView = mapView,
                    reportes = reportesFiltrados,
                    localidades = if (mostrarCapasLocalidad || filtroActivo == FiltroCuadras.LOCALIDADES) localidades else emptyList(),
                    onReporteClick = { rep ->
                        localidadSeleccionadaInfo = null
                        reporteSeleccionado = rep
                    },
                    onLocalidadClick = { loc ->
                        reporteSeleccionado = null
                        localidadSeleccionadaInfo = loc
                    }
                )
            },
            modifier = Modifier.fillMaxSize()
        )

        // 🔍 Barra Superior de Búsqueda y Filtros
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            // Buscador de Barrios / Calles
            OutlinedTextField(
                value = textoBusqueda,
                onValueChange = { q ->
                    textoBusqueda = q
                    if (q.isNotBlank()) {
                        val filtrados = barriosBogota.filter {
                            normalizar(it.nombre).contains(normalizar(q))
                        }.take(5)
                        resultadosBusqueda = filtrados
                        mostrarResultadosBusqueda = filtrados.isNotEmpty()
                    } else {
                        mostrarResultadosBusqueda = false
                    }
                },
                placeholder = {
                    Text(
                        "Buscar barrio o zona (ej: Chapinero, Cedritos, Suba)",
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = { Text("🔍", fontSize = 15.sp) },
                trailingIcon = {
                    if (textoBusqueda.isNotEmpty()) {
                        IconButton(onClick = {
                            textoBusqueda = ""
                            mostrarResultadosBusqueda = false
                        }) {
                            Text("✕", fontSize = 14.sp)
                        }
                    }
                },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                ),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            )

            // Desplegable de Resultados de Búsqueda
            if (mostrarResultadosBusqueda) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                        .heightIn(max = 200.dp)
                ) {
                    LazyColumn {
                        items(resultadosBusqueda) { barrio ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        textoBusqueda = barrio.nombre
                                        mostrarResultadosBusqueda = false
                                        val target = GeoPoint(barrio.lat, barrio.lng)
                                        mapaRef?.controller?.animateTo(target, 16.5, 700L)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("📍", modifier = Modifier.padding(end = 8.dp))
                                Text(
                                    text = barrio.nombre,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Fila de Chips de Filtro
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f),
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
                            label = { Text(filtro.label, fontSize = 11.5.sp, fontWeight = if (seleccionado) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                }

                // Botón de Leyenda
                IconButton(
                    onClick = { mostrarLeyenda = !mostrarLeyenda },
                    modifier = Modifier.size(32.dp)
                ) {
                    Text("ℹ️", fontSize = 16.sp)
                }
            }
        }

        // 🏷️ Banner informativo de "Modo Selección de Punto"
        AnimatedVisibility(
            visible = modoSeleccionarPunto,
            enter = slideInVertically() + fadeIn(),
            exit = slideOutVertically() + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 115.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📍", fontSize = 18.sp)
                    Text(
                        text = "Toca cualquier calle o cuadra en el mapa",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    IconButton(
                        onClick = { modoSeleccionarPunto = false },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text("✕", color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp)
                    }
                }
            }
        }

        // ℹ️ Diálogo de Leyenda y Niveles de Riesgo
        if (mostrarLeyenda) {
            Dialog(onDismissRequest = { mostrarLeyenda = false }) {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "🛡️ Guía del Mapa Comunitario",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            IconButton(onClick = { mostrarLeyenda = false }, modifier = Modifier.size(28.dp)) {
                                Text("✕")
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        ItemLeyenda(color = Color(0xFFD50000), titulo = "🔴 Crítico", descripcion = "Zonas con asaltos violentos frecuentes o peligro inminente.")
                        ItemLeyenda(color = Color(0xFFFF6D00), titulo = "🟠 Alto", descripcion = "Hurtos regulares, cosquilleo o venta de sustancias.")
                        ItemLeyenda(color = Color(0xFFFFD600), titulo = "🟡 Precaución", descripcion = "Poca iluminación, calles solitarias de noche.")
                        ItemLeyenda(color = Color(0xFF00C853), titulo = "🟢 Seguro", descripcion = "Cuadras transitadas, bien iluminadas y vigiladas.")

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "💡 Puedes calificar cualquier cuadra tocándola en el mapa o usando el botón '+' abajo a la derecha.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // 🎛️ Botones Flotantes de Control (Calificar, Recentrar GPS, Zoom, Google Maps)
        FloatingActionButtonGroup(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(
                    end = 16.dp,
                    bottom = if (reporteSeleccionado != null || localidadSeleccionadaInfo != null) 280.dp else 24.dp
                ),
            onCalificarClick = {
                modoSeleccionarPunto = true
                Toast.makeText(context, "Toca cualquier punto del mapa para calificar la calle", Toast.LENGTH_SHORT).show()
            },
            onCalificarMiUbicacionClick = {
                if (miUbicacion != null) {
                    iniciarCalificacionPunto(miUbicacion!!)
                } else {
                    lanzadorPermisos.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }
            },
            onRecentrarClick = {
                if (miUbicacion != null) {
                    mapaRef?.controller?.animateTo(miUbicacion, 16.5, 600L)
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

        // 📋 Ficha Inferior con Detalle del Reporte de Cuadra Seleccionado
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
                            // Actualizar también en la lista local para reflejo inmediato
                            reportesFirebase = reportesFirebase.map {
                                if (it.id == rep.id) it.copy(votosApoyo = it.votosApoyo + 1) else it
                            }
                            Toast.makeText(context, "¡Validación registrada!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onCompartir = {
                        val shareText = "⚠️ Alerta de Seguridad en Bogotá:\n" +
                                "📍 ${rep.direccionAprox}\n" +
                                "Riesgo: ${rep.nivelRiesgo.uppercase()}\n" +
                                "Motivo: ${rep.categoria}\n" +
                                "Horario crítico: ${rep.horarioCritico}\n" +
                                "\"${rep.justificacion}\"\n\n" +
                                "Reportado en la App Barrio Seguro."
                        val sendIntent = Intent().apply {
                            action = Intent.ACTION_SEND
                            putExtra(Intent.EXTRA_TEXT, shareText)
                            type = "text/plain"
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Compartir alerta vecinal"))
                    }
                )
            }
        }

        // 🗺️ Ficha Inferior con Detalle de Localidad Seleccionada
        AnimatedVisibility(
            visible = localidadSeleccionadaInfo != null,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            val loc = localidadSeleccionadaInfo
            if (loc != null) {
                CardDetalleLocalidadMapa(
                    localidad = loc,
                    onCerrar = { localidadSeleccionadaInfo = null },
                    onVerDetalles = {
                        onSeleccionarLocalidad?.invoke(loc.nombre)
                    }
                )
            }
        }

        // ✍️ Diálogo para Calificar y Justificar una Cuadra
        if (puntoParaCalificar != null) {
            DialogoCalificarCuadra(
                punto = puntoParaCalificar!!,
                direccionSugerida = direccionAutoCalculada,
                cargandoDireccion = buscandoDireccion,
                onDismiss = { puntoParaCalificar = null },
                onGuardar = { reporteNuevo ->
                    scope.launch {
                        val exito = FirebaseComunidadManager.guardarCalificacionCuadra(reporteNuevo)
                        // Agregar inmediatamente a la lista local para respuesta instantánea
                        reportesFirebase = listOf(reporteNuevo) + reportesFirebase
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
fun ItemLeyenda(color: Color, titulo: String, descripcion: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
                .padding(top = 4.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(text = titulo, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
            Text(text = descripcion, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun FloatingActionButtonGroup(
    modifier: Modifier = Modifier,
    onCalificarClick: () -> Unit,
    onCalificarMiUbicacionClick: () -> Unit,
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
            Text("➕ Calificar Cuadra", fontWeight = FontWeight.Bold, fontSize = 13.5.sp)
        }

        // Calificar mi ubicación actual
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.size(44.dp)
        ) {
            IconButton(onClick = onCalificarMiUbicacionClick) {
                Text("📍", fontSize = 18.sp)
            }
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

        // Abrir en Google Maps
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
    onApoyar: () -> Unit,
    onCompartir: () -> Unit
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
        shape = RoundedCornerShape(22.dp),
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
                    modifier = Modifier.padding(vertical = 3.dp)
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

            Spacer(modifier = Modifier.height(4.dp))

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

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = onCompartir, modifier = Modifier.size(36.dp)) {
                        Text("📤", fontSize = 16.sp)
                    }
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
}

@Composable
fun CardDetalleLocalidadMapa(
    localidad: LocalidadMapa,
    onCerrar: () -> Unit,
    onVerDetalles: () -> Unit
) {
    val colorNivel = ColoresDatos.relleno(localidad.nivelRiesgo)

    Card(
        shape = RoundedCornerShape(22.dp),
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
                        text = "Localidad ${localidad.nombre}",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onCerrar, modifier = Modifier.size(28.dp)) {
                    Text("✕")
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Nivel de Riesgo General:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = localidad.nivelRiesgo.uppercase(),
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                        color = colorNivel
                    )
                }

                if (localidad.tasa100k > 0) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Tasa Delitos:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${String.format(Locale.US, "%.1f", localidad.tasa100k)} / 100k hab",
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onVerDetalles,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("📊 Ver estadísticas completas de ${localidad.nombreCorto}")
            }
        }
    }
}

@Composable
fun DialogoCalificarCuadra(
    punto: GeoPoint,
    direccionSugerida: String,
    cargandoDireccion: Boolean,
    onDismiss: () -> Unit,
    onGuardar: (ReporteCuadra) -> Unit
) {
    var nivelRiesgo by remember { mutableStateOf("Alto") }
    var categoria by remember { mutableStateOf("Robo frecuente / Atraco") }
    var horario by remember { mutableStateOf("Noche") }
    var direccion by remember(direccionSugerida) {
        mutableStateOf(
            if (direccionSugerida.isNotBlank()) direccionSugerida
            else "Sector Lat ${String.format(Locale.US, "%.4f", punto.latitude)}, Lng ${String.format(Locale.US, "%.4f", punto.longitude)}"
        )
    }
    var justificacion by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("Vecino de Bogotá") }

    val niveles = listOf("Bajo", "Medio", "Alto", "Critico")
    val categorias = listOf(
        "Robo frecuente / Atraco",
        "Mala iluminación",
        "Calle solitaria / Callejón",
        "Consumo en vía pública",
        "Acoso callejero",
        "Riñas / Inseguridad",
        "Hurto de vehículos",
        "Venta de drogas",
        "Otro motivo"
    )
    val horarios = listOf("Noche", "Madrugada", "Día", "Todo el día")

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
                    trailingIcon = {
                        if (cargandoDireccion) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        }
                    },
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
                                    else -> "🟢 Seguro"
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
                    placeholder = { Text("Ej: Poca iluminación después de las 8pm, motociclistas sospechosos cerca de la esquina...") },
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
                                id = "cuadra_${System.currentTimeMillis()}",
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
    onLocalidadClick: (LocalidadMapa) -> Unit
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
                onLocalidadClick(loc)
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

/**
 * Carga lista de barrios de Bogotá desde assets/datos/barrios.json
 */
private fun cargarBarriosJson(context: Context): List<BarrioBogota> {
    val lista = mutableListOf<BarrioBogota>()
    try {
        val jsonStr = context.assets.open("datos/barrios.json").bufferedReader().use { it.readText() }
        val array = JSONArray(jsonStr)
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val nombre = obj.getString("n")
            val lat = obj.getDouble("y")
            val lng = obj.getDouble("x")
            lista.add(BarrioBogota(nombre = nombre, lat = lat, lng = lng))
        }
    } catch (_: Exception) {}
    return lista
}

/**
 * Obtiene la dirección aproximada usando Geocoder y fallback a lista de barrios.
 */
private suspend fun obtenerDireccionAproximada(
    context: Context,
    barrios: List<BarrioBogota>,
    lat: Double,
    lng: Double
): String {
    return withContext(Dispatchers.IO) {
        try {
            @Suppress("DEPRECATION")
            val geocoder = Geocoder(context, Locale("es", "CO"))
            val addresses = geocoder.getFromLocation(lat, lng, 1)
            if (!addresses.isNullOrEmpty()) {
                val addr = addresses[0]
                val linea = addr.getAddressLine(0)
                if (!linea.isNullOrBlank()) {
                    return@withContext linea.replace(", Colombia", "").replace(", Bogotá", ", Bogotá D.C.")
                }
            }
        } catch (_: Exception) {}

        // Fallback al barrio más cercano de la base de datos local
        if (barrios.isNotEmpty()) {
            val masCercano = barrios.minByOrNull { b ->
                val dLat = b.lat - lat
                val dLng = b.lng - lng
                dLat * dLat + dLng * dLng
            }
            if (masCercano != null) {
                return@withContext "Sector ${masCercano.nombre}, Bogotá"
            }
        }

        "Sector Lat ${String.format(Locale.US, "%.4f", lat)}, Lng ${String.format(Locale.US, "%.4f", lng)}"
    }
}

private fun normalizar(s: String): String {
    return s.lowercase()
        .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
        .replace("ñ", "n").trim()
}

/**
 * Reportes iniciales de ejemplo para que el mapa muestre inmediatamente puntos interactivos reales
 */
private fun reportesInicialesEjemplo(): List<ReporteCuadra> = listOf(
    ReporteCuadra(
        id = "rep_ej_1",
        latitud = 4.6486,
        longitud = -74.0628,
        direccionAprox = "Cra. 7 con Calle 60 (Parque de los Hippies, Chapinero)",
        nivelRiesgo = "Medio",
        categoria = "Robo frecuente / Cosquilleo",
        justificacion = "Cuidado con celulares en horas pico y viernes en la noche.",
        horarioCritico = "Noche",
        usuarioAlias = "Vecino Chapinero Central",
        timestamp = System.currentTimeMillis() - 7200000L,
        votosApoyo = 14
    ),
    ReporteCuadra(
        id = "rep_ej_2",
        latitud = 4.6582,
        longitud = -74.0583,
        direccionAprox = "Calle 72 con Cra. 11 (Sector Financiero, Chapinero)",
        nivelRiesgo = "Bajo",
        categoria = "Zona segura",
        justificacion = "Buena iluminación y presencia constante de cuadrantes de policía.",
        horarioCritico = "Día",
        usuarioAlias = "Comerciante Calle 72",
        timestamp = System.currentTimeMillis() - 14400000L,
        votosApoyo = 22
    ),
    ReporteCuadra(
        id = "rep_ej_3",
        latitud = 4.6672,
        longitud = -74.0535,
        direccionAprox = "Cra. 15 con Calle 85 (Zona Rosa, Chapinero)",
        nivelRiesgo = "Alto",
        categoria = "Hurto a personas",
        justificacion = "Motos sospechosas acechando a peatones saliendo de restaurantes.",
        horarioCritico = "Noche",
        usuarioAlias = "Estudiante Zona Rosa",
        timestamp = System.currentTimeMillis() - 21600000L,
        votosApoyo = 19
    ),
    ReporteCuadra(
        id = "rep_ej_4",
        latitud = 4.6300,
        longitud = -74.0660,
        direccionAprox = "Cra. 7 con Calle 45 (Sector Universidades, Javeriana)",
        nivelRiesgo = "Alto",
        categoria = "Robo frecuente / Atraco",
        justificacion = "Evitar puentes peatonales después de las 7:00 pm.",
        horarioCritico = "Noche",
        usuarioAlias = "Universitario Javeriana",
        timestamp = System.currentTimeMillis() - 28800000L,
        votosApoyo = 31
    ),
    ReporteCuadra(
        id = "rep_ej_5",
        latitud = 4.6030,
        longitud = -74.0720,
        direccionAprox = "Carrera 7 con Calle 19 (Centro, Santa Fe)",
        nivelRiesgo = "Critico",
        categoria = "Atraco / Consumo en vía pública",
        justificacion = "Mala iluminación en callejones adyacentes y hurtos con arma blanca.",
        horarioCritico = "Todo el día",
        usuarioAlias = "Trabajador Centro",
        timestamp = System.currentTimeMillis() - 36000000L,
        votosApoyo = 45
    ),
    ReporteCuadra(
        id = "rep_ej_6",
        latitud = 4.6315,
        longitud = -74.0815,
        direccionAprox = "Park Way (Cra. 24 con Cl. 39, Teusaquillo)",
        nivelRiesgo = "Medio",
        categoria = "Poca iluminación",
        justificacion = "Parque tranquilo de día pero con tramos muy oscuros hacia el sur de noche.",
        horarioCritico = "Noche",
        usuarioAlias = "Vecino ParkWay",
        timestamp = System.currentTimeMillis() - 43200000L,
        votosApoyo = 11
    ),
    ReporteCuadra(
        id = "rep_ej_7",
        latitud = 4.6980,
        longitud = -74.0320,
        direccionAprox = "Calle 116 con Cra. 7 (Santa Bárbara, Usaquén)",
        nivelRiesgo = "Bajo",
        categoria = "Zona segura",
        justificacion = "Tránsito fluido, cámaras de seguridad y patrullaje frecuente.",
        horarioCritico = "Día",
        usuarioAlias = "Residente Usaquén",
        timestamp = System.currentTimeMillis() - 50400000L,
        votosApoyo = 16
    ),
    ReporteCuadra(
        id = "rep_ej_8",
        latitud = 4.6150,
        longitud = -74.1550,
        direccionAprox = "Plaza de las Américas (Av. Primero de Mayo, Kennedy)",
        nivelRiesgo = "Alto",
        categoria = "Robo frecuente / Cosquilleo",
        justificacion = "Cuidado en los paraderos de SITP y entradas del centro comercial.",
        horarioCritico = "Noche",
        usuarioAlias = "Comunidad Kennedy",
        timestamp = System.currentTimeMillis() - 57600000L,
        votosApoyo = 28
    )
)
