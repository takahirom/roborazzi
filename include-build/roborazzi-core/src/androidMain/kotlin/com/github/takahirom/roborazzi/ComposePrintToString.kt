package com.github.takahirom.roborazzi

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.AccessibilityAction
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.SemanticsConfiguration
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.toSize

// Brought from From AOSP compose-ui:ui-test Output.kt because it is internal
fun SemanticsNode.printToString(maxDepth: Int = 0): String {
    val sb = StringBuilder()
    printToStringInner(
        sb = sb,
        maxDepth = maxDepth,
        nestingLevel = 0,
        nestingIndent = "",
        isFollowedBySibling = false
    )
    return sb.toString()
}

private fun SemanticsNode.printToStringInner(
    sb: StringBuilder,
    maxDepth: Int,
    nestingLevel: Int,
    nestingIndent: String,
    isFollowedBySibling: Boolean
) {
    val newIndent = if (nestingLevel == 0) {
        ""
    } else if (isFollowedBySibling) {
        "$nestingIndent | "
    } else {
        "$nestingIndent   "
    }

    if (nestingLevel > 0) {
        sb.append("$nestingIndent |-")
    }
    // Node number changed unexpectedly
    // sb.append("Node #$id at ")
    sb.append("Node at ")

    sb.append(rectToShortString(unclippedGlobalBounds))

    if (config.contains(SemanticsProperties.TestTag)) {
        sb.append(", Tag: '")
        sb.append(config[SemanticsProperties.TestTag])
        sb.append("'")
    }

    val maxLevelReached = nestingLevel == maxDepth

    sb.appendConfigInfo(config, newIndent)

    if (maxLevelReached) {
        val childrenCount = children.size
        val siblingsCount = (parent?.children?.size ?: 1) - 1
        if (childrenCount > 0 || (siblingsCount > 0 && nestingLevel == 0)) {
            sb.appendLine()
            sb.append(newIndent)
            sb.append("Has ")
            if (childrenCount > 1) {
                sb.append("$childrenCount children")
            } else if (childrenCount == 1) {
                sb.append("$childrenCount child")
            }
            if (siblingsCount > 0 && nestingLevel == 0) {
                if (childrenCount > 0) {
                    sb.append(", ")
                }
                if (siblingsCount > 1) {
                    sb.append("$siblingsCount siblings")
                } else {
                    sb.append("$siblingsCount sibling")
                }
            }
        }
        return
    }

    val childrenLevel = nestingLevel + 1
    val children = this.children.toList()
    children.forEachIndexed { index, child ->
        val hasSibling = index < children.size - 1
        sb.appendLine()
        child.printToStringInner(sb, maxDepth, childrenLevel, newIndent, hasSibling)
    }
}

private val SemanticsNode.unclippedGlobalBounds: Rect
    get() {
        return Rect(positionInWindow, size.toSize())
    }

private fun rectToShortString(rect: Rect): String {
    return "(l=${rect.left}, t=${rect.top}, r=${rect.right}, b=${rect.bottom})px"
}

private fun StringBuilder.appendConfigInfo(config: SemanticsConfiguration, indent: String = "") {
    val actions = mutableListOf<String>()
    val units = mutableListOf<String>()
    for ((key, value) in config.sortedBy { it.key.name }) {
        if (key == SemanticsProperties.TestTag) {
            continue
        }

        if (value is AccessibilityAction<*> || value is Function<*>) {
            // Avoids printing stuff like "action = 'AccessibilityAction\(label=null, action=.*\)'"
            actions.add(key.name)
            continue
        }

        if (value is Unit) {
            // Avoids printing stuff like "Disabled = 'kotlin.Unit'"
            units.add(key.name)
            continue
        }

        appendLine()
        append(indent)
        append(key.name)
        append(" = '")

        if (value is AnnotatedString) {
            if (value.paragraphStyles.isEmpty() && value.spanStyles.isEmpty() && value
                .getStringAnnotations(0, value.text.length).isEmpty()
            ) {
                append(value.text)
            } else {
                // Save space if we there is text only in the object
                append(value)
            }
        } else if (value is Iterable<*>) {
            append(value.sortedBy { it.toString() })
        } else if (value is CollectionInfo) {
          append("(rowCount=${value.rowCount}, columnCount=${value.columnCount})")
        } else {
            append(value)
        }

        append("'")
    }

    if (units.isNotEmpty()) {
        appendLine()
        append(indent)
        append("[")
        append(units.sorted().joinToString(separator = ", "))
        append("]")
    }

    if (actions.isNotEmpty()) {
        appendLine()
        append(indent)
        append("Actions = [")
        append(actions.sorted().joinToString(separator = ", "))
        append("]")
    }

    if (config.isMergingSemanticsOfDescendants) {
        appendLine()
        append(indent)
        append("MergeDescendants = 'true'")
    }

    if (config.isClearingSemantics) {
        appendLine()
        append(indent)
        append("ClearAndSetSemantics = 'true'")
    }
}
