# Compare Recommended Groups

Desde **Profile → Compare recommended groups**, selecciona dos organizaciones distintas. La pantalla compara nombre, imagen, categoría, número de miembros, descripción y motivos enviados por el backend. La selección sigue disponible al regresar del detalle si ambas organizaciones siguen en el resultado.

| Estrategia | Código para mostrar en la sustentación |
|---|---|
| Concurrencia | `GroupRecommendationsViewModel.loadForComparison()` reutiliza una carga en curso. `loadProfileRecommendations()` inicia la consulta y la lectura local con `async` dentro de `supervisorScope`. `GroupRecommendationsRepository.load()` y las operaciones del almacén usan `Dispatchers.IO`. |
| Almacenamiento local | `ProfileRecommendationsStore.save()` y `read()` guardan y recuperan un resultado de recomendaciones por UID en ObjectBox. El snapshot expira a las 24 horas. |
| Caché de imágenes | `ComparedGroup()` llama `OrganizationImage()`. Su `AsyncImage` comparte el loader de Coil y las cachés de memoria y disco. Una foto aún no disponible puede tener el respaldo visual de la aplicación. |
| Conectividad eventual | `loadProfileRecommendations()` prioriza la red y usa ObjectBox ante un fallo de conectividad. `CompareRecommendedGroupsRoute()` escucha `networkStatus()` para recargar al reconectar. La pantalla muestra el aviso, ofrece `Retry` y permite seguir comparando mientras deshabilita la apertura del detalle. |
| Atribución | `CompareRecommendedGroupsRoute()` pasa el ID del resultado vigente a `CampusHomeScreen.group()` con `entry_point = recommendation`. `CampusHomeViewModel.openGroup()` y `join()` conservan la consulta del detalle y el seguimiento existente de inscripciones. |

Para reproducir en Flutter, usar el mismo resultado de recomendaciones y el mismo flujo por ID. Conservar dos selecciones locales, permitir la lectura de un snapshot vigente offline, y pasar la atribución del resultado online al abrir el detalle.

El loader compartido se encarga de las solicitudes y de las dos cachés de imágenes. [Documentación de Coil](https://coil-kt.github.io/coil/image_loaders/).

## Verificación en el teléfono

1. Abrir Profile, entrar en comparación y escoger dos recomendaciones distintas.
2. Verificar los campos de ambas organizaciones y abrir un detalle. Volver a la comparación para comprobar que conserva la selección.
3. Desactivar la red, verificar el aviso y que se pueda seguir comparando. Pulsar `Retry` para comprobar la copia en ObjectBox.
4. Restablecer la red y comprobar que el resultado se actualiza y se habilita el detalle.

La verificación se ejecutó en un Xiaomi 25078RA3EL físico con la sesión existente y recomendaciones reales. Pasaron el build debug, 231 pruebas unitarias y siete casos instrumentados de almacenamiento y caché de detalles. Por ADB se comprobaron las dos selecciones, el detalle, el regreso al comparador, `Retry` offline, los controles deshabilitados y la actualización automática y manual al reconectar. La prueba de detalle terminó antes de confirmar una inscripción.
