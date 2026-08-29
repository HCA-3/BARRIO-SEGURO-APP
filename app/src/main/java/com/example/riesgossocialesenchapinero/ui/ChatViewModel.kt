package com.example.riesgossocialesenchapinero.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.riesgossocialesenchapinero.data.ApiClient
import com.example.riesgossocialesenchapinero.data.local.AppDatabase
import com.example.riesgossocialesenchapinero.data.local.HechoEntity
import com.example.riesgossocialesenchapinero.data.local.MensajeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val historial: List<ApiClient.MensajeChat> = emptyList(),
    val hechosRecordados: List<String> = emptyList(),
    val enviando: Boolean = false,
    val error: String? = null,
    val cargandoHistorial: Boolean = true,
)

/**
 * A diferencia de la primera versión, el historial y los hechos recordados
 * ahora persisten en una base de datos local (Room) en vez de vivir solo en
 * memoria: sobreviven a cerrar la app. No hay cuenta de usuario/backend con
 * estado -- todo vive en el celular de cada quien.
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.obtener(application)
    private val mensajeDao = db.mensajeDao()
    private val hechoDao = db.hechoDao()

    private val _estado = MutableStateFlow(ChatUiState())
    val estado: StateFlow<ChatUiState> = _estado

    // Solo mensajes de usuario/asistente con contenido: los de rol "tool" y
    // los de "assistant" que solo traen tool_calls (sin content) son ruido
    // de la llamada a herramientas, no algo para mostrarle al usuario.
    val mensajesVisibles: List<ApiClient.MensajeChat>
        get() = _estado.value.historial.filter {
            (it.role == "user" || it.role == "assistant") && it.content.isNotBlank()
        }

    init {
        viewModelScope.launch {
            val (mensajesGuardados, hechosGuardados) = withContext(Dispatchers.IO) {
                mensajeDao.obtenerTodos() to hechoDao.obtenerTextos()
            }
            _estado.value = _estado.value.copy(
                historial = mensajesGuardados.map { ApiClient.MensajeChat(it.role, it.content) },
                hechosRecordados = hechosGuardados,
                cargandoHistorial = false,
            )
        }
    }

    fun enviarMensaje(texto: String) {
        if (texto.isBlank() || _estado.value.enviando) return

        val mensajeUsuario = ApiClient.MensajeChat("user", texto)
        val historialConPregunta = _estado.value.historial + mensajeUsuario
        _estado.value = _estado.value.copy(historial = historialConPregunta, enviando = true, error = null)

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                mensajeDao.insertar(MensajeEntity(role = mensajeUsuario.role, content = mensajeUsuario.content))
            }

            _estado.value = try {
                val respuesta = withContext(Dispatchers.IO) {
                    ApiClient.enviarMensajeChat(historialConPregunta, _estado.value.hechosRecordados)
                }

                withContext(Dispatchers.IO) {
                    // El historial que vuelve del backend ya incluye el mensaje del
                    // usuario que mandamos (más lo nuevo: assistant/tool/etc):
                    // persistimos solo lo NUEVO para no duplicar lo ya guardado.
                    respuesta.mensajes.drop(historialConPregunta.size).forEach {
                        mensajeDao.insertar(MensajeEntity(role = it.role, content = it.content))
                    }
                    respuesta.hechosNuevos.forEach { hecho ->
                        if (hechoDao.existeTexto(hecho) == 0) hechoDao.insertar(HechoEntity(texto = hecho))
                    }
                }

                _estado.value.copy(
                    historial = respuesta.mensajes,
                    hechosRecordados = _estado.value.hechosRecordados + respuesta.hechosNuevos,
                    enviando = false,
                )
            } catch (e: Exception) {
                _estado.value.copy(enviando = false, error = e.message ?: "Error desconocido hablando con el agente")
            }
        }
    }

    fun borrarConversacion() {
        viewModelScope.launch(Dispatchers.IO) {
            mensajeDao.borrarTodos()
            withContext(Dispatchers.Main) { _estado.value = _estado.value.copy(historial = emptyList()) }
        }
    }

    fun borrarHecho(texto: String) {
        _estado.value = _estado.value.copy(hechosRecordados = _estado.value.hechosRecordados - texto)
        viewModelScope.launch(Dispatchers.IO) { hechoDao.borrarPorTexto(texto) }
    }
}
