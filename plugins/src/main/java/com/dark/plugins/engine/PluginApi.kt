package com.dark.plugins.engine

import android.content.Context
import androidx.annotation.CallSuper
import androidx.annotation.Keep
import androidx.annotation.MainThread
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import com.dark.plugins.repo.PluginRegistry

typealias ComposableBlock = @Composable () -> Unit

@Stable
interface ComposePlugin {
    /** Return top-level UI to render. Host will call this frequently; avoid heavy work. */
    fun content(): ComposableBlock
}

interface PLG {
    fun callPlugin(name: String, data: Any)
}

@Immutable
data class PluginInfo(
    val name: String = "",
    val description: String = ""
)

@Keep
open class PluginApi(ctx: Context) : PLG, ComposePlugin {

    protected val appContext: Context = ctx.applicationContext

    var pluginInterface: PLG = this

    open fun getPluginInfo(): PluginInfo = PluginInfo()

    @MainThread
    @CallSuper
    open fun onCreate(data: Any) {}

    @MainThread
    @CallSuper
    open fun onDestroy() {}

    override fun callPlugin(name: String, data: Any) {
        // default dispatcher; hosts may override by subclassing or injection
        PluginRegistry.runPlugin(name, data)
    }

    @Composable
    open fun AppContent() {
        Text("Hello From Default Plugin :)")
    }

    private val cachedContent: ComposableBlock = { AppContent() }

    override fun content(): ComposableBlock = cachedContent
}
