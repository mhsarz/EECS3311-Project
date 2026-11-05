package com.recipeagent;

import com.recipeagent.cli.CLIApp;
import com.recipeagent.config.AppConfig;
import com.recipeagent.controller.AgentController;
import com.recipeagent.gui.MainWindow;
import com.recipeagent.gui.Theme;

import javax.swing.SwingUtilities;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;

/**
 * Entry point.
 * <pre>
 *   mvn exec:java                       starts the GUI (MainWindow)
 *   mvn exec:java -Dexec.args="cli"     starts the interactive CLI (CLIApp)
 *   mvn exec:java -Dexec.args="cli pantry list"   runs one CLI command
 * </pre>
 * Both interfaces use the same AgentController.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        if (!config.hasApiKey()) {
            System.out.println("No LLM API key found. Copy .env.example to .env and set LLM_API_KEY."
                    + " Features without AI still work.");
        }
        AgentController controller = AgentController.create(config);

        boolean cli = args.length > 0 && args[0].equalsIgnoreCase("cli");
        if (cli) {
            String[] cliArgs = Arrays.copyOfRange(args, 1, args.length);
            int code = new CLIApp(controller, System.in, System.out).run(cliArgs);
            if (code != 0) {
                System.exit(code);
            }
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.out.println("No display available for the GUI. Use the CLI instead: mvn exec:java -Dexec.args=\"cli\"");
            return;
        }
        SwingUtilities.invokeLater(() -> {
            if (!Theme.install()) {
                System.out.println("FlatLaf look and feel not found; using the system look and feel.");
            }
            new MainWindow(controller).show();
        });
    }
}
