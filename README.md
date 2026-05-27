# SagaChat: Your Private, Offline AI Roleplay Companion

## The Future of AI Roleplay is Here, and It's Yours.

SagaChat redefines the AI character roleplay experience by putting you in complete control. Unlike cloud-dependent platforms like Character.AI and Chai, SagaChat is a **100% offline, local-first application** designed for Android. This means unparalleled privacy, freedom from censorship, and a truly personalized experience, all running directly on your device.

## Why SagaChat Stands Apart

In the evolving landscape of AI companions, users often face compromises: subscription fees, restrictive content filters, and reliance on internet connectivity. SagaChat eliminates these barriers, offering a robust and secure alternative for dedicated roleplayers and AI enthusiasts.

Recent community feedback highlights significant frustrations with existing platforms:

*   **Chai App** users report issues with regional paywalls, the free tier becoming unusable, and a general decline in quality and stability, leading to drastically lowered app ratings.
*   **Character.AI** users consistently express frustration over strict content filters that 
lobotomize the AI, leading to repetitive, condescending, or even inappropriate responses that are wildly out of character.

### Key Differentiators:

| Feature             | SagaChat                                     | Character.AI                                     | Chai App                                        |
| :------------------ | :------------------------------------------- | :----------------------------------------------- | :---------------------------------------------- |
| **Cost**            | **Totally Free**                             | Freemium (Paid for premium features)             | Freemium (Paid for premium features)            |
| **Content Filters** | **No Filters** (with uncensored models)      | Strict content filters, often frustrating     | Minimal (17+), allows NSFW                  |
| **Connectivity**    | **Offline & Local**                          | Online, cloud-dependent                          | Online, cloud-dependent                         |
| **Privacy**         | **Nothing Leaves Your Device**               | Data collection for improvement                  | Data collection for personalization             |
| **Limits**          | **No Limits**                                | Waiting rooms, slower responses, daily limits    | Limited messages on free tier, regional paywalls|
| **Model Choice**    | **Your Model** (any GGUF)                    | Proprietary models                               | Proprietary models                              |

## Uncompromised Freedom and Privacy

SagaChat is built on the principle that your conversations are yours alone. With **no cloud dependency, no data collection, and no external servers**, your interactions remain entirely private and secure on your Android device. This commitment to privacy extends to content, allowing for **uncensored roleplay** when paired with appropriate models, a stark contrast to the strict filters often encountered on other platforms.

## Powerful Features for Immersive Roleplay

SagaChat is more than just an offline chatbot; it's a sophisticated roleplay engine designed for depth and consistency.

### Character System

Craft detailed characters with comprehensive profiles, including:

*   **Bio:** Background, history, and physical description.
*   **Personality:** Core traits, speaking style, and motivations.
*   **Scenario:** The setting and opening context for your story.
*   **Initial Message:** The character's first line, automatically rendered.
*   **Example Dialogs:** Few-shot examples to guide the model's voice and tone.

All fields support `{{char}}` and `{{user}}` template variables for dynamic interaction.

### System Prompt Engine

SagaChat intelligently constructs model-optimized system prompts from character cards, ensuring consistent character lock and context. It utilizes:

*   Sectioned headers for clear boundaries between profile, rules, and memory.
*   **IS framing** (`You are X`) for improved character adherence.
*   Numbered behavioral rules for reliable instruction following.
*   A narrator framing header to prevent cold-start greetings.
*   Correct ChatML formatting with a trailing empty assistant turn.

### Rolling Memory

Overcome the limitations of small model context windows with SagaChat's two-layer memory system:

*   **Sliding Window:** The last 16 messages are always kept verbatim.
*   **Summarization Pass:** Older messages are summarized and injected as a *Memory Snapshot*, maintaining narrative continuity.
*   **Entity Store:** Tracks key information like user name, relationship status, character emotional state, and recent events.

### Chat Rendering

Enjoy a readable and immersive chat experience with roleplay-specific formatting:

*   `*text in asterisks*` renders as italic, muted (physical actions, narration).
*   `"text in quotes"` renders at normal weight (spoken dialogue).

### HuggingFace Model Browser

Seamlessly discover, search, and download any GGUF model directly to your device from HuggingFace, with a robust background download manager.

### Security

Your data is protected with HexStorage (HXS), an encrypted key-value store featuring:

*   App-level PIN lock via Argon2id.
*   Data encryption key (DEK) wrapped by Android Keystore AES-256-GCM.
*   TOFU integrity manifest of native libraries.
*   Debugger, Frida, and Xposed detection at startup.
*   `FLAG_SECURE` on the password screen to prevent screenshotting.

## Supported Models

SagaChat is compatible with any GGUF file, optimized for **instruct-tuned** models, especially those using ChatML format. Models with abliterated refusal behaviors provide the best experience for unrestricted roleplay.

Tested on a Dimensity 8300 (Poco X6 Pro) with:

*   Qwen2.5-3B-Instruct-abliterated (~2 GB)
*   Qwen2.5-7B-Instruct-abliterated (~4–5 GB)
*   Any ChatML-format 3B–7B GGUF

## Architecture

SagaChat is a focused fork of [ToolNeuron](https://github.com/Siddhesh2377/ToolNeuron), leveraging its native inference engine, HexStorage, download manager, and Jetpack Compose foundation. SagaChat enhances this foundation with its unique character card system, system prompt engine, memory architecture, ChatML formatter, and roleplay-specific UI.

## Building

```sh
# Dev loop
./gradlew :app:compileDebugKotlin
./gradlew :app:installDebug

# Release
./gradlew :app:assembleRelease
```

Release signing reads from `local.properties`.

Minimum SDK: 26. Target SDK: 35. Language: Kotlin. UI: Jetpack Compose + Material 3.

## License

[MIT](LICENSE)

