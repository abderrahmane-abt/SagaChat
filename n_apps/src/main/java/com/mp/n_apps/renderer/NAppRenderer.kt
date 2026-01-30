package com.mp.n_apps.renderer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mp.n_apps.runtime.ExpressionResolver
import com.mp.n_apps.schema.NAppComponent
import com.mp.n_apps.schema.NAppUISchema

@Composable
fun NAppRenderer(
    ui: NAppUISchema,
    state: Map<String, Any?>,
    resolver: ExpressionResolver,
    onStateChange: (String, Any?) -> Unit,
    onAction: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val componentMap = ui.components.associateBy { it.id }

    // Find root components: those not referenced as children by any other component
    val childIds = ui.components.flatMap { it.children.orEmpty() }.toSet()
    val rootComponents = if (ui.layout?.sections != null) {
        // Use layout ordering if available
        ui.layout.sections.flatMap { section ->
            section.components.mapNotNull { componentMap[it] }
        }
    } else {
        ui.components.filter { it.id !in childIds }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(10.dp)
    ) {
        rootComponents.forEach { comp ->
            RenderComponent(
                spec = comp,
                state = state,
                resolver = resolver,
                onStateChange = onStateChange,
                onAction = onAction,
                componentMap = componentMap
            )
        }
    }
}

@Composable
fun RenderComponent(
    spec: NAppComponent,
    state: Map<String, Any?>,
    resolver: ExpressionResolver,
    onStateChange: (String, Any?) -> Unit,
    onAction: (String) -> Unit,
    componentMap: Map<String, NAppComponent>
) {
    // Resolve visibility
    val visible = spec.visible?.let { resolver.resolveBoolean(it, state) } ?: true
    if (!visible) return

    val renderer = ComponentRegistry.get(spec.type)
    if (renderer != null) {
        renderer(
            spec,
            state,
            resolver,
            onStateChange,
            onAction
        ) { childId ->
            val child = componentMap[childId]
            if (child != null) {
                RenderComponent(
                    spec = child,
                    state = state,
                    resolver = resolver,
                    onStateChange = onStateChange,
                    onAction = onAction,
                    componentMap = componentMap
                )
            }
        }
    }
}
