package com.example.riesgossocialesenchapinero

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.riesgossocialesenchapinero.data.ApiClient
import com.example.riesgossocialesenchapinero.location.MonitoreoUbicacionService
import com.example.riesgossocialesenchapinero.ui.RiesgoUiState
import com.example.riesgossocialesenchapinero.ui.RiesgoViewModel
import com.example.riesgossocialesenchapinero.ui.theme.RIESGOSSOCIALESENCHAPINEROTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class Pantalla(val etiqueta: String) {
    RIESGO("Riesgo"),
    CHAT("Agente"),
}

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RIESGOSSOCIALESENCHAPINEROTheme {
                var pantallaActual by remember { mutableStateOf(Pantalla.RIESGO) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { TopAppBar(title = { Text("Barrio Seguro") }) },
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = pantallaActual == Pantalla.RIESGO,
                                onClick = { pantallaActual = Pantalla.RIESGO },
                                icon = { Text("⚠") },
                                label = { Text(Pantalla.RIESGO.etiqueta) },
                            )
                            NavigationBarItem(
                                selected = pantallaActual == Pantalla.CHAT,
                                onClick = { pantallaActual = Pantalla.CHAT },
                                icon = { Text("💬") },
                                label = { Text(Pantalla.CHAT.etiqueta) },
                            )
                        }
                    },
                ) { innerPadding ->
                    when (pantallaActual) {
                        Pantalla.RIESGO -> PantallaRiesgo(modifier = Modifier.padding(innerPadding))
                        Pantalla.CHAT -> PantallaChat(modifier = Modifier.padding(innerPadding))
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
fun PantallaRiesgo(modifier: Modifier = Modifier, viewModel: RiesgoViewModel = viewModel()) {
    val estado by viewModel.estado.collectAsState()
    val context = LocalContext.current
    var monitoreoActivo by remember { mutableStateOf(false) }

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

    val lanzadorBackground = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Se arranca el servicio se haya concedido "todo el tiempo" o no: sin
        // permiso en segundo plano, Android puede pausar el monitoreo cuando
        // la app no está visible, pero sigue funcionando mientras está abierta.
        iniciarServicioMonitoreo(context)
        monitoreoActivo = true
    }

    val lanzadorPrimerPlano = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { resultados ->
        val ubicacionConcedida = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            resultados[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (ubicacionConcedida) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                lanzadorBackground.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            } else {
                iniciarServicioMonitoreo(context)
                monitoreoActivo = true
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        ControlMonitoreo(
            activo = monitoreoActivo,
            onToggle = {
                if (monitoreoActivo) {
                    detenerServicioMonitoreo(context)
                    monitoreoActivo = false
                } else {
                    val permisos = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permisos.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    lanzadorPrimerPlano.launch(permisos.toTypedArray())
                }
            },
        )

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
                    Text("Consultando riesgo por localidad...", modifier = Modifier.padding(top = 12.dp))
                }
            }

            is RiesgoUiState.Error -> {
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("No pude conectarme al backend")
                    Text(
                        actual.mensaje,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    Text(
                        "Verifica que backend_riesgo.py esté corriendo y que ApiClient.baseUrl " +
                            "apunte a la IP correcta (10.0.2.2 para el emulador).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Button(onClick = { viewModel.cargarRanking() }, modifier = Modifier.padding(top = 16.dp)) {
                        Text("Reintentar")
                    }
                }
            }

            is RiesgoUiState.Listo -> {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                ) {
                    items(actual.localidades) { localidad ->
                        TarjetaLocalidad(localidad)
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
                Text("Monitoreo de ubicación", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (activo) {
                        "Activo: te avisamos si entras a una zona de riesgo alto"
                    } else {
                        "Actívalo para recibir una alerta si entras a una zona de riesgo alto"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onToggle) {
                Text(if (activo) "Detener" else "Activar")
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
                        TarjetaResultadoBarrio(busqueda.resultado)
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
                Surface(color = colorRiesgo(resultado.nivelRiesgo)) {
                    Text(
                        resultado.nivelRiesgo.uppercase(),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
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
fun TarjetaLocalidad(localidad: ApiClient.Localidad) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text("${localidad.posicion}. ${localidad.nombre}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Tasa: ${"%.1f".format(localidad.tasaDelitos100k)} por 100k hab.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Surface(color = colorRiesgo(localidad.nivelRiesgo)) {
                Text(
                    localidad.nivelRiesgo.uppercase(),
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

fun colorRiesgo(nivel: String): Color = when (nivel) {
    "alto" -> Color(0xFFC62828)
    "medio" -> Color(0xFFF9A825)
    else -> Color(0xFF2E7D32)
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
