package com.dark.userdata

import android.content.Context
import com.dark.userdata.ntds.getBrainFilePath
import com.dark.userdata.ntds.getOrCreateHardwareBackedAesKey
import com.dark.userdata.ntds.loadEncryptedTree
import com.dark.userdata.ntds.neuron_tree.NeuronNode
import com.dark.userdata.ntds.neuron_tree.NeuronTree
import com.dark.userdata.ntds.neuron_tree.NodeData
import com.dark.userdata.ntds.neuron_tree.NodeType
import com.dark.userdata.ntds.schema.ChildNodeSchema
import com.dark.userdata.ntds.schema.NodeContentSchema
import com.dark.userdata.ntds.schema.NodeTags
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

fun getDefaultBrainStructure(): NeuronTree {
    val tools = generateOperatorNode("tools", DefaultNodes.OperatorNodes.Content.TOOLS)
    val temp = generateOperatorNode("temp-mem", DefaultNodes.OperatorNodes.Content.TEMPORARY_MEMORY)
    val main = generateOperatorNode("main-mem", DefaultNodes.OperatorNodes.Content.MAIN_MEMORY)
    val ai = generateOperatorNode("ai", DefaultNodes.OperatorNodes.Content.AI)

    val root = NeuronNode(
        "root", NodeData(
            NodeContentSchema(
                name = "root",
                tags = listOf(NodeTags.ROOT, NodeTags.PR_VERY_HIGH),
                childNodes = listOf(
                    ChildNodeSchema(tools.id),
                    ChildNodeSchema(temp.id),
                    ChildNodeSchema(main.id),
                    ChildNodeSchema(ai.id)
                )
            ), NodeType.ROOT
        )
    )
    val tree = NeuronTree(root)

    tree.addChild(root.id, tools, temp, main, ai)

    return tree
}

internal fun generateOperatorNode(
    id: String,
    content: NodeContentSchema = NodeContentSchema()
): NeuronNode {
    return NeuronNode(id, NodeData(content, NodeType.OPERATOR))
}

internal object DefaultNodes {
    object OperatorNodes {
        enum class NAMES(val nodeName: String) {
            TOOLS("tools"),
            TEMP_MEMORY("temporary-memory"),
            MAIN_MEMORY("main-memory"),
            AI("ai")
        }

        internal object Content {
            val TOOLS = NodeContentSchema(
                name = NAMES.TOOLS.nodeName,
                tags = listOf(NodeTags.TOOL, NodeTags.APP_OPERATOR, NodeTags.PR_VERY_HIGH),
                childNodes = mutableListOf(ChildNodeSchema())
            )
            val TEMPORARY_MEMORY = NodeContentSchema(
                name = NAMES.TEMP_MEMORY.nodeName,
                tags = listOf(NodeTags.TEMP, NodeTags.PR_LOW, NodeTags.TEMP_MESSAGES),
                childNodes = mutableListOf(ChildNodeSchema())
            )
            val MAIN_MEMORY = NodeContentSchema(
                name = NAMES.MAIN_MEMORY.nodeName,
                tags = listOf(NodeTags.MAIN, NodeTags.PR_MEDIUM, NodeTags.MAIN_CONVERSATION),
                childNodes = mutableListOf(ChildNodeSchema())
            )

            val AI = NodeContentSchema(
                name = NAMES.AI.nodeName,
                tags = listOf(NodeTags.AI, NodeTags.PR_HIGH),
                childNodes = mutableListOf(ChildNodeSchema())
            )
        }
    }

    object HolderNodes {
        enum class NAMES(val nodeName: String) {
            PERSONAL_INFO("personal-info"),
            CONVERSATION("conversation")
        }

        internal object HolderNodesContent {
            val PERSONAL_INFO = NodeContentSchema(
                name = NAMES.PERSONAL_INFO.nodeName,
                tags = listOf(NodeTags.PR_VERY_HIGH),
                childNodes = mutableListOf(ChildNodeSchema())
            )
            val CONVERSATION = NodeContentSchema(
                name = NAMES.CONVERSATION.nodeName,
                tags = listOf(NodeTags.PR_VERY_HIGH),
                childNodes = mutableListOf(ChildNodeSchema())
            )
        }
    }
}

class Operations(context: Context, alise: String) {
    val path = getBrainFilePath(context)
    val key = getOrCreateHardwareBackedAesKey(alise)
    lateinit var brain: NeuronTree

    init {
        CoroutineScope(Dispatchers.IO).launch {
            brain = loadEncryptedTree(path, key) ?: getDefaultBrainStructure()
        }
    }

    fun updatePersonalInfo(personalInfoTYPE: PersonalInfoTYPE, data: JSONObject) {
        when (personalInfoTYPE) {
            PersonalInfoTYPE.RELATIONS -> {

                /*
                sample format

                {
                    "relation": "jon-deo",
                    "update": {
                        "age": 50,
                        "gender": "Male"
                    }
                }
                 */

                //Checking The Key Already Exist's
                val pINodeKey = data.getString("relation")
                val pINode = brain.getNodeDirect(DefaultNodes.HolderNodes.NAMES.PERSONAL_INFO.nodeName) ?: return

                pINode.children.find { it.id == pINodeKey }?.let {

                }
            }

            PersonalInfoTYPE.FAVOURITES -> {

            }
        }
    }

    enum class PersonalInfoTYPE {
        RELATIONS,
        FAVOURITES
    }

}