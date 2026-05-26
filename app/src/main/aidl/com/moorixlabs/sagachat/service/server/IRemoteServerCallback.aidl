package com.moorixlabs.sagachat.service.server;

interface IRemoteServerCallback {
    void onStateChanged(String snapshotJson);
    void onRequestEvent(String eventJson);
}
