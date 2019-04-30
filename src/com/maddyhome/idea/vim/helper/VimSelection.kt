/*
 * IdeaVim - Vim emulator for IDEs based on the IntelliJ platform
 * Copyright (C) 2003-2019 The IdeaVim authors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.maddyhome.idea.vim.helper

import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LogicalPosition
import com.maddyhome.idea.vim.command.CommandState
import com.maddyhome.idea.vim.command.SelectionType
import com.maddyhome.idea.vim.command.SelectionType.*
import com.maddyhome.idea.vim.common.TextRange
import com.maddyhome.idea.vim.group.visual.toNativeSelection
import kotlin.math.max
import kotlin.math.min

/**
 * @author Alex Plate
 *
 * Interface for storing selection range.
 *
 * Type of selection is stored in [type]
 * [vimStart] and [vimEnd] - selection offsets in vim model. There values will be stored in '< and '> marks.
 *   Actually [vimStart] - initial caret position when visual mode entered and [vimEnd] - current caret position.
 *
 * This selection has direction. That means that by moving in left-up direction (e.g. `vbbbb`)
 *   [vimStart] will be greater then [vimEnd].
 *
 * All starts are included and ends are excluded
 */
sealed class VimSelection {
    abstract val type: SelectionType
    abstract val vimStart: Int
    abstract val vimEnd: Int
    protected abstract val editor: Editor

    /**
     * Converting to an old TextRange class
     */
    abstract fun toVimTextRange(skipNewLineForLineMode: Boolean = false): TextRange

    /**
     * Execute [action] for each line of selection.
     * Action will be executed in bottom-up direction if [vimStart] > [vimEnd]
     *
     * [action#start] and [action#end] are offsets in current line
     */
    abstract fun forEachLine(action: (start: Int, end: Int) -> Unit)

    companion object {
        fun create(vimStart: Int, vimEnd: Int, type: SelectionType, editor: Editor): VimSelection {
            return when (type) {
                CHARACTER_WISE -> VimCharacterSelection(vimStart, vimEnd, editor)
                LINE_WISE -> VimLineSelection(vimStart, vimEnd, editor)
                BLOCK_WISE -> VimBlockSelection(vimStart, vimEnd, editor, false)
            }
        }
    }

    override fun toString(): String {
        val startLogPosition = editor.offsetToLogicalPosition(vimStart)
        val endLogPosition = editor.offsetToLogicalPosition(vimEnd)
        return "Selection [$type]: vim start[offset: $vimStart : col ${startLogPosition.column} line ${startLogPosition.line}]" +
                " vim end[offset: $vimEnd : col ${endLogPosition.column} line ${endLogPosition.line}]"
    }
}

/**
 * Interface for storing simple selection range.
 *   Simple means that this selection can be represented only by start and end values.
 *   There selections in vim are character- and linewise selections.
 *
 *  [nativeStart] and [nativeEnd] are the offsets of native selection
 *
 * [vimStart] and [vimEnd] - selection offsets in vim model. There values will be stored in '< and '> marks.
 *   There values can differ from [nativeStart] and [nativeEnd] in case of linewise selection because [vimStart] - initial caret
 *   position when visual mode entered and [vimEnd] - current caret position.
 *
 * This selection has direction. That means that by moving in left-up direction (e.g. `vbbbb`)
 *   [nativeStart] will be greater than [nativeEnd].
 * If you need normalized [nativeStart] and [nativeEnd] (start always less than end) you
 *   can use [normNativeStart] and [normNativeEnd]
 *
 * All starts are included and ends are excluded
 */
sealed class VimSimpleSelection : VimSelection() {
    abstract val nativeStart: Int
    abstract val nativeEnd: Int
    abstract val normNativeStart: Int
    abstract val normNativeEnd: Int

    override fun forEachLine(action: (start: Int, end: Int) -> Unit) {
        val logicalStart = editor.offsetToLogicalPosition(nativeStart)
        val logicalEnd = editor.offsetToLogicalPosition(nativeEnd)
        val lineRange = if (logicalStart.line > logicalEnd.line) logicalStart.line downTo logicalEnd.line else logicalStart.line..logicalEnd.line
        lineRange.map { line ->
            val start = editor.logicalPositionToOffset(LogicalPosition(line, logicalStart.column))
            val end = editor.logicalPositionToOffset(LogicalPosition(line, logicalEnd.column))
            action(start, end)
        }
    }
}

class VimCharacterSelection(
        override val vimStart: Int,
        override val vimEnd: Int,
        override val editor: Editor
) : VimSimpleSelection() {
    override val nativeStart: Int
    override val nativeEnd: Int
    override val normNativeStart: Int
    override val normNativeEnd: Int
    override val type: SelectionType = CHARACTER_WISE

    init {
        val nativeSelection = toNativeSelection(editor, vimStart, vimEnd, CommandState.Mode.VISUAL, type.toSubMode())
        nativeStart = nativeSelection.first
        nativeEnd = nativeSelection.second
        normNativeStart = min(nativeStart, nativeEnd)
        normNativeEnd = max(nativeStart, nativeEnd)
    }

    override fun toVimTextRange(skipNewLineForLineMode: Boolean) = TextRange(normNativeStart, normNativeEnd)
}

class VimLineSelection(
        override val vimStart: Int,
        override val vimEnd: Int,
        override val editor: Editor
) : VimSimpleSelection() {
    override val type = LINE_WISE
    override val nativeStart: Int
    override val nativeEnd: Int
    override val normNativeStart: Int
    override val normNativeEnd: Int

    init {
        val nativeSelection = toNativeSelection(editor, vimStart, vimEnd, CommandState.Mode.VISUAL, type.toSubMode())
        nativeStart = nativeSelection.first
        nativeEnd = nativeSelection.second
        normNativeStart = min(nativeStart, nativeEnd)
        normNativeEnd = max(nativeStart, nativeEnd)
    }

    override fun toVimTextRange(skipNewLineForLineMode: Boolean) =
            if (skipNewLineForLineMode && editor.document.textLength >= normNativeEnd && normNativeEnd > 0 && editor.document.text[normNativeEnd - 1] == '\n') {
                TextRange(normNativeStart, (normNativeEnd - 1).coerceAtLeast(0))
            } else {
                TextRange(normNativeStart, normNativeEnd)
            }
}

class VimBlockSelection(
        override val vimStart: Int,
        override val vimEnd: Int,
        override val editor: Editor,
        val toLineEnd: Boolean
) : VimSelection() {
    override val type = BLOCK_WISE

    override fun toVimTextRange(skipNewLineForLineMode: Boolean): TextRange {
        val starts = mutableListOf<Int>()
        val ends = mutableListOf<Int>()
        val lineRanges = forEachLine { start, end ->
            starts += start
            ends += end
        }
        return TextRange(starts.toIntArray(), ends.toIntArray()).also { it.normalize(editor.document.textLength) }
    }

    override fun forEachLine(action: (start: Int, end: Int) -> Unit) {
        val offsets = toNativeSelection(editor, vimStart, vimEnd, CommandState.Mode.VISUAL, type.toSubMode())
        val logicalStart = editor.offsetToLogicalPosition(min(offsets.first, offsets.second))
        val logicalEnd = editor.offsetToLogicalPosition(max(offsets.first, offsets.second))
        val lineRange = if (logicalStart.line > logicalEnd.line) logicalStart.line downTo logicalEnd.line else logicalStart.line..logicalEnd.line
        lineRange.map { line ->
            val start = editor.logicalPositionToOffset(LogicalPosition(line, logicalStart.column))
            val end = if (toLineEnd) {
                EditorHelper.getLineEndOffset(editor, line, true)
            } else {
                editor.logicalPositionToOffset(LogicalPosition(line, logicalEnd.column))
            }
            action(start, end)
        }
    }
}