package com.mp.user_data.helpers

import android.util.Log
import com.mp.user_data.ntds.neuron_tree.NeuronNode
import com.mp.user_data.ntds.neuron_tree.NeuronTree
import com.mp.user_data.ntds.neuron_tree.NodeData
import com.mp.user_data.ntds.neuron_tree.NodeType
import org.json.JSONObject

/**
 * Base class for all data helpers.
 * Provides common functionality for managing specific data domains within the NeuronTree.
 *
 * @param tree The NeuronTree instance this helper operates on
 * @param operatorNodeId The ID of the operator node this helper manages (e.g., "chatHistory")
 */
abstract class BaseHelper(
    protected val tree: NeuronTree,
    protected val operatorNodeId: String
) {
    
    protected val TAG = this::class.java.simpleName

    /**
     * Get the operator node this helper manages.
     * Creates it if it doesn't exist.
     */
    protected fun getOperatorNode(): NeuronNode {
        return tree.getNodeDirectOrNull(operatorNodeId) ?: run {
            Log.w(TAG, "Operator node '$operatorNodeId' not found, creating it")
            val newNode = NeuronNode(
                id = operatorNodeId,
                data = NodeData("", NodeType.OPERATOR)
            )
            tree.addChild("root", newNode)
            newNode
        }
    }

    /**
     * Get all child nodes under this helper's operator node.
     */
    protected fun getAllChildren(): List<NeuronNode> {
        return getOperatorNode().children
    }

    /**
     * Add a new child node under this helper's operator node.
     * @return The ID of the newly created node
     */
    protected fun addChildNode(content: String, type: NodeType = NodeType.LEAF): String {
        val newNode = NeuronNode(
            data = NodeData(content, type)
        )
        tree.addChild(operatorNodeId, newNode)
        Log.d(TAG, "Added new node: ${newNode.id} under $operatorNodeId")
        return newNode.id
    }

    /**
     * Get a specific node by ID.
     * @return The node, or null if not found
     */
    protected fun getNodeById(id: String): NeuronNode? {
        return tree.getNodeDirectOrNull(id)
    }

    /**
     * Update the content of an existing node.
     * @return true if successful, false if node not found
     */
    protected fun updateNodeContent(id: String, newContent: String): Boolean {
        val node = getNodeById(id)
        if (node == null) {
            Log.w(TAG, "Cannot update node $id: not found")
            return false
        }
        node.data.content = newContent
        Log.d(TAG, "Updated node $id")
        return true
    }

    /**
     * Delete a node by ID.
     * @return true if successful, false if node not found
     */
    protected fun deleteNode(id: String): Boolean {
        val result = tree.deleteNodeById(id)
        if (result) {
            Log.d(TAG, "Deleted node $id")
        } else {
            Log.w(TAG, "Failed to delete node $id")
        }
        return result
    }

    /**
     * Parse JSON content from a node safely.
     * @return JSONObject if valid, null otherwise
     */
    protected fun parseNodeContent(node: NeuronNode): JSONObject? {
        return try {
            if (node.data.content.isBlank()) {
                JSONObject()
            } else {
                JSONObject(node.data.content)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse node content: ${node.id}", e)
            null
        }
    }

    /**
     * Get the count of items managed by this helper.
     */
    fun getCount(): Int {
        return getAllChildren().size
    }

    /**
     * Check if this helper has any data.
     */
    fun isEmpty(): Boolean {
        return getCount() == 0
    }

    /**
     * Clear all data managed by this helper.
     * WARNING: This operation cannot be undone!
     */
    fun clearAll() {
        val children = getAllChildren().toList() // Copy to avoid concurrent modification
        children.forEach { child ->
            deleteNode(child.id)
        }
        Log.w(TAG, "Cleared all data from $operatorNodeId")
    }
}