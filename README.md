# ClauCode Phone

Prototipo Android nativo que orquesta un gateway local **OmniRoute** y una sesión de
**Claude Code** sin depender de Termux como aplicación separada.

## Flujo implementado

1. Al abrir la app se inicia un servicio foreground y el ejecutable `omniroute`.
2. La app detecta la primera URL que el gateway imprime y ofrece abrirla en el navegador.
3. El asistente guía la creación del combo `cc/claude-opus-5` y solicita la API key.
4. La key queda cifrada mediante Android Keystore (AES-GCM).
5. Claude Code se inicia con `ANTHROPIC_BASE_URL=http://127.0.0.1:8080/v1`,
   `ANTHROPIC_AUTH_TOKEN`, `ANTHROPIC_MODEL=cc/claude-opus-5` y `--model`.
6. El servicio mantiene ambos procesos vivos al cambiar de aplicación y muestra una
   notificación persistente exigida por Android.

## Estado del runtime

Este repositorio contiene el **launcher/orquestador**, no binarios de terceros. Para una
APK funcional, el empaquetado o instalador debe colocar ejecutables Android ARM64 y sus
dependencias en el almacenamiento privado de la aplicación:

```text
files/usr/bin/omniroute
files/usr/bin/claude
```

Ambos archivos deben tener permiso de ejecución. No se incluyen artefactos de Termux,
Node.js, Claude Code u OmniRoute para evitar publicar binarios sin fijar versión, licencia,
arquitectura o cadena de suministro. El siguiente paso recomendado es generar un rootfs
ARM64 reproducible en CI, validar hashes y extraerlo en el primer arranque. Ejecutar un CLI
interactivo plenamente compatible también requiere reemplazar las tuberías actuales por
un PTY nativo (por ejemplo, una integración acotada de `termux-app`/`terminal-emulator`).

## Compilación

Requisitos: JDK 17+, Android SDK 35 y aceptación de sus licencias.

```bash
gradle :app:assembleDebug
```

> `usesCleartextTraffic` está habilitado únicamente porque el gateway escucha en loopback
> por HTTP. La API key no se registra ni se escribe como texto plano.
