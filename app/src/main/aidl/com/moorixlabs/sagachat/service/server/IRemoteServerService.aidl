package com.moorixlabs.sagachat.service.server;

import com.moorixlabs.sagachat.service.server.IRemoteServerCallback;

interface IRemoteServerService {

    void start(String configJson);
    void stop();
    boolean isRunning();
    String currentSnapshotJson();
    void rotateToken(String newToken);
    String recentRequestEventsJson(int max);
    void clearAuditLog();

    void registerCallback(IRemoteServerCallback cb);
    void unregisterCallback(IRemoteServerCallback cb);
}
