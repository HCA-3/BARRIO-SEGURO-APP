package com.example.riesgossocialesenchapinero

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.riesgossocialesenchapinero.data.ApiClient
import com.example.riesgossocialesenchapinero.ui.ChatViewModel

private val PREGUNTAS_SUGERIDAS = listOf(
    "¿Cuál es la localidad más peligrosa?",
    "¿Cuál es la más segura?",
    "Cuéntame de Chapinero",
    "¿Cómo se calcula el riesgo?",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaChat(modifier: Modifier = Modifier, viewModel: ChatViewModel = viewModel()) {
    val estado by viewModel.estado.collectAsState()
    val mensajes = viewModel.mensajesVisibles
    var texto by remember { mutableStateOf("") }
    var mostrarMemoria by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(mensajes.size) {
        if (mensajes.isNotEmpty()) listState.animateScrollToItem(mensajes.size - 1)
    }

    if (mostrarMemoria) {
        DialogoMemoria(
            hechos = estado.hechosRecordados,
            onBorrarHecho = { viewModel.borrarHecho(it) },
            onBorrarConversacion = { viewModel.borrarConversacion() },
            onCerrar = { mostrarMemoria = false },
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(onClick = { mostrarMemoria = true }) {
                Text(
                    if (estado.hechosRecordados.isEmpty()) {
                        "🧠 Memoria"
                    } else {
                        "🧠 Memoria (${estado.hechosRecordados.size})"
                    },
                )
            }
        }

        if (estado.cargandoHistorial) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (mensajes.isEmpty() && !estado.enviando) {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.Center,
            ) {
                Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        "Pregúntale al agente por el riesgo de una localidad, o cuál es " +
                            "la más o menos segura.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                FilaSugerencias(onSugerenciaClick = { viewModel.enviarMensaje(it) })
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                state = listState,
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(mensajes) { mensaje -> BurbujaMensaje(mensaje) }
                if (estado.enviando) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
                            CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                        }
                    }
                }
            }
        }

        if (estado.error != null) {
            Text(
                "No pude hablar con el agente: ${estado.error}",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Escribe tu pregunta...") },
                enabled = !estado.enviando,
            )
            IconButton(
                onClick = {
                    viewModel.enviarMensaje(texto)
                    texto = ""
                },
                enabled = texto.isNotBlank() && !estado.enviando,
            ) {
                Text("➤")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilaSugerencias(onSugerenciaClick: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(PREGUNTAS_SUGERIDAS) { pregunta ->
            SuggestionChip(onClick = { onSugerenciaClick(pregunta) }, label = { Text(pregunta) })
        }
    }
}

@Composable
fun DialogoMemoria(
    hechos: List<String>,
    onBorrarHecho: (String) -> Unit,
    onBorrarConversacion: () -> Unit,
    onCerrar: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Lo que recuerda el agente de ti") },
        text = {
            Column {
                if (hechos.isEmpty()) {
                    Text(
                        "Todavía no recuerda nada. A medida que converses, va a ir guardando " +
                            "cosas relevantes que le cuentes (ej. dónde vives) para no preguntarte " +
                            "lo mismo cada vez.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    for (hecho in hechos) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(hecho, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            IconButton(onClick = { onBorrarHecho(hecho) }) {
                                Text("✕")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onCerrar) { Text("Listo") }
        },
        dismissButton = {
            TextButton(onClick = { onBorrarConversacion(); onCerrar() }) { Text("Borrar conversación") }
        },
    )
}

@Composable
fun BurbujaMensaje(mensaje: ApiClient.MensajeChat) {
    val esUsuario = mensaje.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (esUsuario) Arrangement.End else Arrangement.Start,
    ) {
        Card(
            modifier = Modifier.widthIn(max = 280.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (esUsuario) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
            ),
        ) {
            Text(mensaje.content, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
