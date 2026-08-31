package com.example.riesgossocialesenchapinero

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class LineaEmergencia(
    val numero: String,
    val entidad: String,
    val descripcion: String,
    val icono: String,
    val color: Color
)

@Composable
fun EmergenciasScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lineas = remember {
        listOf(
            LineaEmergencia("123", "Línea Única de Emergencias", "Atención integrada de Policía, Salud, Bomberos y Tránsito en Bogotá.", "🚨", Color(0xFFD32F2F)),
            LineaEmergencia("119", "Cuerpo Oficial de Bomberos", "Incendios, rescates, colapsos estructurales y materiales peligrosos.", "🚒", Color(0xFFE64A19)),
            LineaEmergencia("132", "Cruz Roja Colombiana", "Atención médica prehospitalaria, ambulancias y auxilio humanitario.", "🚑", Color(0xFFC2185B)),
            LineaEmergencia("144", "Defensa Civil Colombiana", "Prevención, rescate, gestión del riesgo y búsqueda y localización.", "🛡️", Color(0xFFF57C00)),
            LineaEmergencia("116", "Acueducto de Bogotá (EAAB)", "Inundaciones, sumideros taponados, fuga de agua y daños de alcantarillado.", "💧", Color(0xFF1976D2)),
            LineaEmergencia("164", "Emergencias Gas Natural Vanti", "Fugas de gas natural, olor a gas y fallas en instalaciones de gas.", "💨", Color(0xFF0097A7)),
            LineaEmergencia("165", "GAULA Policía Nacional", "Atención especializada las 24 horas contra secuestro y extorsión.", "👮", Color(0xFF388E3C)),
            LineaEmergencia("115", "Enel Colombia (Codensa)", "Cables de alta tensión caídos, postes inclinados y cortocircuitos.", "⚡", Color(0xFFFBC02D)),
            LineaEmergencia("155", "Línea Púrpura (Mujeres)", "Atención y orientación a mujeres víctimas de violencia en Bogotá.", "💜", Color(0xFF7B1FA2)),
            LineaEmergencia("106", "Línea Psicoactiva y Salud Mental", "Ayuda psicológica, crisis emocionales y prevención de suicidio.", "🧠", Color(0xFF00897B))
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = "📞 " + stringResource(R.string.lineas_titulo_banner),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.lineas_desc_banner),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(lineas, key = { it.numero }) { linea ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = linea.color,
                            shape = CircleShape,
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(linea.icono, style = MaterialTheme.typography.titleMedium)
                            }
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${linea.entidad} (${linea.numero})",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = linea.descripcion,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${linea.numero}")).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = linea.color),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text("📞 " + stringResource(R.string.linea_llamar))
                        }
                    }
                }
            }
        }
    }
}
