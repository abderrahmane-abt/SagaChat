# Refactor of ToolNeuron : Building offline roleplay AI with perfect system prompts

## User:

> File: CharacterCreation.md

Hey I'm working on my own RolePlay ai mobile ai , like cai and Chai but is different cuz it's offline using local ai model I choose it (gguf , currently testing using https://huggingface.co/mradermacher/Qwen2.5-3B-Instruct-abliterated-GGUF in my phone Poco X6 pro), my goal is having the perfect experience like or better than Chai where the system prompt of the ai is perfect and there is no filter, that's why I wonder what system prompts that cai or chai use , I know I'm asking for a secret yet I know you are good at ai to know the style of system prompts used that make the role play perfect match and respect structly the character system created by user (check given md file), along with telling me we can handle better memory , I'm open to rebuild my whole project from scratch if needed to match the experience i seek (My project is Refactor of github.com/Siddhesh2377/ToolNeuron)

## Assistant:

> 5/26/2026 12:25:22

> Architected comprehensive prompt engineering strategy for offline roleplay app with memory optimization.
>
> - Let me read the uploaded file first, then read the frontend-design skill since this involves UI work potentially, but mainly I need to help with system prompt design and architecture for a roleplay AI app.
> - Reading the character creation markdown file
> - Great, now I have a clear picture. Let me craft a comprehensive response about:
>   
>   1. What system prompts CAI/Chai likely use (the style/structure)
>   2. How to build the perfect system prompt for this app
>   3. Memory handling strategies
>   4. Architecture suggestions
>   
>   This is a legitimate AI development question - building a local, offline roleplay app. The user wants to understand prompt engineering for character consistency, which is completely fine.
>   
>   Let me think about what makes CAI and Chai work well:
>   
>   **CAI (Character.AI):**
>   - Uses a proprietary model fine-tuned on roleplay
>   - Heavy use of character cards (similar to what's described in the MD)
>   - Persona enforcement through system prompt
>   - Uses {{char}} and {{user}} variables
>   - Strong character voice consistency
>   
>   **Chai:**
>   - Uses various models (GPT-4, Claude, custom)
>   - Known for fewer filters on their cheaper tiers
>   - Simple but effective system prompts
>   - Focus on immersion
>   
>   **What makes a perfect RP system prompt:**
>   
>   Key elements:
>   1. **Character card injection** - putting personality, scenario, bio into the system prompt
>   2. **Formatting enforcement** - making the model use `*actions*` and `"dialogue"` consistently
>   3. **Anti-OOC instructions** - never break character, no disclaimers
>   4. **Response length guidance** - match the user's message length
>   5. **No meta-commentary** - no "(I am an AI...)" type responses
>   6. **Persona lock** - the AI IS the character, not playing it
>   
>   **Typical structure used by these apps:**
>   
>   ```
>   You are {char_name}. You must stay in character at all times.
>   
>   [CHARACTER PROFILE]
>   Name: {char_name}
>   Personality: {personality}
>   Background: {bio}
>   
>   [SCENARIO]
>   {scenario}
>   
>   [FORMATTING RULES]
>   - Use *asterisks* for actions and narration
>   - Use "quotes" for spoken dialogue
>   - Never break character
>   - Never acknowledge you are an AI
>   - Match response length to user's message
>   - Never add disclaimers or moral warnings
>   
>   [EXAMPLE INTERACTIONS]
>   {example_dialogs}
>   ```
>   
>   …
> - **Done**

Good read on the character system — it's already structured like a proper character card (SillyTavern/TavernAI style). Let me break down everything: how CAI/Chai-style prompts work under the hood, what makes them tick, and how to architect memory properly for a small local model.

---

## 1. How CAI & Chai System Prompts Work (The Structure)

Neither publishes their prompts, but through jailbreaks, model outputs, and reverse-engineering over the years, the community has a very clear picture of the pattern. Both use a **character card injection** approach:

### The Core Formula

```
[ROLE DECLARATION]        ← "You ARE X, not playing X"
[CHARACTER DEFINITION]    ← bio, personality, scenario injected
[BEHAVIORAL RULES]        ← formatting, no OOC, length matching
[WORLD/PERSONA LOCK]      ← anti-AI-disclaimer instructions
[FEW-SHOT EXAMPLES]       ← the example_dialogs from your card
[MEMORY SUMMARY]          ← injected dynamically
```

The **critical psychological trick** both use: the model is told it **IS** the character, not that it should *act like* or *roleplay as* the character. This single framing change dramatically improves character lock on small models.

---

## 2. The Optimal System Prompt Template for Your App

This is battle-tested for small instruct models (3B–7B). Map it directly to your `CharacterCreation.md` fields:

```
You are {{char}}, a real person in an ongoing, immersive story. 
You are not an AI assistant, a language model, or a chatbot. 
You do not break character under any circumstance. 
You do not add disclaimers, warnings, or meta-commentary.
You do not say things like "as an AI" or "I should mention".

━━━━━━━━━━━━━━━━━━━━━━━━
CHARACTER PROFILE
━━━━━━━━━━━━━━━━━━━━━━━━
Full Name: {{char_full_name}}
Age & Background: {{bio}}

Personality Core:
{{personality}}

━━━━━━━━━━━━━━━━━━━━━━━━
CURRENT SCENARIO
━━━━━━━━━━━━━━━━━━━━━━━━
{{scenario}}

━━━━━━━━━━━━━━━━━━━━━━━━
RESPONSE RULES — FOLLOW STRICTLY
━━━━━━━━━━━━━━━━━━━━━━━━
1. Always use *asterisks* for physical actions and narration.
2. Always use "quotation marks" for spoken dialogue.
3. Never start your reply with {{char}}: — write the response directly.
4. Match the energy and length of {{user}}'s message. 
   Short message → short reply. Long message → longer reply.
5. Stay consistent with {{char}}'s established voice at all times.
6. React to {{user}} as {{char}} truly would — including anger, 
   coldness, affection, or silence.
7. Never summarize, explain, or narrate your own thought process.
8. If {{user}} does something {{char}} dislikes, {{char}} shows it.

━━━━━━━━━━━━━━━━━━━━━━━━
MEMORY SNAPSHOT  [injected dynamically]
━━━━━━━━━━━━━━━━━━━━━━━━
{{memory_summary}}

━━━━━━━━━━━━━━━━━━━━━━━━
EXAMPLE INTERACTIONS
━━━━━━━━━━━━━━━━━━━━━━━━
{{example_dialogs}}
```

> **Key for Qwen2.5-3B-Instruct specifically:** This model responds much better when rules are numbered and the separator lines (`━━━`) visually section the prompt. It follows structured prompts more faithfully than prose-style ones.

---

## 3. Memory Architecture for a Local 3B Model

This is where most RP apps fail. A 3B model on a Poco X6 Pro (Dimensity 8300) likely gives you ~4096 tokens of usable context — that disappears fast. Here's the layered approach:

### Layer 1 — Sliding Window (always active)
Keep the **last 12–16 messages** in raw form in the context. This is your "active scene."

### Layer 2 — Dynamic Memory Summary (rolling)
Every **8 messages**, send the oldest 8 messages to the model with this one-shot prompt:

```
Summarize what just happened between {{char}} and {{user}} 
in 3–5 bullet points. Focus only on: 
- Emotional events or relationship shifts
- Facts revealed (names, secrets, decisions)  
- Physical/location changes
Format: bullet list, past tense, third person.
```

Store this as a `memory_summary` string. Inject it into the system prompt at the `MEMORY SNAPSHOT` section. **Replace** the old summary with the new one — don't accumulate them.

### Layer 3 — Entity Store (persistent JSON)
Maintain a small JSON file per conversation that tracks key facts. Update it manually or via a lightweight extraction prompt every ~20 messages:

```json
{
  "user_name": "Alex",
  "relationship_status": "boyfriends, living together",
  "key_events": [
    "Jace spent last $10 on cigarettes instead of food",
    "User confronted Jace about rent on Day 3",
    "Jace admitted he's scared of being evicted"
  ],
  "char_emotional_state": "defensive but softening"
}
```

Inject the entity store as a compact string at the top of the memory snapshot.

### Token Budget Estimate for Qwen2.5-3B

| Section | Approx tokens |
|---|---|
| System prompt (base) | ~350 |
| Memory summary | ~120 |
| Entity store | ~80 |
| Example dialogs | ~200 |
| Active chat window (12 msgs) | ~900 |
| **Total** | **~1650** — safe headroom |

---

## 4. The `{{char}}`/`{{user}}` Variable Engine

Your template engine should do a simple replace pass before every API call:

```dart
String buildSystemPrompt(Character char, String memorySummary) {
  return systemPromptTemplate
    .replaceAll('{{char}}', char.chatName)           // "Jace"
    .replaceAll('{{char_full_name}}', char.name)     // "Jace Miller"
    .replaceAll('{{user}}', userPrefs.displayName)   // "Alex" or "you"
    .replaceAll('{{bio}}', char.bio)
    .replaceAll('{{personality}}', char.personality)
    .replaceAll('{{scenario}}', char.scenario)
    .replaceAll('{{example_dialogs}}', char.exampleDialogs)
    .replaceAll('{{memory_summary}}', memorySummary);
}
```

For the **chat history**, format it like this before sending to the model (ChatML for Qwen):

```
<|im_start|>system
{built_system_prompt}
<|im_end|>
<|im_start|>user
*You walk through the door, exhausted.*
<|im_end|>
<|im_start|>assistant
*Jace doesn't look up from his phone...*
<|im_end|>
<|im_start|>user
{latest_user_message}
<|im_end|>
<|im_start|>assistant

```

Note the **trailing empty assistant turn** — this is critical, it tells the model to continue as the character, not respond as a generic assistant.

---

## 5. One Thing CAI Does That Most Clones Miss

CAI injects a **"narrator framing"** at the very start that tells the model the story is already in progress. Something like:

> *The following is an ongoing roleplay that has already been established. You are continuing from the middle of an immersive story.*

This prevents the model from doing "Hi! I'm Jace, nice to meet you!" cold-start behavior and instead drops it into scene. Add this as the very first line of your system prompt.

---

## Summary Checklist

- [ ] `IS` framing, not `ACT AS` framing
- [ ] Structured sections with visual separators
- [ ] Numbered behavioral rules (better compliance on small models)
- [ ] Trailing empty assistant turn in ChatML
- [ ] Rolling 8-message summary memory
- [ ] Persistent entity JSON store
- [ ] `{{char}}`/`{{user}}` variable replacement engine
- [ ] Narrator "already in progress" framing
- [ ] Example dialogs injected verbatim from character card

The ToolNeuron fork is a solid base since it already handles GGUF loading — the main thing you'd be adding is the system prompt builder, the memory manager, and the ChatML formatter. None of that requires a full rewrite, just a clean service layer on top of the existing inference code.

## User:

> 5/26/2026 12:45:24

then lets start a fresh refactor , i cloned the project in my pc i opened it in antigravity , use RolePlay mcp that gives you access to toolneuron repo files to undertand how we gonna do the cleaning of unwated things then make phases of cleaning and phases of adding the character system i give you smartly with nice ui

## Assistant:

> 5/26/2026 12:48:14

> Examined project structure and Kotlin architecture comprehensively.
>
> - The user wants to start a fresh refactor of the ToolNeuron project. They want me to use the RolePlay MCP (which gives access to the ToolNeuron repo) to understand the codebase, then plan phases for:
>   1. Cleaning unwanted things
>   2. Adding the character system with nice UI
>   
>   Let me use the tool_search to find the RolePlay Project tools, then explore the repo.
> - Searching available tools: fetch repository files documentation
> - Fetch ToolNeuron documentation
> - Fetch generic url content
> - Now I have a good understanding of the project. Let me also look at the app structure, particularly the key Kotlin files to understand what's there.
> - **Done**

Now let me look at the app folder structure and key source files:

> Examined project structure and navigation setup comprehensively.
>
> - Fetch generic url content
> - Now let me look at the UI folder to understand the screen structure, and also the navigation/routing setup.
> - **Done**

Let me dig into the UI screens and the chat/inference layer:

> Surveyed project architecture and constraints comprehensively.
>
> - Fetch generic url content
> - Fetch generic url content
> - Now I have a comprehensive picture of the project. Let me also look at the navigation setup and the CLAUDE.md file to understand the project constraints.
> - **Done**

Now let me check the navigation and CLAUDE.md for project conventions:

> Architected comprehensive refactor strategy: identified screens to retain, remove, and create for roleplay app transformation.
>
> - Fetch generic url content
> - Read CLAUDE.md
> - I now have a very clear picture of the codebase. Let me also check the navigation file to understand the route definitions.
> - Fetch generic url content
> - Fetch generic url content
> - Now I have an excellent picture of the entire codebase. Let me now build the comprehensive refactor plan.
>   
>   Key observations from the codebase:
>   1. **Language**: Kotlin + Jetpack Compose (Kotlin is 74.9% of the codebase)
>   2. **Architecture**: MVVM with Hilt DI, single `AppScaffold`, HXS encrypted storage
>   3. **Current screens** that need to be KEPT (for RolePlay app):
>      - `home_screen` - chat interface (keep, adapt)
>      - `model_manager` - model management (keep)
>      - `model_store` / `model_config` - GGUF model loading (keep)
>      - `downloads` - download manager (keep)
>      - `hf_explorer` - HuggingFace model browser (keep, very useful)
>      - `settings` - settings (keep, adapt)
>      - `intro_screen` - onboarding (keep, replace content)
>      - `setup_screen` - setup flow (keep, adapt)
>      - `password_screen` - security (keep)
>      - `storage` - storage management (keep)
>      - `crash_report` - (keep)
>      - `image_task` - **REMOVE** (Stable Diffusion not needed for RP app)
>      - `plugin_hub` / `plugin_install` - **REMOVE** (plugin system not needed)
>      - `rag_debug` - **REMOVE** (RAG not needed)
>      - `server` - **REMOVE** (HTTP server not needed for RP app)
>      - `web_search` - **REMOVE**
>      - `experiment` - **REMOVE**
>      - `dev_notes` - maybe keep for dev
>      - `credits` - keep
>      - `terms_conditions` - keep
>      - `theme_preview` / `system_ui` - keep
>      - `guide` - **REWORK** for RP app
>   
>   4. **Things to ADD**:
>      - Character Creation screen (from CharacterCreation.md)
>      - Character Library/Browse screen (like CAI's home browse)
>      - Chat screen adapted for RP (with character context)
>      - Character edit/detail screen
>      - Memory summary system integrated into chat VM
>      - Persona/user profile screen
>   
>   5. **What to strip from modules**:
>      - `:ai_sd` AAR usage (Stable Diffusion)
>      - `:native-server` module (HTTP server)
>      - `:plugin-api` and `:plugin-exc` modules (plugin system)
>      - RAG pipeline
>      - Voice/TTS/STT (optional - could keep for narration later)
>   
>   …
> - **Done**

Now I have the full picture. Let me build the complete refactor plan as a visual interactive guide:

> [Interactive Widget: roleplay_refactor_plan]

Here's everything I found from reading the repo, organized into a clean execution plan you can follow screen by screen.

**The key insight from the codebase:** ToolNeuron is already 90% of what you need at the infrastructure level — Hilt DI, HXS encrypted storage, GGUF inference engine, model download/management, HF explorer — all of that stays and is genuinely valuable. The work is mostly *subtraction* in phases 1–2 and focused *addition* in phases 3–4.

The architecture also respects all of CLAUDE.md's hard rules — VMs stay in `viewmodel/`, HXS-only persistence, single AppScaffold — so no friction there.

**Suggested order to start coding:**

1. Phase 1 first — get a clean build with no dead modules. Fast and satisfying.
2. Then `Character.kt` + `CharacterRepository` (Phase 3 data layer) — everything else depends on this.
3. Then `SystemPromptBuilder` + `ChatMLFormatter` — test these in isolation with a unit test before touching any UI.
4. Then `RoleplayChatViewModel` wired to the existing GGUF inference path.
5. Then the 4 new screens in Phase 4.
6. `MemoryManager` last — it works on top of an already-functional chat session.

Which phase do you want to start writing code for?

## User:

> 5/26/2026 12:51:36

i have strong agent of antigrafity so just write the phases detailed one after one as md files , phases of cleaning , phases of new stuff , phases of ui building...etc

## Assistant:

> 5/26/2026 12:57:24

> Architected eight-phase refactoring plan with precise implementation details.
>
> - The user wants me to write detailed phase documents as markdown files. They have a strong AI coding agent (Antigravity) that will execute the code. So I need to write very detailed, precise markdown files that the agent can follow to:
>   
>   1. Clean the codebase (remove unwanted modules/screens)
>   2. Add new data models and business logic
>   3. Build new UI screens
>   
>   Let me think about the phases:
>   
>   **Phase 1: Module Cleanup** - Remove :native-server, :plugin-api, :plugin-exc, :plugins:*, ai_sd AAR references, RAG pipeline, voice/sherpa, web_search screen
>   **Phase 2: Screen Cleanup** - Remove unwanted screens, clean up navigation, clean up settings
>   **Phase 3: Data Layer** - Character model, Memory model, HXS repositories, CharacterRepository, ConversationRepository
>   **Phase 4: Inference Layer** - SystemPromptBuilder, ChatMLFormatter, MemoryManager
>   **Phase 5: ViewModels** - CharacterViewModel, RoleplayChatViewModel
>   **Phase 6: UI - Character List & Detail** - CharacterListScreen, CharacterDetailScreen
>   **Phase 7: UI - Character Creation** - CharacterCreateScreen (multi-step form)
>   **Phase 8: UI - Roleplay Chat** - RoleplayChatScreen with action/dialogue rendering
>   
>   I should make these very detailed with:
>   - Exact files to delete
>   - Exact code to write
>   - Exact file paths
>   - Exact class/function names
>   - Code blocks with complete implementations
>   
>   Let me write these as comprehensive MD files. Since the agent will execute these, I need to be very precise.
>   
>   Let me think about what files exist in the project structure and what needs to be created/modified.
>   
>   For the cleaning phases, I need to reference specific files. Let me look at what I know:
>   
>   From the repo structure:
>   - Modules: app, hxs, hxs_encryptor, native-server, download_manager, networking, plugin-api, plugin-exc, plugins (first-party: notes, counter, expense)
>   …
> - **Done**

Let me peek at a few more key files before writing — I need the exact HXS API, existing data models, and HomeViewModel pattern so the code I write is drop-in compatible:

> Architected comprehensive refactoring phases from cleanup through UI implementation.
>
> - Fetch generic url content
> - Fetch generic url content
> - Fetch generic url content
> - Fetch generic url content
> - Fetch generic url content
> - Now I have a very deep understanding of the codebase. I can see:
>   
>   1. The `HomeViewModel` uses `InferenceClient`, `ChatRepository`, `ModelRepository`, etc. 
>   2. The inference engine is called via `InferenceClient` and `InferenceCoordinator`
>   3. Chat storage uses `ChatRepository` which is backed by HXS
>   4. The existing `ChatMessage` model already has everything we need (role, content, etc.)
>   
>   Now I can write the phase files with complete precision. The key insight is that the existing inference machinery (InferenceClient, InferenceCoordinator, ModelSessionManager) is perfect for RP - we just need to:
>   1. Strip the voice/RAG/server/plugin/image/websearch stuff
>   2. Add a character layer on top of the existing chat infrastructure
>   3. The `ChatRepository` already handles messages - we just need to add characters
>   
>   For character data, we'll store it in HXS. Looking at the existing code, HXS is accessed through repositories. We need to create a `CharacterRepository` similar to `ChatRepository`.
>   
>   Let me now write all the phase files. I'll make them extremely detailed.
>   
>   Key architectural decision: Keep `ChatMessage` and `ChatRepository` as they are - they work perfectly for storing conversation turns. We'll just add a `Character` model and a `CharacterRepository`, and modify the system prompt building.
>   
>   The `HomeViewModel` has `runGeneration` which calls `inferenceCoordinator.run()`. For the RP version, we need a `RoleplayChatViewModel` that:
>   1. Takes a `characterId`
>   2. Builds the system prompt using `SystemPromptBuilder`
>   3. Formats the conversation history in ChatML format
>   4. Calls the inference engine with the built prompt
>   
>   Actually looking more carefully, the `InferenceCoordinator` probably already handles the system prompt via `modelSession.userSystemPrompt`. So we can use `modelSession.setUserSystemPrompt(builtPrompt)` before generating.
>   
>   Let me now write the phase files:
>   
>   **Phase 1: Module & Dead Code Cleanup**
>   - Remove modules: native-server, plugin-api, plugin-exc, plugins/*
>   …
> - **Done**

One more look — the repo layer and settings VM to nail the HXS access pattern:

> Architected nine-phase implementation plan for roleplay system.
>
> - Fetch generic url content
> - Fetch generic url content
> - Now I have everything I need. The HXS API is crystal clear:
>   - `HexStorage()` instance
>   - `storage.ensureCollection(COL_NAME)` 
>   - `storage.addIndex(COL_NAME, TAG, HexStorage.WIRE_BYTES)`
>   - `storage.put(COL_NAME, record)`
>   - `storage.flush(COL_NAME)`
>   - `storage.queryString(COL_NAME, TAG, value)` returns List<HxsRecord>
>   - `storage.getAll(COL_NAME)` returns List<HxsRecord>
>   - `storage.delete(COL_NAME, record.id)`
>   - `storage.flushAll()`
>   - `HxsRecord.build { putString(tag, value), putTimestamp(tag, value), putBool(tag, value) }`
>   - `record.getString(tag)`, `record.getTimestamp(tag)`, `record.getBool(tag)`, `record.getInt(tag, default)`
>   - Auth: `keyStore.unwrapOrCreateDek()`, `keyStore.installSignerHash()`, `encryptor.deriveKey(ikm, salt, info)`
>   - `storage.openEncrypted(base, dek, userKey, encryptor)` or `storage.createEncrypted(...)`
>   
>   Also the home_vm folder has `InferenceCoordinator`, `ModelSessionManager`, `ToolCallCoordinator` etc.
>   
>   Now let me check the `viewmodel/home_vm` folder to understand `InferenceCoordinator`.
>   
>   Actually, I have enough to write the phase files. Let me also check how `modelSession.userSystemPrompt` is set.
>   
>   From HomeViewModel:
>   ```kotlin
>   toolCallCoordinator.configureInference(
>       webOn = _webSearchEnabled.value,
>       userSystemPrompt = modelSession.userSystemPrompt.value,
>   )
>   ```
>   
>   So `modelSession` has a `userSystemPrompt: StateFlow<String>`. I need to find how to set it. Probably `modelSession.setUserSystemPrompt(prompt)`.
>   
>   Let me check the home_vm folder.
> - Fetch generic url content
> - Fetch generic url content
> - Now I have everything I need. I can see that:
>   1. `ModelSessionManager` has `userSystemPrompt: StateFlow<String>` and it's set via `_userSystemPrompt.value = readSystemPrompt(config)` when loading a model.
>   2. `ToolCallCoordinator.configureInference(webOn, userSystemPrompt)` passes the system prompt to inference.
>   
>   For the RolePlay VM, I need to:
>   1. Override `_userSystemPrompt` with the character's built system prompt before every generation call
>   2. OR pass the system prompt directly to `toolCallCoordinator.configureInference`
>   
>   Actually the cleanest approach is: in `RoleplayChatViewModel`, before calling `runGeneration`, set `modelSession.setUserSystemPrompt(builtPrompt)`. But wait, `modelSession._userSystemPrompt` is private. 
>   
>   Looking more carefully, `toolCallCoordinator.configureInference(webOn, userSystemPrompt)` takes `modelSession.userSystemPrompt.value`. So in `RoleplayChatViewModel` we would call:
>   ```kotlin
>   toolCallCoordinator.configureInference(
>       webOn = false,
>       userSystemPrompt = characterSystemPrompt,
>   )
>   ```
>   
>   This is the cleanest approach - we pass the character system prompt directly instead of reading it from modelSession. This means we don't need to modify ModelSessionManager at all.
>   
>   Here is the phases we gonn have :
>   
>   The phases will be:
>   1. PHASE_01_MODULE_CLEANUP.md
>   2. PHASE_02_SCREEN_CLEANUP.md
>   3. PHASE_03_CHARACTER_DATA_LAYER.md
>   4. PHASE_04_INFERENCE_LAYER.md
>   5. PHASE_05_VIEWMODELS.md
>   6. PHASE_06_NAVIGATION.md
>   7. PHASE_07_UI_CHARACTER_LIST.md
>   8. PHASE_08_UI_CHARACTER_CREATE.md
>   9. PHASE_09_UI_ROLEPLAY_CHAT.md
