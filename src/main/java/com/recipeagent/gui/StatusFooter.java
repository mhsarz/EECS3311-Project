package com.recipeagent.gui;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;

/** The bottom line: where data is kept, and whether the AI can be used. */
public class StatusFooter extends JPanel {

    private static final long serialVersionUID = 1L;

    private final JLabel ai = Theme.muted("");

    public StatusFooter() {
        super(new BorderLayout());
        setBackground(Theme.BACKGROUND);
        setBorder(BorderFactory.createEmptyBorder(4, 16, 6, 16));
        add(Theme.muted("Personal pantry · All changes saved locally"), BorderLayout.WEST);
        add(ai, BorderLayout.EAST);
    }

    /** @param status e.g. "AI ready (98 of 100 calls left today)" or "AI unavailable: ..." */
    public void setAiStatus(String status, boolean available) {
        ai.setText(status);
        ai.setIcon(new AlertsStrip.Dot(available ? Theme.SALE_FG : Theme.DANGER_FG));
        ai.setForeground(available ? Theme.SALE_FG : Theme.DANGER_FG);
    }

    String getAiText() {
        return ai.getText();
    }
}
