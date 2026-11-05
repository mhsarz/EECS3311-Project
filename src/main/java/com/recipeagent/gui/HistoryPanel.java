package com.recipeagent.gui;

import com.recipeagent.controller.AgentController;
import com.recipeagent.model.Recipe;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableColumn;
import javax.swing.table.TableRowSorter;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Path2D;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Consumer;

/**
 * The History page (F08), as in the Smart Pantry design: saved recipes, most recently cooked
 * first, with a heart for favourites and 1-5 stars that you click to rate (or press 1-5 and F
 * on the selected row). Search by name or ingredient, show favourites only, open a recipe in
 * the Recipe tab or remove it. A footer shows the totals. The top-rated recipes are passed
 * to the agent when a new recipe is generated (F01).
 */
public class HistoryPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String TABLE = "table";
    private static final String EMPTY = "empty";
    static final int STAR_LEFT = 10;
    static final int STAR_SIZE = 14;
    static final int STAR_STEP = 18;
    private static final Color STAR_ON = new Color(0xE0A100);
    private static final Color STAR_OFF = new Color(0xC9CEC6);
    private static final Color HEART_ON = new Color(0xD64545);

    private final transient AgentController controller;
    private final transient Runnable dataChanged;
    private final transient Consumer<Recipe> openRecipe;

    private final PageHeader header = new PageHeader("History", "Keep your favourites close. Make a good meal again.");
    private final HistoryTableModel model = new HistoryTableModel();
    private final JTable table = new JTable(model);
    private final JTextField searchField = new JTextField(22);
    private final JCheckBox favouritesOnly = new JCheckBox("Favourites only");
    private final JButton openButton = Theme.primary("Open recipe");
    private final JButton removeButton = Theme.secondary("Remove");
    private final JLabel statsLabel = Theme.muted(" ");
    private final CardLayout cards = new CardLayout();
    private final JPanel center = new JPanel(cards);
    private final JLabel emptyTitle = Theme.label(" ", Font.BOLD, 15f, Theme.TEXT);
    private final JLabel emptyText = Theme.muted(" ");
    private final JButton clearSearch = Theme.secondary("Clear search");

    /**
     * @param openRecipe   shows a recipe in the Recipe tab (and switches to it)
     * @param goToGenerate switches to the Recipe tab to generate a new recipe
     */
    public HistoryPanel(AgentController controller, Runnable dataChanged,
                        Consumer<Recipe> openRecipe, Runnable goToGenerate) {
        super(new BorderLayout(0, 10));
        this.controller = controller;
        this.dataChanged = dataChanged;
        this.openRecipe = openRecipe;
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 16));

        JPanel north = new JPanel(new BorderLayout(0, 4));
        north.setOpaque(false);
        north.add(header, BorderLayout.NORTH);
        north.add(buildToolbar(), BorderLayout.CENTER);
        add(north, BorderLayout.NORTH);

        buildTable();
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Theme.CARD);
        JPanel tableCard = new JPanel(new BorderLayout(0, 6));
        tableCard.setOpaque(false);
        tableCard.add(sp, BorderLayout.CENTER);
        tableCard.add(Theme.muted("Click a star to rate and the heart to favourite (or press 1-5 and F). "
                + "Double-click a recipe to open it."), BorderLayout.SOUTH);
        center.setOpaque(false);
        center.add(tableCard, TABLE);
        center.add(buildEmptyState(goToGenerate), EMPTY);
        add(center, BorderLayout.CENTER);

        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        footer.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, Theme.BORDER));
        statsLabel.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        footer.add(statsLabel, BorderLayout.WEST);
        add(footer, BorderLayout.SOUTH);

        refresh();
    }

    // ------------------------------------------------------------ building the page

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(Theme.CARD);
        bar.setBorder(Theme.card(10));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        left.setOpaque(false);
        left.add(Theme.muted("Search"));
        searchField.putClientProperty("JTextField.placeholderText", "Recipe name or ingredient");
        searchField.setToolTipText("Search by recipe name or ingredient, e.g. chicken");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                refresh();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                refresh();
            }
        });
        left.add(searchField);
        favouritesOnly.setOpaque(false);
        favouritesOnly.addActionListener(e -> refresh());
        left.add(favouritesOnly);
        bar.add(left, BorderLayout.WEST);

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        right.setOpaque(false);
        removeButton.addActionListener(e -> removeSelected());
        openButton.addActionListener(e -> openSelected());
        right.add(removeButton);
        right.add(openButton);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    private void buildTable() {
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(32);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setRowSorter(new TableRowSorter<>(model));
        table.getTableHeader().setReorderingAllowed(false);
        CellRenderer renderer = new CellRenderer();
        for (Class<?> k : new Class<?>[]{Object.class, Boolean.class, Integer.class, LocalDate.class}) {
            table.setDefaultRenderer(k, renderer);
        }
        TableColumn fav = table.getColumnModel().getColumn(HistoryTableModel.FAV_COLUMN);
        fav.setMinWidth(40);
        fav.setMaxWidth(40);
        TableColumn rating = table.getColumnModel().getColumn(HistoryTableModel.RATING_COLUMN);
        rating.setMinWidth(STAR_LEFT + 5 * STAR_STEP + 10);
        table.getColumnModel().getColumn(HistoryTableModel.TITLE_COLUMN).setPreferredWidth(260);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());

        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clicked(e);
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                int col = table.convertColumnIndexToModel(table.columnAtPoint(e.getPoint()));
                boolean hand = col == HistoryTableModel.FAV_COLUMN
                        || col == HistoryTableModel.RATING_COLUMN && starAt(xInCell(e)) > 0;
                table.setCursor(Cursor.getPredefinedCursor(hand ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
            }
        };
        table.addMouseListener(mouse);
        table.addMouseMotionListener(mouse);

        for (int stars = 1; stars <= 5; stars++) {
            int s = stars;
            bindKey(KeyStroke.getKeyStroke((char) ('0' + s)), "rate" + s, () -> rateSelected(s));
            bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_NUMPAD0 + s, 0), "rateNumpad" + s, () -> rateSelected(s));
        }
        bindKey(KeyStroke.getKeyStroke('f'), "favourite", this::toggleFavouriteSelected);
        bindKey(KeyStroke.getKeyStroke('F'), "favouriteShift", this::toggleFavouriteSelected);
        bindKey(KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "open", this::openSelected);
    }

    private void bindKey(KeyStroke key, String name, Runnable action) {
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(key, name);
        table.getInputMap(JComponent.WHEN_FOCUSED).put(key, name);
        table.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private JPanel buildEmptyState(Runnable goToGenerate) {
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setBackground(Theme.CARD);
        empty.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        emptyText.setHorizontalAlignment(SwingConstants.CENTER);
        JButton generate = Theme.ai("Generate Recipe", true);
        generate.addActionListener(e -> goToGenerate.run());
        clearSearch.addActionListener(e -> {
            searchField.setText("");
            favouritesOnly.setSelected(false);
            refresh();
        });
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
        buttons.setOpaque(false);
        buttons.add(clearSearch);
        buttons.add(generate);
        for (JComponent c : new JComponent[]{emptyTitle, emptyText, buttons}) {
            c.setAlignmentX(CENTER_ALIGNMENT);
            box.add(c);
            box.add(Box.createVerticalStrut(10));
        }
        empty.add(box);
        return empty;
    }

    // ------------------------------------------------------------ showing recipes

    /** Reloads the list using the search text and the favourites filter. */
    public void refresh() {
        Recipe keep = selectedOrNull();
        String query = searchField.getText();
        List<Recipe> found = controller.searchHistory(query);
        if (favouritesOnly.isSelected()) {
            found = found.stream().filter(Recipe::isFavourite).toList();
        }
        model.setRecipes(found);
        List<Recipe> all = controller.getHistory();
        header.setRight(all.size() + (all.size() == 1 ? " recipe" : " recipes"));
        statsLabel.setText(HistoryTableModel.stats(all));
        if (found.isEmpty()) {
            boolean filtered = !query.isBlank() || favouritesOnly.isSelected();
            emptyTitle.setText(emptyTitle(all.isEmpty(), query, favouritesOnly.isSelected()));
            emptyText.setText("<html><div style='text-align:center;width:320px'>" + (filtered
                    ? "Try another word, or generate a new recipe."
                    : "Generate a recipe and click Save to History. Your top-rated recipes help the "
                    + "agent suggest better meals.") + "</div></html>");
            clearSearch.setVisible(filtered);
            cards.show(center, EMPTY);
        } else {
            cards.show(center, TABLE);
            if (keep != null) {
                reselect(keep);
            }
        }
        updateButtons();
    }

    static String emptyTitle(boolean noneSaved, String query, boolean favouritesOnly) {
        if (noneSaved) {
            return "No saved recipes yet";
        }
        if (query != null && !query.isBlank()) {
            return "No recipes match \"" + query.trim() + "\"" + (favouritesOnly ? " in your favourites" : "");
        }
        return favouritesOnly ? "No favourites yet" : "No saved recipes yet";
    }

    private void updateButtons() {
        boolean selected = table.getSelectedRow() >= 0 && model.getRowCount() > 0;
        openButton.setEnabled(selected);
        removeButton.setEnabled(selected);
    }

    // ------------------------------------------------------------ actions

    private void clicked(MouseEvent e) {
        int viewRow = table.rowAtPoint(e.getPoint());
        if (viewRow < 0) {
            return;
        }
        Recipe r = model.getRecipeAt(table.convertRowIndexToModel(viewRow));
        int col = table.convertColumnIndexToModel(table.columnAtPoint(e.getPoint()));
        if (col == HistoryTableModel.FAV_COLUMN) {
            controller.toggleFavourite(r.getId());
            changed(r);
        } else if (col == HistoryTableModel.RATING_COLUMN) {
            int stars = starAt(xInCell(e));
            if (stars > 0) {
                controller.rateRecipe(r.getId(), stars);
                changed(r);
            }
        } else if (e.getClickCount() == 2) {
            openRecipe.accept(r);
        }
    }

    private int xInCell(MouseEvent e) {
        int col = table.columnAtPoint(e.getPoint());
        Rectangle cell = table.getCellRect(Math.max(0, table.rowAtPoint(e.getPoint())), Math.max(0, col), false);
        return e.getX() - cell.x;
    }

    /** Which star (1-5) is at this x position inside the Rating cell, or 0 between/after them. */
    static int starAt(int x) {
        int offset = x - STAR_LEFT;
        if (offset < 0) {
            return 0;
        }
        int index = offset / STAR_STEP;
        return index < 5 && offset % STAR_STEP <= STAR_SIZE ? index + 1 : 0;
    }

    private void rateSelected(int stars) {
        Recipe r = selectedOrNull();
        if (r != null) {
            controller.rateRecipe(r.getId(), stars);
            changed(r);
        }
    }

    private void toggleFavouriteSelected() {
        Recipe r = selectedOrNull();
        if (r != null) {
            controller.toggleFavourite(r.getId());
            changed(r);
        }
    }

    private void openSelected() {
        Recipe r = selectedOrNull();
        if (r != null) {
            openRecipe.accept(r);
        }
    }

    private void removeSelected() {
        Recipe r = selectedOrNull();
        if (r == null) {
            return;
        }
        int ok = JOptionPane.showConfirmDialog(this, "Remove \"" + r.getTitle() + "\" from your history?\n"
                + "Its rating and cook record are removed too.", "Remove recipe", JOptionPane.OK_CANCEL_OPTION);
        if (ok == JOptionPane.OK_OPTION) {
            controller.removeRecipe(r.getId());
            table.clearSelection();
            dataChanged.run();
            refresh();
        }
    }

    /** After a rating or favourite change: refresh everything and keep the row selected. */
    private void changed(Recipe r) {
        dataChanged.run();
        refresh();
        reselect(r);
    }

    private Recipe selectedOrNull() {
        int viewRow = table.getSelectedRow();
        return viewRow < 0 || viewRow >= model.getRowCount() ? null
                : model.getRecipeAt(table.convertRowIndexToModel(viewRow));
    }

    private void reselect(Recipe r) {
        for (int i = 0; i < model.getRowCount(); i++) {
            if (model.getRecipeAt(i).getId().equals(r.getId())) {
                int view = table.convertRowIndexToView(i);
                table.setRowSelectionInterval(view, view);
                return;
            }
        }
    }

    // ------------------------------------------------------------ painting

    /** Draws the heart and stars; formats dates, cook counts and minutes. */
    private static final class CellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private int column;
        private Object value;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean selected,
                                                       boolean focus, int row, int col) {
            column = t.convertColumnIndexToModel(col);
            value = v;
            Object shown;
            switch (column) {
                case HistoryTableModel.FAV_COLUMN:
                case HistoryTableModel.RATING_COLUMN:
                    shown = "";
                    break;
                case HistoryTableModel.LAST_COOKED_COLUMN:
                    shown = HistoryTableModel.lastCooked((LocalDate) v);
                    break;
                case HistoryTableModel.TIME_COLUMN:
                    shown = v + " min";
                    break;
                default:
                    shown = v;
            }
            super.getTableCellRendererComponent(t, shown, selected, false, row, col);
            setHorizontalAlignment(LEFT);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setFont(Theme.font(column == HistoryTableModel.TITLE_COLUMN ? Font.BOLD : Font.PLAIN, 12f));
            boolean never = column == HistoryTableModel.LAST_COOKED_COLUMN && v == null;
            setForeground(never ? Theme.MUTED : Theme.TEXT);
            setBackground(selected ? Theme.PRIMARY_LIGHT : Theme.CARD);
            if (column == HistoryTableModel.FAV_COLUMN) {
                setToolTipText(Boolean.TRUE.equals(v) ? "Favourite (click to remove)" : "Click to make a favourite");
            } else if (column == HistoryTableModel.RATING_COLUMN) {
                int stars = (Integer) v;
                setToolTipText(stars == 0 ? "Not rated. Click a star to rate." : stars + " of 5 stars");
            } else {
                setToolTipText(null);
            }
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (column != HistoryTableModel.FAV_COLUMN && column != HistoryTableModel.RATING_COLUMN) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int y = (getHeight() - STAR_SIZE) / 2;
            if (column == HistoryTableModel.FAV_COLUMN) {
                paintHeart(g2, (getWidth() - STAR_SIZE) / 2, y, Boolean.TRUE.equals(value));
            } else {
                int rating = (Integer) value;
                for (int i = 0; i < 5; i++) {
                    paintStar(g2, STAR_LEFT + i * STAR_STEP, y, i < rating);
                }
            }
            g2.dispose();
        }

        private static void paintStar(Graphics2D g2, int x, int y, boolean on) {
            Polygon star = new Polygon();
            double cx = x + STAR_SIZE / 2.0;
            double cy = y + STAR_SIZE / 2.0 + 0.5;
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
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawPolygon(star);
            }
        }

        private static void paintHeart(Graphics2D g2, int x, int y, boolean on) {
            double s = STAR_SIZE;
            Path2D heart = new Path2D.Double();
            heart.moveTo(x + s / 2, y + s * 0.95);
            heart.curveTo(x - s * 0.15, y + s * 0.45, x + s * 0.1, y - s * 0.05, x + s / 2, y + s * 0.28);
            heart.curveTo(x + s * 0.9, y - s * 0.05, x + s * 1.15, y + s * 0.45, x + s / 2, y + s * 0.95);
            heart.closePath();
            if (on) {
                g2.setColor(HEART_ON);
                g2.fill(heart);
            } else {
                g2.setColor(STAR_OFF);
                g2.setStroke(new BasicStroke(1.3f));
                g2.draw(heart);
            }
        }
    }
}
