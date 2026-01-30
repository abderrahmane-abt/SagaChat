package com.mp.n_apps.renderer

import androidx.compose.runtime.Composable
import com.mp.n_apps.renderer.components.registerActionComponents
import com.mp.n_apps.renderer.components.registerDisplayComponents
import com.mp.n_apps.renderer.components.registerInputComponents
import com.mp.n_apps.renderer.components.registerLayoutComponents
import com.mp.n_apps.runtime.ExpressionResolver
import com.mp.n_apps.schema.NAppComponent

typealias NAppComponentRenderer = @Composable (
    spec: NAppComponent,
    state: Map<String, Any?>,
    resolver: ExpressionResolver,
    onStateChange: (String, Any?) -> Unit,
    onAction: (String) -> Unit,
    renderChild: @Composable (String) -> Unit
) -> Unit

object ComponentRegistry {

    private val renderers = mutableMapOf<String, NAppComponentRenderer>()

    fun register(type: String, renderer: NAppComponentRenderer) {
        renderers[type] = renderer
    }

    fun get(type: String): NAppComponentRenderer? = renderers[type]

    fun registerAll() {
        registerInputComponents(this)
        registerDisplayComponents(this)
        registerActionComponents(this)
        registerLayoutComponents(this)
    }

    init {
        registerAll()
    }
}
