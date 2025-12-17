package com.mp.ai_engine.workers.model

import android.content.Context
import com.mp.ai_engine.workers.installer.ModelInstaller


//Single Point Of Communication
object ModelManager {


    fun init(context: Context){
        //Init Installers
        ModelInstaller.initialize(context)
    }

    fun loadModel(modelName: String){

    }

}