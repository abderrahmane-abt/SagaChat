package com.dark.download_manager

/**
 * JNI bridge to the C++ download engine (libdownload_manager.so).
 *
 * All methods are internal — callers use [HxdManager] instead.
 */
internal object HxdNative {

    init { System.loadLibrary("download_manager") }

    // ── File I/O engine ───────────────────────────────────────────────────────

    /**
     * Opens (or appends to) <destPath>.hxd_tmp.
     * Returns the resume offset (size of existing partial file), 0 for a fresh download,
     * or -1 on error (e.g. directory doesn't exist, no disk space).
     */
    external fun nativePrepare(id: Int, destPath: String): Long

    /** Sets the expected total bytes (resume offset + Content-Length from server). */
    external fun nativeSetTotal(id: Int, total: Long)

    /**
     * Writes [len] bytes from [data] starting at [offset] to the temp file.
     * Returns false if cancel or pause was signalled, or if an I/O error occurred.
     * Caller must stop reading the stream when false is returned.
     */
    external fun nativeWriteChunk(id: Int, data: ByteArray, offset: Int, len: Int): Boolean

    /**
     * fsync + atomic rename of .hxd_tmp → destPath.
     * Returns true on success. Removes task context from engine.
     */
    external fun nativeComplete(id: Int): Boolean

    /**
     * Closes the file descriptor and removes task context.
     * Does NOT delete the temp file (caller decides — delete on cancel, keep on error/pause).
     */
    external fun nativeFail(id: Int)

    /** Signals cancel: next nativeWriteChunk() returns false. */
    external fun nativeCancel(id: Int)

    /** Signals pause: next nativeWriteChunk() returns false. Temp file is preserved for resume. */
    external fun nativePause(id: Int)

    /**
     * Returns [downloaded_bytes, total_bytes, speed_bps, state_ordinal].
     * state_ordinal maps to [HxdStatus] ordinals.
     */
    external fun nativeGetProgress(id: Int): LongArray

    /** Closes fd and removes task context. Temp file is preserved (resume). */
    external fun nativeCleanup(id: Int)

    // ── Persistence ───────────────────────────────────────────────────────────

    /**
     * Saves the current download queue to a binary file at [path].
     * Called on service stop / task state changes so restarts can resume.
     */
    external fun nativeSaveQueue(
        path: String,
        ids: IntArray,
        states: ByteArray,
        downloaded: LongArray,
        total: LongArray,
        urls: Array<String>,
        destPaths: Array<String>
    ): Boolean

    /**
     * Loads a previously saved queue from [path].
     * Returns an array of rows; each row is Array<String>[6]:
     *   [0] id, [1] state_ordinal, [2] downloaded_bytes, [3] total_bytes, [4] url, [5] destPath
     */
    external fun nativeLoadQueue(path: String): Array<Array<String>>
}
