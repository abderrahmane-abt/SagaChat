package com.dark.userdata.ntds.neuron_tree

import com.dark.userdata.ntds.schema.NodeContentSchema
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
open class NeuronNode(
    val id: String = UUID.randomUUID().toString(),
    val data: NodeData,
    internal val children: MutableList<NeuronNode> = mutableListOf()
) {

    fun getChildNodes(): List<NeuronNode> = children

    fun addChild(node: NeuronNode) {
        children.add(node)
    }

    open fun onDataRequested(request: String): NodeData {
        return data
    }
}


@Serializable
data class NodeData(
    val content: NodeContentSchema,
    val type: NodeType
)

@Serializable
enum class NodeType {
    ROOT,
    OPERATOR,
    HOLDER,
    STEAM,
    LEAF
}