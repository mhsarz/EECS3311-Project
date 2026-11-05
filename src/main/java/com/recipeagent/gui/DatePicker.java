package com.recipeagent.gui;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * A date field that opens a month calendar when clicked: move between months (‹ ›) and
 * years (« »), click a day to pick it, or click Today. With the keyboard, the arrow keys move
 * the date by a day (left/right) or a week (up/down), and Space opens the calendar.
 */
public class DatePicker extends JPanel {

    private static final long serialVersionUID = 1L;
    static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);
    private static final Color OTHER_MONTH = new Color(0xB0B5AE);

    private final transient Supplier<LocalDate> today;
    private final JButton field = new JButton();
    private final JPopupMenu popup = new JPopupMenu();
    private final JLabel monthLabel = Theme.label(" ", Font.BOLD, 13f, Theme.TEXT);
    private final JPanel days = new JPanel(new GridLayout(0, 7, 2, 2));
    private transient LocalDate date;
    private transient YearMonth shownMonth;

    /** @param today gives today's date (the controller's clock), for the Today button and marker */
    public DatePicker(LocalDate initial, Supplier<LocalDate> today) {
        super(new BorderLayout());
        this.today = today;
        setOpaque(false);
        field.setHorizontalAlignment(SwingConstants.LEFT);
        field.setFont(Theme.font(Font.PLAIN, 12f));
        field.setIcon(new CalendarIcon());
        field.setIconTextGap(8);
        field.setFocusPainted(true);
        field.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        field.setToolTipText("Click to pick a date. Arrow keys change it by a day or a week.");
        field.addActionListener(e -> openCalendar());
        bindKey(KeyEvent.VK_LEFT, -1);
        bindKey(KeyEvent.VK_RIGHT, 1);
        bindKey(KeyEvent.VK_UP, -7);
        bindKey(KeyEvent.VK_DOWN, 7);
        add(field, BorderLayout.CENTER);
        buildPopup();
        setDate(initial);
    }

    private void bindKey(int key, int daysToAdd) {
        String name = "move" + daysToAdd;
        field.getInputMap(JComponent.WHEN_FOCUSED).put(KeyStroke.getKeyStroke(key, 0), name);
        field.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                setDate(date.plusDays(daysToAdd));
            }
        });
    }

    private void buildPopup() {
        JPanel panel = new JPanel(new BorderLayout(0, 6));
        panel.setBackground(Theme.CARD);
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        JPanel back = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        back.setOpaque(false);
        back.add(navButton("«", "Previous year", () -> showMonth(shownMonth.minusYears(1))));
        back.add(navButton("‹", "Previous month", () -> showMonth(shownMonth.minusMonths(1))));
        JPanel forward = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        forward.setOpaque(false);
        forward.add(navButton("›", "Next month", () -> showMonth(shownMonth.plusMonths(1))));
        forward.add(navButton("»", "Next year", () -> showMonth(shownMonth.plusYears(1))));
        monthLabel.setHorizontalAlignment(SwingConstants.CENTER);
        head.add(back, BorderLayout.WEST);
        head.add(monthLabel, BorderLayout.CENTER);
        head.add(forward, BorderLayout.EAST);
        panel.add(head, BorderLayout.NORTH);

        days.setOpaque(false);
        panel.add(days, BorderLayout.CENTER);

        JPanel foot = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        foot.setOpaque(false);
        JButton todayButton = Theme.secondary("Today");
        todayButton.addActionListener(e -> pick(today.get()));
        foot.add(todayButton);
        panel.add(foot, BorderLayout.SOUTH);

        popup.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        popup.add(panel);
    }

    private static JButton navButton(String text, String tip, Runnable action) {
        JButton b = Theme.secondary(text);
        b.setToolTipText(tip);
        b.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        b.addActionListener(e -> action.run());
        return b;
    }

    private void openCalendar() {
        showMonth(YearMonth.from(date));
        popup.show(field, 0, field.getHeight());
    }

    private void showMonth(YearMonth month) {
        shownMonth = month;
        monthLabel.setText(monthTitle(month));
        days.removeAll();
        for (String d : weekdayNames()) {
            JLabel l = Theme.label(d, Font.BOLD, 11f, Theme.MUTED);
            l.setHorizontalAlignment(SwingConstants.CENTER);
            days.add(l);
        }
        LocalDate now = today.get();
        for (LocalDate d : monthCells(month)) {
            days.add(new DayButton(d, d.getMonth() == month.getMonth(), d.equals(date), d.equals(now)));
        }
        days.revalidate();
        days.repaint();
        popup.pack();
    }

    private void pick(LocalDate d) {
        setDate(d);
        popup.setVisible(false);
        field.requestFocusInWindow();
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate d) {
        if (d == null) {
            throw new IllegalArgumentException("Date must not be null (use the No date box instead)");
        }
        date = d;
        field.setText(d.format(SHOWN));
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        field.setEnabled(enabled);
    }

    @Override
    public Dimension getPreferredSize() {
        Dimension d = super.getPreferredSize();
        return new Dimension(Math.max(d.width, 150), d.height);
    }

    String getShownText() {
        return field.getText();
    }

    // ------------------------------------------------------------ calendar maths (tested)

    /** The 42 days (6 weeks, Sunday first) shown for a month, including days of the months around it. */
    static List<LocalDate> monthCells(YearMonth month) {
        LocalDate first = month.atDay(1);
        int back = first.getDayOfWeek().getValue() % 7;     // Sunday = 0 ... Saturday = 6
        LocalDate start = first.minusDays(back);
        List<LocalDate> cells = new ArrayList<>();
        for (int i = 0; i < 42; i++) {
            cells.add(start.plusDays(i));
        }
        return cells;
    }

    /** "November 2026". */
    static String monthTitle(YearMonth month) {
        return month.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH) + " " + month.getYear();
    }

    /** "Su", "Mo", ... "Sa". */
    static List<String> weekdayNames() {
        List<String> names = new ArrayList<>();
        DayOfWeek d = DayOfWeek.SUNDAY;
        for (int i = 0; i < 7; i++) {
            names.add(d.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).substring(0, 2));
            d = d.plus(1);
        }
        return names;
    }

    // ------------------------------------------------------------ painting

    /** One day in the calendar: green when selected, outlined when it is today. */
    private final class DayButton extends JButton {
        private static final long serialVersionUID = 1L;
        private final boolean selected;
        private final boolean isToday;

        DayButton(LocalDate d, boolean inMonth, boolean selected, boolean isToday) {
            super(String.valueOf(d.getDayOfMonth()));
            this.selected = selected;
            this.isToday = isToday;
            setFont(Theme.font(selected || isToday ? Font.BOLD : Font.PLAIN, 12f));
            setForeground(selected ? Color.WHITE : inMonth ? Theme.TEXT : OTHER_MONTH);
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setMargin(new java.awt.Insets(0, 0, 0, 0));
            setPreferredSize(new Dimension(32, 28));
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setToolTipText(d.format(SHOWN));
            addActionListener(e -> pick(d));
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (selected) {
                g2.setColor(Theme.PRIMARY);
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);
            } else if (getModel().isRollover()) {
                g2.setColor(Theme.PRIMARY_LIGHT);
                g2.fillRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 8, 8);
            }
            if (isToday && !selected) {
                g2.setColor(Theme.PRIMARY);
                g2.drawRoundRect(1, 1, getWidth() - 3, getHeight() - 3, 8, 8);
            }
            g2.dispose();
            super.paintComponent(g);
        }
    }

    /** A small calendar drawn in code for the date field. */
    private static final class CalendarIcon implements javax.swing.Icon {
        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(c.isEnabled() ? Theme.PRIMARY : Theme.MUTED);
            g2.setStroke(new BasicStroke(1.4f));
            g2.drawRoundRect(x + 1, y + 3, 12, 11, 3, 3);
            g2.drawLine(x + 1, y + 7, x + 13, y + 7);
            g2.drawLine(x + 4, y + 1, x + 4, y + 4);
            g2.drawLine(x + 10, y + 1, x + 10, y + 4);
            g2.dispose();
        }

        @Override
        public int getIconWidth() {
            return 15;
        }

        @Override
        public int getIconHeight() {
            return 15;
        }
    }
}
