package com.dark.networking

class NativeLib {

    /**
     * A native method that is implemented by the 'networking' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'networking' library on application startup.
        init {
            System.loadLibrary("networking")
        }
    }
}