package com.example.riesgossocialesenchapinero.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Pantalla / Modal interactivo de Términos y Condiciones y Política de Privacidad.
 * Fundamentado en la Ley 1581 de 2012, Decreto 1074 de 2015 y arquitectura técnica de Barrio Seguro.
 */
@Composable
fun ModalBienvenidaTerminos(
    onAceptar: () -> Unit,
    onRechazar: () -> Unit
) {
    var checkAceptado by remember { mutableStateOf(false) }
    var tabSeleccionada by remember { mutableIntStateOf(0) } // 0: Términos, 1: Privacidad

    Dialog(
        onDismissRequest = { /* Bloqueante, no se cierra al tocar fuera */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Encabezado
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("⚖️", fontSize = 24.sp)
                        }
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Bienvenido a Barrio Seguro",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Seguridad Ciudadana e Inteligencia Urbana • Bogotá",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Selector de pestañas para lectura cómoda
                PrimaryTabRow(
                    selectedTabIndex = tabSeleccionada,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = tabSeleccionada == 0,
                        onClick = { tabSeleccionada = 0 },
                        text = { Text("📜 Términos", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = tabSeleccionada == 1,
                        onClick = { tabSeleccionada = 1 },
                        text = { Text("🔒 Privacidad", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = tabSeleccionada == 2,
                        onClick = { tabSeleccionada = 2 },
                        text = { Text("👨‍💻 Créditos", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Contenido desplazable con el texto legal oficial
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        when (tabSeleccionada) {
                            0 -> ContenidoTerminosCondiciones()
                            1 -> ContenidoPoliticaPrivacidad()
                            else -> ContenidoCreditosDesarrolladores()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Checkbox de consentimiento explícito
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { checkAceptado = !checkAceptado }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = checkAceptado,
                            onCheckedChange = { checkAceptado = it }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "He leído y acepto los Términos y Condiciones de Uso y la Política de Tratamiento de Datos Personales (Ley 1581 de 2012).",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Botones de acción
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onRechazar,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Rechazar y Salir")
                    }
                    Button(
                        onClick = onAceptar,
                        enabled = checkAceptado,
                        modifier = Modifier.weight(1.3f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("Aceptar y Continuar", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Diálogo modal para consultar los Términos y la Política desde Ajustes en cualquier momento.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogoVerTerminosAjustes(
    onCerrar: () -> Unit
) {
    var tabSeleccionada by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onCerrar,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Barra superior
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("⚖️", fontSize = 22.sp)
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Legal y Desarrolladores",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    IconButton(onClick = onCerrar) {
                        Text(
                            text = "✕",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                PrimaryTabRow(
                    selectedTabIndex = tabSeleccionada,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = tabSeleccionada == 0,
                        onClick = { tabSeleccionada = 0 },
                        text = { Text("📜 Términos", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = tabSeleccionada == 1,
                        onClick = { tabSeleccionada = 1 },
                        text = { Text("🔒 Privacidad", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                    )
                    Tab(
                        selected = tabSeleccionada == 2,
                        onClick = { tabSeleccionada = 2 },
                        text = { Text("👨‍💻 Desarrolladores", fontWeight = FontWeight.SemiBold, fontSize = 12.sp) }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest)
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                    ) {
                        when (tabSeleccionada) {
                            0 -> ContenidoTerminosCondiciones()
                            1 -> ContenidoPoliticaPrivacidad()
                            else -> ContenidoCreditosDesarrolladores()
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = onCerrar,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Cerrar")
                }
            }
        }
    }
}

// -------------------------------------------------------------------------------------------------
// SECCIONES CON EL TEXTO LEGAL OFICIAL COMPLETO
// -------------------------------------------------------------------------------------------------

@Composable
fun ContenidoTerminosCondiciones() {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(
            text = "TÉRMINOS Y CONDICIONES DE USO",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Última actualización: 2026",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "El presente documento establece las condiciones que rigen el acceso y uso de la aplicación móvil de análisis y gestión de riesgos urbanos Barrio Seguro (en adelante, la \"Aplicación\"), diseñada para la prevención, monitoreo inteligente y seguridad comunitaria en Bogotá D.C.",
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(14.dp))
        SeccionLegal(
            numero = "1",
            titulo = "Naturaleza del Servicio y Objeto",
            cuerpo = "La Aplicación es una plataforma de análisis y gestión de riesgo urbano diseñada con fines estrictamente preventivos e informativos. Su función principal es procesar las coordenadas geográficas emitidas por el dispositivo móvil del usuario para compararlas, en tiempo real, con polígonos y umbrales de riesgo construidos a partir de registros estadísticos abiertos de delitos de alto impacto suministrados por entidades distritales de Bogotá D.C."
        )

        SeccionLegal(
            numero = "2",
            titulo = "Alcance y Exclusión de Responsabilidad",
            cuerpo = "• Carácter Estadístico y Preventivo: Las alertas emitidas corresponden a estimaciones estadísticas derivadas de datos históricos. En ningún caso constituyen una garantía absoluta de seguridad, ni certifican que una zona esté libre de incidentes en tiempo real.\n\n" +
                    "• No Sustitución de Autoridades ni Servicios de Emergencia: La Aplicación no es un botón de pánico, ni un canal de atención de emergencias, ni sustituye a la Policía Nacional o a la línea 123.\n\n" +
                    "• Tolerancia del Hardware: El usuario reconoce que la precisión de la geolocalización depende del hardware de su dispositivo y de factores del entorno urbano (con márgenes de desviación estándar que oscilan habitualmente entre 10 y 50 metros). Los desarrolladores no asumen responsabilidad por demoras, fallos de cobertura o imprecisiones ajenas al software."
        )

        SeccionLegal(
            numero = "3",
            titulo = "Permisos del Dispositivo y Uso Adecuado",
            cuerpo = "• Ubicación en Segundo Plano: Para que el sistema evalúe perímetros virtuales (geofencing) y envíe advertencias oportunas mientras el usuario se desplaza, la Aplicación requiere acceso a la geolocalización en primer y segundo plano (Background Location Services). El usuario puede activar o revocar este permiso en cualquier momento desde los ajustes de su sistema operativo.\n\n" +
                    "• Uso Lícito: El usuario se compromete a no utilizar la Aplicación para fines fraudulentos, pruebas de penetración no autorizadas, descompilación del código o cualquier actividad contraria a la ley."
        )

        SeccionLegal(
            numero = "4",
            titulo = "Propiedad Intelectual",
            cuerpo = "Todos los derechos sobre el diseño, arquitectura de software, código fuente, algoritmos del agente inteligente y documentación técnica pertenecen a sus autores y desarrolladores del proyecto. Se autoriza su uso de forma personal, no comercial y revocable."
        )

        SeccionLegal(
            numero = "5",
            titulo = "Ley Aplicable y Jurisdicción",
            cuerpo = "Estos Términos se rigen e interpretan de acuerdo con las leyes de la República de Colombia. Cualquier controversia se someterá a los jueces y tribunales competentes de Bogotá D.C."
        )
    }
}

@Composable
fun ContenidoPoliticaPrivacidad() {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(
            text = "POLÍTICA DE PRIVACIDAD Y TRATAMIENTO DE DATOS PERSONALES",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Última actualización: 2026",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "En cumplimiento del Artículo 15 de la Constitución Política de Colombia, la Ley Estatutaria 1581 de 2012, el Decreto Reglamentario 1074 de 2015 y bajo el principio de Responsabilidad Demostrada (Accountability), se describe a continuación el tratamiento de los datos recopilados por la Aplicación.",
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(14.dp))
        SeccionLegal(
            numero = "1",
            titulo = "Responsable del Tratamiento",
            cuerpo = "• Plataforma: Barrio Seguro - Monitoreo y Análisis de Riesgo Ciudadano\n" +
                    "• Desarrolladores Líderes: David Santiago Castelblanco Artunduaga & Johan Sebastian Fuentes Pinto\n" +
                    "• Canal de Contacto y Soporte: contacto@barrioseguro.app / soporte@barrioseguro.co"
        )

        SeccionLegal(
            numero = "2",
            titulo = "Principio de Privacidad desde el Diseño (Accountability)",
            cuerpo = "La Aplicación opera bajo un modelo estricto de privacidad por diseño:\n\n" +
                    "• No Recolección de Datos Identificativos: La Aplicación no solicita ni almacena nombres reales, números de identificación, números telefónicos ni contraseñas.\n\n" +
                    "• Procesamiento Volátil (Stateless): Las coordenadas espaciales (latitud y longitud) se procesan temporalmente en memoria RAM exclusivamente para la evaluación de proximidad y geofencing.\n\n" +
                    "• Purga Inmediata: Una vez emitida la respuesta de riesgo, la coordenada se descarta de la memoria. No se generan historiales de ruta ni perfiles de desplazamiento."
        )

        SeccionLegal(
            numero = "3",
            titulo = "Finalidad del Tratamiento de Datos",
            cuerpo = "El único dato técnico capturado durante el uso de la Aplicación son las coordenadas GPS enviadas periódicamente. Este dato se utiliza exclusivamente para:\n\n" +
                    "1. Evaluar si la posición cruza perímetros virtuales de riesgo de 100m a 200m.\n" +
                    "2. Enviar notificaciones preventivas y permitir reportes comunitarios anónimos."
        )

        SeccionLegal(
            numero = "4",
            titulo = "Seguridad de la Información y Transmisión",
            cuerpo = "Toda la comunicación entre la aplicación móvil y el backend se realiza a través de canales encriptados mediante TLS 1.2 o superior, garantizando la confidencialidad de los paquetes JSON e impidiendo la interceptación de datos en tránsito."
        )

        SeccionLegal(
            numero = "5",
            titulo = "Derechos de los Titulares (Habeas Data)",
            cuerpo = "De conformidad con la Ley 1581 de 2012, el usuario ejerce pleno control sobre su información:\n\n" +
                    "• Deshabilitando los permisos de geolocalización (GPS) en los ajustes del dispositivo.\n" +
                    "• Desinstalando la Aplicación en cualquier instante."
        )
    }
}

@Composable
fun ContenidoCreditosDesarrolladores() {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(
            text = "CRÉDITOS Y EQUIPO DE DESARROLLO",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Proyecto de Innovación Tecnológica y Seguridad Ciudadana",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))

        Text(
            text = "Esta aplicación ha sido diseñada y desarrollada por:",
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Tarjeta Desarrollador 1
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("👨‍💻", fontSize = 24.sp)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "David Santiago Castelblanco Artunduaga",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Tarjeta Desarrollador 2
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                modifier = Modifier.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("👨‍💻", fontSize = 24.sp)
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(
                    text = "Johan Sebastian Fuentes Pinto",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Tarjeta de Propósito
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "🚀 Misión del Proyecto",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Proveer a la ciudadanía de Bogotá herramientas tecnológicas de vanguardia, mapas predictivos de riesgo por cuadra, alertas tempranas y un canal comunitario anónimo para construir entornos urbanos más seguros.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

@Composable
fun SeccionLegal(
    numero: String,
    titulo: String,
    cuerpo: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = numero,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = titulo,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = cuerpo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

