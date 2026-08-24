package com.example.riesgossocialesenchapinero

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.riesgossocialesenchapinero.data.ApiClient
import com.example.riesgossocialesenchapinero.ui.RiesgoUiState
import com.example.riesgossocialesenchapinero.ui.RiesgoViewModel
import com.example.riesgossocialesenchapinero.ui.theme.RIESGOSSOCIALESENCHAPINEROTheme

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            RIESGOSSOCIALESENCHAPINEROTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { TopAppBar(title = { Text("Barrio Seguro") }) },
                ) { innerPadding ->
                    PantallaRiesgo(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun PantallaRiesgo(modifier: Modifier = Modifier, viewModel: RiesgoViewModel = viewModel()) {
    val estado by viewModel.estado.collectAsState()

    when (val actual = estado) {
        is RiesgoUiState.Cargando -> {
            Column(
                modifier = modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text("Consultando riesgo por localidad...", modifier = Modifier.padding(top = 12.dp))
            }
        }

        is RiesgoUiState.Error -> {
            Column(
                modifier = modifier.fillMaxSize().padding(24.dp),
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
            LazyColumn(modifier = modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp)) {
                items(actual.localidades) { localidad ->
                    TarjetaLocalidad(localidad)
                }
            }
        }
    }
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
