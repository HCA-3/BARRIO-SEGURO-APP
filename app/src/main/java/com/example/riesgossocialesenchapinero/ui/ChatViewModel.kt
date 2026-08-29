package com.example.riesgossocialesenchapinero.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.riesgossocialesenchapinero.data.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ChatUiState(
    val historial: List<ApiClient.MensajeChat> = emptyList(),
    val enviando: Boolean = false,
    val error: String? = null,
)

class ChatViewModel : ViewModel() {
    private val _estado = MutableStateFlow(ChatUiState())
    val estado: StateFlow<ChatUiState> = _estado

    // Solo mensajes de usuario/asistente con contenido: los de rol "tool" y
    // los de "assistant" que solo traen tool_calls (sin content) son ruido
    // de la llamada a herramientas, no algo para mostrarle al usuario.
    val mensajesVisibles: List<ApiClient.MensajeChat>
        get() = _estado.value.historial.filter {
            (it.role == "user" || it.role == "assistant") && it.content.isNotBlank()
        }

    fun enviarMensaje(texto: String) {
        if (texto.isBlank() || _estado.value.enviando) return

        val historialConPregunta = _estado.value.historial + ApiClient.MensajeChat("user", texto)
        _estado.value = _estado.value.copy(historial = historialConPregunta, enviando = true, error = null)

        viewModelScope.launch {
            _estado.value = try {
                val respuesta = withContext(Dispatchers.IO) {
                    ApiClient.enviarMensajeChat(historialConPregunta)
                }
                _estado.value.copy(historial = respuesta.mensajes, enviando = false)
            } catch (e: Exception) {
                _estado.value.copy(enviando = false, error = e.message ?: "Error desconocido hablando con el agente")
            }
        }
    }
}
