package com.moorixlabs.sagachat.service;

interface IModelLoadCallback {
    void onSuccess(String modelInfoJson);
    void onError(String message);
}
