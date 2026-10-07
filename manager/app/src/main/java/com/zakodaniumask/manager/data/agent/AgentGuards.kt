// SPDX-License-Identifier: GPL-3.0-or-later
//
// Adapted from OpenMinis (https://github.com/OpenMinis/OpenMinis), GPL-3.0:
//   agent/InterruptedTailDetector.kt (classify/isInterrupted)
//   agent/ToolLoopDetector.kt (repeat / circuit-breaker idea, compact port)
// Modified for this project's LlmMessage model.
package com.zakodaniumask.manager.data.agent

import com.zakodaniumask.manager.data.agent.llm.LlmMessage

/** Which "the agent loop stopped early" shape a conversation tail matches. */
enum class InterruptedTailShape {
    /** Tool results are the tail, but the follow-up model call never fired. */
    TOOL_RESULT_TAIL,

    /** The model asked for tools that never executed. */
    ASSISTANT_TOOL_USE,

    /** A plain user turn with no reply after it. */
    UNANSWERED_USER_TURN,

    /** Not interrupted. */
    NONE,
}

object InterruptedTailDetector {
    fun classify(last: LlmMessage?): InterruptedTailShape {
        if (last == null) return InterruptedTailShape.NONE
        return when (last.role) {
            "tool" -> InterruptedTailShape.TOOL_RESULT_TAIL
            "assistant" -> if (last.toolCalls.isNotEmpty()) {
                InterruptedTailShape.ASSISTANT_TOOL_USE
            } else {
                InterruptedTailShape.NONE
            }

            "user" -> if (last.content.isBlank()) {
                InterruptedTailShape.NONE
            } else {
                InterruptedTailShape.UNANSWERED_USER_TURN
            }

            else -> InterruptedTailShape.NONE
        }
    }

    fun isInterrupted(last: LlmMessage?): Boolean =
        classify(last) != InterruptedTailShape.NONE
}

enum class LoopVerdict { OK, REPEAT, CIRCUIT_BREAK }

/**
 * Compact anti-loop guard: flags an identical (tool + arguments) call repeated
 * too many times, and a hard circuit breaker on total calls in one run.
 */
class ToolLoopDetector(
    private val maxIdentical: Int = 3,
    private val maxTotal: Int = 40,
) {
    private val counts = HashMap<String, Int>()
    private var total = 0

    /** Register a call and return whether it should be allowed to run. */
    fun check(toolName: String, arguments: String): LoopVerdict {
        total += 1
        if (total > maxTotal) return LoopVerdict.CIRCUIT_BREAK
        val key = "$toolName\u0000${normalize(arguments)}"
        val count = (counts[key] ?: 0) + 1
        counts[key] = count
        return if (count > maxIdentical) LoopVerdict.REPEAT else LoopVerdict.OK
    }

    fun reset() {
        counts.clear()
        total = 0
    }

    private fun normalize(arguments: String): String = arguments.trim()
}
