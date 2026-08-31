package com.example.riesgossocialesenchapinero

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
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
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.ajustes_titulo),
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(24.dp))

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

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

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
