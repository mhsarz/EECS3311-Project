package com.recipeagent.cli;

import com.recipeagent.agent.AgentAction;
import com.recipeagent.config.AppConfig;
import com.recipeagent.controller.AgentController;
import com.recipeagent.llm.LLMException;
import com.recipeagent.llm.LLMUnavailableException;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The standalone agent mode of the CLI: runs the agent on a task given in a file, on standard
 * input or on the command line, prints the answer and the tools the agent used, and exits.
 * <pre>
 *   java -jar recipe-agent.jar -LLMAPI claude -input task.txt
 *   type task.txt | java -jar recipe-agent.jar -stdin            (Windows; cat on macOS/Linux)
 *   java -jar recipe-agent.jar -task "What can I cook with what expires soon?"
 *   java -jar recipe-agent.jar -input sample-data/setup-commands.txt -commands
 * </pre>
 * A task is plain English. The agent reads the pantry, profile, cart and flyers through its
 * tools, can change the cart and the profile, and answers (the same agent loop as the chat,
 * F09). Several tasks can be put in one file, separated by a line with only {@code ---}; they
 * run in order and later tasks can refer to earlier answers. With {@code -commands} the input
 * is a list of CLI commands instead (see {@code help}), which covers every feature.
 */
public final class AgentRunner {

    /** The LLM APIs this build can use (the app talks to the Anthropic Messages API). */
    static final Set<String> SUPPORTED_APIS = Set.of("claude", "anthropic");
    static final String TASK_SEPARATOR = "---";
    static final int MAX_TASK_CHARS = 4000;
    static final int MAX_ARGS_SHOWN = 120;

    /** Exit codes. */
    static final int OK = 0;
    static final int FAILED = 1;
    static final int BAD_OPTIONS = 2;

    /** The parsed command-line options. */
    static final class Options {
        String llmApi = "claude";
        String model;
        String dataDir;
        Path input;
        boolean stdin;
        String task;
        boolean commands;
        Path output;
        boolean help;
    }

    private final AgentController controller;
    private final InputStream in;
    private final PrintStream out;

    public AgentRunner(AgentController controller, InputStream in, PrintStream out) {
        if (controller == null || in == null || out == null) {
            throw new IllegalArgumentException("Controller, input and output are required");
        }
        this.controller = controller;
        this.in = in;
        this.out = out;
    }

    /** True if the program arguments ask for the agent mode (they start with an option such as -input). */
    public static boolean isAgentMode(String[] args) {
        return args != null && args.length > 0 && args[0].startsWith("-");
    }

    /**
     * Parses the options, loads the configuration (with the overrides), creates the controller
     * and runs. Used by {@code Main}.
     *
     * @return the exit code: 0 done, 1 a task or command failed, 2 bad options
     */
    public static int launch(String[] args, AppConfig config, InputStream in, PrintStream out, PrintStream err) {
        Options o;
        try {
            o = parse(args);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            err.println();
            err.println(usage());
            return BAD_OPTIONS;
        }
        if (o.help) {
            out.println(usage());
            return OK;
        }
        AppConfig c = config.with(AppConfig.KEY_MODEL, o.model).with(AppConfig.KEY_DATA_DIR, o.dataDir);
        return new AgentRunner(AgentController.create(c), in, out).run(o);
    }

    /** Reads the options. Single or double dashes both work (-input or --input). */
    static Options parse(String[] args) {
        Options o = new Options();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            String name = a.replaceFirst("^--?", "").toLowerCase(Locale.ROOT);
            String value = null;
            int eq = name.indexOf('=');
            if (a.startsWith("-") && eq > 0) {
                value = a.substring(a.indexOf('=') + 1);
                name = name.substring(0, eq);
            }
            if (!a.startsWith("-")) {
                throw new IllegalArgumentException("Unexpected '" + a + "'. Options start with a dash, e.g. -input task.txt");
            }
            if (a.equals("-")) {
                o.stdin = true;
                continue;
            }
            switch (name) {
                case "llmapi":
                case "llm-api":
                case "api":
                    // the value is optional: "-LLMAPI" alone means the default (Claude)
                    if (value == null && i + 1 < args.length && !args[i + 1].startsWith("-")) {
                        value = args[++i];
                    }
                    if (value != null) {
                        o.llmApi = value.toLowerCase(Locale.ROOT);
                    }
                    if (!SUPPORTED_APIS.contains(o.llmApi)) {
                        throw new IllegalArgumentException("LLM API '" + o.llmApi + "' is not supported. "
                                + "This app uses the Claude API from Anthropic: -LLMAPI claude");
                    }
                    break;
                case "input":
                case "i":
                case "file":
                    value = value != null ? value : next(args, ++i, a);
                    if (value.equals("-")) {
                        o.stdin = true;
                    } else {
                        o.input = path(value, a);
                    }
                    break;
                case "stdin":
                    o.stdin = true;
                    break;
                case "task":
                case "t":
                    o.task = value != null ? value : next(args, ++i, a);
                    break;
                case "commands":
                case "cmd":
                    o.commands = true;
                    break;
                case "output":
                case "o":
                    o.output = path(value != null ? value : next(args, ++i, a), a);
                    break;
                case "model":
                case "m":
                    o.model = value != null ? value : next(args, ++i, a);
                    break;
                case "data":
                    o.dataDir = value != null ? value : next(args, ++i, a);
                    break;
                case "help":
                case "h":
                case "?":
                    o.help = true;
                    break;
                default:
                    throw new IllegalArgumentException("Unknown option " + a);
            }
        }
        if (o.help) {
            return o;
        }
        int sources = (o.input != null ? 1 : 0) + (o.stdin ? 1 : 0) + (o.task != null ? 1 : 0);
        if (sources == 0) {
            throw new IllegalArgumentException("Say where the task is: -input task.txt, -stdin, or -task \"...\"");
        }
        if (sources > 1) {
            throw new IllegalArgumentException("Use only one of -input, -stdin and -task.");
        }
        return o;
    }

    private static String next(String[] args, int i, String option) {
        if (i >= args.length || (args[i].startsWith("-") && !args[i].equals("-"))) {
            throw new IllegalArgumentException(option + " needs a value");
        }
        return args[i];
    }

    private static Path path(String value, String option) {
        try {
            return Path.of(value);
        } catch (InvalidPathException e) {
            throw new IllegalArgumentException("Bad file name for " + option + ": " + value);
        }
    }

    /** The help text, also printed after an option error. */
    static String usage() {
        return String.join(System.lineSeparator(),
                "Smart Pantry agent, standalone mode: runs the agent on a task and exits.",
                "",
                "Usage:",
                "  java -jar recipe-agent.jar [-LLMAPI claude] (-input FILE | -stdin | -task TEXT) [options]",
                "",
                "Where the task comes from (use one):",
                "  -input FILE    read the task from a text file (-input - reads standard input)",
                "  -stdin         read the task from standard input, e.g. type task.txt | java -jar ... -stdin",
                "  -task TEXT     the task on the command line, in quotes",
                "",
                "Options:",
                "  -LLMAPI NAME   the LLM API to use; claude (the default and the only one supported)",
                "  -model NAME    Claude model, overrides LLM_MODEL in .env (e.g. claude-haiku-5-5)",
                "  -commands      the input is CLI commands, one per line (see: cli help), not a task",
                "  -output FILE   also write the agent's answers to FILE",
                "  -data DIR      folder for the pantry, cart and history (default: data, or DATA_DIR)",
                "  -help          show this text",
                "",
                "A task is plain English, e.g. \"Plan a dinner for 2 with what expires soon and add",
                "anything missing to my cart\". Put several tasks in one file separated by a line ---.",
                "The API key is read from .env (LLM_API_KEY), never from the command line.",
                "Exit code: 0 done, 1 a task failed (e.g. no AI key), 2 bad options.");
    }

    /** Runs the task(s) or commands. */
    int run(Options o) {
        String text;
        String source;
        try {
            if (o.task != null) {
                text = o.task;
                source = "the command line";
            } else if (o.stdin) {
                text = readAll(in);
                source = "standard input";
            } else {
                text = Files.readString(o.input, StandardCharsets.UTF_8);
                source = o.input.toString();
            }
        } catch (NoSuchFileException e) {
            out.println("Error: file not found: " + o.input);
            return FAILED;
        } catch (IOException e) {
            out.println("Error: could not read " + (o.input != null ? o.input : "standard input") + ": " + e.getMessage());
            return FAILED;
        }
        if (text.startsWith("﻿")) {
            text = text.substring(1);                               // BOM from Notepad
        }
        if (o.commands) {
            List<String> lines = text.lines().collect(Collectors.toList());
            return new CLIApp(controller, new BufferedReader(new InputStreamReader(
                    new ByteArrayInputStream(new byte[0]), StandardCharsets.UTF_8)), out).runCommands(lines, source);
        }
        List<String> tasks;
        try {
            tasks = splitTasks(text);
        } catch (IllegalArgumentException e) {
            out.println("Error: " + e.getMessage());
            return FAILED;
        }
        return runTasks(tasks, source, o.output);
    }

    private int runTasks(List<String> tasks, String source, Path output) {
        if (tasks.isEmpty()) {
            out.println("Error: no task found in " + source + ".");
            return FAILED;
        }
        out.println("Smart Pantry agent - " + plural(tasks.size(), "task") + " from " + source);
        out.println(controller.getAiStatus());
        if (!controller.isAiAvailable()) {
            out.println("Error: the agent needs the AI for a task. Set LLM_API_KEY in .env (see README),"
                    + " or run CLI commands without AI: -commands.");
            return FAILED;
        }
        StringBuilder answers = new StringBuilder();
        int failed = 0;
        for (int i = 0; i < tasks.size(); i++) {
            String task = tasks.get(i);
            String label = tasks.size() == 1 ? "Task" : "Task " + (i + 1);
            out.println();
            out.println(label + ": " + indent(task));
            try {
                String answer = CLIApp.plainText(controller.chat(task)).strip();
                List<AgentAction> actions = controller.getLastChatActions();
                printSteps(actions);
                out.println("Agent: " + indent(answer));
                answers.append(label).append(": ").append(task).append(System.lineSeparator())
                        .append(answer).append(System.lineSeparator()).append(System.lineSeparator());
            } catch (LLMUnavailableException e) {
                out.println("Error: AI unavailable: " + e.getMessage());
                failed += tasks.size() - i;
                break;
            } catch (LLMException | IllegalArgumentException | IllegalStateException e) {
                out.println("Error: " + e.getMessage());
                failed++;
            }
        }
        if (output != null && answers.length() > 0) {
            try {
                Files.writeString(output, answers.toString(), StandardCharsets.UTF_8);
                out.println();
                out.println("Answers written to " + output);
            } catch (IOException e) {
                out.println("Error: could not write " + output + ": " + e.getMessage());
                failed++;
            }
        }
        out.println();
        out.println(failed == 0 ? "Done." : "Done; " + plural(failed, "task") + " did not work.");
        return failed == 0 ? OK : FAILED;
    }

    /** "Steps: pantry {"action":"expiring"}", one line per tool call, so the agent's work is visible. */
    private void printSteps(List<AgentAction> actions) {
        if (actions.isEmpty()) {
            out.println("Steps: answered without using a tool");
            return;
        }
        out.println("Steps:");
        int n = 1;
        for (AgentAction a : actions) {
            String args = a.args().replaceAll("\\s+", " ");
            if (args.length() > MAX_ARGS_SHOWN) {
                args = args.substring(0, MAX_ARGS_SHOWN) + "...";
            }
            out.println("  " + n++ + ". used the " + a.tool() + " tool " + args
                    + (a.result().contains("\"error\"") ? " (it reported an error)" : ""));
        }
    }

    /** Splits the input into tasks at lines that hold only "---"; blank tasks are dropped. */
    static List<String> splitTasks(String text) {
        List<String> tasks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String line : text.split("\\R", -1)) {
            if (line.strip().equals(TASK_SEPARATOR)) {
                add(tasks, current);
                current.setLength(0);
            } else if (!line.strip().startsWith("#")) {             // # lines are notes
                current.append(line).append('\n');
            }
        }
        add(tasks, current);
        return tasks;
    }

    private static void add(List<String> tasks, StringBuilder sb) {
        String t = sb.toString().strip();
        if (!t.isEmpty()) {
            if (t.length() > MAX_TASK_CHARS) {
                throw new IllegalArgumentException("A task is longer than " + MAX_TASK_CHARS + " characters.");
            }
            tasks.add(t);
        }
    }

    private static String readAll(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String indent(String s) {
        return s.replace("\r\n", "\n").replace("\n", System.lineSeparator() + "  ");
    }

    private static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }
}
