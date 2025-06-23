package com.dark.userdata.neuron_tree

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * Represents a basic node within the NeuronTree structure.
 * Each node can store data, maintain children, and respond to data requests.
 */
@Serializable
open class NeuronNode(
    val id: String = UUID.randomUUID().toString(), // Unique identifier for the node (must be unique across tree)
    val data: String,            // Flexible data stored in the node (e.g., String, Object)
    internal val children: MutableList<NeuronNode> = mutableListOf()  // Child nodes of this node
) {

    /**
     * Returns a list of child nodes attached to this node.
     * Example:
     * node.getChildNodes().forEach { println(it.id) }
     */
    fun getChildNodes(): List<NeuronNode> = children

    /**
     * Adds a child node to this node's children list.
     * Example:
     * val child = NeuronNode("child1", "Child Data")
     * node.addChild(child)
     */
    fun addChild(node: NeuronNode) {
        children.add(node)
    }

    /**
     * Called when data is requested from this node.
     * Override in child classes for custom behavior.
     * Example:
     * println(node.onDataRequested("status"))
     */
    open fun onDataRequested(request: String): String? {
        return data
    }
}
