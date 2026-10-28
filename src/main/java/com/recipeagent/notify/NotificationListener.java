package com.recipeagent.notify;

import com.recipeagent.model.Notification;

/** Something that shows alerts, such as MainWindow or CLIApp. */
public interface NotificationListener {

    void onNotify(Notification n);
}
