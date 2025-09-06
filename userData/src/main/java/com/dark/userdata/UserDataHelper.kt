package com.dark.userdata

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.dark.userdata.ntds.getBrainFilePath
import com.dark.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.userdata.ntds.loadEncryptedTree
import com.dark.userdata.ntds.neuron_tree.NeuronNode
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import com.dark.userdata.ntds.neuron_tree.NodeData
import com.dark.userdata.ntds.neuron_tree.NodeType
import com.dark.userdata.ntds.saveEncryptedTree
import kotlinx.serialization.json.Json
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import javax.crypto.SecretKey

fun getDefaultBrainStructure(): NeuronTree {
    val root = NeuronNode("root", NodeData("", NodeType.ROOT))
    val chatHistory = NeuronNode("chatHistory", NodeData("", NodeType.OPERATOR))

    val tree = NeuronTree(root)
    tree.addChild(root.id, chatHistory)
    return tree
}

fun readBrainFile(key: SecretKey, context: Context): NeuronTree {
    val brainFile = getBrainFilePath(context)
    return loadEncryptedTree(brainFile, key) ?: getDefaultBrainStructure()
}

fun getDefaultChatHistory(root: NeuronNode): NeuronNode {
    return NeuronTree(root).getNodeDirect("chatHistory")
}

fun addNewChat(root: NeuronNode, data: JSONObject): NeuronNode {
    val chatHistory = getDefaultChatHistory(root)
    val newChat = NeuronNode(data = NodeData(data.toString(), NodeType.LEAF))
    NeuronTree(root).addChild(chatHistory.id, newChat)
    return newChat
}

fun writeBitmapImage(
    bitmap: Bitmap,
    format: Bitmap.CompressFormat = Bitmap.CompressFormat.PNG, // or PNG/JPEG
    quality: Int = 100
): String {
    val bos = ByteArrayOutputStream()
    bitmap.compress(format, quality, bos)
    val bytes = bos.toByteArray()
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}

fun readBitmapImage(b64: String): Bitmap? {
    return try {
        val bytes = Base64.decode(b64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (_: Throwable) { null }
}

fun saveTree(tree: NeuronTree, context: Context, alise: String){
    val key = getOrCreateHardwareBackedAesKey(alise)
    val file = getBrainFilePath(context)
    saveEncryptedTree(tree, file, key)
}