package com.example.riesgossocialesenchapinero

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class LineaEmergencia(
    val numero: String,
    val entidad: String,
    val descripcion: String,
    val resIdLogo: Int,
    val colorBoton: Color
)

@Composable
fun EmergenciasScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lineas = remember {
        listOf(
            LineaEmergencia(
                numero = "123",
                entidad = "Línea Única de Emergencias",
                descripcion = "Atención integrada de Policía, Salud, Bomberos y Tránsito en Bogotá.",
                resIdLogo = R.drawable.ic_logo_123,
                colorBoton = Color(0xFFD32F2F)
            ),
            LineaEmergencia(
                numero = "119",
                entidad = "Cuerpo Oficial de Bomberos",
                descripcion = "Incendios, rescates, colapsos estructurales y materiales peligrosos.",
                resIdLogo = R.drawable.ic_logo_bomberos,
                colorBoton = Color(0xFFB71C1C)
            ),
            LineaEmergencia(
                numero = "132",
                entidad = "Cruz Roja Colombiana",
                descripcion = "Atención médica prehospitalaria, ambulancias y auxilio humanitario.",
                resIdLogo = R.drawable.ic_logo_cruz_roja,
                colorBoton = Color(0xFFC2185B)
            ),
            LineaEmergencia(
                numero = "144",
                entidad = "Defensa Civil Colombiana",
                descripcion = "Prevención, rescate, gestión del riesgo y búsqueda y localización.",
                resIdLogo = R.drawable.ic_logo_defensa_civil,
                colorBoton = Color(0xFFE65100)
            ),
            LineaEmergencia(
                numero = "116",
                entidad = "Acueducto de Bogotá (EAAB)",
                descripcion = "Inundaciones, sumideros taponados, fuga de agua y daños de alcantarillado.",
                resIdLogo = R.drawable.ic_logo_acueducto,
                colorBoton = Color(0xFF0D47A1)
            ),
            LineaEmergencia(
                numero = "164",
                entidad = "Emergencias Gas Natural Vanti",
                descripcion = "Fugas de gas natural, olor a gas y fallas en instalaciones de gas.",
                resIdLogo = R.drawable.ic_logo_vanti,
                colorBoton = Color(0xFF00838F)
            ),
            LineaEmergencia(
                numero = "165",
                entidad = "GAULA Policía Nacional",
                descripcion = "Atención especializada las 24 horas contra secuestro y extorsión.",
                resIdLogo = R.drawable.ic_logo_gaula,
                colorBoton = Color(0xFF1B5E20)
            ),
            LineaEmergencia(
                numero = "115",
                entidad = "Enel Colombia (Codensa)",
                descripcion = "Cables de alta tensión caídos, postes inclinados y cortocircuitos.",
                resIdLogo = R.drawable.ic_logo_enel,
                colorBoton = Color(0xFFF57F17)
            ),
            LineaEmergencia(
                numero = "155",
                entidad = "Línea Púrpura (Mujeres)",
                descripcion = "Atención y orientación a mujeres víctimas de violencia en Bogotá.",
                resIdLogo = R.drawable.ic_logo_purpura,
                colorBoton = Color(0xFF6A1B9A)
            ),
            LineaEmergencia(
                numero = "106",
                entidad = "Línea Psicoactiva y Salud Mental",
                descripcion = "Ayuda psicológica, crisis emocionales y prevención de suicidio.",
                resIdLogo = R.drawable.ic_logo_salud_mental,
                colorBoton = Color(0xFF00695C)
            )
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
                        Image(
                            painter = painterResource(id = linea.resIdLogo),
                            contentDescription = linea.entidad,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                        )

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
                            colors = ButtonDefaults.buttonColors(containerColor = linea.colorBoton),
                            shape = RoundedCornerShape(8.dp),
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
