# Sprint 4 · Events BEFORE

Medido el 26 de noviembre de 2026 en un **Xiaomi 25078RA3EL** físico, Android 16 (API 36), 720 × 1600, con batería al 90 %, 26 °C y estado térmico `0`. APK debug `com.senecapp` 1.0. El build y las **229 pruebas unitarias** pasaron. Se verificó la instalación conservando la sesión.

## Cómo repetir la prueba

La pantalla conectada mostró **0 eventos**. `EventsBenchmarkActivity.kt` carga **160 eventos fijos** en el composable real de Events. Está disponible solo en debug y los datos de prueba se crean en memoria.

Archivos de la prueba, desde la raíz del repositorio.

| Archivo | Función |
|---|---|
| `app/src/debug/java/com/senecapp/benchmark/EventsBenchmarkActivity.kt` | Genera los eventos de prueba y monta la pantalla real. |
| `scripts/performance/sprint4/profile_events.py` | Repite las acciones y guarda las métricas. |
| `scripts/performance/sprint4/events.pbtxt` | Configura la traza de Perfetto. |

Conecta solo el Xiaomi por USB, desbloquéalo y ejecuta desde la raíz del proyecto en PowerShell.

```powershell
.\gradlew.bat assembleDebug testDebugUnitTest --offline
$sprint4Adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$sprint4Serial = (& $sprint4Adb get-serialno).Trim()
& $sprint4Adb -s $sprint4Serial install -r -t app/build/outputs/apk/debug/app-debug.apk
python scripts/performance/sprint4/profile_events.py --adb $sprint4Adb --serial $sprint4Serial --output captures/sprint4/before --runs 3 --trace
```

El script repite el flujo tres veces. Toma memoria antes de abrir Events, abre la lista, toma otra muestra y hace tres deslizamientos. Luego aplica `Robotics` (40 resultados), quita el filtro (160 resultados), repite los deslizamientos y toma la última muestra. `--trace` agrega una ronda aparte con Perfetto. El script termina ejecutando el benchmark de filtros, itinerario y fechas. Para comparar la versión optimizada en el mismo Xiaomi, usa `--output captures/sprint4/after`.

## Resultados

| Interacción | Media BEFORE | p95 BEFORE | Máximo BEFORE | Jank BEFORE | CPU media BEFORE | AFTER |
|---|---:|---:|---:|---:|---:|---|
| Abrir Events | 254,21 ms | 11,74 ms | 8.568,30 ms | 3/848 | 7.900 ms | |
| Scroll inicial | 29,14 ms | 41,22 ms | 114,88 ms | 25/632 | 961 ms | |
| Aplicar filtro | 1.306,24 ms | 1.481,19 ms | 1.489,32 ms | 4/328 | 1.853 ms | |
| Quitar filtro | 4.178,48 ms | 4.226,10 ms | 4.232,97 ms | 3/6 | 5.380 ms | |
| Scroll final | 16,93 ms | 19,04 ms | 20,65 ms | 3/986 | 946 ms | |

Duraciones tomadas de `gfxinfo framestats` (`FrameCompleted − IntendedVsync`). Android reporta jank con un contador separado. Los máximos de apertura fueron **8,57 s**, **7,59 s** y **8,08 s**. El p95 de apertura está dominado por frames de animación que Android conservó en la primera ronda. `gfxinfo` conservó seis duraciones al aplicar el filtro y tres al quitarlo. La CPU suma el tiempo del proceso en todos sus hilos durante cada acción.

| Memoria media | Antes de abrir | Al abrir | Al terminar | AFTER |
|---|---:|---:|---:|---|
| PSS | 155,99 MiB | 182,55 MiB | 195,63 MiB | |
| Heap Dalvik asignado | 5,04 MiB | 17,36 MiB | 19,00 MiB | |

| Procesamiento aislado (40 muestras por operación) | Media BEFORE | p95 BEFORE | AFTER |
|---|---:|---:|---|
| `filterEvents`, lista completa | 102,33 ms | 118,96 ms | |
| `filterEvents`, búsqueda `Robotics` | 24,27 ms | 29,31 ms | |
| `buildItinerary` | 116,81 ms | 122,79 ms | |
| Parsear una fecha por evento | 6,72 ms | 6,75 ms | |

**Qué probar primero.** Abrir Events y recuperar las 160 filas después de filtrar fueron las operaciones más lentas. La primera hipótesis es el costo de crear todas las filas con `forEach` en `EventFeedScreen.kt`, dentro de una `Column` con `verticalScroll` en `ViewComponents.kt`. Probar `LazyColumn` y comparar esos máximos, el scroll y la memoria. Evaluar por separado el parseo repetido en `EventPlanning.kt`.

## Evidencia local

La traza de Perfetto, las capturas, las métricas por ronda, el Logcat y el informe original están en `captures/sprint4/SENECApp-Sprint4-BEFORE.zip` en el equipo de medición. `captures/` está ignorada. Al reproducir la prueba, el script genera evidencias nuevas en el directorio indicado por `--output`. Para examinar la traza, extrae `diagnostic/events.perfetto-trace` y ábrela en Perfetto UI. Las asignaciones por clase requieren una captura aparte en el Profiler de Android Studio.
