# Resumen de creación e implementación

## Creación del proyecto

El proyecto base se creó en Android Studio siguiendo este flujo:

```text
Android Studio
    ↓
New Project
    ↓
Empty Activity
    ↓
Language: Kotlin
    ↓
Proyecto: SENECApp
    ↓
Implementar únicamente las pantallas necesarias
```

## Implementación

El diseño de Figma se adaptó a Android Views con Kotlin. Se implementaron las pantallas **Discover**, **Events**, **My RSOs**, **Profile**, detalle de organización, notificaciones y creación de una RSO.

La aplicación incluye navegación inferior, búsqueda, filtros por categoría, favoritos, membresías y formulario de propuesta. Los datos son locales y se utilizaron la paleta definida, las fuentes Bricolage Grotesque y Nunito, los iconos vectoriales y las imágenes suministradas.

## Estructura

```text
SENECApp_Kotlin/
├── app/
│   ├── build.gradle.kts
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── java/com/uniandes/senecapp_kotlin/
│       │   │   ├── MainActivity.kt
│       │   │   └── SenecData.kt
│       │   └── res/
│       │       ├── drawable/          (11 iconos y recursos XML)
│       │       ├── drawable-nodpi/    (12 imágenes)
│       │       ├── font/              (2 tipografías)
│       │       ├── layout/            (activity_main.xml)
│       │       ├── mipmap-*/          (iconos de lanzamiento)
│       │       ├── values/            (colores, textos y tema)
│       │       ├── values-night/      (tema oscuro)
│       │       └── xml/               (reglas de respaldo)
│       ├── test/                      (prueba unitaria base)
│       └── androidTest/               (prueba instrumentada base)
├── gradle/                            (catálogo y wrapper)
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew / gradlew.bat
├── README.md
└── SUMMARY.md
```

En total hay **63 archivos relevantes**, sin contar archivos generados en `build/`, `.gradle/` o configuraciones locales de `.idea/`.
