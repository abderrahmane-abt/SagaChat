package com.moorixlabs.sagachat.service;

import com.moorixlabs.sagachat.service.IModelLoadCallback;
import com.moorixlabs.sagachat.service.IGenerationCallback;
import com.moorixlabs.sagachat.service.ITnEventCallback;
import android.graphics.Bitmap;

interface IInferenceService {

    // Model lifecycle
    void loadModel(String modelPath, String configJson, IModelLoadCallback callback);
    void loadModelFromFd(in ParcelFileDescriptor pfd, String configJson, IModelLoadCallback callback);
    void unloadModel();
    boolean isModelLoaded();
    String getModelInfo();

    // Generation
    void generate(String prompt, int maxTokens, IGenerationCallback callback);
    void generateMultiTurn(String messagesJson, int maxTokens, IGenerationCallback callback);
    void stopGeneration();

    // Summarize the conversation, drop the in-context KV cache, and emit the
    // summary text via the callback (same event vocabulary as generateMultiTurn).
    // Caller is expected to replace its in-memory chat history with a fresh
    // thread embedding the returned summary.
    void compactConversation(String messagesJson, int maxTokens, IGenerationCallback callback);

    // Sampling
    void setSampling(String samplingJson);
    void setSystemPrompt(String prompt);
    void setChatTemplate(String template);

    // Tool calling
    boolean isToolCallingSupported();
    void setToolsJson(String toolsJson);
    void enableToolCalling(String toolsJson, int grammarMode, boolean useTypedGrammar);
    void clearTools();

    // Context
    float getContextUsage();
    String getContextInfo(String prompt);
    String getMemoryStatsJson();
    String getVtCacheStatsJson();

    // Thinking
    boolean supportsThinking();
    void setThinkingEnabled(boolean enabled);

    // Optimizations
    void setPromptCacheDir(String path);
    boolean warmUp();
    void setThreadMode(int mode);

    // KV state persistence
    long getStateSize();
    boolean stateSaveToFile(String path);
    boolean stateLoadFromFile(String path);



    // Errors / crash diagnostics — unified via :tn_security.
    // The service is the producer; clients subscribe with registerTnEvents.
    // Multiple callbacks supported (RemoteCallbackList).
    void registerTnEvents(ITnEventCallback cb);
    void unregisterTnEvents(ITnEventCallback cb);

    /**
     * Replay any crash JSON files left on disk by previous service-process
     * lifetimes (written by tn_security's signal handlers). The service
     * deletes each file as it ships it via onCrashReplay. Idempotent; safe
     * to call on every bind.
     */
    void replayPendingCrashes();
}
