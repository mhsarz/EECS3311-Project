package com.recipeagent;

import com.recipeagent.config.AppConfig;

import java.util.Arrays;

/**
 * Entry point.
 * <pre>
 *   java -jar recipe-agent.jar          starts the GUI (MainWindow)
 *   java -jar recipe-agent.jar cli ...  starts the CLI (CLIApp)
 * </pre>
 * MainWindow and CLIApp are added in later steps; for now this loads the
 * configuration and reports which interface was requested.
 */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) {
        AppConfig config = AppConfig.load();
        System.out.println("AI Smart Recipe Agent - " + config);
        if (!config.hasApiKey()) {
            System.out.println("No LLM API key found. Copy .env.example to .env and set LLM_API_KEY."
                    + " Deterministic features will still work.");
        }

        boolean cli = args.length > 0 && args[0].equalsIgnoreCase("cli");
        if (cli) {
            String[] cliArgs = Arrays.copyOfRange(args, 1, args.length);
            System.out.println("CLI mode requested with args " + Arrays.toString(cliArgs)
                    + " (CLIApp not implemented yet).");
        } else {
            System.out.println("GUI mode requested (MainWindow not implemented yet).");
        }
    }
}
