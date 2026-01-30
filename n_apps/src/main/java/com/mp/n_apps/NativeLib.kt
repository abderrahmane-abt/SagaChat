package com.mp.n_apps

class NativeLib {

    /**
     * A native method that is implemented by the 'n_apps' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'n_apps' library on application startup.
        init {
            System.loadLibrary("n_apps")
        }
    }
}