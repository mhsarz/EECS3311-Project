package com.recipeagent.gui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * "How was it?" after a recipe is marked as cooked for the first time (F06, F08): five stars
 * to click (or keys 1-5), then Save rating, or Skip for now (also Esc). The rating can be
 * changed later in the History tab.
 */
final class RateDialog extends JDialog {

    private static final long serialVersionUID = 1L;
    static final int STAR_SIZE = 30;
    static final int STAR_GAP = 8;
    private static final Color STAR_ON = new Color(0xE0A100);
    private static final Color STAR_OFF = new Color(0xC9CEC6);

    private final Stars stars = new Stars();
    private final JLabel hint = Theme.muted(ratingText(0));
    private final JButton save = Theme.primary("Save rating");
    private int chosen;
    private int result;

    private RateDialog(Component parent, String recipeTitle) {
        super(SwingUtilities.getWindowAncestor(parent), "Rate this recipe", Dialog.ModalityType.APPLICATION_MODAL);
        JPanel body = new JPanel(new BorderLayout(0, 12));
        body.setBackground(Theme.CARD);
        body.setBorder(BorderFactory.createEmptyBorder(16, 20, 14, 20));

        JPanel top = new JPanel(new BorderLayout(0, 4));
        top.setOpaque(false);
        top.add(Theme.label("How was \"" + recipeTitle + "\"?", Font.BOLD, 15f, Theme.TEXT), BorderLayout.NORTH);
        top.add(Theme.muted("Your rating helps the agent suggest recipes you like. "
                + "You can change it later in History."), BorderLayout.SOUTH);
        body.add(top, BorderLayout.NORTH);

        JPanel middle = new JPanel(new BorderLayout(0, 6));
        middle.setOpaque(false);
        JPanel starRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        starRow.setOpaque(false);
        starRow.add(stars);
        middle.add(starRow, BorderLayout.CENTER);
        middle.add(hint, BorderLayout.SOUTH);
        body.add(middle, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        buttons.setOpaque(false);
        JButton skip = Theme.secondary("Skip for now");
        skip.addActionListener(e -> dispose());
        save.setEnabled(false);
        save.addActionListener(e -> {
            result = chosen;
            dispose();
        });
        buttons.add(skip);
        buttons.add(save);
        body.add(buttons, BorderLayout.SOUTH);

        for (int n = 1; n <= 5; n++) {
            int value = n;
            String key = "rate" + n;
            body.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke((char) ('0' + n)), key);
            body.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + n, 0), key);
            body.getActionMap().put(key, new AbstractAction() {
                private static final long serialVersionUID = 1L;

                @Override
                public void actionPerformed(ActionEvent e) {
                    choose(value);
                }
            });
        }
        body.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "skip");
        body.getActionMap().put("skip", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        setContentPane(body);
        getRootPane().setDefaultButton(save);
        pack();
        setMinimumSize(new Dimension(380, getHeight()));
        setResizable(false);
        setLocationRelativeTo(parent);
    }

    /**
     * Shows the dialog.
     *
     * @return the stars chosen (1-5), or 0 if the user skipped
     */
    static int ask(Component parent, String recipeTitle) {
        RateDialog d = new RateDialog(parent, recipeTitle);
        d.setVisible(true);
        return d.result;
    }

    private void choose(int n) {
        chosen = n;
        save.setEnabled(true);
        hint.setText(ratingText(n));
        stars.repaint();
    }

    /** "Click a star to rate", "1 of 5 stars", "4 of 5 stars". */
    static String ratingText(int n) {
        if (n <= 0) {
            return "Click a star to rate (or press 1-5).";
        }
        return n + " of 5 " + (n == 1 ? "star" : "stars");
    }

    /** Which star (1-5) is at this x position in the star row, or 0 in a gap or outside. */
    static int starAt(int x) {
        if (x < 0) {
            return 0;
        }
        int step = STAR_SIZE + STAR_GAP;
        int n = x / step;
        if (n >= 5 || x - n * step >= STAR_SIZE) {
            return 0;
        }
        return n + 1;
    }

    /** The five stars: hovering shows the rating under the mouse, a click chooses it. */
    private final class Stars extends JComponent {
        private static final long serialVersionUID = 1L;
        private int hover;

        Stars() {
            setPreferredSize(new Dimension(5 * STAR_SIZE + 4 * STAR_GAP, STAR_SIZE + 2));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            MouseAdapter mouse = new MouseAdapter() {
                @Override
                public void mouseMoved(MouseEvent e) {
                    int n = starAt(e.getX());
                    if (n != hover) {
                        hover = n;
                        hint.setText(ratingText(n > 0 ? n : chosen));
                        repaint();
                    }
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    hover = 0;
                    hint.setText(ratingText(chosen));
                    repaint();
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    int n = starAt(e.getX());
                    if (n > 0) {
                        choose(n);
                        if (e.getClickCount() == 2) {
                            save.doClick();
                        }
                    }
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int shown = hover > 0 ? hover : chosen;
            for (int i = 0; i < 5; i++) {
                paintStar(g2, i * (STAR_SIZE + STAR_GAP), 1, i < shown);
            }
            g2.dispose();
        }

        private void paintStar(Graphics2D g2, int x, int y, boolean on) {
            Polygon star = new Polygon();
            double cx = x + STAR_SIZE / 2.0;
            double cy = y + STAR_SIZE / 2.0 + 1;
            for (int i = 0; i < 10; i++) {
                double r = i % 2 == 0 ? STAR_SIZE / 2.0 : STAR_SIZE / 4.6;
                double a = Math.PI / 2 + i * Math.PI / 5;
                star.addPoint((int) Math.round(cx + r * Math.cos(a)), (int) Math.round(cy - r * Math.sin(a)));
            }
            if (on) {
                g2.setColor(STAR_ON);
                g2.fillPolygon(star);
            } else {
                g2.setColor(STAR_OFF);
                g2.setStroke(new BasicStroke(1.6f));
                g2.drawPolygon(star);
            }
        }
    }
}
