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

package org.jetbrains.plugins.ideavim;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.editor.Caret;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.project.Project;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.UsefulTestCase;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.fixtures.impl.LightTempDirTestFixtureImpl;
import com.maddyhome.idea.vim.KeyHandler;
import com.maddyhome.idea.vim.VimPlugin;
import com.maddyhome.idea.vim.command.CommandState;
import com.maddyhome.idea.vim.ex.ExOutputModel;
import com.maddyhome.idea.vim.ex.vimscript.VimScriptGlobalEnvironment;
import com.maddyhome.idea.vim.helper.*;
import com.maddyhome.idea.vim.option.Options;
import com.maddyhome.idea.vim.option.ToggleOption;
import com.maddyhome.idea.vim.ui.ExEntryPanel;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;

/**
 * @author vlan
 */
public abstract class VimTestCase extends UsefulTestCase {
  protected CodeInsightTestFixture myFixture;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    final IdeaTestFixtureFactory factory = IdeaTestFixtureFactory.getFixtureFactory();
    final LightProjectDescriptor projectDescriptor = LightProjectDescriptor.EMPTY_PROJECT_DESCRIPTOR;
    final TestFixtureBuilder<IdeaProjectTestFixture> fixtureBuilder = factory.createLightFixtureBuilder(projectDescriptor);
    final IdeaProjectTestFixture fixture = fixtureBuilder.getFixture();
    myFixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(fixture,
                                                                                    new LightTempDirTestFixtureImpl(true));
    myFixture.setUp();
    myFixture.setTestDataPath(getTestDataPath());
    KeyHandler.getInstance().fullReset(myFixture.getEditor());
    Options.getInstance().resetAllOptions();
    VimPlugin.getKey().resetKeyMappings();

    // Make sure the entry text field gets a bounds, or we won't be able to work out caret location
    ExEntryPanel.getInstance().getEntry().setBounds(0,0, 100, 25);
  }

  protected String getTestDataPath() {
    return PathManager.getHomePath() + "/community/plugins/ideavim/testData";
  }

  @Override
  protected void tearDown() throws Exception {
    myFixture.tearDown();
    myFixture = null;
    ExEntryPanel.getInstance().deactivate(false);
    VimScriptGlobalEnvironment.getInstance().getVariables().clear();
    super.tearDown();
  }

  protected void enableExtensions(@NotNull String... extensionNames) {
    for (String name : extensionNames) {
      ToggleOption option = (ToggleOption)Options.getInstance().getOption(name);
      option.set();
    }
  }

  @NotNull
  protected Editor typeTextInFile(@NotNull final List<KeyStroke> keys, @NotNull String fileContents) {
    configureByText(fileContents);
    return typeText(keys);
  }

  @NotNull
  protected Editor configureByText(@NotNull String content) {
    myFixture.configureByText(PlainTextFileType.INSTANCE, content);
    return myFixture.getEditor();
  }

  @NotNull
  protected Editor configureByJavaText(@NotNull String content) {
    myFixture.configureByText(JavaFileType.INSTANCE, content);
    return myFixture.getEditor();
  }

  @NotNull
  protected Editor configureByXmlText(@NotNull String content) {
    myFixture.configureByText(XmlFileType.INSTANCE, content);
    return myFixture.getEditor();
  }

  @NotNull
  protected Editor typeText(@NotNull List<KeyStroke> keys) {
    final Editor editor = myFixture.getEditor();
    final KeyHandler keyHandler = KeyHandler.getInstance();
    final EditorDataContext dataContext = new EditorDataContext(editor);
    final Project project = myFixture.getProject();
    TestInputModel.getInstance(editor).setKeyStrokes(keys);
    RunnableHelper.runWriteCommand(project, new Runnable() {
      @Override
      public void run() {
        final TestInputModel inputModel = TestInputModel.getInstance(editor);
        for (KeyStroke key = inputModel.nextKeyStroke(); key != null; key = inputModel.nextKeyStroke()) {
          final ExEntryPanel exEntryPanel = ExEntryPanel.getInstance();
          if (exEntryPanel.isActive()) {
            exEntryPanel.handleKey(key);
          }
          else {
            keyHandler.handleKey(editor, key, dataContext);
          }
        }
      }
    }, null, null);
    return editor;
  }

  @NotNull
  protected static List<KeyStroke> commandToKeys(@NotNull String command) {
    List<KeyStroke> keys = new ArrayList<KeyStroke>();
    keys.addAll(StringHelper.parseKeys(":"));
    keys.addAll(StringHelper.stringToKeys(command));
    keys.addAll(StringHelper.parseKeys("<Enter>"));
    return keys;
  }

  public void assertOffset(int... expectedOffsets) {
    final List<Caret> carets = myFixture.getEditor().getCaretModel().getAllCarets();
    assertEquals("Wrong amount of carets", expectedOffsets.length, carets.size());
    for (int i = 0; i < expectedOffsets.length; i++) {
      assertEquals(expectedOffsets[i], carets.get(i).getOffset());
    }
  }

  public void assertMode(@NotNull CommandState.Mode expectedMode) {
    final CommandState.Mode mode = CommandState.getInstance(myFixture.getEditor()).getMode();
    assertEquals(expectedMode, mode);
  }

  public void assertSelection(@Nullable String expected) {
    final String selected = myFixture.getEditor().getSelectionModel().getSelectedText();
    assertEquals(expected, selected);
  }

  public void assertExOutput(@NotNull String expected) {
    final String actual = ExOutputModel.getInstance(myFixture.getEditor()).getText();
    assertNotNull("No Ex output", actual);
    assertEquals(expected, actual);
  }

  public void assertPluginError(boolean isError) {
    assertEquals(isError, VimPlugin.isError());
  }

  public void doTest(final List<KeyStroke> keys, String before, String after) {
    configureByText(before);
    typeText(keys);
    myFixture.checkResult(after);
  }
}
