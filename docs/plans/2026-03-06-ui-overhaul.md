# UI Overhaul — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Overhaul ToolNeuron's UI codebase: replace all Material icons with TnIcons, adopt M3 Expressive fully, standardize animations, replace rDp/rSp with fixed dp + WindowSizeClass, reorganize files into feature packages, extract shared patterns, remove persona UI, delete dead code.

**Architecture:** Feature-based package structure (`screen/home/`, `screen/model_store/`, etc.). Shared components in `components/`. Theme uses `MaterialExpressiveTheme` with `MotionScheme.expressive()`. All icons from `TnIcons` (tabler.io + Lucide). Fixed dp spacing via expanded `Standards` object. Single-line comments only.

**Tech Stack:** Kotlin, Jetpack Compose, Material3 Expressive 1.5.0-alpha15, Compose BOM 2026.02.01, WindowSizeClass

---

### Task 1: Clean Up Dependencies (build.gradle.kts + libs.versions.toml)

Remove material-icons-extended, let BOM manage compose-runtime and compose-ui-text versions.

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

**Step 1: Update libs.versions.toml**

Remove these version pins (they should be managed by BOM):
- Line 11: Delete `androidx-compose-runtime = "1.10.3"`
- Line 12: Delete `androidx-compose-ui-text = "1.10.3"`
- Line 16: Delete `androidx-material-icons-extended = "1.7.8"`

Remove these library entries:
- Line 46: Delete `androidx-compose-runtime` library entry
- Line 47: Delete `androidx-compose-ui-text` library entry
- Line 59: Delete `androidx-material-icons-extended` library entry

Update `compose-runtime` and `compose-ui-text` to BOM-managed (no version):
```toml
androidx-compose-runtime = { group = "androidx.compose.runtime", name = "runtime" }
androidx-compose-ui-text = { group = "androidx.compose.ui", name = "ui-text" }
```

**Step 2: Update app/build.gradle.kts**

Remove material-icons-extended dependency (line ~144):
```kotlin
// DELETE this line:
implementation(libs.androidx.material.icons.extended)
```

**Step 3: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin`

Expected: Build WILL FAIL because 35+ files still import Material icons. This is expected — we fix them in Tasks 2-3.

**Step 4: Commit**

```
chore: remove material-icons-extended dependency, let BOM manage compose versions
```

---

### Task 2: Expand TnIcons with All Missing Icons

Add ~50 new icons to TnIcons.kt to cover every Material icon and XML drawable used in the codebase. Use tabler.io SVG paths for all. For the 2-3 icons without a tabler equivalent, use Lucide SVG paths.

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/icons/TnIcons.kt`

**Step 1: Add a `lucide()` helper function**

After the existing `tabler()` function, add:
```kotlin
private fun lucide(vararg paths: String): ImageVector {
    val builder = ImageVector.Builder(
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    )
    paths.forEach { pathData ->
        builder.addPath(
            pathData = PathParser().parsePathString(pathData).toNodes(),
            fill = null,
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        )
    }
    return builder.build()
}
```

**Step 2: Add all missing icons**

Look up each icon's SVG path from https://tabler.io/icons (primary) or https://lucide.dev/icons (fallback). Add each as a `lazy` val inside the `TnIcons` object.

Required new icons (Material name → TnIcons name → source):

```
// Navigation & Arrows
ChevronLeft         → ChevronLeft        (tabler: chevron-left)
ArrowBack           → ArrowBack          (use existing ArrowLeft)
ArrowForward        → ArrowForward       (use existing ArrowRight)
ArrowUpward         → ArrowUp            (tabler: arrow-up)
SubdirectoryArrowLeft → CornerDownLeft   (tabler: corner-down-left)
ArrowOutward        → ExternalLink       (tabler: external-link)
ExpandLess          → ChevronUp          (already exists!)
ExpandMore          → ChevronDown        (already exists!)
KeyboardArrowDown   → ChevronDown        (already exists!)

// Actions
Add                 → Plus               (already exists!)
Close               → X                  (already exists!)
Delete              → Trash              (already exists!)
DeleteSweep         → TrashX             (tabler: trash-x)
DeleteForever       → TrashX             (reuse TrashX)
Clear               → XCircle            (tabler: circle-x)
Save                → DeviceFloppy       (tabler: device-floppy)
Stop                → PlayerStop         (already exists!)
Send                → Send               (tabler: send)

// Files & Data
FileOpen            → FileSearch         (tabler: file-search)
Description         → FileText           (tabler: file-text)
InsertDriveFile     → File               (tabler: file)
Folder              → Folder             (tabler: folder)
FolderOpen          → FolderOpen         (tabler: folder-open)
UploadFile          → FileUpload         (tabler: file-upload)
GridOn              → LayoutGrid         (tabler: layout-grid)

// Status & Info
Error               → AlertTriangle      (tabler: alert-triangle)
Warning             → AlertTriangle      (reuse AlertTriangle)
CheckCircle         → CircleCheck        (already exists!)
Pending             → Clock              (tabler: clock)
Info                → InfoCircle         (already exists!)

// Content
ContentCopy         → Copy               (already exists!)
Visibility          → Eye                (already exists!)
VisibilityOff       → EyeOff             (already exists!)
Language            → World              (already exists!)
Link                → Link               (tabler: link)

// Media & Images
Image               → Photo              (already exists!)

// People
Person              → User               (already exists!)
PersonAdd           → UserPlus           (tabler: user-plus)

// Hardware & System
Memory              → Cpu                (tabler: cpu)
Storage             → Database           (tabler: database)
Speed               → Gauge              (tabler: gauge)
Psychology          → Brain              (already exists!)
ModelTraining       → BrainCircuit       (lucide: brain-circuit)
Build               → Tool               (tabler: tool)
Tune                → Adjustments        (already exists!)

// Layout & UI
Menu                → Menu               (already exists!)
ExpandMore/Less     → ChevronDown/Up     (already exist!)
SearchOff           → SearchOff          (tabler: search-off)

// Backup & Restore
Backup              → CloudUpload        (tabler: cloud-upload)
Restore             → CloudDownload      (tabler: cloud-download)
CloudDownload       → CloudDownload      (reuse)

// Communication
Chat                → MessageCircle      (tabler: message-circle)
ChatBubbleOutline   → Message            (tabler: message)

// Misc
Refresh             → Refresh            (already exists!)
Lock                → Lock               (already exists!)
LockOpen            → LockOpen           (already exists!)
Star                → Star               (already exists!)
Settings            → Settings           (already exists!)
Terminal            → Terminal            (tabler: terminal-2)
FilterAlt           → Filter             (already exists!)
Layers              → Stack2             (tabler: stack-2)
CleaningServices    → Eraser             (tabler: eraser)
PlayArrow           → PlayerPlay         (already exists!)
Pause               → PlayerPause        (already exists!)
VolumeUp            → Volume             (tabler: volume)
SwapHoriz           → ArrowsExchange     (tabler: arrows-exchange)
Label               → Tag                (tabler: tag)
AccessTime          → Clock              (reuse Clock)
Schedule            → CalendarTime       (tabler: calendar-time)
Edit                → Edit               (already exists!)

// XML drawable replacements
Tool (drawable)     → Wrench             (tabler: tool)
Thinking (drawable) → BulbFilled         (tabler: bulb-filled)
Chats (drawable)    → Messages           (tabler: messages)
Tokens (drawable)   → Coins              (tabler: coins)
Prompt (drawable)   → Prompt             (tabler: message-2)
Generated (drawable)→ Wand               (tabler: wand)
MemoryVault         → ShieldLock         (already exists!)
SmartTempMessage    → Sparkles           (already exists!)
VlModels            → Photo              (already exists!)
AiModel (drawable)  → Brain              (already exists!)
LoadModel (drawable)→ Upload             (already exists!)
ErrorDrawable       → AlertTriangle      (reuse)
```

For each new icon, fetch the SVG path from tabler.io and add:
```kotlin
val IconName by lazy { tabler("M path data here") }
```

**Step 3: Verify TnIcons.kt compiles**

Run: `./gradlew :app:compileDebugKotlin` (will still fail on Material icon imports, but TnIcons.kt itself should compile)

**Step 4: Commit**

```
feat: expand TnIcons with 30+ new tabler/lucide icons for full Material icon replacement
```

---

### Task 3: Replace All Material Icon References with TnIcons

Mass replacement across 35+ files. Replace every `Icons.Default.X`, `Icons.Outlined.X`, `Icons.Filled.X`, `Icons.AutoMirrored.Filled.X` with the corresponding `TnIcons.Y`.

**Files:**
- Modify: All 35+ files listed in the icon audit (see design doc Section 1)

**Step 1: Create the icon mapping reference**

Use this mapping for find-and-replace:

```
Icons.Default.Add              → TnIcons.Plus
Icons.Default.AccessTime       → TnIcons.Clock
Icons.Default.ArrowOutward     → TnIcons.ExternalLink
Icons.Default.Book             → TnIcons.Books
Icons.Default.Build            → TnIcons.Wrench
Icons.Default.Check            → TnIcons.Check
Icons.Default.CheckCircle      → TnIcons.CircleCheck
Icons.Default.ChevronLeft      → TnIcons.ChevronLeft (add if missing)
Icons.Default.ChevronRight     → TnIcons.ChevronRight
Icons.Default.Clear            → TnIcons.XCircle
Icons.Default.Close            → TnIcons.X
Icons.Default.CloudDownload    → TnIcons.CloudDownload
Icons.Default.ContentCopy      → TnIcons.Copy
Icons.Default.Delete           → TnIcons.Trash
Icons.Default.DeleteSweep      → TnIcons.TrashX
Icons.Default.Description      → TnIcons.FileText
Icons.Default.Download         → TnIcons.Download
Icons.Default.Edit             → TnIcons.Edit
Icons.Default.Error            → TnIcons.AlertTriangle
Icons.Default.ExpandLess       → TnIcons.ChevronUp
Icons.Default.ExpandMore       → TnIcons.ChevronDown
Icons.Default.FileOpen         → TnIcons.FileSearch
Icons.Default.GridOn           → TnIcons.LayoutGrid
Icons.Default.Image            → TnIcons.Photo
Icons.Default.Info             → TnIcons.InfoCircle
Icons.Default.KeyboardArrowDown → TnIcons.ChevronDown
Icons.Default.Language         → TnIcons.World
Icons.Default.Link             → TnIcons.Link
Icons.Default.Lock             → TnIcons.Lock
Icons.Default.Memory           → TnIcons.Cpu
Icons.Default.Menu             → TnIcons.Menu
Icons.Default.ModelTraining    → TnIcons.BrainCircuit
Icons.Default.Pending          → TnIcons.Clock
Icons.Default.Person           → TnIcons.User
Icons.Default.PersonAdd        → TnIcons.UserPlus
Icons.Default.Psychology       → TnIcons.Brain
Icons.Default.Refresh          → TnIcons.Refresh
Icons.Default.Save             → TnIcons.DeviceFloppy
Icons.Default.Search           → TnIcons.Search
Icons.Default.SearchOff        → TnIcons.SearchOff
Icons.Default.Settings         → TnIcons.Settings
Icons.Default.Share            → TnIcons.Share
Icons.Default.Speed            → TnIcons.Gauge
Icons.Default.Stop             → TnIcons.PlayerStop
Icons.Default.Storage          → TnIcons.Database
Icons.Default.SubdirectoryArrowLeft → TnIcons.CornerDownLeft
Icons.Default.SwapHoriz        → TnIcons.ArrowsExchange
Icons.Default.TextFields       → TnIcons.Code
Icons.Default.Tune             → TnIcons.Adjustments
Icons.Default.UploadFile       → TnIcons.FileUpload
Icons.Default.Visibility       → TnIcons.Eye
Icons.Default.VisibilityOff    → TnIcons.EyeOff
Icons.Default.Warning          → TnIcons.AlertTriangle

Icons.Outlined.ArrowBack       → TnIcons.ArrowLeft
Icons.Outlined.Backup          → TnIcons.CloudUpload
Icons.Outlined.ChatBubbleOutline → TnIcons.Message
Icons.Outlined.Check           → TnIcons.Check
Icons.Outlined.ChevronRight    → TnIcons.ChevronRight
Icons.Outlined.CleaningServices → TnIcons.Eraser
Icons.Outlined.Close           → TnIcons.X
Icons.Outlined.Delete          → TnIcons.Trash
Icons.Outlined.DeleteForever   → TnIcons.TrashX
Icons.Outlined.Download        → TnIcons.Download
Icons.Outlined.ExpandLess      → TnIcons.ChevronUp
Icons.Outlined.ExpandMore      → TnIcons.ChevronDown
Icons.Outlined.FilterAlt       → TnIcons.Filter
Icons.Outlined.Folder          → TnIcons.Folder
Icons.Outlined.Home            → TnIcons.Home
Icons.Outlined.Info            → TnIcons.InfoCircle
Icons.Outlined.Language        → TnIcons.World
Icons.Outlined.Layers          → TnIcons.Stack2
Icons.Outlined.Memory          → TnIcons.Cpu
Icons.Outlined.Pause           → TnIcons.PlayerPause
Icons.Outlined.PlayArrow       → TnIcons.PlayerPlay
Icons.Outlined.Refresh         → TnIcons.Refresh
Icons.Outlined.Restore         → TnIcons.CloudDownload
Icons.Outlined.Settings        → TnIcons.Settings
Icons.Outlined.Terminal        → TnIcons.Terminal
Icons.Outlined.Tune            → TnIcons.Adjustments

Icons.Filled.Add               → TnIcons.Plus
Icons.Filled.CheckCircle       → TnIcons.CircleCheck
Icons.Filled.Close             → TnIcons.X
Icons.Filled.Delete            → TnIcons.Trash
Icons.Filled.Stop              → TnIcons.PlayerStop

Icons.AutoMirrored.Filled.ArrowBack    → TnIcons.ArrowLeft
Icons.AutoMirrored.Filled.ArrowForward → TnIcons.ArrowRight
Icons.AutoMirrored.Filled.Chat         → TnIcons.MessageCircle
Icons.AutoMirrored.Filled.InsertDriveFile → TnIcons.File
Icons.AutoMirrored.Filled.Label        → TnIcons.Tag
Icons.AutoMirrored.Filled.MenuBook     → TnIcons.Books
Icons.AutoMirrored.Filled.VolumeUp     → TnIcons.Volume
Icons.AutoMirrored.Outlined.ArrowBack  → TnIcons.ArrowLeft
```

**Step 2: Replace icons file by file**

For each file:
1. Replace all `Icons.XXX.YYY` with the corresponding `TnIcons.ZZZ`
2. Remove Material icon imports (`import androidx.compose.material.icons.*`)
3. Add `import com.dark.tool_neuron.ui.icons.TnIcons` if not present

**Step 3: Replace XML drawable references**

In files using `R.drawable.*`, replace with TnIcons:
- `painterResource(R.drawable.tool)` → `ImageVector` usage: `Icon(TnIcons.Wrench, ...)`
- `painterResource(R.drawable.thinking)` → `Icon(TnIcons.BulbFilled, ...)`
- `painterResource(R.drawable.user)` → `Icon(TnIcons.User, ...)`
- `painterResource(R.drawable.copy)` → `Icon(TnIcons.Copy, ...)`
- `painterResource(R.drawable.volume)` → `Icon(TnIcons.Volume, ...)`
- `painterResource(R.drawable.speed)` → `Icon(TnIcons.Gauge, ...)`
- `painterResource(R.drawable.chats)` → `Icon(TnIcons.Messages, ...)`
- `painterResource(R.drawable.error)` → `Icon(TnIcons.AlertTriangle, ...)`
- `R.drawable.smart_temp_message` → `TnIcons.Sparkles`
- `R.drawable.vl_models` → `TnIcons.Photo`
- `R.drawable.ai_model` → `TnIcons.Brain`
- `R.drawable.load_model` → `TnIcons.Upload`
- `R.drawable.memory_vault` → `TnIcons.ShieldLock`
- `R.drawable.tokens` → `TnIcons.Coins`
- `R.drawable.prompt` → `TnIcons.Prompt`
- `R.drawable.generated` → `TnIcons.Wand`

Note: Some composables accept `Int` (resource ID) icons. These signatures must change to accept `ImageVector` instead, or use the ImageVector overload.

**Step 4: Delete unused XML drawables**

Delete from `app/src/main/res/drawable/`:
- tool.xml, thinking.xml, user.xml, copy.xml, volume.xml, speed.xml, chats.xml, error.xml, smart_temp_message.xml, vl_models.xml, ai_model.xml, load_model.xml, memory_vault.xml, tokens.xml, prompt.xml, generated.xml

Keep any drawables still referenced by Android system (notification icons, launcher icons).

**Step 5: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 6: Commit**

```
feat: replace all Material icons and XML drawables with TnIcons
```

---

### Task 4: Create Motion.kt and Standardize Animations

Create the animation token system using M3 Expressive's `MotionScheme`.

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/ui/theme/Motion.kt`
- Modify: All files using inline animation specs (~40 files)

**Step 1: Create Motion.kt**

```kotlin
@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.dark.tool_neuron.ui.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.ui.Alignment

// Delegates to MotionScheme.expressive() tokens where possible.
// Use these instead of inline spring()/tween() calls.
object Motion {

    // Interactive press/toggle feedback — snappy, slight bounce
    fun <T> interactive(): AnimationSpec<T> = spring(
        dampingRatio = 0.7f,
        stiffness = 500f
    )

    // Content appear/disappear, expand/collapse
    fun <T> content(): AnimationSpec<T> = spring(
        dampingRatio = 0.9f,
        stiffness = Spring.StiffnessMedium
    )

    // State changes — color, alpha, size tweens
    fun <T> state(): AnimationSpec<T> = tween(
        durationMillis = 200,
        easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    )

    // Page/modal entrance — iOS-style curve
    fun <T> entrance(): AnimationSpec<T> = tween(
        durationMillis = 350,
        easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    )

    // Exit — faster than entrance
    fun <T> exit(): AnimationSpec<T> = tween(
        durationMillis = 200,
        easing = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)
    )

    // Standard enter transition for AnimatedVisibility
    val Enter: EnterTransition = fadeIn(tween(300)) +
        expandVertically(tween(300), expandFrom = Alignment.Top)

    // Standard exit transition for AnimatedVisibility
    val Exit: ExitTransition = fadeOut(tween(200)) +
        shrinkVertically(tween(200), shrinkTowards = Alignment.Top)
}
```

**Step 2: Replace inline animation specs across the codebase**

For each file with animation code:
- Replace `spring(dampingRatio = 0.Xf, stiffness = Yf)` with `Motion.interactive()` (for presses/toggles) or `Motion.content()` (for expand/collapse)
- Replace `tween(150)` / `tween(200)` with `Motion.state()` (for color/alpha changes)
- Replace `tween(300)` / `tween(400)` entrance animations with `Motion.entrance()`
- Replace `tween(200)` exit animations with `Motion.exit()`
- Replace inline `AnimatedVisibility` enter/exit specs with `Motion.Enter` / `Motion.Exit`
- Ensure all `animate*AsState` calls have a `label` parameter

**Step 3: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 4: Commit**

```
feat: create Motion.kt animation tokens and standardize all animations
```

---

### Task 5: Switch to MaterialExpressiveTheme

Update the theme wrapper and adopt Expressive components.

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/theme/Theme.kt`
- Modify: Files using `CircularProgressIndicator` (replace with `LoadingIndicator` where appropriate)

**Step 1: Update NeuroVerseTheme in Theme.kt**

Replace `MaterialTheme(...)` with `MaterialExpressiveTheme(...)`:
```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NeuroVerseTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme) dynamicDarkColorScheme(context)
        else dynamicLightColorScheme(context)

    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        motionScheme = MotionScheme.expressive(),
        typography = Typography,
        content = content
    )
}
```

**Step 2: Replace CircularProgressIndicator with LoadingIndicator**

In files showing loading states (model loading, generation, etc.), replace:
```kotlin
// Old
CircularProgressIndicator(modifier = Modifier.size(24.dp))

// New
LoadingIndicator(modifier = Modifier.size(24.dp))
```

Use `ContainedLoadingIndicator` for contained/boxed loading states.

**Step 3: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 4: Commit**

```
feat: switch to MaterialExpressiveTheme with MotionScheme.expressive()
```

---

### Task 6: Create ParticleProgress Component

Replace PixelProgressBar with a standard LinearProgressIndicator + particle celebration effect.

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/ui/components/ParticleProgress.kt`
- Delete: `app/src/main/java/com/dark/tool_neuron/ui/components/PixelProgress.kt`
- Modify: All files referencing `PixelProgressBar`

**Step 1: Create ParticleProgress.kt**

```kotlin
package com.dark.tool_neuron.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.sin
import kotlin.random.Random

// Particle data class for the celebration effect
private data class Particle(
    var x: Float,
    var y: Float,
    var velocityX: Float,
    var velocityY: Float,
    var alpha: Float,
    var radius: Float,
    var life: Float = 1f,
    var decay: Float = Random.nextFloat() * 0.02f + 0.01f
)

// Linear progress bar with particle celebration at the thumb
@Composable
fun ParticleProgress(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 6.dp,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    indicatorColor: Color = MaterialTheme.colorScheme.primary,
    particleColor: Color = MaterialTheme.colorScheme.primary,
    particleCount: Int = 6
) {
    var particles by remember { mutableStateOf(listOf<Particle>()) }
    val time = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        time.animateTo(
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            )
        )
    }

    // Spawn particles at progress edge
    LaunchedEffect(progress) {
        if (progress > 0.01f && progress < 0.99f) {
            val newParticles = List(particleCount) {
                Particle(
                    x = 0f,
                    y = 0f,
                    velocityX = (Random.nextFloat() - 0.5f) * 3f,
                    velocityY = -(Random.nextFloat() * 4f + 1f),
                    alpha = 1f,
                    radius = Random.nextFloat() * 2f + 1f
                )
            }
            particles = (particles.filter { it.life > 0f } + newParticles).takeLast(30)
        }
    }

    Box(
        modifier = modifier.fillMaxWidth().height(height + 24.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(height),
            color = indicatorColor,
            trackColor = trackColor,
        )

        // Particle canvas overlay
        Canvas(modifier = Modifier.matchParentSize()) {
            val progressX = size.width * progress
            val baseY = size.height - (height.toPx() / 2f)

            particles = particles.mapNotNull { p ->
                val updated = p.copy(
                    x = p.x + p.velocityX,
                    y = p.y + p.velocityY,
                    velocityY = p.velocityY + 0.15f,
                    life = p.life - p.decay,
                    alpha = (p.life - p.decay).coerceIn(0f, 1f)
                )
                if (updated.life > 0f) {
                    drawCircle(
                        color = particleColor.copy(alpha = updated.alpha * 0.7f),
                        radius = updated.radius,
                        center = Offset(
                            x = progressX + updated.x,
                            y = baseY + updated.y
                        )
                    )
                    updated
                } else null
            }
        }
    }
}
```

**Step 2: Replace all PixelProgressBar usages**

Find all references to `PixelProgressBar` and replace with `ParticleProgress`:
```kotlin
// Old
PixelProgressBar(progress = value, mode = ProgressMode.DETERMINATE)

// New
ParticleProgress(progress = value)
```

**Step 3: Delete PixelProgress.kt**

**Step 4: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 5: Commit**

```
feat: replace PixelProgressBar with ParticleProgress celebration effect
```

---

### Task 7: Replace rDp/rSp with Fixed Dp and Expand Standards

Remove all 1,459+ `rDp()`/`rSp()` calls. Replace with direct dp values or `Standards.*` constants.

**Files:**
- Modify: `app/src/main/java/com/dark/tool_neuron/global/Standards.kt` (add missing constants)
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/theme/Theme.kt` (delete rDp/rSp functions)
- Modify: All UI files using `rDp()` or `rSp()` (~50 files)

**Step 1: Expand Standards.kt with missing spacing values**

Add to Standards object:
```kotlin
// Additional spacing
val SpacingXxs = 2.dp
val SpacingXxl = 32.dp
val SpacingXxxl = 48.dp

// Border radius
val RadiusSm = 6.dp
val RadiusMd = 8.dp
val RadiusLg = 12.dp
val RadiusXl = 16.dp
val RadiusXxl = 20.dp
val RadiusFull = 100.dp
```

**Step 2: Replace all rDp() calls mechanically**

The mapping is straightforward since `rDp(X.dp)` at baseline (360dp width) returns `X.dp`:

```
rDp(2.dp)   → Standards.SpacingXxs   OR  2.dp
rDp(4.dp)   → Standards.SpacingXs    OR  4.dp
rDp(6.dp)   → Standards.RadiusSm     OR  6.dp
rDp(8.dp)   → Standards.SpacingSm    OR  8.dp
rDp(10.dp)  → 10.dp
rDp(12.dp)  → Standards.SpacingMd    OR  12.dp
rDp(16.dp)  → Standards.SpacingLg    OR  16.dp
rDp(18.dp)  → Standards.IconMd       OR  18.dp
rDp(20.dp)  → 20.dp
rDp(24.dp)  → Standards.SpacingXl    OR  24.dp
rDp(30.dp)  → Standards.ActionIconSize OR 30.dp
rDp(32.dp)  → Standards.SpacingXxl   OR  32.dp
rDp(48.dp)  → Standards.SpacingXxxl  OR  48.dp
rDp(Standards.SpacingLg)  → Standards.SpacingLg
rDp(Standards.SpacingSm)  → Standards.SpacingSm
rDp(Standards.CardCornerRadius) → Standards.CardCornerRadius
```

For each file:
1. Remove `import com.dark.tool_neuron.ui.theme.rDp`
2. Remove `import com.dark.tool_neuron.ui.theme.rSp`
3. Replace each `rDp(X)` with the appropriate value
4. Replace each `rSp(X)` with the raw `TextUnit` value (the system font scale handles accessibility)

**Step 3: Delete rDp() and rSp() from Theme.kt**

Remove both functions entirely (lines 25-91 in Theme.kt).

**Step 4: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 5: Commit**

```
refactor: replace rDp/rSp with fixed dp values and Standards constants
```

---

### Task 8: Extract Shared UI Patterns

Extract duplicated patterns into reusable components.

**Files:**
- Create: `app/src/main/java/com/dark/tool_neuron/ui/components/ExpandCollapseIcon.kt`
- Create: `app/src/main/java/com/dark/tool_neuron/ui/components/PasswordTextField.kt`
- Modify: 9+ files using expand/collapse pattern
- Modify: 5+ files using password toggle pattern

**Step 1: Create ExpandCollapseIcon.kt**

```kotlin
package com.dark.tool_neuron.ui.components

import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.dark.tool_neuron.ui.icons.TnIcons

@Composable
fun ExpandCollapseIcon(
    isExpanded: Boolean,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    size: Dp = 18.dp
) {
    Icon(
        imageVector = if (isExpanded) TnIcons.ChevronUp else TnIcons.ChevronDown,
        contentDescription = if (isExpanded) "Collapse" else "Expand",
        modifier = modifier.size(size),
        tint = tint
    )
}
```

**Step 2: Replace all expand/collapse patterns**

In these files, replace the inline `Icon(if (isExpanded) Icons.Default.ExpandLess ...)` pattern with `ExpandCollapseIcon(isExpanded)`:
- PluginOverlayBottomSheet.kt (line 316)
- MemoryResultsDisplay.kt (line 57)
- AgentExecutionView.kt (line 114)
- ToolChainUI.kt (line 106)
- PluginResultDisplay.kt (line 129)
- SecureRagCreationScreen.kt (line 422)
- ModelStoreScreen.kt (lines 984, 1523)
- VaultDashboard.kt (line 272)
- BodyContent.kt (lines 696, 1164, 1355, 1486, 1662, 1845)

**Step 3: Create PasswordTextField.kt**

```kotlin
package com.dark.tool_neuron.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import com.dark.tool_neuron.ui.icons.TnIcons

@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    supportingText: @Composable (() -> Unit)? = null
) {
    var showPassword by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier,
        singleLine = true,
        visualTransformation = if (showPassword) VisualTransformation.None
            else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        leadingIcon = { Icon(TnIcons.Lock, contentDescription = null) },
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                    if (showPassword) TnIcons.EyeOff else TnIcons.Eye,
                    contentDescription = if (showPassword) "Hide password"
                        else "Show password"
                )
            }
        },
        isError = isError,
        supportingText = supportingText
    )
}
```

**Step 4: Replace all password toggle patterns** with `PasswordTextField(...)`

**Step 5: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 6: Commit**

```
refactor: extract ExpandCollapseIcon and PasswordTextField shared components
```

---

### Task 9: Remove Persona UI

Delete persona screens and strip navigation/settings references.

**Files:**
- Delete: `app/src/main/java/com/dark/tool_neuron/ui/screen/PersonaScreen.kt`
- Delete: `app/src/main/java/com/dark/tool_neuron/ui/screen/PersonaEditorScreen.kt`
- Modify: `app/src/main/java/com/dark/tool_neuron/activity/MainActivity.kt`
- Modify: `app/src/main/java/com/dark/tool_neuron/ui/screen/SettingsScreen.kt`

**Step 1: Remove persona routes from MainActivity.kt**

In the `Screen` sealed class (line ~173), delete:
```kotlin
data object Personas : Screen("personas")
data class PersonaEditor(val personaId: String? = null) : Screen("persona_editor/{personaId}") { ... }
```

In the NavHost, delete the `composable(Screen.Personas.route)` and `composable(Screen.PersonaEditor.route)` blocks.

Remove `onPersonasClick` lambda from SettingsScreen call in NavHost.

**Step 2: Remove persona section from SettingsScreen.kt**

Delete the "AI Personality & Memory" section header and the Personas navigation Surface (lines ~314-339).

Remove `onPersonasClick: () -> Unit = {}` from SettingsScreen signature.

**Step 3: Delete PersonaScreen.kt and PersonaEditorScreen.kt**

**Step 4: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 5: Commit**

```
refactor: remove persona UI screens and navigation routes
```

---

### Task 10: Reorganize Files into Feature Packages

Move screen files into feature-based package directories.

**Files:**
- Move files into new package directories (see design doc Section 5)

**Step 1: Create package directories**

```
ui/screen/home/          (rename from home_screen/)
ui/screen/model_store/
ui/screen/model_config/
ui/screen/memory/        (already exists)
ui/screen/settings/
ui/screen/rag/
ui/screen/files/         (already exists)
ui/screen/setup/
ui/screen/gate/
ui/screen/guide/
```

**Step 2: Move files**

```
home_screen/HomeScreen.kt        → home/HomeScreen.kt
home_screen/BodyContent.kt       → home/BodyContent.kt
home_screen/HomeDrawerScreen.kt  → home/HomeDrawerScreen.kt
home_screen/DynamicActionWindow.kt → home/DynamicActionWindow.kt
ModelStoreScreen.kt              → model_store/ModelStoreScreen.kt
ModelConfigEditorScreen.kt       → model_config/ModelConfigEditorScreen.kt
SettingsScreen.kt                → settings/SettingsScreen.kt
SecureRagCreationScreen.kt       → rag/SecureRagCreationScreen.kt
SetupScreen.kt                   → setup/SetupScreen.kt
EmbeddingSetupScreen.kt          → setup/EmbeddingSetupScreen.kt
```

Update package declarations in each moved file.

**Step 3: Update all imports across codebase**

Fix imports referencing the old package paths. Key pattern:
```
com.dark.tool_neuron.ui.screen.home_screen → com.dark.tool_neuron.ui.screen.home
com.dark.tool_neuron.ui.screen.ModelStoreScreen → com.dark.tool_neuron.ui.screen.model_store.ModelStoreScreen
```

**Step 4: Move VaultGateScreen**

```
ui/screens/VaultGateScreen.kt → ui/screen/gate/VaultGateScreen.kt
```

Update package from `com.dark.tool_neuron.ui.screens` to `com.dark.tool_neuron.ui.screen.gate`.

**Step 5: Delete empty old directories and stub files**

Delete `VaultManagementScreen.kt` (3-line stub).
Delete `ui/screens/` directory if empty after moves.

**Step 6: Verify build compiles**

Run: `./gradlew :app:compileDebugKotlin`

**Step 7: Commit**

```
refactor: reorganize UI screens into feature-based packages
```

---

### Task 11: Delete Dead Code and Final Cleanup

Clean up remaining dead code, ensure single-line comments, verify no stale references.

**Files:**
- Delete: `app/src/main/java/com/dark/tool_neuron/ui/components/CuteSwitch.kt` (if all usages replaced by ActionSwitch/M3 Switch)
- Modify: Various files for comment cleanup

**Step 1: Check CuteSwitch usage**

Grep for `CuteSwitch`, `CuteSwitchResIcon`, `CuteToggle`, `CuteIconToggle` across codebase. If all are replaceable with `ActionSwitch` or M3 `Switch`, delete the file. If some are still needed, keep.

**Step 2: Remove any multi-line comments, replace with single-line**

Search for `/*` and `*/` block comments in modified files. Replace with `//` single-line comments.

**Step 3: Remove stale imports**

Grep for any remaining:
- `import androidx.compose.material.icons.*`
- `import com.dark.tool_neuron.ui.theme.rDp`
- `import com.dark.tool_neuron.ui.theme.rSp`
- `import com.dark.tool_neuron.ui.screen.home_screen.*` (old path)
- `import com.dark.tool_neuron.ui.screens.*` (old path)

**Step 4: Verify full build**

Run: `./gradlew :app:assembleDebug`

**Step 5: Commit**

```
chore: final cleanup — delete dead code, fix stale imports, single-line comments
```

---

## Execution Order and Dependencies

```
Task 1 (dependencies) — standalone
Task 2 (expand TnIcons) — standalone
Task 3 (replace Material icons) — depends on Task 1 + 2
Task 4 (Motion.kt + animation standardization) — standalone
Task 5 (MaterialExpressiveTheme) — standalone
Task 6 (ParticleProgress) — standalone
Task 7 (rDp/rSp removal) — standalone (can parallel with 3-6)
Task 8 (shared patterns) — depends on Task 3 (uses TnIcons)
Task 9 (persona UI removal) — standalone
Task 10 (file reorg) — depends on Tasks 3, 7, 8, 9
Task 11 (final cleanup) — last, depends on all
```

**Recommended linear order:** 1 → 2 → 3 → 4 → 5 → 6 → 7 → 8 → 9 → 10 → 11

**Parallelizable groups:**
- Group A: Tasks 1+2 (deps + icons)
- Group B: Tasks 4+5+6 (animations + theme + progress)
- Group C: Task 7 (rDp removal — largest single task)
- Group D: Tasks 8+9 (shared patterns + persona)
- Sequential: Task 3 (after A), Task 10 (after all), Task 11 (last)
