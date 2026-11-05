package com.recipeagent.gui;

import com.recipeagent.controller.AgentController;
import com.recipeagent.model.Notification;
import com.recipeagent.model.PantryItem;
import com.recipeagent.notify.NotificationListener;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.util.List;

/**
 * The main window, laid out as in the "Smart Pantry" design: header and tabs at the top,
 * the page in the middle with the Chat panel on the right, the Alerts strip and a status
 * footer at the bottom.
 * <p>
 * It only talks to the {@link AgentController} (Facade) and listens to the
 * {@link com.recipeagent.notify.NotificationCenter} (Singleton) for alerts. On start, and
 * once a day while it stays open, it runs the expiry check (F05).
 */
public class MainWindow implements NotificationListener {

    static final int DAY_MILLIS = 24 * 60 * 60 * 1000;
    static final String PANTRY = "Pantry";
    static final String RECIPE = "Recipe";
    static final String CART = "Cart";
    static final String HISTORY = "History";
    static final String FLYERS = "Flyers";
    static final String PROFILE = "Profile";

    private final AgentController controller;
    private final JFrame frame = new JFrame("Smart Pantry – AI Recipe & Grocery Agent");
    private final HeaderBar header;
    private final TabBar tabBar = new TabBar();
    private final CardLayout pages = new CardLayout();
    private final JPanel pageArea = new JPanel(pages);
    private final AlertsStrip alerts;
    private final StatusFooter footer = new StatusFooter();
    private final PantryPanel pantryPanel;
    private final RecipePanel recipePanel;
    private final CartPanel cartPanel;
    private final HistoryPanel historyPanel;
    private final FlyersPanel flyersPanel;
    private final ProfilePanel profilePanel;
    private final ChatPanel chatPanel;
    private Timer dailyCheck;

    public MainWindow(AgentController controller) {
        this.controller = controller;
        Runnable refresh = this::refreshAll;
        this.header = new HeaderBar(controller.today());
        this.pantryPanel = new PantryPanel(controller);
        this.recipePanel = new RecipePanel(controller, refresh, tabBar::select);
        this.cartPanel = new CartPanel(controller, refresh);
        this.flyersPanel = new FlyersPanel(controller, refresh);
        this.historyPanel = new HistoryPanel(controller, refresh,
                r -> {
                    recipePanel.showRecipe(r);
                    tabBar.select(RECIPE);
                },
                () -> tabBar.select(RECIPE));
        this.chatPanel = new ChatPanel(controller, refresh);
        this.profilePanel = new ProfilePanel(controller, refresh, chatPanel::send, chatPanel::isBusy);
        this.alerts = new AlertsStrip(this::suggestForExpiring);

        addPage(PANTRY, pantryPanel);
        addPage(RECIPE, recipePanel);
        addPage(CART, cartPanel);
        addPage(HISTORY, historyPanel);
        addPage(FLYERS, flyersPanel);
        addPage(PROFILE, profilePanel);
        tabBar.onSelect(name -> {
            pages.show(pageArea, name);
            refreshAll();
        });

        JPanel top = new JPanel(new BorderLayout());
        top.add(header, BorderLayout.NORTH);
        top.add(tabBar, BorderLayout.SOUTH);

        JPanel middle = new JPanel(new BorderLayout());
        middle.add(pageArea, BorderLayout.CENTER);
        middle.add(chatPanel, BorderLayout.EAST);

        JPanel bottom = new JPanel(new BorderLayout());
        bottom.add(alerts, BorderLayout.NORTH);
        bottom.add(footer, BorderLayout.SOUTH);

        JPanel content = new JPanel(new BorderLayout());
        content.add(top, BorderLayout.NORTH);
        content.add(middle, BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        frame.setContentPane(content);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setMinimumSize(new Dimension(1000, 680));
        frame.setSize(1240, 800);
        frame.setLocationRelativeTo(null);
    }

    private void addPage(String name, JPanel page) {
        pageArea.add(page, name);
        tabBar.addTab(name);
    }

    /** F05 "Suggest Recipes": a recipe that must use the items expiring soon (not expired ones). */
    private void suggestForExpiring() {
        List<PantryItem> soon = controller.getItemsToUseSoon();
        if (soon.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Nothing in your pantry is about to expire.",
                    "Suggest a recipe", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        tabBar.select(RECIPE);
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
            header.setDate(controller.today());
            refreshAll();
        });
        dailyCheck.start();
    }

    /** Reloads every page after something changed (pantry, cart, history, AI usage). */
    void refreshAll() {
        pantryPanel.refresh();
        recipePanel.refresh();
        cartPanel.refresh();
        historyPanel.refresh();
        flyersPanel.refresh();
        profilePanel.refresh();
        footer.setAiStatus(controller.getAiStatus(), controller.isAiAvailable());
    }

    /** Called by the NotificationCenter; may come from any thread. */
    @Override
    public void onNotify(Notification n) {
        SwingUtilities.invokeLater(() -> alerts.addAlert(n));
    }

    /** Number of alerts currently shown (for tests). */
    int getAlertCount() {
        return alerts.getAlertCount();
    }

    JFrame getFrame() {
        return frame;
    }
}
