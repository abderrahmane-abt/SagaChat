package com.dark.ai_manager.ai.neuron_tree

/**
 * Represents a basic node within the NeuronTree structure.
 * Each node can store data, maintain children, and respond to data requests.
 */
open class NeuronNode(
    val id: String,                    // Unique identifier for the node (must be unique across tree)
    internal val data: Any,            // Flexible data stored in the node (e.g., String, Object)
    internal val children: MutableList<NeuronNode> = mutableListOf()  // Child nodes of this node
) {

    /**
     * Returns the data stored in this node.
     * Example:
     * val node = NeuronNode("id", "User Data")
     * println(node.getData()) // Outputs: User Data
     */
    open fun getData(): Any = data

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
    open fun onDataRequested(request: String): Any? {
        return data
    }
}
