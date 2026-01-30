package com.mp.n_apps.runtime

import com.mp.n_apps.schema.NApp
import com.mp.n_apps.schema.NAppParser

class NAppRuntime {

    val stateStore = NAppStateStore()
    val resolver = ExpressionResolver()

    private var _napp: NApp? = null
    val napp: NApp? get() = _napp

    private var _actionExecutor: ActionExecutor? = null

    var onToast: ((message: String, duration: String) -> Unit)? = null
        set(value) {
            field = value
            _actionExecutor?.onToast = value
        }

    var onAICall: ((prompt: String, resultTarget: String?, loadingTarget: String?) -> Unit)? = null
        set(value) {
            field = value
            _actionExecutor?.onAICall = value
        }

    fun load(napp: NApp) {
        _napp = napp
        stateStore.initializeFrom(napp.stateSchema)
        _actionExecutor = ActionExecutor(
            stateStore = stateStore,
            resolver = resolver,
            actions = napp.actionsSchema.actions,
            onToast = onToast,
            onAICall = onAICall
        )
    }

    fun loadFromJson(
        manifestJson: String? = null,
        stateJson: String? = null,
        uiJson: String,
        actionsJson: String? = null
    ): Result<NApp> {
        val result = NAppParser.parse(manifestJson, stateJson, uiJson, actionsJson)
        result.onSuccess { load(it) }
        return result
    }

    fun loadSingleJson(json: String): Result<NApp> {
        val result = NAppParser.parseSingleJson(json)
        result.onSuccess { load(it) }
        return result
    }

    fun onStateChange(key: String, value: Any?) {
        stateStore[key] = value
    }

    fun onAction(actionId: String) {
        _actionExecutor?.execute(actionId)
    }

    fun getState(): Map<String, Any?> = stateStore.getSnapshot()
}
