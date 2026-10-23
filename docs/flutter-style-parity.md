# Flutter / Kotlin visual parity

Flutter is the visual reference. Keep the native presentation aligned with:

- `lib/core/theme/app_colors.dart` and `app_theme.dart`.
- `lib/core/widgets/org_image.dart`, `rso_list_tile.dart` and `surfaces.dart`.
- `lib/features/discover/discover_screen.dart`, `shell/home_shell.dart`, `my_rsos/my_rsos_screen.dart`, and `profile/profile_screen.dart`.

## Shared visual foundations

| Role | Value |
| --- | --- |
| Background | `#171A21` |
| Foreground | `#F0E2E7` |
| Brand / primary action | `#A50104` |
| Accent | `#FFBA08` |
| Secondary surface | `#1D3557` |
| Card | `#1E2633` |
| Muted text | `#8B94B0` |
| Body text | `#C8CDE0` |
| Headings / body | Bricolage Grotesque / Nunito |
| Page gutter | 24 dp |
| Chip / card / hero radius | 12 / 16 / 24 dp |
| Feed thumbnail / carousel card | 56 square / 208 x 144 dp |

`ui/ViewComponents.kt` and `AuthTheme` hold the shared native styling. Discover retains its existing outdoor/high-contrast palette. Screen headers, forms, cards, chips and buttons use these foundations.

Bundled group photos, the logo and both mascots are copied from Flutter without image modifications. The launcher uses the SENECApp goat-and-chess logo on a black adaptive background; the installed app is named `SENECApp Kotlin` to distinguish the clients. `OrganizationImage` resolves bundled photos from normalized group names, with a colored gradient fallback. When adding Flutter artwork, copy it into `res/drawable-nodpi` and extend that mapping.

## Funciones conectadas en Kotlin

El backend y Flutter se mantienen sin cambios. Kotlin usa las rutas existentes y conserva la base de AWS configurada.

- Discover: búsqueda, categorías, próximos eventos, filtros por orden/intereses/edificios/oficiales, recomendaciones completas, nueva RSO y notificaciones.
- Datos reales: portadas remotas con respaldo local, colores, insignias oficiales, próximo evento, membresías y asistencia del semestre.
- Grupos: portada y botón fijo como Flutter, información completa, guardar, formulario de ingreso, salir, eventos del grupo y acceso para líderes.
- Líderes: crear eventos con sugerencias de horario/lugar del servidor, cambiar portada desde galería/cámara y resolver solicitudes de administradores.
- Eventos: búsqueda y fechas desde el encabezado, resumen semanal, All Events/My RSOs/Saved, marcadores por cuenta en el celular e itinerario sin cruces.
- Detalles de eventos: copiar información, abrir la RSO, advertencia de cruce con clases, estado/horario de check-in, lector QR y código para organizadores.
- Perfil: contadores reales, horario semanal editable, ubicación y contraste Auto/On/Off. El contraste manual se conserva en el celular.
- Navegación: historial por pestaña y conservación de búsquedas/desplazamiento. Volver de cámara, galería o lector QR conserva la pantalla actual.
- Inicio de sesión: botón Microsoft usando el proveedor de Firebase existente; conserva la validación universitaria del proyecto.

La fuente Nunito original tiene peso predeterminado 200. Se generaron versiones estáticas 400/600/700/800 desde ese mismo archivo para mostrar los pesos de Flutter también en Android 7, sin agregar lógica de fuentes en tiempo de ejecución.

## Límites y pendientes

- Hace falta probar con una cuenta real verificada de `@uniandes.edu.co` para validar de extremo a extremo acciones sobre AWS. Las herramientas de líder necesitan una cuenta administradora de una RSO aprobada.
- Microsoft requiere que ese proveedor esté habilitado en Firebase `senecapp`, con la configuración de Microsoft y la huella del APK de Kotlin. No se modificó la consola de Firebase ni se agregó ningún secreto al repositorio.
- No se agregaron notificaciones push ni recordatorios automáticos de salida en segundo plano. La bandeja dentro de la app sí usa el backend. No hay registro de dispositivos FCM ni nuevas alarmas/servicios.
- Editar intereses y las opciones adicionales Notifications/Privacy/Verification/Help muestran el aviso de “coming soon”, igual que Flutter. El horario y la ubicación sí se guardan.
- La sección de miembros muestra el total real. Los avatares con iniciales de muestra de Flutter no corresponden a una lista de miembros disponible en este contrato.
- Los selectores nativos de Android para fecha, hora, cámara y galería pueden verse diferentes. Colores, imágenes, tipografías, tarjetas y distribución general siguen la referencia Flutter.
- Los marcadores de eventos se guardan localmente por cuenta, como Flutter; no se sincronizan entre dispositivos.

## Verificación

- `:app:assembleDebug :app:assembleRelease :app:testDebugUnitTest`.
- 38 pruebas: sesiones y registro existentes, lectura de grupos/eventos/horario/notificaciones, carga de imágenes con el cliente existente, filtros, itinerario y cruces de clases.
- Revisión visual en emulador temporal con ejemplos sin cuenta. Se comprobó guardar/filtrar un evento y agregar una clase conservando las anteriores. La Activity y el manifiesto de prueba se retiran antes de la compilación final.
- Swagger público de AWS: las rutas de portadas, solicitudes, sugerencias, horario, notificaciones y QR están publicadas. Health de base de datos respondió `ok`.