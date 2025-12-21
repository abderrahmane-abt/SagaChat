package com.mp.user_data

import android.util.Log
import com.mp.user_data.ntds.neuron_tree.NeuronNode
import com.mp.user_data.ntds.neuron_tree.NeuronTree
import com.mp.user_data.ntds.neuron_tree.NodeData
import com.mp.user_data.ntds.neuron_tree.NodeType

/**
 * Simple brain structure migration.
 * Ensures all required operator nodes exist and data is properly formatted.
 */
fun migrateBrainStructure(root: NeuronNode): NeuronTree {
    Log.d("Migration", "Running brain structure migration")

    val tree = NeuronTree(root)

    // Ensure core operator nodes exist
    ensureOperatorNode(tree, root, "chatHistory")
    ensureOperatorNode(tree, root, "memoryHistory")
    ensureOperatorNode(tree, root, "modelState")
    ensureOperatorNode(tree, root, "systemLogs")


    Log.d("Migration", "Migration completed")
    return tree
}

/**
 * Ensure an operator node exists, creating it if necessary.
 */
private fun ensureOperatorNode(
    tree: NeuronTree,
    root: NeuronNode,
    nodeId: String,
    defaultContent: String = ""
) {
    if (tree.getNodeDirectOrNull(nodeId) == null) {
        val newNode = NeuronNode(
            id = nodeId,
            data = NodeData(defaultContent, NodeType.OPERATOR)
        )
        tree.addChild(root.id, newNode)
        Log.d("Migration", "Created missing operator node: $nodeId")
    }
}