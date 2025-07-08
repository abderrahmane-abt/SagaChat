package com.dark.neuroverse.data.repo

import android.content.Context
import com.dark.userdata.ntds.getBrainFilePath
import com.dark.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.userdata.ntds.loadEncryptedTree
import com.dark.userdata.ntds.neuron_tree.NeuronNode
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import com.dark.userdata.ntds.neuron_tree.NodeData
import com.dark.userdata.ntds.neuron_tree.NodeType
import com.dark.userdata.ntds.saveEncryptedTree
import com.dark.userdata.ntds.schema.NodeContentSchema

object UserData {
    var rootTree: NeuronTree? =
        NeuronTree(NeuronNode("root", NodeData(NodeContentSchema(), NodeType.ROOT)))

    fun init(context: Context){
        rootTree = loadEncryptedTree(getBrainFilePath(context), getOrCreateHardwareBackedAesKey())
    }

    fun updateBrain(context: Context){
        saveEncryptedTree(rootTree!!, getBrainFilePath(context), getOrCreateHardwareBackedAesKey())
    }
}