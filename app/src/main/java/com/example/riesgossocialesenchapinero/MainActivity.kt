package com.example.riesgossocialesenchapinero

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.runtime.CompositionLocalProvider
import java.util.Locale
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.riesgossocialesenchapinero.data.ApiClient
import com.example.riesgossocialesenchapinero.location.MonitoreoUbicacionService
import com.example.riesgossocialesenchapinero.ui.BadgeRiesgo
import com.example.riesgossocialesenchapinero.ui.BarraDelito
import com.example.riesgossocialesenchapinero.ui.AjustesViewModel
import com.example.riesgossocialesenchapinero.ui.FilaDato
import com.example.riesgossocialesenchapinero.ui.RiesgoUiState
import com.example.riesgossocialesenchapinero.ui.SeccionPlegable
import com.example.riesgossocialesenchapinero.ui.TileDato
import com.example.riesgossocialesenchapinero.ui.RiesgoViewModel
import com.example.riesgossocialesenchapinero.ui.theme.RIESGOSSOCIALESENCHAPINEROTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Pantalla {
    RIESGO, CHAT, AJUSTES
}

class MainActivity : AppCompatActivity() {
    // Se revalidan en onResume porque pueden cambiar fuera de la app: el
    // usuario puede conceder o quitar el permiso desde Ajustes, y el servicio
    // puede haberse detenido solo. Guardarlos en un `remember` de la pantalla
    // dejaba el botón mostrando un estado viejo.
    private val permisoUbicacion = mutableStateOf(false)
    private val monitoreoActivo = mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        permisoUbicacion.value = hayPermisoUbicacion(this)
        monitoreoActivo.value = MonitoreoUbicacionService.activo
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Recupera la URL del backend que el usuario haya guardado antes; sin
        // esto siempre volvería al candidato del emulador tras reinstalar.
        ApiClient.inicializar(this)
        android.util.Log.d("BarrioSeguro", "MainActivity iniciada. Backend: ${ApiClient.baseUrl}")
        setContent {
            val ajustesViewModel: AjustesViewModel = viewModel()
            val ajustesEstado by ajustesViewModel.estado.collectAsState()

            val locale = remember(ajustesEstado.idioma) {
                if (ajustesEstado.idioma.isEmpty()) {
                    Locale.getDefault()
                } else {
                    Locale.forLanguageTag(ajustesEstado.idioma)
                }
            }
            val currentConfiguration = LocalConfiguration.current
            val currentContext = LocalContext.current
            val localizedConfiguration = remember(locale, currentConfiguration) {
                Configuration(currentConfiguration).apply {
                    setLocale(locale)
                    setLayoutDirection(locale)
                }
            }
            val localizedContext = remember(locale, currentContext) {
                currentContext.createConfigurationContext(localizedConfiguration)
            }

            CompositionLocalProvider(
                LocalConfiguration provides localizedConfiguration,
                LocalContext provides localizedContext,
                LocalActivityResultRegistryOwner provides this
            ) {
                RIESGOSSOCIALESENCHAPINEROTheme(tema = ajustesEstado.tema) {
                var pantallaActual by remember { mutableStateOf(Pantalla.RIESGO) }

                val actividad = this
                // true cuando el diálogo lo pidió el usuario pulsando "Activar",
                // no el arranque automático: solo en ese caso tiene sentido
                // mandarlo a Ajustes si Android ya no muestra el diálogo.
                var pedidoPorElUsuario by remember { mutableStateOf(false) }

                val lanzadorPermisos = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions(),
                ) { resultados ->
                    val concedido = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        resultados[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    permisoUbicacion.value = concedido
                    if (concedido) {
                        // Lo pidió para activar el monitoreo: arráncalo ya, sin
                        // obligarle a pulsar "Activar" una segunda vez.
                        if (pedidoPorElUsuario) {
                            iniciarServicioMonitoreo(actividad)
                            monitoreoActivo.value = true
                        }
                    } else if (pedidoPorElUsuario &&
                        !actividad.shouldShowRequestPermissionRationale(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                        )
                    ) {
                        // Negado de forma permanente: Android ya no volverá a
                        // mostrar el diálogo, así que la única vía es Ajustes.
                        abrirAjustesDeLaApp(actividad)
                    }
                    pedidoPorElUsuario = false
                }

                val pedirPermisoUbicacion = {
                    val permisos = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permisos.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    lanzadorPermisos.launch(permisos.toTypedArray())
                }

                // Se pide al entrar, y solo si todavía no está concedido: si el
                // usuario ya dijo que sí, el diálogo no vuelve a salir. Va en la
                // raíz y no dentro de PantallaRiesgo para que no dependa de en
                // qué pestaña esté.
                LaunchedEffect(Unit) {
                    if (!hayPermisoUbicacion(actividad)) pedirPermisoUbicacion()
                }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    when (pantallaActual) {
                                        Pantalla.RIESGO -> stringResource(R.string.pantalla_riesgo)
                                        Pantalla.CHAT -> stringResource(R.string.pantalla_agente)
                                        Pantalla.AJUSTES -> stringResource(R.string.pantalla_ajustes)
                                    }
                                )
                            }
                        )
                    },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = pantallaActual == Pantalla.RIESGO,
                                onClick = { pantallaActual = Pantalla.RIESGO },
                                icon = { Text("⚠") },
                                label = { Text(stringResource(R.string.pantalla_riesgo)) },
                            )
                            NavigationBarItem(
                                selected = pantallaActual == Pantalla.CHAT,
                                onClick = { pantallaActual = Pantalla.CHAT },
                                icon = { Text("💬") },
                                label = { Text(stringResource(R.string.pantalla_agente)) },
                            )
                            NavigationBarItem(
                                selected = pantallaActual == Pantalla.AJUSTES,
                                onClick = { pantallaActual = Pantalla.AJUSTES },
                                icon = { Text("⚙") },
                                label = { Text(stringResource(R.string.pantalla_ajustes)) },
                            )
                        }
                    },
                ) { innerPadding ->
                    when (pantallaActual) {
                        Pantalla.RIESGO -> PantallaRiesgo(
                            modifier = Modifier.padding(innerPadding),
                            monitoreoActivo = monitoreoActivo.value,
                            onToggleMonitoreo = {
                                if (monitoreoActivo.value) {
                                    detenerServicioMonitoreo(actividad)
                                    monitoreoActivo.value = false
                                } else if (permisoUbicacion.value) {
                                    iniciarServicioMonitoreo(actividad)
                                    monitoreoActivo.value = true
                                } else {
                                    // Falta el permiso: se pide aquí porque lo
                                    // pidió el usuario explícitamente, no de
                                    // forma automática.
                                    pedidoPorElUsuario = true
                                    pedirPermisoUbicacion()
                                }
                            },
                        )
                        Pantalla.CHAT -> PantallaChat(modifier = Modifier.padding(innerPadding))
                        Pantalla.AJUSTES -> AjustesScreen(
                            modifier = Modifier.padding(innerPadding),
                            viewModel = ajustesViewModel
                        )
                    }
                }
            }
        }
    }
}
}

sealed interface EstadoBusquedaBarrio {
    object Inactivo : EstadoBusquedaBarrio
    object Buscando : EstadoBusquedaBarrio
    data class Resultado(val busqueda: ApiClient.BusquedaBarrio) : EstadoBusquedaBarrio
    data class Error(val mensaje: String) : EstadoBusquedaBarrio
}

@Composable
fun PantallaRiesgo(
    modifier: Modifier = Modifier,
    monitoreoActivo: Boolean,
    onToggleMonitoreo: () -> Unit,
    viewModel: RiesgoViewModel = viewModel(),
) {
    val estado by viewModel.estado.collectAsState()
    var textoBusqueda by remember { mutableStateOf("") }
    var estadoBusqueda by remember { mutableStateOf<EstadoBusquedaBarrio>(EstadoBusquedaBarrio.Inactivo) }
    val scope = rememberCoroutineScope()

    fun buscarBarrio(nombre: String, localidad: String? = null) {
        estadoBusqueda = EstadoBusquedaBarrio.Buscando
        scope.launch {
            estadoBusqueda = try {
                val resultado = withContext(Dispatchers.IO) { ApiClient.buscarBarrio(nombre, localidad) }
                EstadoBusquedaBarrio.Resultado(resultado)
            } catch (e: Exception) {
                EstadoBusquedaBarrio.Error(e.message ?: "Error desconocido buscando el barrio")
            }
        }
    }

    // Ficha que se abre al tocar un recuadro, sea de localidad o de barrio.
    var seleccion by remember { mutableStateOf<SeleccionDetalle?>(null) }
    var detalle by remember { mutableStateOf<ApiClient.DetalleLocalidad?>(null) }
    var errorDetalle by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(seleccion) {
        val actual = seleccion
        detalle = null
        errorDetalle = null
        if (actual != null) {
            try {
                detalle = withContext(Dispatchers.IO) { ApiClient.obtenerDetalleLocalidad(actual.localidad) }
            } catch (e: Exception) {
                errorDetalle = e.message ?: "No pude cargar los datos de esta zona"
            }
        }
    }

    seleccion?.let { actual ->
        DialogoDetalle(
            seleccion = actual,
            detalle = detalle,
            error = errorDetalle,
            onCerrar = { seleccion = null },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        ControlMonitoreo(activo = monitoreoActivo, onToggle = onToggleMonitoreo)

        BarraBusquedaBarrio(
            texto = textoBusqueda,
            onTextoChange = { textoBusqueda = it },
            onBuscar = { buscarBarrio(textoBusqueda) },
            onLimpiar = {
                textoBusqueda = ""
                estadoBusqueda = EstadoBusquedaBarrio.Inactivo
            },
            mostrarLimpiar = estadoBusqueda != EstadoBusquedaBarrio.Inactivo,
        )

        val busquedaActual = estadoBusqueda
        if (busquedaActual != EstadoBusquedaBarrio.Inactivo) {
            ResultadoBusquedaBarrio(
                estado = busquedaActual,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                onElegirOpcion = { opcion -> buscarBarrio(textoBusqueda, opcion.localidad) },
                onAbrirDetalle = { r ->
                    seleccion = SeleccionDetalle(localidad = r.localidad, barrio = r.barrio, upz = r.upz)
                },
            )
            return@Column
        }

        when (val actual = estado) {
            is RiesgoUiState.Cargando -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                    Text(stringResource(R.string.cargando_riesgo), modifier = Modifier.padding(top = 12.dp))
                }
            }

            is RiesgoUiState.Error -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(stringResource(R.string.error_backend))
                    Text(
                        actual.mensaje,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        stringResource(R.string.error_ayuda),
                        style = MaterialTheme.typography.bodySmall,
                    )

                    var servidor by remember { mutableStateOf(ApiClient.baseUrl) }
                    OutlinedTextField(
                        value = servidor,
                        onValueChange = { servidor = it },
                        label = { Text(stringResource(R.string.servidor_label)) },
                        placeholder = { Text("192.168.0.107:8000") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    )
                    Button(
                        onClick = { viewModel.cambiarServidor(servidor) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        Text(stringResource(R.string.btn_conectar))
                    }
                    Button(onClick = { viewModel.cargarRanking() }, modifier = Modifier.padding(top = 8.dp)) {
                        Text(stringResource(R.string.btn_reintentar))
                    }
                }
            }

            is RiesgoUiState.Listo -> {
                if (actual.esCache) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            stringResource(R.string.sin_conexion_cache),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(actual.localidades) { localidad ->
                        TarjetaLocalidad(
                            localidad,
                            modifier = Modifier.clickable {
                                seleccion = SeleccionDetalle(localidad = localidad.nombre)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ControlMonitoreo(activo: Boolean, onToggle: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(stringResource(R.string.monitoreo_titulo), style = MaterialTheme.typography.titleSmall)
                Text(
                    if (activo) {
                        stringResource(R.string.monitoreo_activo)
                    } else {
                        stringResource(R.string.monitoreo_inactivo)
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            // Siempre Activar/Detener: el botón nunca deja de ofrecer el toggle.
            // Si falta el permiso, al pulsar "Activar" se pide (y si ya estaba
            // negado a perpetuidad, se abre Ajustes) — ver MainActivity.
            Button(onClick = onToggle) {
                Text(if (activo) stringResource(R.string.btn_detener) else stringResource(R.string.btn_activar))
            }
        }
    }
}

@Composable
fun BarraBusquedaBarrio(
    texto: String,
    onTextoChange: (String) -> Unit,
    onBuscar: () -> Unit,
    onLimpiar: () -> Unit,
    mostrarLimpiar: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = texto,
            onValueChange = onTextoChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Buscar barrio (ej. Acapulco)...") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (texto.isNotBlank()) onBuscar() }),
        )
        IconButton(onClick = onBuscar, enabled = texto.isNotBlank()) {
            Text("🔍")
        }
        if (mostrarLimpiar) {
            IconButton(onClick = onLimpiar) {
                Text("✕")
            }
        }
    }
}

@Composable
fun ResultadoBusquedaBarrio(
    estado: EstadoBusquedaBarrio,
    onAbrirDetalle: (ApiClient.ResultadoBarrio) -> Unit,
    modifier: Modifier = Modifier,
    onElegirOpcion: (ApiClient.ResultadoBarrio) -> Unit,
) {
    when (estado) {
        is EstadoBusquedaBarrio.Inactivo -> Unit

        is EstadoBusquedaBarrio.Buscando -> {
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
            }
        }

        is EstadoBusquedaBarrio.Error -> {
            Column(modifier = modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No pude buscar el barrio")
                Text(estado.mensaje, style = MaterialTheme.typography.bodySmall)
            }
        }

        is EstadoBusquedaBarrio.Resultado -> {
            when (val busqueda = estado.busqueda) {
                is ApiClient.BusquedaBarrio.Encontrado -> {
                    Column(modifier = modifier.padding(12.dp)) {
                        TarjetaResultadoBarrio(
                            busqueda.resultado,
                            modifier = Modifier.clickable { onAbrirDetalle(busqueda.resultado) },
                        )
                        Text(
                            "Toca el recuadro para ver todos los datos de la zona.",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }

                is ApiClient.BusquedaBarrio.Ambiguo -> {
                    Column(modifier = modifier.padding(12.dp)) {
                        Text(
                            "Hay varios barrios con ese nombre — ¿cuál?",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        LazyColumn {
                            items(busqueda.opciones) { opcion ->
                                TarjetaResultadoBarrio(
                                    opcion,
                                    modifier = Modifier.clickable { onElegirOpcion(opcion) },
                                )
                            }
                        }
                    }
                }

                is ApiClient.BusquedaBarrio.NoEncontrado -> {
                    Column(
                        modifier = modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(busqueda.mensaje, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun TarjetaResultadoBarrio(resultado: ApiClient.ResultadoBarrio, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(resultado.barrio, style = MaterialTheme.typography.titleMedium)
                    Text(resultado.localidad, style = MaterialTheme.typography.bodySmall)
                }
                BadgeRiesgo(resultado.nivelRiesgo)
            }
            resultado.upz?.let { upz ->
                Text(
                    "UPZ ${upz.upz}: llamadas de emergencia ${upz.nivelLlamadas} " +
                        "(${"%.0f".format(upz.tasaLlamadas100k)} por 100k hab., acumulado 2023-2025)",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

/**
 * Qué recuadro se tocó. [barrio] y [upz] solo vienen cuando fue un resultado de
 * búsqueda de barrio; desde el ranking solo se sabe la localidad.
 */
data class SeleccionDetalle(
    val localidad: String,
    val barrio: String? = null,
    val upz: ApiClient.RiesgoUpz? = null,
)

/**
 * Ficha con todo lo que el pipeline recopiló de la zona.
 *
 * Las cifras se agrupan y etiquetan por la capa de la que salen (localidad o
 * UPZ) porque NO existen datos por barrio: OpenStreetMap solo aporta el nombre
 * y un punto. Presentar los delitos de la localidad bajo el título del barrio,
 * sin decirlo, daría a entender una precisión que los datos no tienen.
 */
@Composable
fun DialogoDetalle(
    seleccion: SeleccionDetalle,
    detalle: ApiClient.DetalleLocalidad?,
    error: String?,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
        title = {
            Column {
                Text(seleccion.barrio ?: seleccion.localidad)
                if (seleccion.barrio != null) {
                    Text(
                        "Barrio de ${seleccion.localidad}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                when {
                    error != null -> Text(error, style = MaterialTheme.typography.bodyMedium)

                    detalle == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.padding(end = 12.dp))
                        Text(stringResource(R.string.detalle_cargando))
                    }

                    else -> {
                        if (seleccion.barrio != null) {
                            Text(
                                stringResource(R.string.detalle_nota_barrio),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp),
                            )
                        }

                        // Encabezado: el nivel de riesgo y la cifra que mejor
                        // resume la zona, antes de cualquier desglose.
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            BadgeRiesgo(detalle.nivelRiesgo)
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    "%,.0f".format(detalle.tasaDelitos100k),
                                    style = MaterialTheme.typography.headlineSmall,
                                )
                                Text(
                                    stringResource(R.string.delitos_100k_label),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        seleccion.upz?.let { upz ->
                            SeccionPlegable(stringResource(R.string.seccion_upz, upz.upz)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    TileDato(
                                        stringResource(R.string.nivel_llamadas_label),
                                        upz.nivelLlamadas.uppercase(),
                                        Modifier.weight(1f),
                                    )
                                    TileDato(
                                        stringResource(R.string.por_100k_label),
                                        "%,.0f".format(upz.tasaLlamadas100k),
                                        Modifier.weight(1f),
                                    )
                                }
                            }
                        }

                        SeccionPlegable(stringResource(R.string.seccion_localidad, detalle.localidad)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TileDato(
                                    stringResource(R.string.poblacion_label),
                                    "%,d".format(detalle.poblacion),
                                    Modifier.weight(1f),
                                )
                                TileDato(
                                    stringResource(R.string.delitos_periodo_label),
                                    "%,d".format(detalle.delitosTotal),
                                    Modifier.weight(1f),
                                )
                            }
                            FilaDato(stringResource(R.string.score_mixto_label), "%.4f".format(detalle.scoreMixto))
                        }

                        SeccionPlegable(stringResource(R.string.seccion_delitos_tipo)) {
                            val maximo = detalle.detalleDelitos.maxOfOrNull { it.second } ?: 0
                            detalle.detalleDelitos.forEachIndexed { i, (tipo, cantidad) ->
                                BarraDelito(
                                    tipo = tipo,
                                    cantidad = cantidad,
                                    maximo = maximo,
                                    indice = i,
                                )
                            }
                        }

                        SeccionPlegable(stringResource(R.string.seccion_contexto), abiertaPorDefecto = false) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                TileDato(
                                    stringResource(R.string.estrato_label),
                                    "%.2f".format(detalle.estratoPromedio),
                                    Modifier.weight(1f),
                                )
                                TileDato(stringResource(R.string.area_label), "%,.1f km²".format(detalle.areaKm2), Modifier.weight(1f))
                            }
                            FilaDato(stringResource(R.string.luminarias_label), "%,d".format(detalle.luminarias))
                            FilaDato(stringResource(R.string.luminarias_km2_label), "%.1f".format(detalle.luminariasPorKm2))
                            FilaDato(stringResource(R.string.vias_label), "%,.1f km".format(detalle.longitudViasKm))
                            FilaDato(stringResource(R.string.incidentes_label), "%,d".format(detalle.incidentesNuse))
                        }
                    }
                }
            }
        },
    )
}



private fun hayPermisoUbicacion(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

/** Pantalla de ajustes de la app, para conceder a mano un permiso ya negado. */
private fun abrirAjustesDeLaApp(context: Context) {
    val intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", context.packageName, null),
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(intent)
}

private fun iniciarServicioMonitoreo(context: Context) {
    val intent = Intent(context, MonitoreoUbicacionService::class.java).apply {
        action = MonitoreoUbicacionService.ACTION_INICIAR
    }
    ContextCompat.startForegroundService(context, intent)
}

private fun detenerServicioMonitoreo(context: Context) {
    val intent = Intent(context, MonitoreoUbicacionService::class.java).apply {
        action = MonitoreoUbicacionService.ACTION_DETENER
    }
    context.startService(intent)
}

@Composable
fun TarjetaLocalidad(localidad: ApiClient.Localidad, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("${localidad.posicion}. ${localidad.nombre}", style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(R.string.tasa_formato, localidad.tasaDelitos100k),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            BadgeRiesgo(localidad.nivelRiesgo)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PantallaRiesgoPreview() {
    RIESGOSSOCIALESENCHAPINEROTheme {
        TarjetaLocalidad(
            ApiClient.Localidad(
                posicion = 1,
                nombre = "Los Mártires",
                nivelRiesgo = "alto",
                scorePonderado100k = 1251827.07,
                tasaDelitos100k = 50826.91,
            )
        )
    }
}
