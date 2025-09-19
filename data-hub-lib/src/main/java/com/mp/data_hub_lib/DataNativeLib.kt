package com.mp.data_hub_lib

class DataNativeLib {

    external fun loadVecx(path: String, key: String): Boolean
    external fun updateEntity(entity: String, key: String, value: String): Boolean
    external fun getEntity(entity: String): String
    external fun saveVecx(path: String): Boolean

    companion object {
        init {
            System.loadLibrary("data_hub_lib")
        }
    }
}
