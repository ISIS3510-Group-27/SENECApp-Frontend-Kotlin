# Sprint 4 · Events con LazyColumn

Medición del 26 de noviembre de 2026 en el Xiaomi **25078RA3EL** físico, Android 16 (API 36), 720 × 1600. Se instaló el APK debug 1.0 con `adb install -r -t` para conservar la sesión y los datos. El build debug y las 229 pruebas unitarias pasaron.

## Qué cambió

En `app/src/main/java/com/senecapp/ui/EventFeedScreen.kt`, una sola `LazyColumn` contiene la cabecera, controles, filas y botón de actualización. Conserva los márgenes, insets y espaciado. Cada evento usa `items(events, key = { it.id })`, de modo que Compose identifica sus filas por ID cuando cambia el filtro y compone y mide las que necesita cerca del área visible. La acción del escáner mantiene el estado y la corrutina en la pantalla, así que su ciclo de vida no depende de si la cabecera está visible. `DemoView` sigue siendo el contenedor de los demás usos. [Documentación de listas lazy en Compose](https://developer.android.com/develop/ui/compose/lists).

## Cómo se midió

Se ejecutaron tres rondas con el script y la Activity debug originales. Cada ronda abre Events con 160 eventos fijos, hace tres deslizamientos, aplica `Robotics` (40 resultados), quita el filtro (160) y repite los tres deslizamientos. Una cuarta ronda diagnóstica genera la traza de Perfetto y queda fuera de las cifras comparativas. El benchmark aislado se ejecuta al final.

```powershell
.\gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --offline
$perfAdb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$perfSerial = (& $perfAdb get-serialno).Trim()
& $perfAdb -s $perfSerial install -r -t app/build/outputs/apk/debug/app-debug.apk
python scripts/performance/sprint4/profile_events.py --adb $perfAdb --serial $perfSerial --output captures/sprint4/after --runs 3 --trace
```

El dispositivo estaba conectado por USB y en estado térmico `0`. La batería estaba al 90 % y 26 °C en BEFORE y al 96 % y 26–27 °C en AFTER. Todas las muestras de scroll conservadas en ambas versiones reportaron intervalos de **60 Hz**. Las duraciones conservadas en `gfxinfo` se calculan como `FrameCompleted − IntendedVsync`. Jank viene del contador de Android y la CPU suma `utime + stime` del proceso por acción. La memoria usa `dumpsys meminfo`. El build debug mantiene la comparación con la línea base. Los resultados de release requieren otra medición.

## BEFORE → AFTER

Las flechas muestran el cambio desde la línea base. En las métricas de duración y CPU, la mejora se calcula como `(BEFORE − AFTER) / BEFORE × 100` con los valores sin redondear.

| Interacción | Media de frames (ms) | p95 (ms) | Máximo (ms) | Mejora del máximo | CPU media (ms) | Jank (frames afectados/total) |
|---|---:|---:|---:|---:|---:|---:|
| Abrir Events | 254,21 → 19,48 | 11,74 → 13,07 | 8.568,30 → 1.100,60 | 87,16 % | 7.900 → 2.253 | 3/848 → 3/558 |
| Scroll inicial | 29,14 → 18,12 | 41,22 → 42,14 | 114,88 → 77,29 | 32,72 % | 961 → 761 | 25/632 → 14/620 |
| Aplicar `Robotics` | 1.306,24 → 169,09 | 1.481,19 → 207,45 | 1.489,32 → 208,45 | 86,00 % | 1.853 → 337 | 4/328 → 0/46 |
| Quitar filtro | 4.178,48 → 336,70 | 4.226,10 → 345,21 | 4.232,97 → 346,60 | 91,81 % | 5.380 → 433 | 3/6 → 3/6 |
| Scroll final | 16,93 → 12,04 | 19,04 → 24,41 | 20,65 → 29,26 | Empeoró 41,70 % | 946 → 912 | 3/986 → 3/1.038 |

La CPU bajó **71,48 %** al abrir y **91,95 %** al quitar el filtro. El trabajo diferido hacia el scroll es una explicación posible de que el p95 subiera **2,23 %** en el scroll inicial y **28,21 %** en el final, incluso con medias más bajas. El p95 de apertura subió **11,36 %** y registró la misma cantidad de frames janky con un denominador menor. El buffer de `framestats` conserva una mezcla variable de frames de animación y de contenido. En la apertura se conservaron 122 duraciones BEFORE y 246 AFTER. En quitar el filtro se conservaron solo tres por versión y el jank siguió en 3/6. Los máximos son los mayores frames conservados, no intervalos de toque a pantalla lista.

| Memoria media | BEFORE | AFTER | Cambio |
|---|---:|---:|---:|
| PSS antes de abrir | 155,99 MiB | 173,12 MiB | +17,13 MiB |
| PSS al abrir | 182,55 MiB | 170,54 MiB | −12,01 MiB |
| PSS al terminar | 195,63 MiB | 160,12 MiB | −35,51 MiB (18,15 %) |
| Heap Dalvik asignado al abrir | 17,36 MiB | 5,92 MiB | −11,44 MiB |
| Heap Dalvik asignado al terminar | 19,00 MiB | 6,97 MiB | −12,03 MiB |

La PSS inicial difirió entre builds. Los valores de heap son instantáneas. Las asignaciones por clase requieren una sesión aparte de Android Studio Profiler. El benchmark aislado dio medias similares en ambas versiones para `filterEvents` completo (102,33 → 100,22 ms) y `buildItinerary` (116,81 → 114,75 ms), con 40 muestras por operación. El principal beneficio medido aparece al montar o volver a mostrar la lista completa. Conviene mantener el cambio y revisar el p95 de scroll en un perfil de release separado antes de decidir la siguiente optimización.

## Pruebas en la app

En el Xiaomi pasaron ocho casos de instrumentación de `EventDetailsCacheInstrumentation` y `SprintStorageInstrumentation`. En la app con sesión también se comprobó la navegación Discover ↔ Events, la búsqueda y su limpieza, los filtros Today, My RSOs y Saved, el botón deshabilitado del itinerario vacío y la presencia del acceso al escáner. Con la conexión desactivada, refrescar mostró el estado offline y `Retry`. Se restauró la conexión y se abrió Events de nuevo.

La cuenta mostraba `0 EVENTS`. Queda por probar con un evento real abrir el detalle, guardar y quitar el evento, construir y volver a abrir un itinerario con datos y completar el check-in con un QR válido.

## Evidencia

- [Captura BEFORE](before-events.png) y [captura AFTER](after-events.png), tomadas en el Xiaomi al abrir el mismo listado de 160 eventos. Sirven para comparar el diseño. Las mediciones vienen de los registros de Android.
- [Métricas AFTER](summary.json), agregadas de las tres rondas por el script original.
- Perfetto, `gfxinfo`, `meminfo`, Logcat y resultados por ronda en `captures/sprint4/after/` (local, ignorado). La traza se abre localmente en Perfetto UI desde `diagnostic/events.perfetto-trace`. Los crudos BEFORE se conservan en `captures/sprint4/SENECApp-Sprint4-BEFORE.zip`.
