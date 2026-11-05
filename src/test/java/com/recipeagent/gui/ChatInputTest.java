package com.recipeagent.gui;

import com.recipeagent.controller.AgentController;
import com.recipeagent.storage.JsonStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JComponent;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.text.DefaultEditorKit;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.nio.file.Path;
import java.time.Clock;

import static org.junit.jupiter.api.Assertions.*;

/** Enter sends a chat message; Shift+Enter starts a new line. Runs without a screen. */
class ChatInputTest {

    @TempDir
    Path tmp;

    private static void run(JTextArea area, KeyStroke key) {
        Object name = area.getInputMap(JComponent.WHEN_FOCUSED).get(key);
        area.getActionMap().get(name).actionPerformed(new ActionEvent(area, ActionEvent.ACTION_PERFORMED, ""));
    }

    @Test
    void shiftEnterAddsALineAndEnterSends() throws Exception {
        AgentController c = new AgentController(null, 100, new JsonStorage(tmp), Clock.systemDefaultZone());
        ChatPanel[] panel = new ChatPanel[1];
        SwingUtilities.invokeAndWait(() -> panel[0] = new ChatPanel(c, () -> { }));
        JTextArea input = panel[0].getInput();

        KeyStroke enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0);
        KeyStroke shiftEnter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, InputEvent.SHIFT_DOWN_MASK);
        assertEquals(DefaultEditorKit.insertBreakAction, input.getInputMap(JComponent.WHEN_FOCUSED).get(shiftEnter));

        int rowsBefore = panel[0].getMessageRowCount();
        SwingUtilities.invokeAndWait(() -> {
            input.setText("2 milk");
            input.setCaretPosition(input.getText().length());
            run(input, shiftEnter);
            input.replaceSelection("1 bread");
        });
        assertEquals("2 milk\n1 bread", input.getText());
        assertEquals(rowsBefore, panel[0].getMessageRowCount());        // nothing sent yet

        SwingUtilities.invokeAndWait(() -> run(input, enter));
        assertEquals("", input.getText());                              // sent and cleared
        assertTrue(panel[0].getMessageRowCount() > rowsBefore);

        SwingUtilities.invokeAndWait(() -> {
            input.setText("   ");
            run(input, enter);
        });
        assertEquals("   ", input.getText());                           // blank messages are not sent
    }
}
