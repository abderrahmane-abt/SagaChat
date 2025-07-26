package com.dark.plugins.worker

import android.content.Context
import androidx.compose.runtime.Composable
import com.dark.plugins.engine.PluginManifest
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.util.zip.ZipInputStream

data class LoadedZip(
    val manifest: PluginManifest, val dexBuffer: ByteBuffer
)

/** Reads plugin zip from assets, parses Manifest.json and extracts classes.dex. */
fun loadPluginZipFromAssets(ctx: Context, assetName: String): LoadedZip {
    var manifest: PluginManifest? = null
    var dexBuf: ByteBuffer? = null

    ctx.assets.open(assetName).use { input ->
        ZipInputStream(input).use { zis ->
            var e = zis.nextEntry
            while (e != null) {
                if (!e.isDirectory) {
                    when (e.name) {
                        "Manifest.json", "manifest.json" -> {
                            val text = zis.readBytes().decodeToString()
                            val j = JSONObject(text)
                            manifest = PluginManifest(
                                name = j.getString("name"), mainClass = j.getString("mainClass")
                            )
                        }

                        "classes.dex" -> {
                            dexBuf = ByteBuffer.wrap(zis.readBytes())
                        }

                        "plugin.dex.jar" -> {
                            // Nested jar: open and pull classes.dex from inside
                            val jarBytes = zis.readBytes()
                            dexBuf = extractClassesDexFromJar(jarBytes)
                        }
                    }
                }
                e = zis.nextEntry
            }
        }
    }

    checkNotNull(manifest) { "Manifest.json not found in $assetName." }
    checkNotNull(dexBuf) { "classes.dex not found in $assetName (directly or inside plugin.dex.jar)." }
    return LoadedZip(manifest, dexBuf)
}

fun extractClassesDexFromJar(jarBytes: ByteArray): ByteBuffer {
    ZipInputStream(ByteArrayInputStream(jarBytes)).use { jz ->
        var je = jz.nextEntry
        while (je != null) {
            if (!je.isDirectory && je.name == "classes.dex") {
                return ByteBuffer.wrap(jz.readBytes())
            }
            je = jz.nextEntry
        }
    }
    error("classes.dex not found inside plugin.dex.jar")
}


@Suppress("UNCHECKED_CAST")
fun instantiatePlugin(
    cl: ClassLoader, className: String, appCtx: Context
): Pair<Any, (@Composable () -> Unit)> {
    val clazz = cl.loadClass(className)

    // Prefer cast to your shared interface for type-safety
    val composePluginIface =
        Class.forName("com.dark.plugins.engine.ComposePlugin", false, appCtx.classLoader)

    val instance: Any = createInstanceSmart(clazz, appCtx)

    // Optional onCreate(data)
    runCatching {
        val m = clazz.getMethod("onCreate", Any::class.java)
        m.invoke(instance, emptyMap<String, Any>())
    }

    val contentBlock: @Composable () -> Unit = if (composePluginIface.isAssignableFrom(clazz)) {
        val typed = composePluginIface.cast(instance)
        val m = composePluginIface.getMethod("content")
        m.invoke(typed) as (@Composable () -> Unit)
    } else {
        // Fallback: reflection on method named "content"
        val m = clazz.getMethod("content")
        m.invoke(instance) as (@Composable () -> Unit)
    }

    return instance to contentBlock
}

@Suppress("UNCHECKED_CAST")
fun createInstanceSmart(clazz: Class<*>, appCtx: Context): Any {
    // 1) zero-arg
    runCatching {
        val c = clazz.getDeclaredConstructor()
        c.isAccessible = true
        return c.newInstance()
    }
    // 2) Kotlin object
    runCatching {
        val f = clazz.getField("INSTANCE")
        return f.get(null)
    }
    // 3) Companion.create()/getInstance()
    runCatching {
        val companion = clazz.declaredClasses.firstOrNull { it.simpleName == "Companion" }
        if (companion != null) {
            val compField = clazz.getField("Companion")
            val comp = compField.get(null)
            val m =
                companion.methods.firstOrNull { it.name == "create" || it.name == "getInstance" }
            if (comp != null && m != null) return m.invoke(comp)
        }
    }
    // 4) (Context) constructor
    runCatching {
        val c = clazz.getDeclaredConstructor(Context::class.java)
        c.isAccessible = true
        return c.newInstance(appCtx)
    }

    error("No suitable constructor/factory for ${clazz.name}. Provide a zero-arg or (Context) ctor.")
}
