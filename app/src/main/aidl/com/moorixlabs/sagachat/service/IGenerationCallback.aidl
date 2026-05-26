package com.moorixlabs.sagachat.service;

interface IGenerationCallback {
    void onToken(String token);
    void onToolCall(String name, String argsJson);
    void onDone();
    void onError(String message);
    void onMetrics(String metricsJson);
    void onProgress(float progress);
}
