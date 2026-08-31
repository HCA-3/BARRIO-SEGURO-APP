package com.example.riesgossocialesenchapinero.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.riesgossocialesenchapinero.ui.theme.ColoresDatos

/**
 * Etiqueta del nivel de riesgo. El texto va SIEMPRE ("ALTO"/"MEDIO"/"BAJO"):
 * el color es refuerzo, no el único portador del significado.
 */
@Composable
fun BadgeRiesgo(nivel: String, modifier: Modifier = Modifier) {
    Surface(
        color = ColoresDatos.relleno(nivel),
        shape = RoundedCornerShape(6.dp),
        modifier = modifier,
    ) {
        Text(
            nivel.uppercase(),
            color = ColoresDatos.tinta(nivel),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * Una barra del gráfico de delitos por tipo. Horizontal porque las etiquetas son
 * nombres largos ("Violencia intrafamiliar"): en vertical se solaparían o habría
 * que girarlas.
 *
 * Es una sola serie (todas las barras miden lo mismo: número de casos), así que
 * van todas del mismo color y la longitud es la que codifica la magnitud. Cada
 * barra lleva su cifra al lado, que con 11 categorías se lee mejor que un eje.
 */
@Composable
fun BarraDelito(
    tipo: String,
    cantidad: Int,
    maximo: Int,
    indice: Int,
    modifier: Modifier = Modifier,
) {
    val fraccionObjetivo = if (maximo > 0) cantidad.toFloat() / maximo else 0f
    // Arranca en 0 y crece: la animación escalonada por índice deja ver el orden
    // de mayor a menor en vez de aparecer todo de golpe.
    var visible by remember { mutableStateOf(false) }
    val fraccion by animateFloatAsState(
        targetValue = if (visible) fraccionObjetivo else 0f,
        animationSpec = tween(
            durationMillis = 620,
            delayMillis = 40 * indice,
            easing = LinearOutSlowInEasing,
        ),
        label = "barra",
    )
    androidx.compose.runtime.LaunchedEffect(Unit) { visible = true }

    Column(modifier = modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                tipo,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                "%,d".format(cantidad),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(top = 3.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraccion)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(ColoresDatos.barra()),
            )
        }
    }
}

/** Cifra grande con su etiqueta debajo. Para datos sueltos que no son un gráfico. */
@Composable
fun TileDato(etiqueta: String, valor: String, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                valor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                etiqueta,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Sección plegable. Con 11 delitos + 6 datos de contexto, mostrarlo todo abierto
 * obliga a un scroll largo; plegado se ve la estructura de un vistazo y se abre
 * solo lo que interesa.
 */
@Composable
fun SeccionPlegable(
    titulo: String,
    abiertaPorDefecto: Boolean = true,
    contenido: @Composable () -> Unit,
) {
    var abierta by remember { mutableStateOf(abiertaPorDefecto) }
    val giro by animateFloatAsState(
        targetValue = if (abierta) 90f else 0f,
        animationSpec = tween(220),
        label = "giro",
    )
    Column(modifier = Modifier.fillMaxWidth().padding(top = 14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { abierta = !abierta }
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Triángulo que gira 0 -> 90 grados al abrir: indica el estado sin
            // necesidad de una librería de iconos.
            Text(
                "▸",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .layout { medible, restricciones ->
                        val situado = medible.measure(restricciones)
                        layout(situado.width, situado.height) {
                            situado.placeRelativeWithLayer(0, 0) { rotationZ = giro }
                        }
                    },
            )
            Text(titulo, style = MaterialTheme.typography.titleSmall)
        }
        AnimatedVisibility(
            visible = abierta,
            enter = expandVertically(tween(220)),
            exit = shrinkVertically(tween(180)),
        ) {
            Column { contenido() }
        }
    }
}

/** Fila etiqueta/valor para datos que no merecen un tile. */
@Composable
fun FilaDato(etiqueta: String, valor: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            etiqueta,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(valor, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
    }
}
