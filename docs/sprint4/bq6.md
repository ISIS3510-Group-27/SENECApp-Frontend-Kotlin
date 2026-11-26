# BQ6 · Type 3

**Which entry point produces more joins: personalized recommendations, or search and filters?**

El número de inscripciones muestra qué entrada aporta más miembros. La tasa de inscripciones por vista añade contexto cuando una entrada recibe más tráfico.

## De la app al dashboard

| Paso | Código para mostrar |
|---|---|
| Atribuir el origen | En `CampusHomeScreen.kt`, Profile y Compare Recommended Groups navegan con `recommendation` y el `requestId` disponible. Discover pasa `search` si los resultados tienen texto o filtros activos. El catálogo sin filtros usa `explore`. |
| Registrar la vista | `CampusHomeViewModel.openGroup()` y `CampusRepository.group()` envían `entry_point` y, si existe, `rec_request_id` al GET de detalle. En el backend, `get_group_detail()` escribe `group_viewed` para un grupo aprobado. |
| Registrar la inscripción | `CampusHomeViewModel.join()` conserva el origen y el ID de recomendación de esa visita. `CampusRepository.join()` también envía el `join_attempt_id` existente. El backend `join_group()` guarda el origen en la membresía y escribe `group_joined` al confirmarla. Repetir el Join de una membresía activa devuelve la existente. |
| Consultar BQ6 | `bq6_entry_points()`, en `app/analytics/questions.py` del backend, cuenta vistas e inscripciones en `analytics_events` por origen dentro del periodo. La tasa es `joins / profile_views`. Si hay cero vistas, la tasa queda sin valor. Es una proporción de conteos agregados y puede incluir vistas repetidas. |

## Cambios de Sprint 4

Se añadieron pruebas Kotlin para comprobar que la inscripción desde Discover conserva `search` y que un filtro activo, aun sin texto, se atribuye a búsqueda. La integración usa las rutas que ya están desplegadas en AWS.

La consulta BQ6 desplegada devuelve totales agregados aunque se seleccione Kotlin o Flutter en el dashboard. El refresco del detalle después de una inscripción puede sumar una vista a `direct`, una fila separada de las dos entradas que compara BQ6.

## Evidencia

En el Xiaomi **25078RA3EL** se instaló el APK debug y se abrieron detalles reales desde una recomendación de Profile, desde Compare Recommended Groups y desde la búsqueda `Tennis` de Discover. Las capturas muestran los puntos de entrada, no las filas del servidor desplegado.

- [Compare Recommended Groups en el Xiaomi](bq6-evidence/xiaomi-compare.png)
- [Discover Search en el Xiaomi](bq6-evidence/xiaomi-search.png)

Pasaron el build debug y **233 pruebas unitarias Kotlin**. Las pruebas instrumentadas de almacenamiento y caché de detalles también habían pasado en el Xiaomi durante la verificación de la pantalla.

### Dashboard desplegado

La captura de BQ6 compartida el 26 de noviembre de 2026 muestra estos resultados agregados.

| Entrada | Vistas | Inscripciones | Inscripciones por vista |
|---|---:|---:|---:|
| Recommendation | 2.856 | 219 | 7,67 % |
| Search | 902 | 31 | 3,44 % |

Recomendaciones obtuvo **188 inscripciones más** (7,06 veces el volumen) y una tasa **4,23 puntos porcentuales** mayor. La captura no muestra el período seleccionado. Los totales agregados no permiten vincular las aperturas del Xiaomi con filas individuales.

## Demostración para el Viva Voce

1. Abre Profile, pide recomendaciones y abre el detalle de un grupo o entra por Compare Recommended Groups. Muestra la ruta con `recommendation` y el ID devuelto por el backend.
2. En Discover, busca un grupo (o activa un filtro con la búsqueda vacía) y abre el resultado. Muestra el estado `searchFiltered` y la ruta con `search`.
3. Con una cuenta dedicada en el entorno de prueba, selecciona dos grupos a los que aún no pertenezca. Abre el primero desde recomendaciones y el segundo desde Discover, y confirma **Join RSO** en cada detalle.
4. En `/dashboard`, con acceso administrador configurado mediante `ADMIN_EMAILS`, elige el período, pulsa **Load** y expande **Data** en BQ6. Compara el conteo de inscripciones y la tasa. El resultado agregado también está disponible en `GET /api/v1/analytics/bq/6?days=30`.
