package com.example.riesgossocialesenchapinero

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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

enum class FiltroSismo {
    TODOS, MAG_4, CERCANOS
}

@Composable
fun VistaSismos(
    sismos: List<ApiClient.Sismo>,
    cargando: Boolean,
    error: String?,
    onRefrescar: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var filtro by remember { mutableStateOf(FiltroSismo.TODOS) }

    val sismosFiltrados = remember(sismos, filtro) {
        when (filtro) {
            FiltroSismo.TODOS -> sismos
            FiltroSismo.MAG_4 -> sismos.filter { it.magnitud >= 4.0 }
            FiltroSismo.CERCANOS -> sismos.filter { it.distanciaBogotaKm <= 350.0 }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        // BANNER INFORMATIVO
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📡 " + stringResource(R.string.sismos_recientes_titulo),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Monitoreo en vivo de eventos telúricos en Colombia y la región",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                }
                Button(
                    onClick = onRefrescar,
                    enabled = !cargando,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(stringResource(R.string.sismos_actualizar))
                }
            }
        }

        // CHIPS DE FILTRO RÁPIDO
        if (sismos.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = filtro == FiltroSismo.TODOS,
                    onClick = { filtro = FiltroSismo.TODOS },
                    label = { Text("Todos (${sismos.size})", style = MaterialTheme.typography.labelSmall) }
                )
                val countMag4 = sismos.count { it.magnitud >= 4.0 }
                FilterChip(
                    selected = filtro == FiltroSismo.MAG_4,
                    onClick = { filtro = FiltroSismo.MAG_4 },
                    label = { Text("🔴 Mag ≥ 4.0 ($countMag4)", style = MaterialTheme.typography.labelSmall) }
                )
                val countCercanos = sismos.count { it.distanciaBogotaKm <= 350.0 }
                FilterChip(
                    selected = filtro == FiltroSismo.CERCANOS,
                    onClick = { filtro = FiltroSismo.CERCANOS },
                    label = { Text("📍 < 350 km ($countCercanos)", style = MaterialTheme.typography.labelSmall) }
                )
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
        } else if (sismosFiltrados.isEmpty()) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.sismos_sin_datos), style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(sismosFiltrados, key = { it.id }) { sismo ->
                    TarjetaSismo(
                        sismo = sismo,
                        onAbrirUrl = { url ->
                            if (url.isNotEmpty()) {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url)).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TarjetaSismo(
    sismo: ApiClient.Sismo,
    onAbrirUrl: (String) -> Unit = {}
) {
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

    val tipoProfundidad = when {
        sismo.profundidadKm < 30.0 -> "Superficial"
        sismo.profundidadKm <= 120.0 -> "Intermedia"
        else -> "Profunda"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(enabled = sismo.url.isNotEmpty()) {
                onAbrirUrl(sismo.url)
            },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = colorSeveridad,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(54.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "%.1f".format(sismo.magnitud),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "Mag",
                        color = Color.White.copy(alpha = 0.9f),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sismo.lugar,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Prof. ${sismo.profundidadKm} km ($tipoProfundidad)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = stringResource(R.string.sismos_distancia, sismo.distanciaBogotaKm),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (sismo.distanciaBogotaKm < 150) Color(0xFFD32F2F) else MaterialTheme.colorScheme.primary
                    )
                }
                if (sismo.sentido > 0) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "👥 Sentido por ${sismo.sentido} personas",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFE65100),
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
    val resIdImagen: Int,
    val lineaContacto: String = "123",
    val entidadContacto: String = "Línea 123",
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
                descripcion = "Protocolo de autoprotección ante movimientos telúricos en la sabana de Bogotá.",
                resIdImagen = R.drawable.img_guia_sismo,
                lineaContacto = "123",
                entidadContacto = "Emergencias 123",
                antes = listOf(
                    "Identifica zonas seguras (columnas, vigas y muebles resistentes) y rutas de evacuación en tu hogar o trabajo.",
                    "Fija a las paredes estanterías, televisores, calentadores y cuadros pesados.",
                    "Mantén lista la Mochila de Emergencia 72h con linterna, agua, alimentos y copias de documentos.",
                    "Realiza simulacros de evacuación familiares y define un punto de encuentro fuera de cables de alta tensión."
                ),
                durante = listOf(
                    "Aplica la técnica D-C-A: Agáchate, Cúbrete la cabeza bajo una mesa firme y Agárrate fuerte.",
                    "Aléjate de ventanas de vidrio, fachadas, balcones y objetos colgantes que puedan desprenderse.",
                    "Si estás en un edificio alto en Bogotá, NO uses el ascensor ni bajes corriendo por las escaleras durante el sismo.",
                    "Si estás en la calle, protégete la cabeza y aléjate de postes, transformadores y cornisas antiguas.",
                    "Si vas en TransMilenio o vehículo, detén la marcha lejos de puentes vehiculares y permanece en el interior."
                ),
                despues = listOf(
                    "Cierra inmediatamente las llaves de paso de gas natural, agua y baja los interruptores eléctricos.",
                    "Evacúa con calma por las escaleras hacia el punto de encuentro despejado asignado.",
                    "Usa mensajes de texto (SMS) o datos móviles para comunicarte; deja las líneas de voz para emergencias reales.",
                    "Inspecciona la estructura en busca de grietas en 'X' o inclinación de columnas antes de reingresar."
                ),
                tipsBogota = "En Bogotá, barrios antiguos como Chapinero, La Candelaria y Teusaquillo cuentan con mampostería histórica vulnerable. Prioriza alejarte de voladizos y marquesinas."
            ),
            GuiaDesastre(
                id = "inundacion",
                icono = "🌊",
                titulo = "Inundaciones y Desbordamientos",
                descripcion = "Manejo de emergencias por lluvias torrenciales, crecientes de ríos y encharcamientos.",
                resIdImagen = R.drawable.img_guia_inundacion,
                lineaContacto = "116",
                entidadContacto = "Acueducto 116",
                antes = listOf(
                    "No arrojes basuras, escombros ni grasas a los sumideros de alcantarillado ni quebradas.",
                    "Limpia techos, canaletas, bajantes y cajas de inspección de tu vivienda antes de la temporada invernal.",
                    "Si vives cerca a rondas de río (Bogotá, Tunjuelo, Fucha), ten barreras o sacos de arena listos.",
                    "Ubica los documentos y electrodomésticos valiosos en niveles o repisas elevadas."
                ),
                durante = listOf(
                    "Corta inmediatamente el suministro eléctrico y de gas para prevenir cortocircuitos e incendios.",
                    "Sube a los pisos superiores con tu mochila de emergencia y documentos sellados en bolsas plásticas.",
                    "NUNCA intentes cruzar a pie ni en carro calles inundadas o corrientes rápidas (15 cm de agua pueden tumbar a una persona).",
                    "Evita transitar por deprimidos viales inundados en avenidas principales."
                ),
                despues = listOf(
                    "No consumas agua de la llave hasta que la EAAB garantice su potabilidad; hierve el agua si es necesario.",
                    "No toques cables eléctricos caídos ni enchufes mojados hasta que un técnico verifique la instalación.",
                    "Lava y desinfecta con cloro todas las áreas afectadas para evitar proliferación de bacterias y hongos.",
                    "Reporta taponamientos masivos y tapas de alcantarillado faltantes a la línea 116 de la EAAB."
                ),
                tipsBogota = "Puntos críticos frecuentes en Bogotá: Ronda del Río Tunjuelo (Bosa/Kennedy), depresión Autopista Norte con Calle 222, y paso bajo puente de la NQS con Calle 6."
            ),
            GuiaDesastre(
                id = "incendio",
                icono = "🔥",
                titulo = "Incendios Forestales y Urbanos",
                descripcion = "Prevención y combate inicial de conflagraciones en Cerros Orientales y áreas urbanas.",
                resIdImagen = R.drawable.img_guia_incendio,
                lineaContacto = "119",
                entidadContacto = "Bomberos 119",
                antes = listOf(
                    "No enciendas fogatas ni arrojes colillas de cigarrillos, cerillos o vidrios en los Cerros Orientales.",
                    "No sobrecargues tomacorrientes ni uses cables pelados; revisa la red eléctrica con regularidad.",
                    "Ten en casa un extintor multipropósito Tipo ABC recargado y aprende a usarlo (técnica PASS).",
                    "Mantén despejadas las salidas de emergencia y pasillos comunes en edificios."
                ),
                durante = listOf(
                    "Llama de inmediato a la Línea 119 de Bomberos de Bogotá o al 123 indicando dirección exacta.",
                    "Si hay humo denso, desplázate gateando a ras de suelo y cubre tu boca y nariz con un paño húmedo.",
                    "Toca las puertas con el dorso de la mano antes de abrirlas; si la chapa o puerta está caliente, NO la abras.",
                    "Si tus prendas de vestir se prenden, detente, tírate al suelo y rueda sobre ti mismo cubriendo tu rostro.",
                    "NUNCA uses ascensores; utiliza exclusivamente las escaleras de emergencia."
                ),
                despues = listOf(
                    "No ingreses al inmueble afectado hasta que el Cuerpo Oficial de Bomberos confirme que es 100% seguro.",
                    "Desecha cualquier alimento, bebida o medicamento que haya estado expuesto al calor o humo tóxico.",
                    "Si sufriste quemaduras leves, aplica abundante agua fría limpia (nunca aceites, cremas ni pasta dental)."
                ),
                tipsBogota = "Durante temporadas secas (Fenómeno de El Niño), los Cerros Orientales son muy vulnerables. Si observas humo o fuego en la montaña, llama de inmediato al 119."
            ),
            GuiaDesastre(
                id = "deslizamiento",
                icono = "⛰️",
                titulo = "Deslizamientos y Remoción en Masa",
                descripcion = "Acción ante movimientos de tierra, caída de rocas y fallas de taludes en laderas.",
                resIdImagen = R.drawable.img_guia_deslizamiento,
                lineaContacto = "123",
                entidadContacto = "IDIGER / 123",
                antes = listOf(
                    "Observa si aparecen grietas en el terreno, pisos levantados, muros inclinados o puertas que se traban.",
                    "No realices excavaciones ni cortes en taludes sin asesoría de ingenieros geotécnicos.",
                    "Canaliza las aguas lluvias y evita arrojar aguas residuales directamente a la ladera.",
                    "Siembra vegetación nativa con raíces profundas en zonas de pendiente para afirmar el suelo."
                ),
                durante = listOf(
                    "Si escuchas ruidos sordos de desprendimiento, crujidos o caída de árboles, evacúa de inmediato.",
                    "Aléjate de la trayectoria del derrumbe hacia zonas laterales y elevadas seguras.",
                    "Ayuda a evacuar a niños, personas con discapacidad, adultos mayores y animales de compañía."
                ),
                despues = listOf(
                    "No regreses a la vivienda hasta que ingenieros de IDIGER y Bomberos emitan concepto técnico.",
                    "Monitorea el terreno por posibles deslizamientos secundarios por saturación de agua.",
                    "Sigue las alertas y recomendaciones oficiales emitidas por el Sistema Distrital de Gestión del Riesgo."
                ),
                tipsBogota = "Localidades con mayor riesgo de ladera en Bogotá: Ciudad Bolívar, Usme, San Cristóbal, Santa Fe y laderas altas de Chapinero y Usaquén."
            ),
            GuiaDesastre(
                id = "gas",
                icono = "💨",
                titulo = "Fugas de Gas y Monóxido de Carbono",
                descripcion = "Protocolo ante escape de gas natural, olor a mercaptano o fallas en gasodomésticos.",
                resIdImagen = R.drawable.img_guia_gas,
                lineaContacto = "164",
                entidadContacto = "Vanti 164",
                antes = listOf(
                    "Realiza la revisión técnica periódica obligatoria de instalaciones de gas cada 5 años con firmas certificadas.",
                    "Verifica que la llama de estufas y calentadores sea siempre de color azul uniforme (si es amarilla o naranja, hay mala combustión).",
                    "Garantiza ventilación permanente en recintos con calentadores de gas (rejillas de ventilación despejadas)."
                ),
                durante = listOf(
                    "Si percibes fuerte olor a gas (huevo podrido), NO enciendas luces, fósforos, timbres ni uses el celular dentro.",
                    "Abre ventanas y puertas de par en par inmediatamente para generar ventilación cruzada.",
                    "Cierra la válvula de corte general del centro de medición de gas de la vivienda.",
                    "Evacúa a todos los ocupantes hacia el exterior de la edificación sin accionar interruptores."
                ),
                despues = listOf(
                    "Comunícate desde el exterior a la Línea de Emergencias de Vanti (164) o al 123.",
                    "No reingreses ni enciendas equipos eléctricos hasta que técnicos calificados reparen y certifiquen la red."
                ),
                tipsBogota = "Vanti dispone de la línea gratuita 164 las 24 horas para atención de fugas en Bogotá y municipios de la sabana."
            ),
            GuiaDesastre(
                id = "vendaval",
                icono = "🌪️",
                titulo = "Vendavales y Granizadas Severas",
                descripcion = "Protección frente a ráfagas de viento huracanadas y acumulación de granizo en techos.",
                resIdImagen = R.drawable.img_guia_vendaval,
                lineaContacto = "115",
                entidadContacto = "Enel 115",
                antes = listOf(
                    "Fija firmemente cubiertas, tejas de zinc, tanques de agua, paneles solares y avisos publicitarios.",
                    "Solicita al Jardín Botánico o Enel la poda preventiva de ramas que amenacen redes eléctricas o techos.",
                    "Asegura ventanas y repara vidrios rotos o flojos."
                ),
                durante = listOf(
                    "Aléjate de ventanales grandes, muros provisionales, árboles frondosos y vallas publicitarias.",
                    "No te resguardes bajo árboles durante la tormenta por riesgo de caída de ramas y rayos.",
                    "Si conduces, disminuye la velocidad, enciende luces de emergencia y mantente lejos de postes."
                ),
                despues = listOf(
                    "Retira el exceso de granizo acumulado sobre techos livianos y bajantes para prevenir colapsos por sobrepeso.",
                    "Si encuentras cables de alta tensión caídos en el suelo, mantén distancia de al menos 10 metros y llama al 115 de Enel."
                ),
                tipsBogota = "Las granizadas en Bogotá pueden acumular hasta 30 cm de hielo en pocos minutos, colapsando cubiertas de bodegas, colegios y parqueaderos."
            ),
            GuiaDesastre(
                id = "rayos",
                icono = "⚡",
                titulo = "Tormentas Eléctricas y Rayos",
                descripcion = "Medidas de seguridad ante descargas eléctricas atmosféricas en la sabana.",
                resIdImagen = R.drawable.img_guia_rayos,
                lineaContacto = "123",
                entidadContacto = "Línea 123",
                antes = listOf(
                    "Instala sistemas de puesta a tierra y pararrayos en edificaciones residenciales e industriales.",
                    "Conoce los pronósticos meteorológicos de IDIGER e IDEAM antes de realizar actividades al aire libre."
                ),
                durante = listOf(
                    "Aplica la regla del 30-30: si el tiempo entre el relámpago y el trueno es menor a 30 segundos, busca refugio de inmediato.",
                    "Refúgiate dentro de un edificio cerrado con estructura sólida o dentro de un automóvil cerrado (Jaula de Faraday).",
                    "Aléjate de cuerpos de agua (lagos, piscinas, humedales), campos abiertos y estructuras metálicas.",
                    "Desconecta aparatos electrónicos de la red eléctrica para protegerlos de sobretensiones."
                ),
                despues = listOf(
                    "Espera al menos 30 minutos después del último trueno antes de reanudar actividades al aire libre.",
                    "Si una persona es alcanzada por un rayo, es seguro tocarla y brindarle primeros auxilios/RCP de inmediato."
                ),
                tipsBogota = "La sabana de Bogotá tiene alta densidad de descargas eléctricas en parques metropolitanos (Simón Bolívar, El Virrey) y canchas deportivas descubiertas."
            ),
            GuiaDesastre(
                id = "primeros_auxilios",
                icono = "🩹",
                titulo = "Primeros Auxilios y RCP Básica",
                descripcion = "Soporte vital inicial ante paros cardíacos, asfixia, quemaduras y hemorragias.",
                resIdImagen = R.drawable.img_guia_primeros_auxilios,
                lineaContacto = "132",
                entidadContacto = "Cruz Roja 132",
                antes = listOf(
                    "Capacítate en técnicas básicas de Reanimación Cardiopulmonar (RCP) y desobstrucción de vía aérea (Maniobra de Heimlich).",
                    "Mantén un botiquín de primeros auxilios completo con gasas estériles, vendas, tijeras, guantes de látex y solución salina."
                ),
                durante = listOf(
                    "Evalúa la escena antes de ingresar: verifica que sea segura para ti y el paciente.",
                    "Verifica si la persona responde y respira; si no respira, llama al 123 e inicia compresiones torácicas a ritmo de 100-120 por minuto en el centro del pecho.",
                    "Para hemorragias graves: presiona firmemente la herida con un paño limpio o gasa sin retirar los apósitos saturados.",
                    "Para quemaduras: enfría la zona con agua corriente limpia a temperatura ambiente durante al menos 15 minutos.",
                    "Para atragantamiento en adultos conscientes: realiza compresiones abdominales hacia arriba por encima del ombligo."
                ),
                despues = listOf(
                    "Coloca a la persona inconsciente que sí respira en Posición Lateral de Seguridad mientras arriba la ambulancia.",
                    "Informa con precisión al personal médico de la ambulancia sobre los tiempos y maniobras realizadas."
                ),
                tipsBogota = "El Centro Regulador de Urgencias y Emergencias (CRUE) de Bogotá despacha ambulancias a través de la Línea 123. Mantén la calma y proporciona la dirección exacta."
            ),
            GuiaDesastre(
                id = "quimicos",
                icono = "🧪",
                titulo = "Emergencias Químicas y Materiales Peligrosos",
                descripcion = "Procedimiento ante derrames de sustancias tóxicas, vapores químicos o explosivos.",
                resIdImagen = R.drawable.img_guia_quimicos,
                lineaContacto = "119",
                entidadContacto = "Bomberos HazMat",
                antes = listOf(
                    "Almacena productos químicos de limpieza en sus envases originales, rotulados y fuera del alcance de niños.",
                    "Nunca mezcles cloro con vinagre, alcohol o amoníaco (produce gas cloramina altamente tóxico y letal)."
                ),
                durante = listOf(
                    "Si hay una fuga o nube química exterior, ingresa a un recinto interior cerrado (refugio en el lugar).",
                    "Cierra puertas y ventanas; sella las rendijas con toallas húmedas o cinta adhesiva.",
                    "Apaga los sistemas de aire acondicionado, ventilación y extractores de aire.",
                    "Ubícate en la habitación más alta si el químico es más pesado que el aire, o en la más baja si es más ligero."
                ),
                despues = listOf(
                    "Espera las indicaciones de las autoridades antes de abrir ventanas para ventilar.",
                    "Si hubo contacto en piel u ojos, lava con abundante agua corriente durante 20 minutos y acude a urgencias."
                ),
                tipsBogota = "Localidades con corredores industriales químicos en Bogotá: Puente Aranda, Fontibón y zonas adyacentes a la Calle 13 y Autopista Sur."
            ),
            GuiaDesastre(
                id = "accidente_vial",
                icono = "🚗",
                titulo = "Accidentes de Tránsito y Choques Múltiples",
                descripcion = "Protocolo P.A.S. (Proteger, Avisar, Socorrer) en vías principales y autopistas.",
                resIdImagen = R.drawable.img_guia_accidente,
                lineaContacto = "123",
                entidadContacto = "Policía Tránsito 123",
                antes = listOf(
                    "Mantén al día el kit de carretera obligatorio (chaleco reflectivo, 2 conos/triángulos, extintor, botiquín, tacos y linterna).",
                    "Respeta los límites de velocidad de 50 km/h en vías urbanas de Bogotá y usa siempre el cinturón de seguridad."
                ),
                durante = listOf(
                    "PROTEGER: Enciende luces de parqueo, ponte el chaleco reflectivo y ubica los triángulos de señalización a 30 y 90 metros del vehículo.",
                    "AVISAR: Comunícate a la Línea 123 indicando kilómetro, sentido vial, número de vehículos y si hay heridos o personas atrapadas.",
                    "SOCORRER: Si hay heridos graves, NO muevas a la persona ni le retires el casco a motociclistas, salvo riesgo inminente de explosión o incendio."
                ),
                despues = listOf(
                    "Permanece en un lugar seguro fuera de la calzada mientras llegan las unidades de Policía de Tránsito y ambulancias.",
                    "Toma fotografías de las posiciones finales de los vehículos y la señalización para los reportes oficiales."
                ),
                tipsBogota = "En corredores de alta velocidad de Bogotá (Av. Boyacá, Av. Ciudad de Cali, Autopista Norte, Av. Las Américas) extrema las medidas de señalización para evitar atropellamientos secundarios."
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
                    // ILUSTRACIÓN VISUAL DEL PROTOCOLO DE EMERGENCIA
                    Image(
                        painter = painterResource(id = guia.resIdImagen),
                        contentDescription = guia.titulo,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clip(RoundedCornerShape(10.dp)),
                        contentScale = ContentScale.FillWidth
                    )

                    Spacer(modifier = Modifier.height(12.dp))
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

                    Spacer(modifier = Modifier.height(10.dp))

                    val context = androidx.compose.ui.platform.LocalContext.current
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Button(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${guia.lineaContacto}")).apply {
                                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text("📞 Llamar a ${guia.entidadContacto}")
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
