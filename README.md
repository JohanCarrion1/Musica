# ONDA — Reproductor de música local

App Android nativa (Kotlin + Jetpack Compose) que reproduce la música guardada en el teléfono, con reproducción en segundo plano y notificación con controles.

## Funciones

- Lee la biblioteca del dispositivo con `MediaStore`
- Lista de canciones con carátulas (Coil) y buscador por título, artista o álbum
- Controles: play/pausa, anterior/siguiente, aleatorio, repetir y volumen
- Reproducción en segundo plano con Media3 (ExoPlayer + MediaSession)

## Requisitos

- Android Studio (versión reciente)
- JDK 17
- Dispositivo o emulador con Android 8.0+ (minSdk 26)

## Cómo compilar

1. Abre esta carpeta en Android Studio (**File → Open**).
2. Espera el Gradle Sync.
3. **Build → Build APK(s)**. El APK queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Estructura

```
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/gradle-wrapper.properties
└── app/
    ├── build.gradle.kts
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/com/onda/music/
        │   ├── MainActivity.kt      # UI (Compose), permisos y lectura de canciones
        │   └── PlaybackService.kt   # Servicio de reproducción y notificación
        └── res/
            ├── drawable/ic_launcher_foreground.xml   # ícono de la app
            ├── mipmap-anydpi-v26/                    # ícono adaptativo
            └── values/                               # colores y tema
```

## Stack

AGP 8.5.2 · Kotlin 2.0.20 · Gradle 8.7 · Compose BOM 2024.09.03 · Media3 1.4.1 · Coil 2.6.0
