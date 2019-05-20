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

package com.maddyhome.idea.vim.handler.specialkeys

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.maddyhome.idea.vim.VimPlugin
import com.maddyhome.idea.vim.command.Command
import com.maddyhome.idea.vim.command.CommandState
import com.maddyhome.idea.vim.handler.EditorActionHandlerBase
import com.maddyhome.idea.vim.option.Options

/**
 * @author Alex Plate
 *
 * Handler for SHIFTED special keys except arrows, that are defined in `:h keymodel`
 * There are: <End>, <Home>, <PageUp> and <PageDown>
 *
 * This handler is used to properly handle there keys according to current `keymodel` and `selectmode` options
 *
 * Handler is called once for all carets
 */
abstract class ShiftedSpecialKeyHandler : EditorActionHandlerBase() {
    final override fun execute(editor: Editor, context: DataContext, cmd: Command): Boolean {
        val keymodelOption = Options.getInstance().getListOption(Options.KEYMODEL)
        val startSel = keymodelOption?.contains("startsel") == true
        if (startSel && !CommandState.inVisualMode(editor) && !CommandState.inSelectMode(editor)) {
            if (Options.getInstance().getListOption(Options.SELECTMODE)?.contains("key") == true) {
                VimPlugin.getVisualMotion().enterSelectMode(editor, CommandState.SubMode.VISUAL_CHARACTER)
            } else {
                VimPlugin.getVisualMotion()
                        .toggleVisual(editor, 1, 0, CommandState.SubMode.VISUAL_CHARACTER)
            }
        }
        motion(editor, context, cmd)
        return true
    }

    /**
     * This method is called when `keymodel` doesn't contain `startsel`,
     * or contains one of `continue*` values but in different mode.
     */
    abstract fun motion(editor: Editor, context: DataContext, cmd: Command)
}