package com.example.riesgossocialesenchapinero.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.riesgossocialesenchapinero.data.ApiClient
import com.example.riesgossocialesenchapinero.util.FiltroGroserias
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.random.Random

private const val PREFS_NAME = "barrio_seguro_comunidad_prefs"
private const val KEY_ALIAS_ANONIMO = "key_alias_anonimo"

val LOCALIDADES_BOGOTA_OPCIONES = listOf(
    "Todas las zonas", "1. Usaquén", "2. Chapinero", "3. Santa Fe", "4. San Cristóbal",
    "5. Usme", "6. Tunjuelito", "7. Bosa", "8. Kennedy", "9. Fontibón", "10. Engativá",
    "11. Suba", "12. Barrios Unidos", "13. Teusaquillo", "14. Los Mártires", "15. Antonio Nariño",
    "16. Puente Aranda", "17. La Candelaria", "18. Rafael Uribe Uribe", "19. Ciudad Bolívar", "20. Sumapaz"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatGlobalScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Gestión del Alias Anónimo persistente
    val sharedPrefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    var aliasAnonimo by remember {
        val guardado = sharedPrefs.getString(KEY_ALIAS_ANONIMO, null)
        if (!guardado.isNullOrBlank()) {
            mutableStateOf(guardado)
        } else {
            val nuevo = "Vecino #${Random.nextInt(1000, 9999)}"
            sharedPrefs.edit().putString(KEY_ALIAS_ANONIMO, nuevo).apply()
            mutableStateOf(nuevo)
        }
    }

    var mensajes by remember { mutableStateOf<List<ApiClient.MensajeComunidad>>(emptyList()) }
    var cargando by remember { mutableStateOf(true) }
    var enviando by remember { mutableStateOf(false) }
    var textoMensaje by remember { mutableStateOf("") }
    var imagenUriSeleccionada by remember { mutableStateOf<Uri?>(null) }
    var imagenBase64Seleccionada by remember { mutableStateOf<String?>(null) }
    var esAlertaSeleccionada by remember { mutableStateOf(false) }
    var localidadSeleccionada by remember { mutableStateOf("2. Chapinero") }
    var filtroLocalidad by remember { mutableStateOf("Todas las zonas") }
    var imagenZoomBase64 by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // Selector de imagen de galería
    val launcherImagen = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            imagenUriSeleccionada = uri
            coroutineScope.launch(Dispatchers.IO) {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
                    val originalBitmap = BitmapFactory.decodeStream(inputStream)
                    inputStream?.close()

                    if (originalBitmap != null) {
                        // Redimensionar para optimizar envío
                        val maxDim = 800
                        val ratio = minOf(maxDim.toFloat() / originalBitmap.width, maxDim.toFloat() / originalBitmap.height, 1f)
                        val scaled = Bitmap.createScaledBitmap(
                            originalBitmap,
                            (originalBitmap.width * ratio).toInt(),
                            (originalBitmap.height * ratio).toInt(),
                            true
                        )
                        val out = ByteArrayOutputStream()
                        scaled.compress(Bitmap.CompressFormat.JPEG, 75, out)
                        val b64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
                        withContext(Dispatchers.Main) {
                            imagenBase64Seleccionada = b64
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ChatGlobal", "Error procesando imagen: ${e.message}")
                }
            }
        }
    }

    // Carga y sondeo periódico de mensajes
    suspend fun cargarMensajesSilencioso() {
        try {
            val lista = withContext(Dispatchers.IO) {
                ApiClient.obtenerMensajesComunidad()
            }
            mensajes = lista
        } catch (_: Exception) {}
    }

    LaunchedEffect(Unit) {
        cargando = true
        withContext(Dispatchers.IO) {
            try {
                mensajes = ApiClient.obtenerMensajesComunidad()
            } catch (_: Exception) {}
        }
        cargando = false

        // Sondeo cada 5 segundos
        while (true) {
            delay(5000)
            cargarMensajesSilencioso()
        }
    }

    // Auto-scroll al último mensaje cuando cambia la lista
    LaunchedEffect(mensajes.size) {
        if (mensajes.isNotEmpty()) {
            listState.animateScrollToItem(mensajes.size - 1)
        }
    }

    val mensajesFiltrados = remember(mensajes, filtroLocalidad) {
        if (filtroLocalidad == "Todas las zonas") {
            mensajes
        } else {
            mensajes.filter { it.localidad.contains(filtroLocalidad.replace(Regex("^[0-9]+\\.\\s*"), ""), ignoreCase = true) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Encabezado de Identidad Anónima y Moderación
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(colorDesdeHash(aliasAnonimo)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("👤", fontSize = 16.sp)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = aliasAnonimo,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "100% Anónimo",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                            Text(
                                text = "🛡️ Filtro de moderación activo • Sin registro",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Botón regenerar alias
                    IconButton(
                        onClick = {
                            val nuevo = "Vecino #${Random.nextInt(1000, 9999)}"
                            sharedPrefs.edit().putString(KEY_ALIAS_ANONIMO, nuevo).apply()
                            aliasAnonimo = nuevo
                        },
                        modifier = Modifier.bounceClick()
                    ) {
                        Text("🎲", fontSize = 20.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Selector horizontal de filtro por localidad
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(LOCALIDADES_BOGOTA_OPCIONES.take(8)) { loc ->
                        FilterChip(
                            selected = filtroLocalidad == loc,
                            onClick = { filtroLocalidad = loc },
                            label = { Text(loc, fontSize = 11.sp) }
                        )
                    }
                }
            }
        }

        // Feed de Mensajes
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (cargando && mensajes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Conectando con la red comunitaria...")
                    }
                }
            } else if (mensajesFiltrados.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("💬", fontSize = 42.sp)
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "No hay reportes en esta zona aún",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Sé el primero en informar o advertir a tus vecinos de forma anónima y segura.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(mensajesFiltrados, key = { it.id }) { msg ->
                        BurbujaMensajeComunidad(
                            mensaje = msg,
                            esMio = msg.esMio || msg.aliasAnonimo == aliasAnonimo,
                            onVerImagen = { b64 -> imagenZoomBase64 = b64 }
                        )
                    }
                }
            }
        }

        // Barra inferior de envío y composición
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Previsualización de imagen adjunta si hay
                if (imagenBase64Seleccionada != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(60.dp)
                        ) {
                            val bmp = remember(imagenBase64Seleccionada) {
                                try {
                                    val bytes = Base64.decode(imagenBase64Seleccionada, Base64.DEFAULT)
                                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                } catch (_: Exception) { null }
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = "Vista previa",
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Foto adjunta lista para enviar",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            imagenUriSeleccionada = null
                            imagenBase64Seleccionada = null
                        }) {
                            Text("✕", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Opciones de reporte: Zona y Switch de Alerta
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Selector compacto de localidad
                    var desplegarMenuLocalidad by remember { mutableStateOf(false) }
                    Box {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .clickable { desplegarMenuLocalidad = true }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("📍", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = localidadSeleccionada,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("▾", fontSize = 12.sp)
                            }
                        }

                        DropdownMenu(
                            expanded = desplegarMenuLocalidad,
                            onDismissRequest = { desplegarMenuLocalidad = false }
                        ) {
                            LOCALIDADES_BOGOTA_OPCIONES.filter { it != "Todas las zonas" }.forEach { loc ->
                                DropdownMenuItem(
                                    text = { Text(loc) },
                                    onClick = {
                                        localidadSeleccionada = loc
                                        desplegarMenuLocalidad = false
                                    }
                                )
                            }
                        }
                    }

                    // Toggle de Alerta
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (esAlertaSeleccionada) MaterialTheme.colorScheme.errorContainer
                                else Color.Transparent
                            )
                            .clickable { esAlertaSeleccionada = !esAlertaSeleccionada }
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (esAlertaSeleccionada) "🚨 Alerta Activada" else "🚨 Alerta",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (esAlertaSeleccionada) FontWeight.Bold else FontWeight.Normal,
                            color = if (esAlertaSeleccionada) MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Switch(
                            checked = esAlertaSeleccionada,
                            onCheckedChange = { esAlertaSeleccionada = it },
                            modifier = Modifier.height(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Campo de texto y botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón adjuntar imagen
                    IconButton(
                        onClick = { launcherImagen.launch("image/*") },
                        modifier = Modifier.bounceClick()
                    ) {
                        Text("📷", fontSize = 22.sp)
                    }

                    // Caja de texto con censura en tiempo real
                    OutlinedTextField(
                        value = textoMensaje,
                        onValueChange = { nuevoTexto ->
                            textoMensaje = FiltroGroserias.censurar(nuevoTexto)
                        },
                        placeholder = {
                            Text(
                                text = if (esAlertaSeleccionada) "Escribe el aviso de seguridad urgente..." else "Escribe a la comunidad (100% anónimo)...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4
                    )

                    // Botón Enviar
                    val puedeEnviar = (textoMensaje.isNotBlank() || imagenBase64Seleccionada != null) && !enviando
                    IconButton(
                        onClick = {
                            if (puedeEnviar) {
                                enviando = true
                                val textoAEnviar = FiltroGroserias.censurar(textoMensaje)
                                val img = imagenBase64Seleccionada
                                val loc = localidadSeleccionada
                                val alerta = esAlertaSeleccionada

                                // Limpiar campos de inmediato
                                textoMensaje = ""
                                imagenUriSeleccionada = null
                                imagenBase64Seleccionada = null
                                esAlertaSeleccionada = false

                                coroutineScope.launch {
                                    val enviado = withContext(Dispatchers.IO) {
                                        ApiClient.enviarMensajeComunidad(
                                            texto = textoAEnviar,
                                            imagenBase64 = img,
                                            aliasAnonimo = aliasAnonimo,
                                            localidad = loc,
                                            esAlerta = alerta
                                        )
                                    }
                                    mensajes = mensajes + enviado
                                    enviando = false
                                }
                            }
                        },
                        enabled = puedeEnviar,
                        modifier = Modifier.bounceClick()
                    ) {
                        if (enviando) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = if (puedeEnviar) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = "➤",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = if (puedeEnviar) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal de Zoom de Imagen
    if (imagenZoomBase64 != null) {
        Dialog(onDismissRequest = { imagenZoomBase64 = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val bmp = remember(imagenZoomBase64) {
                        try {
                            val bytes = Base64.decode(imagenZoomBase64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        } catch (_: Exception) { null }
                    }

                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Imagen ampliada",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 420.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { imagenZoomBase64 = null },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Cerrar")
                    }
                }
            }
        }
    }
}

@Composable
fun BurbujaMensajeComunidad(
    mensaje: ApiClient.MensajeComunidad,
    esMio: Boolean,
    onVerImagen: (String) -> Unit
) {
    val alineacion = if (esMio) Alignment.End else Alignment.Start
    val colorFondo = when {
        mensaje.esAlerta -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.85f)
        esMio -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val colorTexto = when {
        mensaje.esAlerta -> MaterialTheme.colorScheme.onErrorContainer
        esMio -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val fechaFormateada = remember(mensaje.timestamp) {
        val sdf = SimpleDateFormat("h:mm a", Locale.getDefault())
        sdf.format(Date(mensaje.timestamp))
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .staggeredEntrance(),
        horizontalAlignment = alineacion
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            if (!esMio) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(colorDesdeHash(mensaje.aliasAnonimo))
                )
                Spacer(modifier = Modifier.width(6.dp))
            }
            Text(
                text = if (esMio) "Tú (${mensaje.aliasAnonimo})" else mensaje.aliasAnonimo,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = mensaje.localidad,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
            if (mensaje.esAlerta) {
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "🚨 ALERTA",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onError,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (esMio) 16.dp else 2.dp,
                bottomEnd = if (esMio) 2.dp else 16.dp
            ),
            color = colorFondo,
            border = if (mensaje.esAlerta) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.error) else null,
            tonalElevation = 2.dp,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                // Renderizar imagen si existe
                if (!mensaje.imagenBase64.isNullOrBlank()) {
                    val bitmap = remember(mensaje.imagenBase64) {
                        try {
                            val decoded = Base64.decode(mensaje.imagenBase64, Base64.DEFAULT)
                            BitmapFactory.decodeByteArray(decoded, 0, decoded.size)
                        } catch (_: Exception) { null }
                    }

                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Foto reporte",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onVerImagen(mensaje.imagenBase64) },
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }

                if (mensaje.texto.isNotBlank()) {
                    Text(
                        text = mensaje.texto,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorTexto
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = fechaFormateada,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 10.sp,
                    color = colorTexto.copy(alpha = 0.65f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

private fun colorDesdeHash(str: String): Color {
    val h = kotlin.math.abs(str.hashCode())
    val r = (h and 0xFF0000) shr 16
    val g = (h and 0x00FF00) shr 8
    val b = h and 0x0000FF
    return Color(r, g, b, 255)
}
