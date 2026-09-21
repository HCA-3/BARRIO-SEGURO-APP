package com.example.riesgossocialesenchapinero.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TabDenuncias(val titulo: String, val icono: String) {
    TUTORIAL("Guía y Tutorial", "📖"),
    PLATAFORMAS("Plataformas Oficiales", "🏛️"),
    ASISTENTE("Asistente de Redacción", "✍️"),
    LINEAS("Líneas de Atención", "📞")
}

data class PlataformaOficial(
    val id: String,
    val entidad: String,
    val nombreSistema: String,
    val descripcion: String,
    val tipoCasos: List<String>,
    val urlOficial: String,
    val telefonoDirecto: String? = null,
    val colorBadge: Color
)

@Composable
fun DenunciasScreen(
    modifier: Modifier = Modifier,
    onAbrirTutorialInicial: Boolean = false
) {
    val context = LocalContext.current
    var pestanaSeleccionada by remember { mutableStateOf(if (onAbrirTutorialInicial) TabDenuncias.TUTORIAL else TabDenuncias.PLATAFORMAS) }
    var urlVisorWeb by remember { mutableStateOf<Pair<String, String>?>(null) } // Pair(titulo, url)
    var delitoPreseleccionado by remember { mutableStateOf<String?>(null) }

    val plataformas = remember {
        listOf(
            PlataformaOficial(
                id = "adenunciar",
                entidad = "Policía Nacional & Fiscalía General",
                nombreSistema = "Sistema ¡A Denunciar!",
                descripcion = "Plataforma oficial unificada para denunciar delitos comunes con plena validez judicial.",
                tipoCasos = listOf("Hurto a personas", "Hurto de celulares (IMEI)", "Hurto a comercio", "Extorsión", "Estafa", "Delitos informáticos", "Violencia de género"),
                urlOficial = "https://adenunciar.policia.gov.co/adenunciar/",
                telefonoDirecto = "122",
                colorBadge = Color(0xFF1E88E5)
            ),
            PlataformaOficial(
                id = "fiscalia",
                entidad = "Fiscalía General de la Nación",
                nombreSistema = "Ventanilla Única de Denuncias",
                descripcion = "Denuncia de delitos graves, homicidios, microtráfico estructurado y seguimiento al radicado SPOA.",
                tipoCasos = listOf("Homicidio", "Microtráfico organizado", "Amenazas graves", "Delitos contra menores", "Corrupción"),
                urlOficial = "https://www.fiscalia.gov.co/colombia/servicios-de-informacion-al-ciudadano/donde-y-como-denunciar/",
                telefonoDirecto = "122",
                colorBadge = Color(0xFF00897B)
            ),
            PlataformaOficial(
                id = "alcaldia",
                entidad = "Alcaldía Mayor de Bogotá",
                nombreSistema = "Bogotá Te Escucha (SDQS)",
                descripcion = "Reporte distrital de focos de inseguridad, fallas en alumbrado público, parques solitarios y convivencia.",
                tipoCasos = listOf("Alumbrado público dañado", "Inseguridad en parques/puentes", "Ocupación indebida", "Convivencia vecinal"),
                urlOficial = "https://bogota.gov.co/sdqs/",
                telefonoDirecto = "195",
                colorBadge = Color(0xFFFB8C00)
            ),
            PlataformaOficial(
                id = "caivirtual",
                entidad = "Centro Cibernético Policial",
                nombreSistema = "CAI Virtual de la Policía",
                descripcion = "Atención especializada y chat 24/7 con peritos informáticos para delitos y estafas digitales.",
                tipoCasos = listOf("Estafas por WhatsApp / Nequi", "Ciberacoso / Sextorsión", "Suplantación de identidad", "Robo de cuentas bancarias"),
                urlOficial = "https://caivirtual.policia.gov.co/",
                telefonoDirecto = "123",
                colorBadge = Color(0xFF8E24AA)
            ),
            PlataformaOficial(
                id = "spoa",
                entidad = "Rama Judicial & Fiscalía",
                nombreSistema = "Consulta de Radicado SPOA",
                descripcion = "Verifica el estado de tu denuncia, fiscal asignado y avance procesal con tu código de 21 dígitos.",
                tipoCasos = listOf("Seguimiento de denuncia", "Consulta de fiscal asignado", "Historial de procesos"),
                urlOficial = "https://consultaprocesos.ramajudicial.gov.co/",
                telefonoDirecto = "122",
                colorBadge = Color(0xFF3949AB)
            ),
            PlataformaOficial(
                id = "personeria",
                entidad = "Personería de Bogotá",
                nombreSistema = "Veeduría y Derechos Humanos",
                descripcion = "Atención a víctimas, abuso de autoridad policial y protección de derechos humanos en Bogotá.",
                tipoCasos = listOf("Abuso policial", "Vulneración de derechos", "Quejas contra servidores públicos"),
                urlOficial = "https://www.personeriabogota.gov.co/",
                telefonoDirecto = "143",
                colorBadge = Color(0xFFE53935)
            )
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Banner Superior con acceso rápido al tutorial
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "⚖️ Denuncias Oficiales Virtuales",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Canales gubernamentales y distritales de Bogotá",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Button(
                    onClick = { pestanaSeleccionada = TabDenuncias.TUTORIAL },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text("📖 Ver Guía", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Pestañas Principales
        PrimaryScrollableTabRow(
            selectedTabIndex = pestanaSeleccionada.ordinal,
            modifier = Modifier.fillMaxWidth(),
            edgePadding = 12.dp
        ) {
            TabDenuncias.values().forEach { tab ->
                Tab(
                    selected = pestanaSeleccionada == tab,
                    onClick = { pestanaSeleccionada = tab },
                    text = {
                        Text(
                            text = "${tab.icono} ${tab.titulo}",
                            fontWeight = if (pestanaSeleccionada == tab) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp
                        )
                    }
                )
            }
        }

        // Contenido de la pestaña
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (pestanaSeleccionada) {
                TabDenuncias.TUTORIAL -> SeccionTutorialGuiado(
                    onIrAPlataformas = { pestanaSeleccionada = TabDenuncias.PLATAFORMAS },
                    onIrAAsistente = { pestanaSeleccionada = TabDenuncias.ASISTENTE }
                )
                TabDenuncias.PLATAFORMAS -> SeccionPlataformasOficiales(
                    plataformas = plataformas,
                    onAbrirEnApp = { titulo, url ->
                        urlVisorWeb = Pair(titulo, url)
                    },
                    onRedactarHechoClick = { delito ->
                        delitoPreseleccionado = delito
                        pestanaSeleccionada = TabDenuncias.ASISTENTE
                    }
                )
                TabDenuncias.ASISTENTE -> SeccionAsistenteRedaccion(
                    delitoInicial = delitoPreseleccionado,
                    onAbrirEnApp = { titulo, url ->
                        urlVisorWeb = Pair(titulo, url)
                    },
                    onIrAFormularios = { pestanaSeleccionada = TabDenuncias.PLATAFORMAS }
                )
                TabDenuncias.LINEAS -> SeccionLineasAtencion()
            }
        }
    }

    // Modal Visor Web Integrado en la App
    if (urlVisorWeb != null) {
        DialogoVisorWeb(
            titulo = urlVisorWeb!!.first,
            url = urlVisorWeb!!.second,
            onDismiss = { urlVisorWeb = null }
        )
    }
}

/**
 * PESTAÑA 1: Tutorial Completo y Paso a Paso
 */
@Composable
fun SeccionTutorialGuiado(
    onIrAPlataformas: () -> Unit,
    onIrAAsistente: () -> Unit
) {
    val context = LocalContext.current
    val pasos = listOf(
        TutorialPaso(
            numero = 1,
            titulo = "1. Identifica ante qué entidad denunciar",
            icono = "🏛️",
            resumen = "No todos los problemas van al mismo lugar. Elegir el canal correcto evita que tu caso se archive.",
            detalles = listOf(
                "• Policía / Fiscalía (¡A Denunciar!): Hurtos, atracos, extorsiones, estafas y ciberdelitos.",
                "• Fiscalía General (Línea 122): Homicidios, agresiones graves, microtráfico y delitos sexuales.",
                "• Alcaldía de Bogotá (Bogotá Te Escucha / Línea 195): Zonas oscuras, problemas vecinales, espacio público y alumbrado.",
                "• CAI Virtual: Fraudes bancarios, robo de WhatsApp, extorsión digital."
            )
        ),
        TutorialPaso(
            numero = 2,
            titulo = "2. Ten listos tus documentos y datos",
            icono = "📋",
            resumen = "Antes de abrir el formulario oficial, asegúrate de tener a la mano:",
            detalles = listOf(
                "• Tu cédula de ciudadanía o documento de identidad.",
                "• Fecha, hora y dirección exacta en Bogotá (barrio y localidad).",
                "• En caso de celular hurtado: Código IMEI (lo encuentras en la caja o factura).",
                "• Descripción detallada de sospechosos (ropa, tatuajes, contextura, motos o placas).",
                "• Pruebas si existen (fotos, capturas de pantalla de chats, comprobantes bancarios)."
            )
        ),
        TutorialPaso(
            numero = 3,
            titulo = "3. Cómo relatar los hechos con validez jurídica",
            icono = "✍️",
            resumen = "El relato debe ser cronológico, claro y objetivo (bajo gravedad de juramento):",
            detalles = listOf(
                "• Escribe en orden de tiempo: 'A las 7:30 PM transitaba por la Cra 7 con Cl 60 cuando...'",
                "• Evita opiniones o suposiciones; relata hechos concretos.",
                "• Detalla los objetos hurtados y su avalúo aproximado en pesos colombianos.",
                "• Consejo: Puedes usar nuestro 'Asistente de Redacción' para que estructure el texto por ti."
            )
        ),
        TutorialPaso(
            numero = 4,
            titulo = "4. Guarda tu número de Radicado SPOA",
            icono = "🔢",
            resumen = "Al finalizar la denuncia en la plataforma oficial, el sistema te generará un código único:",
            detalles = listOf(
                "• Guarda una captura o PDF con el Número de Noticia Criminal (21 dígitos).",
                "• Este radicado es tu comprobante legal para reclamaciones de seguros o trámites bancarios.",
                "• Con este código puedes consultar el avance de la investigación en fiscalia.gov.co."
            )
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("💡", fontSize = 28.sp)
                    Column {
                        Text(
                            text = "Guía Oficial de Denuncia Ciudadana",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Sigue estos 4 pasos clave para que tu denuncia tenga validez y trámite judicial inmediato.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
            }
        }

        items(pasos) { paso ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(paso.icono, fontSize = 22.sp)
                        Text(
                            text = paso.titulo,
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = paso.resumen,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    paso.detalles.forEach { detalle ->
                        Text(
                            text = detalle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onIrAAsistente,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("✍️ Redactar mi caso", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = onIrAPlataformas,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f).height(48.dp)
                ) {
                    Text("🏛️ Ver Plataformas", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

data class TutorialPaso(
    val numero: Int,
    val titulo: String,
    val icono: String,
    val resumen: String,
    val detalles: List<String>
)

/**
 * PESTAÑA 2: Plataformas Oficiales y Formularios
 */
@Composable
fun SeccionPlataformasOficiales(
    plataformas: List<PlataformaOficial>,
    onAbrirEnApp: (String, String) -> Unit,
    onRedactarHechoClick: (String?) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🛡️", fontSize = 24.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Portales Oficiales del Estado",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Puedes abrir los portales en tu navegador, verlos dentro de la app o copiar el enlace directo.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }

        items(plataformas) { plat ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = plat.colorBadge.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f, fill = false)
                        ) {
                            Text(
                                text = plat.entidad,
                                color = plat.colorBadge,
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        if (plat.telefonoDirecto != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.clickable {
                                    marcarTelefonoSeguro(context, plat.telefonoDirecto)
                                }
                            ) {
                                Text(
                                    text = "📞 Línea ${plat.telefonoDirecto}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = plat.nombreSistema,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Text(
                        text = plat.descripcion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Delitos y casos que recibe:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        items(plat.tipoCasos) { caso ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant
                            ) {
                                Text(
                                    text = caso,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Botones de acción
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onRedactarHechoClick(plat.tipoCasos.firstOrNull()) },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("✍️ Redactar", fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = { onAbrirEnApp(plat.nombreSistema, plat.urlOficial) },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("📱 En App", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }

                        Button(
                            onClick = { abrirEnlaceSeguro(context, plat.urlOficial) },
                            colors = ButtonDefaults.buttonColors(containerColor = plat.colorBadge),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Text("🌐 Navegador", fontWeight = FontWeight.Bold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    // Botón secundario para copiar URL
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { copiarAlPortapapeles(context, plat.nombreSistema, plat.urlOficial) }
                        ) {
                            Text("📋 Copiar enlace directo", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

/**
 * PESTAÑA 3: Asistente Guiado de Redacción de Hechos Formales
 */
@Composable
fun SeccionAsistenteRedaccion(
    delitoInicial: String? = null,
    onAbrirEnApp: (String, String) -> Unit,
    onIrAFormularios: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tipoDelito by remember { mutableStateOf(delitoInicial ?: "Hurto a personas (Celular / Pertenencias)") }
    var fechaHora by remember {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        mutableStateOf(sdf.format(Date()))
    }
    var direccionLugar by remember { mutableStateOf("") }
    var relatoHechos by remember { mutableStateOf("") }
    var bienesAfectados by remember { mutableStateOf("") }
    var descripcionSospechosos by remember { mutableStateOf("") }
    var relatoGenerado by remember { mutableStateOf("") }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun obtenerUbicacionGPS() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { loc: Location? ->
                        if (loc != null) {
                            direccionLugar = "Coordenadas Lat: ${String.format(Locale.US, "%.5f", loc.latitude)}, Lng: ${String.format(Locale.US, "%.5f", loc.longitude)} (Bogotá D.C.)"
                            Toast.makeText(context, "Ubicación GPS agregada", Toast.LENGTH_SHORT).show()
                        }
                    }
            } catch (_: SecurityException) {}
        } else {
            Toast.makeText(context, "Concede permisos de ubicación para capturar coordenadas", Toast.LENGTH_SHORT).show()
        }
    }

    fun compilarRelatoFormal() {
        val sb = StringBuilder()
        sb.append("HECHOS OBJETO DE DENUNCIA:\n\n")
        sb.append("1. TIPO DE DELITO: $tipoDelito\n")
        sb.append("2. FECHA Y HORA DEL SUCESO: $fechaHora\n")
        sb.append("3. LUGAR DE LOS HECHOS: ${direccionLugar.ifBlank { "Bogotá D.C." }}\n\n")
        sb.append("4. RELATO CRONOLÓGICO DE LO OCURRIDO:\n")
        sb.append(relatoHechos.ifBlank { "El suscrito denuncia los hechos ocurridos en la fecha y lugar indicados donde fui víctima de la conducta punible." })
        sb.append("\n\n")
        if (bienesAfectados.isNotBlank()) {
            sb.append("5. BIENES, PERTENENCIAS O ELEMENTOS AFECTADOS:\n")
            sb.append(bienesAfectados)
            sb.append("\n\n")
        }
        if (descripcionSospechosos.isNotBlank()) {
            sb.append("6. INDIVIDUALIZACIÓN / CARACTERÍSTICAS DE LOS AGRESORES:\n")
            sb.append(descripcionSospechosos)
            sb.append("\n\n")
        }
        sb.append("Manifiesto bajo la gravedad de juramento que la información aquí consignada es veraz y corresponde fielmente a los hechos acontecidos.")
        relatoGenerado = sb.toString()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "✍️ Asistente de Estructuración Jurídica",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Diligencia estos campos y la aplicación generará un relato formal listo para copiar y pegar en el sistema '¡A Denunciar!' de la Policía o Fiscalía.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        item {
            Text("1. Tipo de Delito o Incidente:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            val tipos = listOf(
                "Hurto a personas (Celular / Pertenencias)",
                "Hurto a residencia / comercio",
                "Extorsión / Amenaza",
                "Estafa / Fraude virtual",
                "Ciberdelito / Suplantación",
                "Violencia física o verbal",
                "Otro delito"
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                items(tipos) { t ->
                    val sel = tipoDelito == t
                    FilterChip(
                        selected = sel,
                        onClick = { tipoDelito = t },
                        label = { Text(t, fontSize = 12.sp) }
                    )
                }
            }
        }

        item {
            Text("2. Fecha y Hora exacta:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            OutlinedTextField(
                value = fechaHora,
                onValueChange = { fechaHora = it },
                label = { Text("Fecha y Hora") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("3. Lugar / Dirección del hecho:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
                TextButton(onClick = { obtenerUbicacionGPS() }) {
                    Text("📍 Usar mi GPS")
                }
            }
            OutlinedTextField(
                value = direccionLugar,
                onValueChange = { direccionLugar = it },
                placeholder = { Text("Ej: Cra 7 con Calle 60, Barrio Chapinero Central, Bogotá") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text("4. ¿Cómo ocurrieron los hechos? (Relato claro):", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            OutlinedTextField(
                value = relatoHechos,
                onValueChange = { relatoHechos = it },
                placeholder = { Text("Describe en orden cronológico lo que pasó: 'Iba caminando cuando fui abordado por dos sujetos armados que me intimidaron...'") },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text("5. Pertenencias o Bienes afectados (Marca, Serial, IMEI, Valor aprox):", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            OutlinedTextField(
                value = bienesAfectados,
                onValueChange = { bienesAfectados = it },
                placeholder = { Text("Ej: Celular Samsung S21 color negro IMEI 354678..., Billetera con cédula y \$150.000 COP") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Text("6. Características de los agresores / Vehículos:", style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
            OutlinedTextField(
                value = descripcionSospechosos,
                onValueChange = { descripcionSospechosos = it },
                placeholder = { Text("Ej: 2 hombres jóvenes, uno con chaqueta roja y gorra negra, escaparon en motocicleta negra placa XYZ-123") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Button(
                onClick = { compilarRelatoFormal() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("📋 Generar Texto Jurídico Formal", fontWeight = FontWeight.Bold)
            }
        }

        if (relatoGenerado.isNotBlank()) {
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("📄 Texto Formal Generado", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                            Button(
                                onClick = {
                                    copiarAlPortapapeles(context, "Denuncia Formal", relatoGenerado)
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("📋 Copiar")
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Text(
                            text = relatoGenerado,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    copiarAlPortapapeles(context, "Denuncia Formal", relatoGenerado)
                                    onAbrirEnApp("Sistema ¡A Denunciar!", "https://adenunciar.policia.gov.co/adenunciar/")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f).height(48.dp)
                            ) {
                                Text("📱 Abrir en la App", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }

                            Button(
                                onClick = {
                                    copiarAlPortapapeles(context, "Denuncia Formal", relatoGenerado)
                                    abrirEnlaceSeguro(context, "https://adenunciar.policia.gov.co/adenunciar/")
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E88E5)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.3f).height(48.dp)
                            ) {
                                Text("🌐 Copiar e ir a Web", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * PESTAÑA 4: Líneas de Atención Telefónica Inmediata
 */
@Composable
fun SeccionLineasAtencion() {
    val context = LocalContext.current
    val lineas = listOf(
        LineaEmergencia("123", "Línea 123 Bogotá", "Policía, Bomberos y Ambulancias en el Distrito Capital.", Color(0xFFD32F2F)),
        LineaEmergencia("122", "Fiscalía General de la Nación", "Denuncia de delitos penales gratis desde celulares Tigo, Claro, Movistar.", Color(0xFF00897B)),
        LineaEmergencia("155", "Línea Púrpura Mujeres", "Orientación y atención a mujeres víctimas de violencia y acoso 24/7.", Color(0xFF7B1FA2)),
        LineaEmergencia("165", "Gaula Policía Nacional", "Atención inmediata contra Secuestro y Extorsión 'Yo no pago, yo denuncio'.", Color(0xFF1565C0)),
        LineaEmergencia("141", "ICBF Protección Infantil", "Protección inmediata de niños, niñas y adolescentes.", Color(0xFFE65100)),
        LineaEmergencia("195", "Línea 195 Alcaldía de Bogotá", "Información distrital, quejas, convivencia y servicios de la ciudad.", Color(0xFF388E3C)),
        LineaEmergencia("143", "Personería de Bogotá", "Atención de derechos humanos y veeduría ciudadana.", Color(0xFFE53935))
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(lineas) { lin ->
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = lin.color.copy(alpha = 0.15f)
                            ) {
                                Text(
                                    text = lin.numero,
                                    color = lin.color,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                            Text(lin.nombre, style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
                        }
                        Text(
                            text = lin.descripcion,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = { copiarAlPortapapeles(context, lin.nombre, lin.numero) },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Text("📋", fontSize = 16.sp)
                        }

                        Button(
                            onClick = { marcarTelefonoSeguro(context, lin.numero) },
                            colors = ButtonDefaults.buttonColors(containerColor = lin.color),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("📞 Llamar", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

data class LineaEmergencia(
    val numero: String,
    val nombre: String,
    val descripcion: String,
    val color: Color
)

/**
 * Diálogo modal con WebView integrado para visualizar y radicar denuncias dentro de la aplicación
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun DialogoVisorWeb(
    titulo: String,
    url: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var progresoCarga by remember { mutableIntStateOf(0) }
    var cargando by remember { mutableStateOf(true) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Barra superior de navegación del Visor Web
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            IconButton(onClick = onDismiss) {
                                Text("✕", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = titulo,
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { webViewRef?.reload() }) {
                                Text("🔄", fontSize = 16.sp)
                            }
                            IconButton(onClick = { copiarAlPortapapeles(context, titulo, url) }) {
                                Text("📋", fontSize = 16.sp)
                            }
                            IconButton(onClick = { abrirEnlaceSeguro(context, url) }) {
                                Text("🌐", fontSize = 16.sp)
                            }
                        }
                    }
                }

                // Barra de progreso de carga
                if (cargando) {
                    LinearProgressIndicator(
                        progress = { progresoCarga / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                // Vista Web
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                loadWithOverviewMode = true
                                useWideViewPort = true
                                builtInZoomControls = true
                                displayZoomControls = false
                                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            }
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progresoCarga = newProgress
                                    cargando = newProgress < 100
                                }
                            }
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                                    if (url != null) {
                                        if (url.startsWith("http://") || url.startsWith("https://")) {
                                            view?.loadUrl(url)
                                            return true
                                        } else if (url.startsWith("tel:")) {
                                            marcarTelefonoSeguro(ctx, url.removePrefix("tel:"))
                                            return true
                                        }
                                    }
                                    return false
                                }
                            }
                            loadUrl(url)
                            webViewRef = this
                        }
                    },
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }
    }
}

/**
 * Función robusta para abrir enlaces web externos de manera segura.
 * Añade flags necesarios, maneja esquemas y provee fallback al portapapeles.
 */
fun abrirEnlaceSeguro(context: Context, url: String) {
    try {
        var urlFinal = url.trim()
        if (!urlFinal.startsWith("http://") && !urlFinal.startsWith("https://")) {
            urlFinal = "https://$urlFinal"
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(urlFinal)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        copiarAlPortapapeles(context, "Enlace de Denuncia", url)
        Toast.makeText(context, "No se pudo abrir el navegador. Enlace copiado al portapapeles.", Toast.LENGTH_LONG).show()
    }
}

/**
 * Función robusta para marcar números de emergencia o denuncias.
 */
fun marcarTelefonoSeguro(context: Context, numero: String) {
    try {
        val numeroLimpio = numero.filter { it.isDigit() || it == '+' }
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$numeroLimpio")).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        copiarAlPortapapeles(context, "Línea de Atención", numero)
        Toast.makeText(context, "Número $numero copiado al portapapeles", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Copia un texto al portapapeles del dispositivo con confirmación visual.
 */
fun copiarAlPortapapeles(context: Context, etiqueta: String, texto: String) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        val clip = ClipData.newPlainText(etiqueta, texto)
        clipboard?.setPrimaryClip(clip)
        Toast.makeText(context, "¡Enlace copiado al portapapeles!", Toast.LENGTH_SHORT).show()
    } catch (_: Exception) {}
}
