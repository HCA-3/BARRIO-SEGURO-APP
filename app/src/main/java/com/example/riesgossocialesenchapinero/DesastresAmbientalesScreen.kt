package com.example.riesgossocialesenchapinero

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.riesgossocialesenchapinero.data.ApiClient
import com.example.riesgossocialesenchapinero.ui.EmergenciasViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SubPestanaDesastre {
    SISMOS, GUIAS, MOCHILA
}

@Composable
fun DesastresAmbientalesScreen(
    modifier: Modifier = Modifier,
    viewModel: EmergenciasViewModel = viewModel()
) {
    val estado by viewModel.estado.collectAsState()
    var subPestana by remember { mutableStateOf(SubPestanaDesastre.SISMOS) }

    Column(modifier = modifier.fillMaxSize()) {
        ScrollableTabRow(
            selectedTabIndex = subPestana.ordinal,
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = subPestana == SubPestanaDesastre.SISMOS,
                onClick = { subPestana = SubPestanaDesastre.SISMOS },
                text = { Text("🔴 " + stringResource(R.string.tab_sismos)) }
            )
            Tab(
                selected = subPestana == SubPestanaDesastre.GUIAS,
                onClick = { subPestana = SubPestanaDesastre.GUIAS },
                text = { Text("📘 " + stringResource(R.string.tab_guias)) }
            )
            Tab(
                selected = subPestana == SubPestanaDesastre.MOCHILA,
                onClick = { subPestana = SubPestanaDesastre.MOCHILA },
                text = { Text("🎒 " + stringResource(R.string.tab_mochila)) }
            )
        }

        when (subPestana) {
            SubPestanaDesastre.SISMOS -> VistaSismos(
                sismos = estado.sismos,
                cargando = estado.cargandoSismos,
                error = estado.errorSismos,
                onRefrescar = { viewModel.cargarSismos() }
            )
            SubPestanaDesastre.GUIAS -> VistaGuiasDesastres()
            SubPestanaDesastre.MOCHILA -> VistaMochila72h(
                itemsMarcados = estado.mochilaChecklist,
                onToggle = { viewModel.toggleItemMochila(it) }
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 1. VISTA SISMOS EN TIEMPO REAL
// -------------------------------------------------------------------------------------------------

@Composable
fun VistaSismos(
    sismos: List<ApiClient.Sismo>,
    cargando: Boolean,
    error: String?,
    onRefrescar: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.sismos_recientes_titulo),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Button(
                onClick = onRefrescar,
                enabled = !cargando,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(stringResource(R.string.sismos_actualizar))
            }
        }

        if (cargando) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(stringResource(R.string.sismos_cargando), style = MaterialTheme.typography.bodyMedium)
                }
            }
        } else if (error != null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(onClick = onRefrescar) {
                        Text(stringResource(R.string.btn_reintentar))
                    }
                }
            }
        } else if (sismos.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.sismos_sin_datos), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sismos, key = { it.id }) { sismo ->
                    TarjetaSismo(sismo)
                }
            }
        }
    }
}

@Composable
fun TarjetaSismo(sismo: ApiClient.Sismo) {
    val colorSeveridad = when {
        sismo.magnitud >= 6.0 -> Color(0xFFD32F2F)
        sismo.magnitud >= 5.0 -> Color(0xFFF57C00)
        sismo.magnitud >= 3.5 -> Color(0xFFFBC02D)
        else -> Color(0xFF388E3C)
    }

    val formatoHora = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val fechaStr = remember(sismo.tiempo) { formatoHora.format(Date(sismo.tiempo)) }
    val minutosAtras = remember(sismo.tiempo) {
        val dif = (System.currentTimeMillis() - sismo.tiempo) / (1000 * 60)
        dif.coerceAtLeast(0)
    }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = colorSeveridad,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "%.1f".format(sismo.magnitud),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sismo.lugar,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$fechaStr • " + if (minutosAtras < 60) {
                        stringResource(R.string.sismo_hace_minutos, minutosAtras)
                    } else {
                        stringResource(R.string.sismo_hace_horas, minutosAtras / 60)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.sismos_profundidad, sismo.profundidadKm),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = stringResource(R.string.sismos_distancia, sismo.distanciaBogotaKm),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 2. VISTA GUÍAS INTERACTIVAS DE DESASTRES
// -------------------------------------------------------------------------------------------------

data class GuiaDesastre(
    val id: String,
    val icono: String,
    val titulo: String,
    val descripcion: String,
    val antes: List<String>,
    val durante: List<String>,
    val despues: List<String>,
    val tipsBogota: String
)

@Composable
fun VistaGuiasDesastres() {
    val guias = remember {
        listOf(
            GuiaDesastre(
                id = "sismo",
                icono = "🏢",
                titulo = "Sismos y Terremotos",
                descripcion = "Bogotá se encuentra en una zona de amenaza sísmica intermedia con suelos blandos en la sabana.",
                antes = listOf(
                    "Identifica zonas seguras y puntos de encuentro en tu casa, trabajo o estudio.",
                    "Asegura muebles altos, cuadros y objetos pesados a las paredes.",
                    "Ten lista la mochila de emergencia con documentos y medicinas."
                ),
                durante = listOf(
                    "Agáchate, Cúbrete bajo una mesa resistente y Agárrate (Técnica D-C-A).",
                    "Aléjate de ventanas, vidrios, fachadas y objetos que puedan caer.",
                    "Si estás en un edificio alto en Bogotá, NO uses el ascensor ni bajes escaleras durante el sismo."
                ),
                despues = listOf(
                    "Cierra los pasos de gas, agua y baja los tacos de la luz.",
                    "Evacúa por las escaleras hacia el punto de encuentro establecido.",
                    "Verifica posibles daños estructurales antes de reingresar."
                ),
                tipsBogota = "En Bogotá, muchas estructuras de Chapinero y Teusaquillo son patrimonio o mampostería no reforzada: ten especial cuidado con cornisas y fachadas antiguas."
            ),
            GuiaDesastre(
                id = "inundacion",
                icono = "🌊",
                titulo = "Inundaciones y Encharcamientos",
                descripcion = "Común durante las temporadas de lluvias (ola invernal) por desbordamiento de canales y basuras.",
                antes = listOf(
                    "No arrojes basuras a las calles ni sumideros de alcantarillado.",
                    "Limpia canaletas, sifones y bajantes de tu vivienda periódicamente.",
                    "Mantén sacos de arena si vives cerca de rondas de río o quebradas."
                ),
                durante = listOf(
                    "Desconecta los electrodomésticos y corta el fluido eléctrico.",
                    "Sube objetos de valor y documentos a los pisos superiores.",
                    "Nunca intentes cruzar corrientes de agua a pie ni en vehículo."
                ),
                despues = listOf(
                    "No consumas agua de la llave hasta que el Acueducto certifique su potabilidad.",
                    "Desinfecta pisos y paredes con cloro antes de volver a habitar.",
                    "Reporta taponamientos de alcantarillado a la línea 116 de la EAAB."
                ),
                tipsBogota = "Zonas de alta vulnerabilidad: orillas del Río Bogotá, Quebrada La Vieja, Tunjuelo y pasos a desnivel deprimidos (ej. Autopista Norte con Calle 222)."
            ),
            GuiaDesastre(
                id = "deslizamiento",
                icono = "⛰️",
                titulo = "Deslizamientos y Remoción en Masa",
                descripcion = "Riesgo latente en los Cerros Orientales, laderas de Chapinero, Ciudad Bolívar, Usme y San Cristóbal.",
                antes = listOf(
                    "Observa si hay grietas en el terreno, muros agrietados o árboles inclinados.",
                    "No construyas en pendientes pronunciadas ni cortes taludes sin asesoría técnica.",
                    "Canaliza adecuadamente las aguas lluvias y servidas."
                ),
                durante = listOf(
                    "Si escuchas ruidos de desprendimiento o crujidos, evacúa de inmediato.",
                    "Aléjate de la trayectoria del derrumbe hacia zonas altas y laterales.",
                    "Ayuda a evacuar a niños, adultos mayores y mascotas."
                ),
                despues = listOf(
                    "No regreses a la vivienda hasta que IDIGER o Bomberos evalúen el terreno.",
                    "Permanece alerta a réplicas o nuevos desprendimientos por saturación de agua."
                ),
                tipsBogota = "IDIGER Bogotá realiza monitoreo constante de taludes. Ante cualquier grieta llama a la Línea 123 para visita técnica de evaluación de riesgo."
            ),
            GuiaDesastre(
                id = "incendio",
                icono = "🔥",
                titulo = "Incendios Forestales y Urbanos",
                descripcion = "Frecuentes en temporadas secas (Fenómeno del Niño) en los Cerros Orientales y áreas residenciales.",
                antes = listOf(
                    "No arrojes colillas de cigarrillo, fósforos ni botellas de vidrio en zonas verdes.",
                    "Revisa instalaciones eléctricas y no sobrecargues tomacorrientes.",
                    "Ten un extintor multipropósito (tipo ABC) cargado y vigente en casa."
                ),
                durante = listOf(
                    "Llama inmediatamente a la línea 119 de Bomberos de Bogotá o al 123.",
                    "Si hay humo espeso, desplázate a gatas cubriendo nariz y boca con un trapo húmedo.",
                    "Toca las puertas con el dorso de la mano antes de abrirlas; si están calientes, no abras."
                ),
                despues = listOf(
                    "No ingreses al inmueble hasta que los bomberos declaren el área segura.",
                    "Desecha alimentos y medicinas que hayan estado expuestos al calor o humo."
                ),
                tipsBogota = "En caso de humo denso por incendios en los Cerros de Bogotá, usa tapabocas N95 y mantén cerradas ventanas de colegios y hogares."
            ),
            GuiaDesastre(
                id = "vendaval",
                icono = "🌪️",
                titulo = "Vendavales y Granizadas",
                descripcion = "Ráfagas de viento súbitas y granizadas densas que provocan colapso de techos y tejas.",
                antes = listOf(
                    "Asegura tejas de zinc, láminas, tanques de agua y avisos en cubiertas.",
                    "Poda ramas de árboles secos que puedan caer sobre cables o techos."
                ),
                durante = listOf(
                    "Aléjate de ventanales, muros perimetrales y árboles frondosos.",
                    "Si estás conduciendo, reduce la velocidad y no te detengas debajo de árboles o postes."
                ),
                despues = listOf(
                    "Retira el exceso de granizo acumulado en tejados y sifones para evitar colapsos.",
                    "Ten cuidado con cables caídos en la vía pública (llama a Enel al 115)."
                ),
                tipsBogota = "El granizo suele taponar bajantes y colapsar cubiertas livianas en zonas comerciales y deprimidos viales de la ciudad."
            ),
            GuiaDesastre(
                id = "gas",
                icono = "💨",
                titulo = "Fugas de Gas y Químicos",
                descripcion = "Emergencias domésticas o industriales con riesgo de explosión e intoxicación.",
                antes = listOf(
                    "Revisa periódicamente mangueras y válvulas de la estufa y calentador.",
                    "Garantiza ventilación adecuada en áreas donde operen calentadores de gas."
                ),
                durante = listOf(
                    "Si huele a gas (olor a huevo podrido), NO enciendas luces, fósforos ni timbres.",
                    "Abre ventanas y puertas de par en par para ventilar.",
                    "Cierra la llave de paso general del gas y sal de la vivienda."
                ),
                despues = listOf(
                    "Comunícate desde el exterior con Vanti (Línea 164) o al 123.",
                    "No enciendas aparatos eléctricos hasta que el técnico certifique que la fuga fue reparada."
                ),
                tipsBogota = "Vanti atiende emergencias de gas natural 24/7 en Bogotá a través de la línea gratuita 164."
            )
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(guias, key = { it.id }) { guia ->
            TarjetaGuiaInteractiva(guia)
        }
    }
}

@Composable
fun TarjetaGuiaInteractiva(guia: GuiaDesastre) {
    var expandida by remember { mutableStateOf(false) }
    var faseSeleccionada by remember { mutableIntStateOf(0) }

    Card(
        modifier = Modifier.fillMaxWidth().animateContentSize(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expandida = !expandida },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(guia.icono, style = MaterialTheme.typography.headlineMedium)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(guia.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(guia.descripcion, style = MaterialTheme.typography.bodySmall, maxLines = if (expandida) 4 else 2)
                    }
                }
                Text(if (expandida) "▲" else "▼", style = MaterialTheme.typography.bodyLarge)
            }

            AnimatedVisibility(
                visible = expandida,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = faseSeleccionada == 0,
                            onClick = { faseSeleccionada = 0 },
                            label = { Text(stringResource(R.string.guia_fase_antes)) }
                        )
                        FilterChip(
                            selected = faseSeleccionada == 1,
                            onClick = { faseSeleccionada = 1 },
                            label = { Text(stringResource(R.string.guia_fase_durante)) }
                        )
                        FilterChip(
                            selected = faseSeleccionada == 2,
                            onClick = { faseSeleccionada = 2 },
                            label = { Text(stringResource(R.string.guia_fase_despues)) }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    val pasos = when (faseSeleccionada) {
                        0 -> guia.antes
                        1 -> guia.durante
                        else -> guia.despues
                    }

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            pasos.forEachIndexed { index, paso ->
                                Row(modifier = Modifier.padding(vertical = 4.dp)) {
                                    Text(
                                        text = "${index + 1}. ",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        text = paso,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("📍 ", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = guia.tipsBogota,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// 3. VISTA MOCHILA DE EMERGENCIA 72 HORAS
// -------------------------------------------------------------------------------------------------

data class ItemMochila(val id: String, val icono: String, val nombre: String, val descripcion: String)

@Composable
fun VistaMochila72h(
    itemsMarcados: Set<String>,
    onToggle: (String) -> Unit
) {
    val items = remember {
        listOf(
            ItemMochila("agua", "💧", "Agua embotellada", "Al menos 2 litros por persona al día (para 72 horas)."),
            ItemMochila("comida", "🥫", "Alimentos no perecederos", "Enlatados con abrelatas manual, barras energéticas y frutos secos."),
            ItemMochila("linterna", "🔦", "Linterna y pilas de repuesto", "Preferiblemente linterna LED o de dinamo."),
            ItemMochila("radio", "📻", "Radio portátil AM/FM", "Para sintonizar boletines oficiales de emergencia."),
            ItemMochila("botiquin", "🩹", "Botiquín de primeros auxilios", "Gasas, alcohol, vendas, analgésicos y medicamentos personales esenciales."),
            ItemMochila("documentos", "📄", "Copia de documentos clave", "Cédulas, escrituras, pólizas y carné de salud en bolsa impermeable."),
            ItemMochila("silbato", "📢", "Silbato de emergencia", "Para pedir auxilio acústico en caso de quedar atrapado."),
            ItemMochila("manta", "🧥", "Manta térmica o abrigo", "Para protegerse del frío nocturno de la sabana de Bogotá."),
            ItemMochila("powerbank", "🔋", "Batería portátil (Power bank)", "Cargada al 100% con cables para el teléfono celular."),
            ItemMochila("llaves", "🔑", "Copia de llaves", "Juego duplicado de llaves de la vivienda y vehículo."),
            ItemMochila("higiene", "🧼", "Artículos de higiene", "Papel higiénico, jabón, toallas húmedas y bolsas plásticas resistentes.")
        )
    }

    val listos = itemsMarcados.size
    val total = items.size
    val porcentaje = if (total > 0) (listos.toFloat() / total * 100).toInt() else 0

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "🎒 Kit de Supervivencia 72h",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.mochila_progreso, listos, total, porcentaje),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { listos.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(items, key = { it.id }) { item ->
                val marcado = itemsMarcados.contains(item.id)
                Card(
                    modifier = Modifier.fillMaxWidth().clickable { onToggle(item.id) },
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = marcado,
                            onCheckedChange = { onToggle(item.id) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(item.icono, style = MaterialTheme.typography.headlineSmall)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.nombre,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = if (marcado) FontWeight.Normal else FontWeight.SemiBold
                            )
                            Text(
                                text = item.descripcion,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
