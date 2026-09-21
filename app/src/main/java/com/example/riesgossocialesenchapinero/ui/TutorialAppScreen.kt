package com.example.riesgossocialesenchapinero.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

data class PasoTutorial(
    val id: Int,
    val seccionApp: String,
    val titulo: String,
    val subtitulo: String,
    val icono: String,
    val colorAcento: Color,
    val caracteristicas: List<CaracteristicaPaso>,
    val consejoTip: String
)

data class CaracteristicaPaso(
    val icono: String,
    val titulo: String,
    val descripcion: String
)

/**
 * Modal interactivo a pantalla completa con el Tour y Tutorial guiado de la aplicación.
 * Se muestra la primera vez que el usuario abre la app y puede revisitarse desde Ajustes.
 */
@Composable
fun ModalTutorialBienvenida(
    onFinalizarTutorial: () -> Unit
) {
    val pasos = remember {
        listOf(
            PasoTutorial(
                id = 0,
                seccionApp = "Bienvenido a Barrio Seguro",
                titulo = "🛡️ Tu Seguridad y la de tu Comunidad",
                subtitulo = "Una plataforma integral diseñada para proteger a los ciudadanos en Bogotá mediante datos oficiales, prevención en tiempo real e inteligencia artificial.",
                icono = "🛡️",
                colorAcento = Color(0xFF6750A4),
                caracteristicas = listOf(
                    CaracteristicaPaso("🗺️", "Mapas de Calor en Vivo", "Identifica las zonas seguras y los sectores críticos por localidad y por cuadra."),
                    CaracteristicaPaso("⚖️", "Denuncias Oficiales Virtuales", "Radica denuncias ante la Policía y Fiscalía con validez judicial desde tu celular."),
                    CaracteristicaPaso("🚨", "Monitoreo GPS Proactivo", "Recibe alertas preventivas si caminas hacia una zona con alta tasa de delitos."),
                    CaracteristicaPaso("💬", "Agente IA y Chat Vecinal", "Consulta rutas seguras 24/7 y mantente informado con tus vecinos.")
                ),
                consejoTip = "💡 Desliza o pulsa 'Siguiente' para conocer cada una de las 6 secciones de la app."
            ),
            PasoTutorial(
                id = 1,
                seccionApp = "Pestaña 1: 🗺️ Mapa de Riesgo",
                titulo = "🗺️ Mapa de Calor y Calificación de Cuadras",
                subtitulo = "Visualiza el nivel de riesgo delictivo en Bogotá y colabora advirtiendo sobre peligros específicos.",
                icono = "📍",
                colorAcento = Color(0xFF1E88E5),
                caracteristicas = listOf(
                    CaracteristicaPaso("🔴", "Convención de Colores", "Rojo (Crítico), Naranja (Alto), Amarillo (Precaución) y Verde (Seguro)."),
                    CaracteristicaPaso("➕", "Calificar una Cuadra", "Toca cualquier punto en el mapa o usa '➕ Calificar Cuadra' para detallar poca iluminación, hurtos frecuentes o calles solitarias."),
                    CaracteristicaPaso("🔍", "Buscador de Barrios", "Busca cualquier barrio o zona de Bogotá (ej: Chapinero, Cedritos, Suba) para ver su estado actual."),
                    CaracteristicaPaso("👍", "Validación Comunitaria", "Apoya los reportes de otros vecinos para confirmar la veracidad del peligro.")
                ),
                consejoTip = "💡 Al tocar una cuadra para calificarla, la app calcula automáticamente la dirección y el barrio aproximado."
            ),
            PasoTutorial(
                id = 2,
                seccionApp = "Pestaña 2: ⚖️ Denuncias",
                titulo = "⚖️ Denuncias Oficiales y Asistente Jurídico",
                subtitulo = "Accede a los canales directos del Estado colombiano y estructura tus denuncias con validez legal.",
                icono = "⚖️",
                colorAcento = Color(0xFF00897B),
                caracteristicas = listOf(
                    CaracteristicaPaso("🏛️", "Portales del Estado", "Acceso a '¡A Denunciar!' (Policía/Fiscalía), Ventanilla Única, Bogotá Te Escucha (SDQS) y CAI Virtual."),
                    CaracteristicaPaso("✍️", "Asistente de Redacción", "Diligencia datos básicos y la app generará un relato formal, cronológico y objetivo listo para radicar."),
                    CaracteristicaPaso("📱", "Visor Web en la App", "Abre los formularios de denuncia directamente dentro de Barrio Seguro sin necesidad de salir al navegador."),
                    CaracteristicaPaso("🔢", "Radicado SPOA", "Guía para consultar el avance de tu Noticia Criminal en fiscalia.gov.co.")
                ),
                consejoTip = "💡 Redactar los hechos en orden de tiempo y con avalúo estimado acelera el trámite judicial de tu caso."
            ),
            PasoTutorial(
                id = 3,
                seccionApp = "Pestaña 3: 📞 Emergencias",
                titulo = "📞 Botón 123 y Prevención de Desastres",
                subtitulo = "Atención inmediata en situaciones críticas y protocolos ante emergencias ambientales en Bogotá.",
                icono = "🚨",
                colorAcento = Color(0xFFD32F2F),
                caracteristicas = listOf(
                    CaracteristicaPaso("🆘", "Línea 123 de un Toque", "Marcación inmediata a Policía Nacional, Cuerpo de Bomberos y Ambulancias en Bogotá."),
                    CaracteristicaPaso("📞", "Líneas Especializadas", "Fiscalía (122), Línea Púrpura Mujeres (155), Gaula Anti-extorsión (165) e ICBF (141)."),
                    CaracteristicaPaso("🌋", "Guías ante Desastres", "Protocolos de actuación ante sismos, inundaciones, deslizamientos e incendios forestales."),
                    CaracteristicaPaso("🎒", "Mochila de Emergencia 72h", "Lista interactiva de elementos indispensables para ti y tu familia.")
                ),
                consejoTip = "💡 La llamada a la Línea 123 y a la Línea 122 es 100% gratuita desde cualquier operador móvil."
            ),
            PasoTutorial(
                id = 4,
                seccionApp = "Pestañas 4 y 5: 💬 Agente & 🌐 Comunidad",
                titulo = "💬 Agente Inteligente y Chat Vecinal en Vivo",
                subtitulo = "Resuelve dudas de seguridad al instante e interactúa con los vecinos de tu localidad.",
                icono = "🌐",
                colorAcento = Color(0xFF8E24AA),
                caracteristicas = listOf(
                    CaracteristicaPaso("🤖", "Agente IA 24/7", "Pregúntale al asistente inteligente sobre rutas seguras, recomendaciones de viaje y análisis de delitos."),
                    CaracteristicaPaso("📢", "Alertas Vecinales en Tiempo Real", "Envía avisos inmediatos con fotos a la comunidad si observas situaciones sospechosas."),
                    CaracteristicaPaso("🏙️", "Filtro por Localidad", "Sigue las conversaciones y avisos específicos de Chapinero, Usaquén, Suba o cualquier zona."),
                    CaracteristicaPaso("🔒", "Privacidad Protegida", "Tus mensajes usan alias anónimos para proteger tu identidad y número celular.")
                ),
                consejoTip = "💡 El chat comunitario funciona en tiempo real sin recargar la pantalla gracias a Firebase."
            ),
            PasoTutorial(
                id = 5,
                seccionApp = "Pestaña 6: ⚙️ Ajustes",
                titulo = "⚙️ Monitoreo GPS y Personalización",
                subtitulo = "Configura cómo te protege la aplicación y personaliza tu experiencia visual.",
                icono = "🛡️",
                colorAcento = Color(0xFFFB8C00),
                caracteristicas = listOf(
                    CaracteristicaPaso("🔔", "Monitoreo en Segundo Plano", "La app verifica periódicamente tu ubicación y te envía una alerta si ingresas a un sector crítico."),
                    CaracteristicaPaso("⏱️", "Frecuencia de GPS", "Elige el intervalo de chequeo (1 min, 2 min o 5 min) según tu nivel de desplazamiento y ahorro de batería."),
                    CaracteristicaPaso("🌓", "Temas y Accesibilidad", "Cambia entre Tema Claro, Oscuro o Automático según el sistema."),
                    CaracteristicaPaso("🌍", "Idiomas", "Compatible con Español, Inglés, Portugués, Francés, Alemán e Italiano.")
                ),
                consejoTip = "💡 ¡Listo! Ya conoces todas las herramientas de Barrio Seguro para proteger tu entorno."
            )
        )
    }

    var pasoActual by remember { mutableIntStateOf(0) }
    val totalPasos = pasos.size
    val paso = pasos[pasoActual]

    Dialog(
        onDismissRequest = { /* Bloqueante */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Barra superior: Sección, Contador y Botón Omitir
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = paso.colorAcento.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = paso.seccionApp,
                            color = paso.colorAcento,
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }

                    if (pasoActual < totalPasos - 1) {
                        TextButton(onClick = onFinalizarTutorial) {
                            Text("Omitir", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "¡Paso Final!",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Contenido dinámico con animación horizontal de cambio de paso
                AnimatedContent(
                    targetState = paso,
                    transitionSpec = {
                        if (targetState.id > initialState.id) {
                            (slideInHorizontally(animationSpec = tween(300)) { it } + fadeIn(animationSpec = tween(300)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { -it } + fadeOut(animationSpec = tween(200)))
                        } else {
                            (slideInHorizontally(animationSpec = tween(300)) { -it } + fadeIn(animationSpec = tween(300)))
                                .togetherWith(slideOutHorizontally(animationSpec = tween(300)) { it } + fadeOut(animationSpec = tween(200)))
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    label = "animacion_paso_tutorial"
                ) { pasoItem ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Título y Subtítulo
                        Text(
                            text = pasoItem.titulo,
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = pasoItem.subtitulo,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        // Lista de Características del apartado
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            pasoItem.caracteristicas.forEach { c ->
                                Card(
                                    shape = RoundedCornerShape(14.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.Top,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        Text(c.icono, fontSize = 20.sp, modifier = Modifier.padding(top = 2.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = c.titulo,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = c.descripcion,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Tarjeta de Consejo / Tip
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = pasoItem.colorAcento.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = pasoItem.consejoTip,
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = pasoItem.colorAcento,
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Indicador de Puntos (Dots de progreso)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(totalPasos) { index ->
                        val activo = index == pasoActual
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (activo) 22.dp else 8.dp, 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (activo) paso.colorAcento else MaterialTheme.colorScheme.outlineVariant)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Botones de Navegación: Anterior / Siguiente / Comenzar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (pasoActual > 0) {
                        OutlinedButton(
                            onClick = { pasoActual -= 1 },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1f).height(48.dp)
                        ) {
                            Text("⬅ Anterior", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    if (pasoActual < totalPasos - 1) {
                        Button(
                            onClick = { pasoActual += 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = paso.colorAcento),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1.2f).height(48.dp)
                        ) {
                            Text("Siguiente ➔", fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = onFinalizarTutorial,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.weight(1.3f).height(48.dp)
                        ) {
                            Text("🚀 ¡Comenzar!", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }
}
