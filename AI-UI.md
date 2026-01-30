# ArtificialUI System — Implementation Plan

## Overview

A JSON-driven UI system where LLMs generate app interfaces that render as native Compose components. Think Gradio, but offline-first, for Android, with your existing ToolNeuron infrastructure.

---

## Part 1: Architecture Decisions

### 1.1 Why This is Hard (And How We'll Solve It)

**Problem:** Jetpack Compose doesn't support "dynamic" composables — you can't just `eval()` UI code at runtime like web frameworks.

**Solution:** Pre-define a finite component library. JSON references component **types** by name, and a renderer maps them to actual Composables. This is the Gradio model.

```
JSON → Parser → Sealed Class Tree → Renderer → Compose UI
```

**Performance Concern:** Parsing JSON and building UI on every recomposition would be catastrophic.

**Solution:**
- Parse JSON **once** → Store as immutable `ArtificialUI` data class
- Renderer reads from stable data class
- Use `key()` for stable identity
- State lives **outside** the UI tree (in ViewModel)

---

### 1.2 File Structure

```
ui/artificialui/
├── ArtificialUIActivity.kt          # Host activity
├── ArtificialUIViewModel.kt         # State management
├── schema/
│   ├── ArtificialUISchema.kt        # Data classes (JSON mapping)
│   ├── ArtificialUIParser.kt        # JSON → Schema
│   └── ArtificialUIValidator.kt     # Schema validation
├── renderer/
│   ├── ArtificialUIRenderer.kt      # Schema → Compose (main dispatcher)
│   ├── components/                   # Individual component renderers
│   │   ├── InputComponents.kt
│   │   ├── DisplayComponents.kt
│   │   ├── ActionComponents.kt
│   │   ├── LayoutComponents.kt
│   │   └── AIComponents.kt          # Special AI-integrated components
│   └── ComponentRegistry.kt         # Type name → Renderer mapping
├── runtime/
│   ├── ArtificialUIState.kt         # Runtime state container
│   ├── ActionExecutor.kt            # Handle button clicks, form submits
│   └── AIBridge.kt                  # Connect to your LLM/Sarvam
└── export/
    ├── ArtificialUIExporter.kt      # Export as .neuron-app
    └── ArtificialUIImporter.kt      # Import community apps
```

---

## Part 2: JSON Schema Design

### 2.1 Design Principles

1. **Flat over Nested** — LLMs produce fewer errors
2. **Explicit IDs** — Every component has unique ID for state binding
3. **Enum-based Types** — Closed set of component types
4. **Actions are Separate** — Components reference actions by ID, not inline
5. **No Logic in JSON** — Conditionals/loops handled by renderer, not schema

### 2.2 Top-Level Structure

```
{
  "meta": { ... },           // App metadata
  "theme": { ... },          // Optional theming
  "state": { ... },          // Initial state values
  "components": [ ... ],     // Flat list of all components
  "actions": { ... },        // Named action definitions
  "layout": { ... }          // How components are arranged
}
```

### 2.3 Meta Block

```
meta:
  - id: string (unique app identifier)
  - name: string (display name)
  - version: string (semver)
  - description: string
  - author: string (optional)
  - icon: string (optional, base64 or asset name)
  - created_at: timestamp
  - requires_ai: boolean (does this app need LLM?)
```

### 2.4 Component Types (The Library)

#### Input Components
| Type | Description | Properties |
|------|-------------|------------|
| `text_input` | Single-line text | id, label, hint, default, max_length |
| `text_area` | Multi-line text | id, label, hint, default, max_lines |
| `number_input` | Numeric input | id, label, min, max, step, default |
| `slider` | Range slider | id, label, min, max, step, default |
| `dropdown` | Select one | id, label, options[], default |
| `checkbox` | Boolean toggle | id, label, default |
| `radio_group` | Select one (visible) | id, label, options[], default |
| `file_picker` | File selection | id, label, accept_types[], multiple |
| `image_input` | Image upload/capture | id, label, source (gallery/camera/both) |

#### Display Components
| Type | Description | Properties |
|------|-------------|------------|
| `text` | Static/dynamic text | id, content, style (h1/h2/body/caption) |
| `markdown` | Rendered markdown | id, content |
| `image` | Display image | id, source (url/base64/state_ref), fit |
| `code_block` | Syntax highlighted | id, content, language |
| `divider` | Horizontal line | id |
| `spacer` | Empty space | id, height |
| `card` | Elevated container | id, children[] |
| `progress` | Loading indicator | id, type (linear/circular), value (null=indeterminate) |

#### Action Components
| Type | Description | Properties |
|------|-------------|------------|
| `button` | Clickable button | id, text, style (primary/secondary/outline), action_id, icon |
| `icon_button` | Icon only | id, icon, action_id |
| `ai_button` | Send to AI (special) | id, text, input_refs[], system_prompt, stream |
| `submit_group` | Multiple actions | id, buttons[] |

#### Layout Components
| Type | Description | Properties |
|------|-------------|------------|
| `row` | Horizontal layout | id, children[], spacing, alignment |
| `column` | Vertical layout | id, children[], spacing, alignment |
| `grid` | Grid layout | id, children[], columns |
| `tabs` | Tabbed sections | id, tabs[{label, children[]}] |
| `accordion` | Collapsible sections | id, sections[{label, children[], expanded}] |
| `scroll_area` | Scrollable region | id, children[], max_height |

#### AI-Specific Components
| Type | Description | Properties |
|------|-------------|------------|
| `ai_chat` | Chat interface | id, system_prompt, placeholder |
| `ai_output` | LLM response display | id, format (text/markdown/code) |
| `ai_status` | Generation status | id |
| `model_selector` | Pick loaded model | id, label |

### 2.5 State References

Components can reference state values using `{{state_id}}` syntax:

```
"content": "Hello, {{user_name}}!"
"visible": "{{show_advanced}}"
"disabled": "{{is_loading}}"
```

This is evaluated at render time, NOT in JSON parsing.

### 2.6 Actions Schema

```
actions:
  action_id:
    type: enum (ai_call | set_state | navigate | export | custom)
    
  ai_call:
    - model: string (optional, use loaded model if not specified)
    - prompt_template: string (with {{refs}})
    - input_refs: string[] (component IDs to read values from)
    - output_ref: string (component ID to write result to)
    - stream: boolean
    - system_prompt: string (optional)
    
  set_state:
    - target: string (state key)
    - value: any | "{{component_id}}" (read from component)
    
  navigate:
    - target: string (screen_id or "back")
    
  export:
    - format: enum (text | json | image | file)
    - content_ref: string
    
  custom:
    - handler: string (registered custom handler name)
    - params: object
```

### 2.7 Layout Block

Defines spatial arrangement. For your 5x2/3 card portrait layout:

```
layout:
  type: "portrait_card"
  header:
    height: "compact" | "standard" | number
    children: string[] (component IDs)
  body:
    scroll: boolean
    children: string[] (component IDs)  
  footer:
    type: "action_bar"
    children: string[] (component IDs)
```

---

## Part 3: Parser Design

### 3.1 Parsing Strategy

**Step 1: Raw Parse**
- Use `kotlinx.serialization` with `JsonObject` for flexibility
- Don't deserialize to final types yet

**Step 2: Validate**
- Check required fields exist
- Validate component types are known
- Validate action references exist
- Validate state references are valid

**Step 3: Build Immutable Tree**
- Convert to sealed class hierarchy
- Resolve all references to direct object references (no string lookups at render time)
- Pre-compute any derived data

**Step 4: Freeze**
- Result is fully immutable `ArtificialUIApp` data class
- Can be cached, compared by reference

### 3.2 Error Handling

Parser should return `Result<ArtificialUIApp, List<ParseError>>`:

```
ParseError:
  - path: string (JSON path to error, e.g., "components[3].action_id")
  - code: enum (UNKNOWN_TYPE, MISSING_FIELD, INVALID_REF, TYPE_MISMATCH, ...)
  - message: string (human readable)
  - suggestion: string (optional, for LLM to fix)
```

On error, show errors in UI AND offer "Ask AI to fix" button that sends errors back to LLM.

---

## Part 4: Renderer Design

### 4.1 Core Renderer Pattern

```
ArtificialUIRenderer:
  - Takes: ArtificialUIApp (immutable)
  - Takes: ArtificialUIState (mutable state holder)
  - Emits: Compose UI

For each component:
  1. Look up renderer in ComponentRegistry by type
  2. Read current values from state
  3. Call component renderer with (spec, state, onAction)
  4. Renderer emits Compose UI
```

### 4.2 Component Registry

Map of `type string → @Composable renderer function`

```
registry:
  "text_input" → TextInputRenderer
  "button" → ButtonRenderer
  "ai_chat" → AIChatRenderer
  ...
```

Benefits:
- Easy to add new components
- Easy to swap implementations
- Can be extended by plugins (future)

### 4.3 State Management

**ArtificialUIState** holds:
- All component values (keyed by component ID)
- Computed/derived values
- Loading states
- Error states

**Rules:**
- State is external to composables (lives in ViewModel)
- Components receive `value` and `onValueChange` callbacks
- State updates trigger recomposition only for affected components

### 4.4 Rendering Optimizations

1. **Memoize Component Specs** — Component specs don't change, wrap in `remember`

2. **Key by ID** — Every component rendered with `key(component.id)`

3. **Lazy Rendering for Lists** — Any list > 10 items uses `LazyColumn`

4. **Derived State** — Use `derivedStateOf` for computed values

5. **Stable Lambdas** — Action callbacks are remembered, not recreated

6. **Skip Hidden Components** — Components with `visible: false` emit nothing (no `if` wrapper)

7. **Structural Sharing** — When JSON updates, diff against previous, only rebuild changed subtrees

---

## Part 5: AI Integration

### 5.1 AI Bridge

Connects UI actions to your existing LLM infrastructure:

```
AIBridge:
  - generateResponse(prompt, systemPrompt, stream) → Flow<String>
  - Uses: Your existing GGUFEngine OR Sarvam API
  - Handles: Streaming, cancellation, error recovery
```

### 5.2 The `ai_button` Component

Special component that:
1. Collects values from referenced input components
2. Builds prompt from template
3. Triggers AI generation
4. Streams result to output component
5. Shows loading state

This is the "Send to AI" button from Gradio.

### 5.3 The `ai_chat` Component

Full chat interface:
- Message history (stored in state)
- Input field + send button
- Streaming responses
- Uses your existing chat UI components (reuse!)

### 5.4 Sarvam API Integration

For BYOK/cloud option:
- User provides API key (stored encrypted in your vault)
- AIBridge can route to Sarvam when:
    - No local model loaded
    - User explicitly selects cloud
    - App requires specific model not available locally

---

## Part 6: Activity Structure

### 6.1 ArtificialUIActivity Layout

```
┌─────────────────────────────────────┐
│ HEADER                              │
│ ┌─────┐ App Name        [▶][⚙][✕] │
│ └─────┘                             │
├─────────────────────────────────────┤
│                                     │
│ BODY (Scrollable Canvas)            │
│                                     │
│   ┌─────────────────────────────┐   │
│   │                             │   │
│   │   Rendered Components       │   │
│   │                             │   │
│   │                             │   │
│   └─────────────────────────────┘   │
│                                     │
├─────────────────────────────────────┤
│ FOOTER (Action Bar)                 │
│ ┌─────────────────────────────────┐ │
│ │ [Input Field          ] [Send] │ │
│ │ [Style] [Settings] [Export]    │ │
│ └─────────────────────────────────┘ │
└─────────────────────────────────────┘
```

### 6.2 Header Components

- **Back/Close** — Exit to app list
- **App Icon + Name** — From meta
- **Play/Compile** — Toggle between edit mode (see JSON) and run mode
- **Settings** — App-specific settings (AI model selection, etc.)
- **Share** — Export as .neuron-app

### 6.3 Body Canvas

- Renders the `layout.body.children` components
- Scrollable via `LazyColumn` or `Column` with `verticalScroll`
- Respects the card-style portrait layout (your 5x2/3 grid)
- Padding and spacing from theme

### 6.4 Footer Action Bar

Two modes:

**Standard Mode:**
- Shows components from `layout.footer.children`
- Typically: primary action button, secondary actions

**AI Chat Mode (when `ai_chat` is present):**
- Text input field
- Style selector (if app supports styles)
- Send button
- Expands to full keyboard height

---

## Part 7: Edit/Build Mode

### 7.1 Two Modes

**Run Mode (Default):**
- UI is interactive
- Actions execute
- Normal user experience

**Edit Mode (Developer):**
- See raw JSON alongside rendered preview
- Edit JSON directly
- "Refresh" button to re-parse
- Validation errors shown inline
- "Ask AI to fix" for errors

### 7.2 Edit Mode Layout

```
┌─────────────────────────────────────┐
│ [JSON] [Preview] [Split]      [▶]  │
├─────────────────────────────────────┤
│ {                  │ ┌───────────┐ │
│   "meta": {...},   │ │           │ │
│   "components":    │ │  Live     │ │
│   [...]            │ │  Preview  │ │
│ }                  │ │           │ │
│                    │ └───────────┘ │
├─────────────────────────────────────┤
│ ⚠ Error on line 34: unknown type   │
│ [Ask AI to Fix]                     │
└─────────────────────────────────────┘
```

---

## Part 8: Optimization Deep-Dive

### 8.1 Parsing Optimization

| Technique | Impact |
|-----------|--------|
| Parse once, cache result | Eliminates repeated parsing |
| Use `JsonReader` streaming for large JSON | Lower memory for big apps |
| Pre-validate schema with JSON Schema | Fast rejection of invalid input |
| Intern strings (component IDs, types) | Reduce allocations |

### 8.2 Rendering Optimization

| Technique | Impact |
|-----------|--------|
| `@Immutable` on all schema classes | Compose skips recomposition |
| `key(id)` on every component | Stable identity in lists |
| `remember` for spec objects | No reallocation |
| `derivedStateOf` for computed values | Minimal recomposition |
| `LazyColumn` for scrollable areas | Only render visible items |
| Separate `RecomposeScope` per component | Isolate state changes |

### 8.3 State Optimization

| Technique | Impact |
|-----------|--------|
| Use `SnapshotStateMap` for state | Granular observation |
| State reads inside component, not parent | Narrow recomposition scope |
| Debounce text input state updates | Reduce state churn |
| Batch related state updates | Single recomposition |

### 8.4 AI Streaming Optimization

| Technique | Impact |
|-----------|--------|
| Append tokens to existing string (not recreate) | O(1) vs O(n) per token |
| Throttle UI updates (every 50ms, not every token) | Smooth scrolling |
| Use `TextLayoutCache` | Faster text measurement |

---

## Part 9: Example App JSON

### "Quick Summarizer" App

```json
{
  "meta": {
    "id": "quick-summarizer-001",
    "name": "Quick Summarizer",
    "version": "1.0.0",
    "description": "Paste text, get a summary",
    "requires_ai": true
  },
  "state": {
    "input_text": "",
    "summary": "",
    "is_loading": false
  },
  "components": [
    {
      "id": "title",
      "type": "text",
      "content": "📝 Quick Summarizer",
      "style": "h1"
    },
    {
      "id": "input",
      "type": "text_area",
      "label": "Paste your text",
      "hint": "Enter text to summarize...",
      "state_ref": "input_text",
      "max_lines": 10
    },
    {
      "id": "summarize_btn",
      "type": "ai_button",
      "text": "Summarize",
      "style": "primary",
      "input_refs": ["input"],
      "output_ref": "output",
      "system_prompt": "You are a concise summarizer. Provide a 2-3 sentence summary.",
      "prompt_template": "Summarize the following text:\n\n{{input}}",
      "stream": true
    },
    {
      "id": "output",
      "type": "ai_output",
      "format": "markdown",
      "state_ref": "summary"
    }
  ],
  "layout": {
    "type": "portrait_card",
    "body": {
      "scroll": true,
      "children": ["title", "input", "output"]
    },
    "footer": {
      "children": ["summarize_btn"]
    }
  }
}
```

---

## Part 10: Implementation Order

### Phase 1: Foundation
1. Schema data classes (`ArtificialUISchema.kt`)
2. JSON parser with validation (`ArtificialUIParser.kt`)
3. Basic component registry (5 components: text, text_input, button, column, row)
4. Simple renderer that outputs to screen
5. Basic activity shell

### Phase 2: Core Components
1. All input components
2. All display components
3. Layout components (grid, tabs)
4. State management system
5. Action executor (non-AI actions)

### Phase 3: AI Integration
1. `ai_button` component
2. `ai_output` component
3. AIBridge with your GGUFEngine
4. Sarvam API integration (BYOK)
5. Streaming support

### Phase 4: Polish
1. Edit mode with JSON editor
2. Error display and "Ask AI to fix"
3. Export/import .neuron-app
4. Performance optimization pass
5. Community sharing hooks

---

## Part 11: Open Questions for You

1. **State persistence** — Should app state survive activity restart? (Probably yes → save to DataStore)

2. **Theming** — Allow apps to define colors? Or inherit from ToolNeuron theme?

3. **Permissions** — If an app needs camera (image_input), how do you handle permission flow?

4. **Sandboxing** — How much can a community app do? Can it access RAGs? Vault?

5. **Monetization** — BYOK for Sarvam, but what about a "premium components" tier?

6. **Offline-first** — Can apps be flagged as "requires internet" vs "fully offline"?

---

This plan gives you a solid foundation. The key insight is: **don't try to be Turing-complete**. A fixed component library that covers 90% of use cases is better than an infinitely flexible system that's slow and buggy.

Want me to elaborate on any section?