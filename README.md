# SagaChat

On-device AI character roleplay for Android. No cloud, no API keys, no telemetry. Characters, chat logs, and cryptographic keys stay fully localized on your device.

The objective isn't to clone ChatGPT, but to provide a secure, private, and customizable interface to run offline LLMs via GGUF, optimized specifically for character-based roleplay.

### Credits & Attribution

SagaChat is a specialized, roleplay-focused fork of the [ToolNeuron](https://github.com/Siddhesh2377/ToolNeuron) project, originally created by [Siddhesh Sonar](https://github.com/Siddhesh2377).

- **ToolNeuron**: The underlying architecture, native C++ networking, HexStorage (encrypted vault), and Jetpack Compose foundations are heavily derived from ToolNeuron.
- **SagaChat** strips out the server execution, plugins, VLM (Vision), TTS/STT (Voice), and image generation pipelines from ToolNeuron to achieve a lean, highly-focused, text-only offline roleplaying environment.
- [ToolNeuron Repository](https://github.com/Siddhesh2377/ToolNeuron) | [ToolNeuron Discord](https://discord.gg/mVPwHDhrAP)

## What it does

- **Local Inference**: Chat against any compatible [GGUF](https://huggingface.co/models?other=gguf) model stored on your device. Supports streaming output, multi-turn context, and per-turn metrics.
- **Character Roleplay**: Create, edit, and manage rich character profiles containing bios, personality traits, scenario configurations, and custom initial greetings.
- **Rich Chat Interface**: Custom markdown rendering engine that distinguishes between *character actions* (italicized/muted) and "dialogue" (highlighted), giving it a true roleplay aesthetic.
- **Dynamic Memory**: Includes a background `MemoryManager` that summarizes rolling contexts every few turns to keep the LLM focused without exceeding context limits.
- **HuggingFace Store**: A built-in model manager that lets you discover, download, and manage GGUF models directly from HuggingFace without leaving the app.

## Architecture

- **`com.moorixlabs.sagachat`**: The core application module holding the Compose UI, Hilt dependency injection graph, and ViewModels.
- **`:hxs`**: The HexStorage encrypted KV store with a native C++ core, used for securely storing character configurations and chat logs.
- **`:hxs_encryptor`**: Argon2id / AEAD / BoringSSL cryptographic layer securing the HexStorage vaults.
- **`:download_manager`**: Native background downloader with JNI bridge for pulling models from HuggingFace.
- **`:networking`**: Native C++ network primitives handling API requests.

## Security

Trust decisions live in C++ so an obfuscation bypass at the Kotlin layer doesn't grant access.

- App-level password lock via Argon2id (t=4, m=128 MiB, p=1).
- DEK is wrapped by an Android Keystore AES-256-GCM key (StrongBox-preferred, TEE fallback).
- TOFU manifest of every `.so` in `nativeLibraryDir` catches inline hooks.
- Debugger / Frida / Xposed scans run before any auth path.
- `FLAG_SECURE` is enforced on the password screen.

## Build

```sh
# Dev loop
./gradlew :app:compileDebugKotlin
./gradlew :app:installDebug

# Release (R8 + resource shrink)
./gradlew :app:assembleRelease
```

Release signing reads from `local.properties`:

```properties
TN_KEYSTORE_PATH=/abs/path/to/keystore.jks
TN_KEYSTORE_PASSWORD=...
TN_KEY_ALIAS=...
TN_KEY_PASSWORD=...
```

Missing keys fall back to an unsigned release so the dev flow stays open.

## License

[MIT](LICENSE).
