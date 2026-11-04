package com.recipeagent.gui;

import com.recipeagent.controller.AgentController;
import com.recipeagent.model.Notification;
import com.recipeagent.model.NotificationType;
import com.recipeagent.model.PantryItem;
import com.recipeagent.notify.NotificationListener;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * The main window of the GUI. It only talks to the {@link AgentController} (Facade) and
 * listens to the {@link com.recipeagent.notify.NotificationCenter} (Singleton) for alerts.
 * <p>
 * On start, and then once a day while it stays open, it runs the expiry check (F05);
 * the alerts appear in the panel at the bottom, where "Suggest a recipe" asks the agent
 * for a recipe that uses the food that expires soon.
 */
public class MainWindow implements NotificationListener {

    static final int DAY_MILLIS = 24 * 60 * 60 * 1000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AgentController controller;
    private final JFrame frame = new JFrame("AI Smart Recipe & Grocery Agent");
    private final DefaultListModel<Alert> alerts = new DefaultListModel<>();
    private final JLabel status = new JLabel();
    private final JTabbedPane tabs = new JTabbedPane();
    private final PantryPanel pantryPanel;
    private final RecipePanel recipePanel;
    private final HistoryPanel historyPanel;
    private Timer dailyCheck;

    public MainWindow(AgentController controller) {
        this.controller = controller;
        Runnable refresh = this::refreshAll;
        this.pantryPanel = new PantryPanel(controller);
        this.recipePanel = new RecipePanel(controller, refresh);
        this.historyPanel = new HistoryPanel(controller, refresh,
                r -> {
                    recipePanel.showRecipe(r);
                    tabs.setSelectedComponent(recipePanel);
                },
                () -> tabs.setSelectedComponent(recipePanel));

        tabs.addTab("Pantry", pantryPanel);
        tabs.addTab("Recipe", recipePanel);
        tabs.addTab("History", historyPanel);
        tabs.addChangeListener(e -> refreshAll());

        JPanel content = new JPanel(new BorderLayout());
        content.add(tabs, BorderLayout.CENTER);
        content.add(buildAlertsPanel(), BorderLayout.SOUTH);

        frame.setContentPane(content);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(950, 650));
        frame.setSize(1100, 760);
        frame.setLocationRelativeTo(null);
    }

    private JPanel buildAlertsPanel() {
        JPanel panel = new JPanel(new BorderLayout(6, 6));
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createEmptyBorder(0, 10, 6, 10),
                BorderFactory.createTitledBorder("Alerts")));

        JList<Alert> list = new JList<>(alerts);
        list.setVisibleRowCount(4);
        list.setCellRenderer(new AlertRenderer());
        panel.add(new JScrollPane(list), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(status, BorderLayout.WEST);
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        JButton suggest = new JButton("Suggest a recipe using expiring food (AI)");
        suggest.addActionListener(e -> suggestForExpiring());
        buttons.add(suggest);
        JButton clear = new JButton("Clear alerts");
        clear.addActionListener(e -> alerts.clear());
        buttons.add(clear);
        bottom.add(buttons, BorderLayout.EAST);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    /** F05 "Suggest Recipes": a recipe that must use the items expiring soon (not expired ones). */
    private void suggestForExpiring() {
        List<PantryItem> soon = controller.getItemsToUseSoon();
        if (soon.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Nothing in your pantry is about to expire.",
                    "Suggest a recipe", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        tabs.setSelectedComponent(recipePanel);
        recipePanel.generate(soon.stream().map(PantryItem::getName).toList());
    }

    /** Shows the window, subscribes to alerts and runs the first expiry check. */
    public void show() {
        controller.getNotifications().subscribe(this);
        refreshAll();
        frame.setVisible(true);
        controller.checkExpiry();
        dailyCheck = new Timer(DAY_MILLIS, e -> {
            controller.checkExpiry();
            refreshAll();
        });
        dailyCheck.start();
    }

    /** Reloads every tab after something changed (pantry, cart, history, AI usage). */
    void refreshAll() {
        pantryPanel.refresh();
        recipePanel.refresh();
        historyPanel.refresh();
        status.setText(controller.getAiStatus());
    }

    /** Called by the NotificationCenter; may come from any thread. */
    @Override
    public void onNotify(Notification n) {
        SwingUtilities.invokeLater(() -> {
            alerts.addElement(new Alert(n, LocalTime.now()));   // in the order sent: most urgent first
            status.setText(controller.getAiStatus());
        });
    }

    /** Shows each alert coloured by type, with the time it arrived. */
    private static class AlertRenderer extends DefaultListCellRenderer {
        private static final long serialVersionUID = 1L;
        private static final Color EXPIRY = new Color(183, 28, 28);
        private static final Color SALE = new Color(27, 94, 32);

        @Override
        public Component getListCellRendererComponent(JList<?> list, Object value, int index,
                                                      boolean selected, boolean focus) {
            Alert a = (Alert) value;
            Notification n = a.notification();
            String label = (n.getType() == NotificationType.EXPIRY ? "Expiring: " : "On sale: ")
                    + n.getMessage() + "   (" + a.at().format(TIME) + ")";
            Component c = super.getListCellRendererComponent(list, label, index, selected, focus);
            if (!selected) {
                c.setForeground(n.getType() == NotificationType.EXPIRY ? EXPIRY : SALE);
            }
            return c;
        }
    }

    /** An alert as shown in the list, with the time it arrived. */
    record Alert(Notification notification, LocalTime at) {
    }

    /** Number of alerts currently shown (for tests). */
    int getAlertCount() {
        return alerts.size();
    }

    JFrame getFrame() {
        return frame;
    }
}
