package com.example.riesgossocialesenchapinero

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.animateContentSize
import com.example.riesgossocialesenchapinero.ui.bounceClick
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.width
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.riesgossocialesenchapinero.data.TemaApp
import com.example.riesgossocialesenchapinero.ui.AjustesViewModel

@Composable
fun AjustesScreen(
    modifier: Modifier = Modifier,
    viewModel: AjustesViewModel
) {
    val estado by viewModel.estado.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.ajustes_titulo),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(20.dp))

        // SECCIÓN NOTIFICACIONES DE RIESGO (RF-003)
        Text(
            text = stringResource(R.string.ajustes_alertas_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.ajustes_alertas_subtitulo),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Switch(
                checked = estado.alertasHabilitadas,
                onCheckedChange = { viewModel.cambiarAlertas(it) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // SECCIÓN FRECUENCIA GPS (RF-004)
        Text(
            text = stringResource(R.string.ajustes_frecuencia_titulo),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(Modifier.selectableGroup()) {
            OpcionRadio(
                texto = stringResource(R.string.ajustes_frecuencia_1m),
                seleccionado = estado.intervaloMinutos == 1,
                onClick = { viewModel.cambiarIntervalo(1) }
            )
            OpcionRadio(
                texto = stringResource(R.string.ajustes_frecuencia_2m),
                seleccionado = estado.intervaloMinutos == 2,
                onClick = { viewModel.cambiarIntervalo(2) }
            )
            OpcionRadio(
                texto = stringResource(R.string.ajustes_frecuencia_5m),
                seleccionado = estado.intervaloMinutos == 5,
                onClick = { viewModel.cambiarIntervalo(5) }
            )
            OpcionRadio(
                texto = stringResource(R.string.ajustes_frecuencia_10m),
                seleccionado = estado.intervaloMinutos == 10,
                onClick = { viewModel.cambiarIntervalo(10) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // SECCIÓN TEMA
        Text(
            text = stringResource(R.string.ajustes_tema),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(Modifier.selectableGroup()) {
            OpcionRadio(
                texto = stringResource(R.string.tema_sistema),
                seleccionado = estado.tema == TemaApp.SISTEMA,
                onClick = { viewModel.cambiarTema(TemaApp.SISTEMA) }
            )
            OpcionRadio(
                texto = stringResource(R.string.tema_claro),
                seleccionado = estado.tema == TemaApp.CLARO,
                onClick = { viewModel.cambiarTema(TemaApp.CLARO) }
            )
            OpcionRadio(
                texto = stringResource(R.string.tema_oscuro),
                seleccionado = estado.tema == TemaApp.OSCURO,
                onClick = { viewModel.cambiarTema(TemaApp.OSCURO) }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(12.dp))

        // SECCIÓN IDIOMA
        Text(
            text = stringResource(R.string.ajustes_idioma),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(Modifier.selectableGroup()) {
            OpcionRadio(
                texto = stringResource(R.string.idioma_sistema),
                seleccionado = estado.idioma == "",
                onClick = { viewModel.cambiarIdioma("") }
            )
            OpcionRadio(
                texto = stringResource(R.string.idioma_es),
                seleccionado = estado.idioma == "es",
                onClick = { viewModel.cambiarIdioma("es") }
            )
            OpcionRadio(
                texto = stringResource(R.string.idioma_en),
                seleccionado = estado.idioma == "en",
                onClick = { viewModel.cambiarIdioma("en") }
            )
            OpcionRadio(
                texto = stringResource(R.string.idioma_fr),
                seleccionado = estado.idioma == "fr",
                onClick = { viewModel.cambiarIdioma("fr") }
            )
            OpcionRadio(
                texto = stringResource(R.string.idioma_pt),
                seleccionado = estado.idioma == "pt",
                onClick = { viewModel.cambiarIdioma("pt") }
            )
            OpcionRadio(
                texto = stringResource(R.string.idioma_de),
                seleccionado = estado.idioma == "de",
                onClick = { viewModel.cambiarIdioma("de") }
            )
            OpcionRadio(
                texto = stringResource(R.string.idioma_it),
                seleccionado = estado.idioma == "it",
                onClick = { viewModel.cambiarIdioma("it") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // SECCIÓN LEGAL Y PRIVACIDAD (Ley 1581 de 2012)
        var mostrarDialogoTerminos by remember { mutableStateOf(false) }

        Text(
            text = "⚖️ Términos, Privacidad y Créditos",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .bounceClick(scaleDown = 0.97f) {
                    mostrarDialogoTerminos = true
                }
                .animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Términos de Uso y Tratamiento de Datos",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Ley 1581 de 2012, Decreto 1074 de 2015 y Habeas Data",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text("➔", style = MaterialTheme.typography.titleMedium)
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // SECCIÓN CRÉDITOS DE LOS ESTUDIANTES DESARROLLADORES
        Text(
            text = "👨‍💻 Desarrolladores del Proyecto",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .bounceClick(scaleDown = 0.97f) {
                    mostrarDialogoTerminos = true
                }
                .animateContentSize(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🛡️", fontSize = 20.sp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Equipo de Desarrollo Barrio Seguro",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Daniel Santiago Castelblanco\n• Juan Sebastián Fuentes",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Toca para ver el detalle de créditos y arquitectura.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (mostrarDialogoTerminos) {
            com.example.riesgossocialesenchapinero.ui.DialogoVerTerminosAjustes(
                onCerrar = { mostrarDialogoTerminos = false }
            )
        }
    }
}

@Composable
fun OpcionRadio(
    texto: String,
    seleccionado: Boolean,
    onClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
                selected = seleccionado,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = seleccionado,
            onClick = null // null porque el Row maneja el click
        )
        Text(
            text = texto,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}
