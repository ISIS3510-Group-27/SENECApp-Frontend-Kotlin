# SENECApp

Aplicación Android nativa en Kotlin para descubrir organizaciones estudiantiles de la Universidad de los Andes, consultar eventos y administrar membresías.

## Requisitos

- Android Studio con el SDK de Android 37 instalado.
- Un emulador o dispositivo con Android 7.0 (API 24) o superior.

## Compilación

Desde Android Studio, abrir el proyecto, esperar la sincronización de Gradle y ejecutar **Run**.

Desde PowerShell:

```powershell
.\gradlew.bat :app:assembleDebug
```

El APK se genera en:

```text
app/build/outputs/apk/debug/app-debug.apk
```

Para instalarlo en un dispositivo conectado:

```powershell
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

## Tecnologías

- Kotlin
- Android Views y Material Components
- Gradle
- Recursos locales para imágenes, iconos y tipografías
