# AI Friend for BYD DiLink

Android/Kotlin prototype for a full-screen AI companion running on a BYD DiLink head unit.

## UI concept

![AI Friend UI concept](docs/ui-concept.png)

## Product concept

- Adam (آدم) is a warm, concise Egyptian Arabic car companion.
- Full-width 3D-style balloon face reacts to listening and speaking; the lower screen has one large listen button and a persistent speaker-labelled caption.
- Arabic voice recognition and speech output run locally with Sherpa-ONNX; no Google services are required.
- Speech is transcribed after the utterance ends, then the response is synthesized locally.
- Common vehicle commands and installed-app launching are routed locally. Less predictable vehicle phrasing uses OpenRouter function calls; the app validates and executes those calls through its own controller.
- General questions use OpenRouter and require an API key entered from the in-app settings plus internet access. Voice recognition and speech output remain on-device.

## Architecture

```text
MainActivity
  └── MainViewModel
       ├── VoiceManager
       │    ├── SherpaAsrEngine (Moonshine Arabic)
       │    └── SherpaTtsEngine (Piper Arabic)
       ├── AssistantEngine
       │    ├── CommandRouter (offline rules + app launcher)
       │    ├── OpenRouterLlm (structured intent tools)
       │    └── BydVehicleController → autoservice helper
       └── Compose UI
            └── AiFace Canvas animation
```

## Current milestone

The controller uses the project's DiLink `autoservice` bridge and the current FID catalog. FID maps can vary by vehicle and firmware; unsupported or unverified writes must fail clearly rather than report success. Opening windows, sunroof, unlocking doors, or opening the front trunk is speed-gated and blocked if speed cannot be read.

## Build

Open the project in Android Studio and build the `app` module. The project compiles against API 36 and requires API 32 or newer.

## Voice flow

`Tap listen → AudioRecord → Sherpa-ONNX Moonshine Arabic → final transcript → Adam's command router → local car/app action or OpenRouter tool/answer → Sherpa-ONNX Piper Arabic`.

App names are resolved against installed launcher apps. General questions and flexible phrasing require an OpenRouter API key, configured with the gear button; these requests are sent to OpenRouter. Explicit car controls are blocked while the car service is disconnected, and live car readings report when telemetry is unavailable.

## BYD integration note

BYDMate documents that vehicle automation is performed through the vehicle's system service (`autoservice`) and that different BYD models/firmwares expose different parameter catalogs. This project does not copy BYDMate source code; it only defines an independent abstraction (`VehicleController`) so the integration can be implemented after collecting the exact catalog from the user's Song Plus head unit.

Reference: https://github.com/AndyShaman/BYDMate

## License

This prototype is original code written for this project. No BYDMate source files are included. Review BYDMate's own license before reusing any of its source code.
