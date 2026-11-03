package com.recipeagent.gui;

import com.recipeagent.controller.AgentController;
import com.recipeagent.model.Notification;
import com.recipeagent.model.NotificationType;
import com.recipeagent.notify.NotificationListener;

import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The main window of the GUI. It only talks to the {@link AgentController} (Facade) and
 * listens to the {@link com.recipeagent.notify.NotificationCenter} (Singleton) for alerts.
 * <p>
 * On start, and then once a day while it stays open, it runs the expiry check (F05);
 * the alerts appear in the panel at the bottom.
 */
public class MainWindow implements NotificationListener {

    static final int DAY_MILLIS = 24 * 60 * 60 * 1000;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final AgentController controller;
    private final JFrame frame = new JFrame("AI Smart Recipe & Grocery Agent");
    private final DefaultListModel<Alert> alerts = new DefaultListModel<>();
    private final JLabel status = new JLabel();
    private final PantryPanel pantryPanel;
    private Timer dailyCheck;

    public MainWindow(AgentController controller) {
        this.controller = controller;
        this.pantryPanel = new PantryPanel(controller);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Pantry", pantryPanel);

        JPanel content = new JPanel(new BorderLayout());
        content.add(tabs, BorderLayout.CENTER);
        content.add(buildAlertsPanel(), BorderLayout.SOUTH);

        frame.setContentPane(content);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(900, 600));
        frame.setSize(1050, 700);
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
        JButton clear = new JButton("Clear alerts");
        clear.addActionListener(e -> alerts.clear());
        bottom.add(clear, BorderLayout.EAST);
        panel.add(bottom, BorderLayout.SOUTH);
        return panel;
    }

    /** Shows the window, subscribes to alerts and runs the first expiry check. */
    public void show() {
        controller.getNotifications().subscribe(this);
        updateStatus();
        frame.setVisible(true);
        controller.checkExpiry();
        dailyCheck = new Timer(DAY_MILLIS, e -> {
            controller.checkExpiry();
            pantryPanel.refresh();
        });
        dailyCheck.start();
    }

    /** Called by the NotificationCenter; may come from any thread. */
    @Override
    public void onNotify(Notification n) {
        SwingUtilities.invokeLater(() -> {
            alerts.addElement(new Alert(n, LocalTime.now()));   // in the order sent: most urgent first
            updateStatus();
        });
    }

    private void updateStatus() {
        status.setText(controller.getAiStatus());
    }

    /** Shows each alert coloured by type, with the time it arrived. */
    private static class AlertRenderer extends DefaultListCellRenderer {
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
