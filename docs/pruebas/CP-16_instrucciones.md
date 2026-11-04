# CP-16 — Prueba de campo: latencia de la alerta por geolocalización

Mide cuánto tarda la app en notificar al usuario desde que cruza físicamente
hacia una localidad de `nivel_riesgo` "alto" (o una UPZ con
`nivel_llamadas` "alto"), usando el backend real a través de internet móvil
(no en la misma red Wi-Fi del PC), vía un túnel de Cloudflare.

Mecanismo real que se está midiendo (`MonitoreoUbicacionService.kt`,
`revisarRiesgo()`): el servicio revisa la ubicación cada
`intervaloMinutos` (configurable en Ajustes, 2 min por defecto), llama a
`GET /riesgo?lat&lng`, y si `nivel_riesgo == "alto"` y la localidad es
distinta a la última ya notificada, dispara la notificación. Por diseño,
la latencia esperada NO es instantánea — depende del intervalo de
sondeo, no es una alerta geofence nativa de Android.

## 1. Exponer el backend a internet con `cloudflared`

Si no tienes `cloudflared` instalado:

```powershell
winget install --id Cloudflare.cloudflared -e
```

Con el backend ya corriendo en el PC (`python run_api.py`, puerto 8000 por
defecto), en otra terminal:

```powershell
cloudflared tunnel --url http://localhost:8000
```

Esto imprime una URL pública temporal del tipo
`https://palabras-al-azar.trycloudflare.com`. Esa URL cambia cada vez que
se reinicia el túnel -- cópiala tal cual aparezca en tu ejecución real, no
reutilices una de una corrida anterior.

**Nota de seguridad:** si el backend tiene configuradas
`BARRIO_SEGURO_API_KEY`/`BARRIO_SEGURO_ADMIN_KEY` (ver
`docs/Complemento_DAS.md` §6), el túnel las respeta igual que cualquier
otro acceso -- no las desactiva. Si no están configuradas, el túnel deja
el backend abierto a cualquiera que adivine la URL mientras el túnel esté
activo; cierra `cloudflared` (Ctrl+C) al terminar la prueba.

## 2. Configurar la app con esa URL

1. Abre la app → pestaña **Ajustes**.
2. En el campo **Servidor**, escribe la URL del túnel, con esquema HTTPS
   y barra final: `https://<lo-que-haya-generado-cloudflared>/` (el campo
   espera la base URL con `/` al final, igual que `http://10.0.2.2:8000/`
   que usa el emulador por defecto).
3. Toca **Conectar**. Confirma que la pestaña Riesgo carga el mapa/ranking
   sin error -- eso valida que el túnel y la app están bien conectados
   antes de empezar la prueba física.
4. Activa el monitoreo de ubicación (pestaña Riesgo → botón de monitoreo,
   requiere permiso de ubicación concedido).

## 3. Ejecutar los cruces y registrar en `CP-16_registro.csv`

Para cada cruce real (caminando o en vehículo) desde una localidad hacia
otra de mayor riesgo:

1. Anota la hora exacta en que crees que cruzaste el límite (`hora_cruce`)
   -- usa un mapa de localidades de Bogotá como referencia, o la propia
   pestaña Mapa de la app para verificar después en qué localidad caíste.
2. Anota la hora exacta en que te llega la notificación push
   (`hora_notificacion`).
3. `latencia_s` = `hora_notificacion` - `hora_cruce`, en segundos.
4. Anota si tu celular estaba en 4G o 5G (`red`).
5. En `observaciones`, anota cualquier cosa relevante: si no llegó
   notificación, si llegó duplicada, si el teléfono estaba en modo ahorro
   de batería (puede retrasar servicios en segundo plano), etc.

Repite para las 10 filas. No hace falta que todos los cruces sean hacia
localidades distintas -- lo importante es variar condiciones (hora del
día, red, si el teléfono estaba con la pantalla apagada).

## 4. Calcular el resumen

```powershell
python docs/pruebas/resumen_cp16.py
```

Imprime la latencia promedio, mínima, máxima, y cuántos cruces quedaron
por debajo del umbral objetivo (ver nota de ese script sobre de dónde
sale el umbral -- no hay uno formalmente definido en el código ni en la
documentación del equipo, así que el script usa 10s como supuesto y lo
dice explícitamente).
