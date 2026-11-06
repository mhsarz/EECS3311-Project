package com.recipeagent.gui;

import com.recipeagent.controller.AgentController;
import com.recipeagent.controller.FlyerRefresh;
import com.recipeagent.flyer.FlyerException;
import com.recipeagent.llm.LLMUnavailableException;
import com.recipeagent.model.Flyer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableRowSorter;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Flyers page (F10): pick the stores you shop at, then Refresh flyers (online, when set
 * up in .env) or Upload flyer (a text file copied from the store's flyer). The agent reads each
 * flyer into sale items, shown in the table, and the cart is checked for sales (F02).
 */
public class FlyersPanel extends JPanel {

    private static final long serialVersionUID = 1L;
    private static final String TABLE = "table";
    private static final String EMPTY = "empty";
    static final String ALL_STORES = "All stores";

    private final transient AgentController controller;
    private final transient Runnable dataChanged;

    private final PageHeader header = new PageHeader("Flyers", "This week's sales at your stores. Your cart is checked automatically.");
    private final JPanel storeList = new JPanel();
    private final JTextField newStore = new JTextField(10);
    private final JButton refreshButton = Theme.ai("Refresh flyers", true);
    private final JButton uploadButton = Theme.ai("Upload flyer…", false);
    private final JLabel onlineNote = Theme.muted(" ");

    private final FlyersTableModel model = new FlyersTableModel();
    private final JTable table = new JTable(model);
    private final JTextField searchField = new JTextField(14);
    private final JComboBox<String> storeFilter = new JComboBox<>();
    private final CardLayout cards = new CardLayout();
    private final JPanel tableArea = new JPanel(cards);
    private final JLabel message = Theme.label(" ", Font.PLAIN, 12f, Theme.PRIMARY);
    private transient List<String> shownStores = List.of();
    private boolean busy;
    private boolean updatingFilter;

    public FlyersPanel(AgentController controller, Runnable dataChanged) {
        super(new BorderLayout(12, 12));
        this.controller = controller;
        this.dataChanged = dataChanged;
        setBorder(BorderFactory.createEmptyBorder(16, 20, 12, 16));
        add(header, BorderLayout.NORTH);
        add(buildStores(), BorderLayout.WEST);
        add(buildSales(), BorderLayout.CENTER);
        refresh();
    }

    // ------------------------------------------------------------ building the page

    private JPanel buildStores() {
        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(Theme.CARD);
        card.setBorder(Theme.card(12));
        card.setPreferredSize(new Dimension(230, 100));

        JPanel head = new JPanel(new BorderLayout(0, 2));
        head.setOpaque(false);
        head.add(Theme.label("Your stores", Font.BOLD, 13f, Theme.TEXT), BorderLayout.NORTH);
        head.add(Theme.muted("Pick where you shop."), BorderLayout.SOUTH);
        card.add(head, BorderLayout.NORTH);

        storeList.setOpaque(false);
        storeList.setLayout(new BoxLayout(storeList, BoxLayout.Y_AXIS));
        JScrollPane sp = new JScrollPane(storeList);
        sp.setBorder(null);
        sp.setOpaque(false);
        sp.getViewport().setOpaque(false);
        sp.getVerticalScrollBar().setUnitIncrement(16);
        card.add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.setOpaque(false);
        bottom.setLayout(new BoxLayout(bottom, BoxLayout.Y_AXIS));
        JPanel addRow = new JPanel(new BorderLayout(6, 0));
        addRow.setOpaque(false);
        newStore.putClientProperty("JTextField.placeholderText", "Another store");
        newStore.setToolTipText("Type a store name and press Enter");
        newStore.addActionListener(e -> addStore());
        JButton add = Theme.secondary("Add");
        add.addActionListener(e -> addStore());
        addRow.add(newStore, BorderLayout.CENTER);
        addRow.add(add, BorderLayout.EAST);
        JPanel actions = new JPanel(new GridLayout(2, 1, 0, 6));
        actions.setOpaque(false);
        refreshButton.setToolTipText("Read this week's flyer of every picked store");
        refreshButton.addActionListener(e -> refreshFlyers());
        uploadButton.setToolTipText("Choose a text file with a store's flyer (copied from its website)");
        uploadButton.addActionListener(e -> uploadFlyer());
        actions.add(refreshButton);
        actions.add(uploadButton);
        for (JComponent c : new JComponent[]{addRow, actions, onlineNote}) {
            c.setAlignmentX(LEFT_ALIGNMENT);
            bottom.add(c);
            bottom.add(Box.createVerticalStrut(8));
        }
        card.add(bottom, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildSales() {
        JPanel card = new JPanel(new BorderLayout(0, 8));
        card.setOpaque(false);

        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        top.add(Theme.label("Sale items", Font.BOLD, 13f, Theme.TEXT), BorderLayout.WEST);
        JPanel filters = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        filters.setOpaque(false);
        searchField.putClientProperty("JTextField.placeholderText", "Search items");
        searchField.setToolTipText("Show items whose name contains this, e.g. chicken");
        searchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                showRows();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                showRows();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                showRows();
            }
        });
        storeFilter.addActionListener(e -> {
            if (!updatingFilter) {
                showRows();
            }
        });
        filters.add(Theme.muted("Search"));
        filters.add(searchField);
        filters.add(storeFilter);
        top.add(filters, BorderLayout.EAST);
        card.add(top, BorderLayout.NORTH);

        table.setRowHeight(30);
        table.setShowVerticalLines(false);
        table.setIntercellSpacing(new Dimension(0, 1));
        table.setRowSorter(new TableRowSorter<>(model));
        table.getTableHeader().setReorderingAllowed(false);
        CellRenderer renderer = new CellRenderer();
        for (Class<?> k : new Class<?>[]{Object.class, Double.class, LocalDate.class}) {
            table.setDefaultRenderer(k, renderer);
        }
        table.getColumnModel().getColumn(1).setPreferredWidth(260);
        JScrollPane sp = new JScrollPane(table);
        sp.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        sp.getViewport().setBackground(Theme.CARD);
        tableArea.setOpaque(false);
        tableArea.add(sp, TABLE);
        tableArea.add(buildEmptyState(), EMPTY);
        card.add(tableArea, BorderLayout.CENTER);
        card.add(message, BorderLayout.SOUTH);
        return card;
    }

    private JPanel buildEmptyState() {
        JPanel empty = new JPanel(new GridBagLayout());
        empty.setBackground(Theme.CARD);
        empty.setBorder(BorderFactory.createLineBorder(Theme.BORDER));
        JPanel box = new JPanel();
        box.setOpaque(false);
        box.setLayout(new BoxLayout(box, BoxLayout.Y_AXIS));
        JLabel title = Theme.label("No flyers yet", Font.BOLD, 15f, Theme.TEXT);
        JLabel text = Theme.muted("<html><div style='text-align:center;width:330px'>Pick your stores and click "
                + "Refresh flyers, or click Upload flyer and choose the store's flyer: the PDF from its "
                + "website, or a text file with the flyer page copied into it.</div></html>");
        text.setHorizontalAlignment(SwingConstants.CENTER);
        for (JComponent c : new JComponent[]{title, text}) {
            c.setAlignmentX(CENTER_ALIGNMENT);
            box.add(c);
            box.add(Box.createVerticalStrut(8));
        }
        empty.add(box);
        return empty;
    }

    // ------------------------------------------------------------ showing

    /** Reloads the stores, the sale items and the buttons. */
    public void refresh() {
        List<String> stores = controller.getFlyerStores();
        if (!stores.equals(shownStores)) {
            rebuildStoreList(stores);
        } else {
            for (Component c : storeList.getComponents()) {
                if (c instanceof JCheckBox) {
                    JCheckBox box = (JCheckBox) c;
                    box.setSelected(controller.isStoreSelected(box.getText()));
                }
            }
        }
        List<Flyer> flyers = controller.getCurrentFlyers();
        header.setRight(FlyersTableModel.summary(flyers));
        updateStoreFilter(flyers);
        showRows();
        onlineNote.setText(controller.isOnlineFlyersSetUp() ? " "
                : "<html><div style='width:170px'>Online flyers are not set up, so use Upload flyer for "
                + "each store.</div></html>");
        updateButtons();
    }

    private void rebuildStoreList(List<String> stores) {
        storeList.removeAll();
        for (String s : stores) {
            JCheckBox box = new JCheckBox(s, controller.isStoreSelected(s));
            box.setOpaque(false);
            box.setFont(Theme.font(Font.PLAIN, 12f));
            box.addActionListener(e -> {
                controller.setStoreSelected(s, box.isSelected());
                updateButtons();
            });
            box.setAlignmentX(LEFT_ALIGNMENT);
            storeList.add(box);
        }
        shownStores = List.copyOf(stores);
        storeList.revalidate();
        storeList.repaint();
    }

    private void updateStoreFilter(List<Flyer> flyers) {
        Object chosen = storeFilter.getSelectedItem();
        updatingFilter = true;
        try {
            storeFilter.removeAllItems();
            storeFilter.addItem(ALL_STORES);
            for (Flyer f : flyers) {
                storeFilter.addItem(f.getStore());
            }
            storeFilter.setSelectedItem(chosen == null ? ALL_STORES : chosen);
            if (storeFilter.getSelectedIndex() < 0) {
                storeFilter.setSelectedItem(ALL_STORES);
            }
        } finally {
            updatingFilter = false;
        }
    }

    private void showRows() {
        List<Flyer> flyers = controller.getCurrentFlyers();
        Object store = storeFilter.getSelectedItem();
        model.setFlyers(flyers, controller.getCartItems(), ALL_STORES.equals(store) ? null : (String) store,
                searchField.getText());
        cards.show(tableArea, flyers.isEmpty() ? EMPTY : TABLE);
    }

    private void updateButtons() {
        refreshButton.setEnabled(!busy && !controller.getSelectedStores().isEmpty());
        refreshButton.setToolTipText(controller.getSelectedStores().isEmpty()
                ? "Pick at least one store first" : "Read this week's flyer of every picked store");
        uploadButton.setEnabled(!busy);
    }

    // ------------------------------------------------------------ actions

    private void addStore() {
        try {
            String name = controller.addFlyerStore(newStore.getText());
            newStore.setText("");
            say("Added " + name + ".", Theme.PRIMARY);
            refresh();
        } catch (IllegalArgumentException ex) {
            say(ex.getMessage(), Theme.DANGER_FG);
        }
    }

    private void refreshFlyers() {
        int n = controller.getSelectedStores().size();
        Background.run(b -> setBusy(b, "Reading " + n + (n == 1 ? " flyer" : " flyers") + "… this can take a minute."),
                controller::refreshFlyers,
                result -> {
                    refresh();
                    dataChanged.run();
                    showResult(result);
                },
                this::failed);
    }

    private void uploadFlyer() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Upload a store's flyer");
        chooser.setFileFilter(new FileNameExtensionFilter(CHOOSER_FILTER, CHOOSER_EXTENSIONS));
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        String store = askStore(file.getName());
        if (store == null) {
            return;
        }
        Path path = file.toPath();
        Background.run(b -> setBusy(b, readingText(store, file.getName())),
                () -> {
                    try {
                        return controller.uploadFlyer(path, store);
                    } catch (FlyerException e) {
                        throw new IllegalArgumentException(e.getMessage(), e);
                    }
                },
                result -> {
                    refresh();
                    dataChanged.run();
                    showResult(result);
                },
                this::failed);
    }

    /** Which store the uploaded flyer is from; null if cancelled. */
    private String askStore(String fileName) {
        JComboBox<String> box = new JComboBox<>(controller.getFlyerStores().toArray(new String[0]));
        box.setEditable(true);
        String guess = guessStore(fileName, controller.getFlyerStores());
        if (guess != null) {
            box.setSelectedItem(guess);
        } else if (!controller.getSelectedStores().isEmpty()) {
            box.setSelectedItem(controller.getSelectedStores().get(0));
        }
        JPanel p = new JPanel(new BorderLayout(0, 6));
        p.add(new JLabel("Which store is " + fileName + " from?"), BorderLayout.NORTH);
        p.add(box, BorderLayout.CENTER);
        int ok = JOptionPane.showConfirmDialog(this, p, "Upload flyer", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.PLAIN_MESSAGE);
        if (ok != JOptionPane.OK_OPTION) {
            return null;
        }
        Object v = box.getEditor().getItem();
        String store = v == null ? "" : v.toString().trim();
        if (store.isEmpty()) {
            say("Pick or type the store's name.", Theme.DANGER_FG);
            return null;
        }
        return store;
    }

    private void setBusy(boolean b, String text) {
        busy = b;
        if (b) {
            say(text, Theme.MUTED);
        }
        updateButtons();
    }

    private void showResult(FlyerRefresh result) {
        say(resultMessage(result), result.getProblems().isEmpty() ? Theme.PRIMARY : Theme.WARN_FG);
    }

    private void failed(RuntimeException ex) {
        String text;
        if (ex instanceof LLMUnavailableException) {
            text = "The AI is unavailable, so flyers can't be read now. Your saved flyers are kept. ("
                    + ex.getMessage() + ")";
        } else {
            text = ex.getMessage();
        }
        refresh();
        say(text, Theme.DANGER_FG);
    }

    private void say(String text, Color colour) {
        message.setForeground(colour);
        message.setText("<html><div style='width:560px'>" + ChatFormat.escape(text).replace("\n", "<br>")
                + "</div></html>");
    }

    // ------------------------------------------------------------ text helpers (tested)

    /** What happened after Refresh or Upload, including stores that failed and any sales found. */
    static String resultMessage(FlyerRefresh r) {
        StringBuilder sb = new StringBuilder();
        if (!r.getUpdated().isEmpty()) {
            int items = r.getUpdated().stream().mapToInt(f -> f.getItems().size()).sum();
            sb.append("Read ").append(r.getUpdated().size()).append(r.getUpdated().size() == 1 ? " flyer: " : " flyers: ")
                    .append(items).append(items == 1 ? " sale item." : " sale items.");
            String sales = CartPanel.saleMessage(r.getSales(), false);
            if (sales != null) {
                sb.append(" ").append(sales);
            } else if (r.getSales().wasRun() && !r.getSales().getChecked().isEmpty()) {
                sb.append(" Nothing in your cart is on sale.");
            }
        }
        for (Map.Entry<String, String> p : r.getProblems().entrySet()) {
            if (sb.length() > 0) {
                sb.append("\n");
            }
            sb.append(p.getKey()).append(": ").append(p.getValue());
        }
        return sb.length() == 0 ? "Nothing was read." : sb.toString();
    }

    static final String CHOOSER_FILTER = "Flyers (.pdf, .txt, .html, .csv, .md)";
    static final String[] CHOOSER_EXTENSIONS = {"pdf", "txt", "html", "htm", "csv", "md"};

    /** "Reading the Metro flyer…", with a note that a PDF takes longer. */
    static String readingText(String store, String fileName) {
        boolean pdf = fileName != null && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
        return "Reading the " + store + " flyer" + (pdf ? " (PDF, this can take a minute)…" : "…");
    }

    /** A store whose name appears in the file name, e.g. "nofrills-week45.txt" gives "No Frills". */
    static String guessStore(String fileName, List<String> stores) {
        String f = fileName.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        List<String> hits = new ArrayList<>();
        for (String s : stores) {
            String key = s.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
            if (!key.isEmpty() && f.contains(key)) {
                hits.add(s);
            }
        }
        return hits.isEmpty() ? null : hits.get(0);
    }

    // ------------------------------------------------------------ painting

    /** Prices as "$5.49", end dates as "Nov 12, 2026", and an "In your cart" chip. */
    private static final class CellRenderer extends DefaultTableCellRenderer {
        private static final long serialVersionUID = 1L;
        private boolean chip;

        @Override
        public Component getTableCellRendererComponent(JTable t, Object value, boolean selected,
                                                       boolean focus, int row, int column) {
            int col = t.convertColumnIndexToModel(column);
            Object shown = value;
            if (value instanceof Double) {
                shown = String.format(java.util.Locale.CANADA, "$%.2f", (Double) value);
            } else if (col == FlyersTableModel.ENDS_COLUMN) {
                shown = value == null ? "Not given" : PantryTableModel.formatDate((LocalDate) value);
            }
            super.getTableCellRendererComponent(t, shown, selected, false, row, column);
            setHorizontalAlignment(LEFT);
            setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            setFont(Theme.font(col == 1 ? Font.BOLD : Font.PLAIN, 12f));
            boolean muted = col == FlyersTableModel.ENDS_COLUMN && value == null;
            setForeground(col == FlyersTableModel.PRICE_COLUMN ? Theme.SALE_FG : muted ? Theme.MUTED : Theme.TEXT);
            setBackground(selected ? Theme.PRIMARY_LIGHT : Theme.CARD);
            chip = col == FlyersTableModel.CART_COLUMN && value != null && !value.toString().isEmpty();
            return this;
        }

        @Override
        protected void paintComponent(Graphics g) {
            if (!chip) {
                super.paintComponent(g);
                return;
            }
            g.setColor(getBackground());
            g.fillRect(0, 0, getWidth(), getHeight());
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setFont(Theme.font(Font.BOLD, 11f));
            int w = g2.getFontMetrics().stringWidth(getText()) + 16;
            int h = 20;
            int y = (getHeight() - h) / 2;
            g2.setColor(Theme.PRIMARY_LIGHT);
            g2.fillRoundRect(8, y, w, h, 10, 10);
            g2.setColor(Theme.SALE_FG);
            g2.drawString(getText(), 16, y + (h + g2.getFontMetrics().getAscent()) / 2 - 2);
            g2.dispose();
        }
    }
}
