package com.recipeagent.gui;

import com.recipeagent.controller.AgentController;
import com.recipeagent.llm.LLMException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * The Chat side panel (F09): the user types in plain language ("Add a dozen eggs to my
 * cart"), the agent decides which tools to use, and its answer appears as a bubble with
 * markers for anything it changed ("Cart updated"). The panel can be collapsed to a thin strip.
 */
public class ChatPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    static final int WIDTH = 280;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    private final transient AgentController controller;
    private final transient Runnable dataChanged;

    private final JPanel body = new JPanel(new BorderLayout());
    private final JPanel collapsedStrip = new JPanel(new BorderLayout());
    private final JPanel messages = new JPanel();
    private final JScrollPane scroll;
    private final JTextField input = new JTextField();
    private final JButton send = Theme.primary("Send");
    private JLabel thinking;
    private boolean startedToday;

    /** @param dataChanged called after the agent answered, so other pages can refresh */
    public ChatPanel(AgentController controller, Runnable dataChanged) {
        super(new BorderLayout());
        this.controller = controller;
        this.dataChanged = dataChanged;
        setBorder(BorderFactory.createMatteBorder(0, 1, 0, 0, Theme.BORDER));
        setBackground(Theme.CARD);

        // header
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.CARD);
        header.setBorder(BorderFactory.createEmptyBorder(10, 12, 6, 8));
        JPanel title = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        title.setOpaque(false);
        title.add(Theme.label("Chat", Font.BOLD, 13f, Theme.TEXT));
        title.add(new Badge("AI"));
        header.add(title, BorderLayout.WEST);
        JButton collapse = Theme.secondary("Collapse");
        collapse.setFont(Theme.font(Font.PLAIN, 11f));
        collapse.addActionListener(e -> setCollapsed(true));
        header.add(collapse, BorderLayout.EAST);
        body.add(header, BorderLayout.NORTH);

        // messages
        messages.setLayout(new BoxLayout(messages, BoxLayout.Y_AXIS));
        messages.setBackground(Theme.CARD);
        messages.setBorder(BorderFactory.createEmptyBorder(4, 12, 8, 12));
        JLabel intro = Theme.label("A little help with everyday food.", Font.BOLD, 12f, Theme.TEXT);
        JLabel intro2 = Theme.muted(ChatFormat.bubbleHtml(
                "Add groceries, use what you have, or ask for a quick meal."));
        addRow(intro, false);
        addRow(intro2, false);
        scroll = new JScrollPane(messages);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        body.add(scroll, BorderLayout.CENTER);

        // input
        JPanel bottom = new JPanel(new BorderLayout(6, 4));
        bottom.setBackground(Theme.CARD);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 12, 10, 12));
        input.putClientProperty("JTextField.placeholderText", "Ask or add an item…");
        input.setToolTipText("Ask or add an item, e.g. \"What can I make with chicken?\"");
        input.addActionListener(e -> sendMessage());
        send.addActionListener(e -> sendMessage());
        bottom.add(input, BorderLayout.CENTER);
        bottom.add(send, BorderLayout.EAST);
        bottom.add(Theme.muted("Review AI suggestions before cooking."), BorderLayout.SOUTH);
        body.add(bottom, BorderLayout.SOUTH);
        body.setPreferredSize(new Dimension(WIDTH, 100));

        // collapsed strip
        JButton expand = Theme.secondary("Chat");
        expand.addActionListener(e -> setCollapsed(false));
        collapsedStrip.setBackground(Theme.CARD);
        collapsedStrip.setBorder(BorderFactory.createEmptyBorder(10, 6, 10, 6));
        collapsedStrip.add(expand, BorderLayout.NORTH);

        add(body, BorderLayout.CENTER);
    }

    public void setCollapsed(boolean collapsed) {
        removeAll();
        add(collapsed ? collapsedStrip : body, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    public boolean isCollapsed() {
        return collapsedStrip.getParent() == this;
    }

    /** Sends the typed message to the agent; the answer arrives in the background. */
    void sendMessage() {
        String text = input.getText().trim();
        if (text.isEmpty() || !send.isEnabled()) {
            return;
        }
        input.setText("");
        send(text);
    }

    /** Sends a message as if the user typed it (also used for "Help Me Set Up"). */
    public void send(String text) {
        if (isCollapsed()) {
            setCollapsed(false);
        }
        if (!startedToday) {
            JLabel when = Theme.muted("Today · " + LocalTime.now().format(TIME));
            when.setAlignmentX(CENTER_ALIGNMENT);
            addCentered(when);
            startedToday = true;
        }
        addUserBubble(text);
        Background.run(this::setBusy,
                () -> controller.chat(text),
                answer -> {
                    addAgentBubble(answer, ChatFormat.changeMarkers(controller.getLastChatActions()), false);
                    dataChanged.run();
                },
                ex -> {
                    String msg = ex instanceof LLMException ? ex.getMessage() : "Something went wrong: " + ex.getMessage();
                    addAgentBubble(msg, ChatFormat.changeMarkers(controller.getLastChatActions()), true);
                    dataChanged.run();
                });
    }

    private void setBusy(boolean busy) {
        send.setEnabled(!busy);
        input.setEnabled(!busy);
        if (busy) {
            thinking = Theme.muted("Smart Pantry is thinking…");
            addRow(thinking, false);
        } else if (thinking != null) {
            messages.remove(thinking.getParent());
            thinking = null;
            input.requestFocusInWindow();
        }
        refreshMessages();
    }

    private void addUserBubble(String text) {
        JLabel who = Theme.muted("You");
        addRow(who, true);
        Bubble b = new Bubble(ChatFormat.bubbleHtml(text), Theme.PRIMARY_LIGHT, Theme.TEXT, false);
        addRow(b, true);
    }

    private void addAgentBubble(String text, List<String> markers, boolean error) {
        addRow(Theme.muted("Smart Pantry"), false);
        Bubble b = new Bubble(ChatFormat.bubbleHtml(text), Theme.CARD,
                error ? Theme.DANGER_FG : Theme.TEXT, true);
        for (String m : markers) {
            b.addMarker(m);
        }
        addRow(b, false);
    }

    private void addRow(Component c, boolean right) {
        JPanel row = new JPanel(new FlowLayout(right ? FlowLayout.RIGHT : FlowLayout.LEFT, 0, 2));
        row.setOpaque(false);
        row.add(c);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 4));
        messages.add(row);
        refreshMessages();
    }

    private void addCentered(Component c) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 6));
        row.setOpaque(false);
        row.add(c);
        row.setAlignmentX(LEFT_ALIGNMENT);
        row.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 12));
        messages.add(row);
    }

    private void refreshMessages() {
        messages.revalidate();
        messages.repaint();
        SwingUtilities.invokeLater(() ->
                scroll.getVerticalScrollBar().setValue(scroll.getVerticalScrollBar().getMaximum()));
    }

    int getMessageRowCount() {
        return messages.getComponentCount();
    }

    /** A rounded message bubble, optionally with change markers underneath. */
    private static class Bubble extends JPanel {
        private static final long serialVersionUID = 1L;
        private final Color fill;
        private final boolean outlined;

        Bubble(String html, Color fill, Color textColor, boolean outlined) {
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            this.fill = fill;
            this.outlined = outlined;
            setOpaque(false);
            setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
            JLabel l = new JLabel(html);
            l.setFont(Theme.font(Font.PLAIN, 12f));
            l.setForeground(textColor);
            add(l);
        }

        void addMarker(String text) {
            add(Box.createVerticalStrut(4));
            JLabel m = Theme.label("✓ " + text, Font.PLAIN, 11f, Theme.SALE_FG);
            if (!m.getFont().canDisplay('✓')) {
                m.setText(text);
            }
            add(m);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(fill);
            g2.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            if (outlined) {
                g2.setColor(Theme.BORDER);
                g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 10, 10);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A small "AI" label. */
    static class Badge extends JLabel {
        private static final long serialVersionUID = 1L;

        Badge(String text) {
            super(text);
            setFont(Theme.font(Font.BOLD, 10f));
            setForeground(Theme.PRIMARY);
            setBorder(BorderFactory.createEmptyBorder(1, 5, 1, 5));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(Theme.PRIMARY_LIGHT);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
            g2.dispose();
            super.paintComponent(g);
        }
    }
}
