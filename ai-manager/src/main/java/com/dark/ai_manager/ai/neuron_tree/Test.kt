package com.dark.ai_manager.ai.neuron_tree

fun main() {
    val root = NeuronNode("root", "Root Data")
    val tree = NeuronTree(root)

    val nodeA = NeuronNode("a", "Node A")
    val nodeB = NeuronNode("b", "Node B")
    val nodeC = NeuronNode("c", "Node C")

    tree.addChild("root", nodeA, nodeB)
    tree.addChild("a", nodeC)

    println("Path to Node C: " + tree.requestNodePath("c"))
    println("Data in Node B: " + tree.getNodeDirect("b")?.data)

    println("Printing Tree:")
    tree.printTree()
}
