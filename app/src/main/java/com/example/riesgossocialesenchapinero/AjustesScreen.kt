package com.example.riesgossocialesenchapinero

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
    viewModel: AjustesViewModel,
    onVerTutorialClick: () -> Unit = {}
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

        val context = androidx.compose.ui.platform.LocalContext.current
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
        var tieneUbicacion by remember { mutableStateOf(hayPermisoUbicacion(context)) }
        var tieneNotif by remember { mutableStateOf(hayPermisoNotificaciones(context)) }

        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    tieneUbicacion = hayPermisoUbicacion(context)
                    tieneNotif = hayPermisoNotificaciones(context)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        val lanzadorPermisoNotif = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
        ) { concedido ->
            viewModel.cambiarAlertas(concedido)
            tieneNotif = hayPermisoNotificaciones(context)
        }

        val lanzadorPermisoUbicacion = androidx.activity.compose.rememberLauncherForActivityResult(
            androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            tieneUbicacion = hayPermisoUbicacion(context)
        }

        // SECCIÓN ESTADO DE PERMISOS DE SEGURIDAD
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "🛡️ Permisos del Sistema",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(10.dp))

                // Fila Ubicación
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("📍", fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text("Ubicación GPS", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (tieneUbicacion) "Autorizada para monitoreo" else "Requerida para cuadrantes y mapa",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (tieneUbicacion) {
                        Text("Activa ✅", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    } else {
                        Button(
                            onClick = {
                                lanzadorPermisoUbicacion.launch(
                                    arrayOf(
                                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                                    )
                                )
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Activar")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Spacer(modifier = Modifier.height(10.dp))

                // Fila Notificaciones
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔔", fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text("Notificaciones y Alertas", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                if (tieneNotif) "Autorizadas por el sistema" else "Requeridas para avisos de riesgo",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (tieneNotif) {
                        Text("Activas ✅", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                    } else {
                        Button(
                            onClick = {
                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    lanzadorPermisoNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    abrirAjustesDeNotificaciones(context)
                                }
                            },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Activar")
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

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
                onCheckedChange = { activo ->
                    if (activo) {
                        if (!hayPermisoNotificaciones(context)) {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                lanzadorPermisoNotif.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                abrirAjustesDeNotificaciones(context)
                            }
                        } else {
                            viewModel.cambiarAlertas(true)
                        }
                    } else {
                        viewModel.cambiarAlertas(false)
                    }
                }
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

        // SECCIÓN TUTORIAL Y RECORRIDO DE LA APP
        Text(
            text = "🎓 Guía y Recorrido de la App",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .bounceClick(scaleDown = 0.97f) {
                    onVerTutorialClick()
                }
                .animateContentSize(),
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
                        text = "🎓 Ver Tour Interactivo de Bienvenida",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Repite el recorrido guiado que explica todas las secciones y herramientas de Barrio Seguro.",
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
                    Image(
                        painter = painterResource(R.drawable.ic_escudo_barrio_seguro),
                        contentDescription = "Logo Barrio Seguro",
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Equipo de Desarrollo Barrio Seguro",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Desarrollador 1: David
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(R.drawable.foto_david),
                        contentDescription = "Foto David - Desarrollador",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "David Santiago Castelblanco Artunduaga",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Desarrollador de la App",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Desarrollador 2: Johan
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Image(
                        painter = painterResource(R.drawable.foto_johan),
                        contentDescription = "Foto Johan - Desarrollador",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Johan Sebastian Fuentes Pinto",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Desarrollador de la App",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Toca para ver el detalle de créditos, arquitectura y términos legales.",
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
