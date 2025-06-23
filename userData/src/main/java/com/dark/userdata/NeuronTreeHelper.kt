package com.dark.userdata

import android.content.Context
import com.dark.userdata.neuron_tree.NeuronNode
import com.dark.userdata.neuron_tree.NeuronTree
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.RandomAccessFile
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import javax.crypto.SecretKey

fun getBrainFilePath(context: Context): File {
    return File(context.filesDir, "secure_brain.brain")
}

fun NeuronTree.toByteArray(): ByteArray {
    val output = ByteArrayOutputStream()
    val stream = ObjectOutputStream(output)

    nodeMap.values.forEach { node ->
        stream.writeUTF(node.id)
        stream.writeUTF(node.data.toString())
        stream.writeInt(node.children.size)
        node.children.forEach { stream.writeUTF(it.id) }
    }
    stream.close()
    return output.toByteArray()
}

fun NeuronTree.collectAllNodes(): List<NeuronNode> {
    val result = mutableListOf<NeuronNode>()

    fun traverse(node: NeuronNode) {
        result.add(node)
        node.children.forEach { traverse(it) }
    }

    traverse(root)
    return result
}


fun saveEncryptedTree(tree: NeuronTree, file: File, key: SecretKey) {
    val encrypted = encrypt(tree.toByteArray(), key)
    Files.write(file.toPath(), encrypted)
}

fun memoryMapFile(file: File): MappedByteBuffer {
    val raf = RandomAccessFile(file, "r")
    val channel = raf.channel
    return channel.map(FileChannel.MapMode.READ_ONLY, 0, file.length())
}

fun loadEncryptedTree(file: File, key: SecretKey): NeuronTree? {
    if (!file.exists()) return null

    val mapped = memoryMapFile(file)
    val encrypted = ByteArray(mapped.capacity()).apply { mapped.get(this) }
    val decrypted = decrypt(encrypted, key)

    data class Record(val id: String, val data: String, val childIds: List<String>)

    val records = mutableListOf<Record>()
    ObjectInputStream(ByteArrayInputStream(decrypted)).use { stream ->
        while (stream.available() > 0) {
            val id = stream.readUTF()
            val data = stream.readUTF()
            val nChildren = stream.readInt()
            val childIds = List(nChildren) { stream.readUTF() }
            records += Record(id, data, childIds)
        }
    }

    val nodeMap = records.associate { it.id to NeuronNode(it.id, it.data) }.toMutableMap()
    records.forEach { record ->
        val parent = nodeMap[record.id]!!
        record.childIds.forEach { childId -> parent.addChild(nodeMap[childId]!!) }
    }

    return NeuronTree(nodeMap["root"] ?: nodeMap.values.first())
}
