# AI Smart Recipe and Grocery Shopping List Agent

EECS 3311 course project. A desktop app (GUI and CLI) with an AI agent that tracks your pantry, warns about food close to expiring, generates recipes from what you have, and checks weekly store flyers for sales on your shopping list.

The design (features F01–F11, UML diagrams, and design patterns) is in `AI Smart Recipe and Grocery shopping list Agent.docx` and the `UMLs/` folder.

Stage 2: all features F01–F11 work in both the GUI and the CLI.

## Quick start

```
git clone https://github.com/mhsarz/EECS3311-Project.git
cd EECS3311-Project
cp .env.example .env              # Windows: copy .env.example .env
#   then put your API key in .env (see Setup)
mvn clean package
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar                                   # GUI
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar cli run sample-data/setup-commands.txt   # sample data
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar cli                               # CLI
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar -LLMAPI claude -input sample-data/agent-task.txt   # agent on a task
```

## Prerequisites

- Java 17 or newer (`java -version`)
- Maven 3.8 or newer (`mvn -version`)
- Internet access for the first build (Maven downloads the dependencies) and for the AI features
- A Claude API key from Anthropic (only needed for the AI features; see below). The app talks only
  to the Anthropic Messages API: keys from other AI providers (OpenAI, Gemini, ...) do not work, and a
  Claude.ai Pro or Max subscription does not include API access (the API is billed separately).

### Dependencies

Maven installs them automatically from Maven Central during the first `mvn clean package`; there is
nothing to install by hand. They are listed in `pom.xml`:

| Dependency | Version | Used for |
|---|---|---|
| Gson | 2.11.0 | JSON for saved data and for the AI's replies |
| FlatLaf | 3.5.4 | GUI look and feel (the app falls back to the system look without it) |
| JUnit Jupiter | 5.11.4 | tests only |

The Anthropic API is called with Java's built-in `java.net.http` client, so no SDK is needed.

## Setup

1. Clone the repository:

   ```
   git clone https://github.com/mhsarz/EECS3311-Project.git
   cd EECS3311-Project
   ```

2. Create your local config file from the example:

   ```
   cp .env.example .env        # macOS / Linux
   copy .env.example .env      # Windows
   ```

3. Open `.env` and replace `YOUR_LOCAL_API_KEY_HERE` with your real key.

   Get a key at https://console.anthropic.com (Settings, API keys; create it inside a workspace).
   It must be an Anthropic (Claude) API key; it starts with `sk-ant-`. The account needs API credits.
   `.env` is listed in `.gitignore`, so it is never committed. Never put a real key in `.env.example`,
   the source code, screenshots, or videos. You can also set `LLM_API_KEY` as an environment variable
   instead of using `.env`.

4. Optional: for cheaper testing set `LLM_MODEL=claude-haiku-5-5` in `.env`.

5. Check it: `java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar cli status` prints
   "AI ready" (after building, see below).

**Without a key** the app still runs: pantry, cart, history, profile form, expiry alerts, mark as
cooked and undo all work. The AI features (recipes, substitutes, chat, flyer reading, sale matching,
name matching) say that the AI is unavailable and why.

### Configuration options

| Variable | Default | Meaning |
|---|---|---|
| `LLM_API_KEY` | (none) | Anthropic (Claude) API key. Without it, only the deterministic features work. |
| `LLM_MODEL` | `claude-opus-5-5` | Claude model name sent to the API, e.g. `claude-haiku-5-5` (cheaper) or `claude-opus-5-5`. Only Claude models work. |
| `LLM_DAILY_LIMIT` | `100` | Maximum LLM calls per day (enforced by `LLMClientProxy`). The CLI's `status` shows how many are left. |
| `DATA_DIR` | `data` | Folder where pantry, cart, history, profile and flyers are saved. |
| `FLYER_URL_TEMPLATE` | (none) | Optional web address of a store's flyer with `{store}` for the store name. Without it, upload flyer files in the Flyers tab. |

## Build and test

```
mvn clean package
```

This compiles the code, runs the tests, and builds `target/recipe-agent-0.1.0-jar-with-dependencies.jar`.

Run only the tests (about 300 JUnit 5 tests; they use a fake AI, so no key or internet is needed):

```
mvn test
```

## Run

The GUI follows the "Smart Pantry" design: a header with tabs, the page in the middle, a
**Chat** panel on the right (ask in plain language, e.g. "Add a dozen eggs to my cart", or "I bought 6
eggs", "Make me a quick dinner and add what I'm missing to my cart", "I cooked it", "Save this
recipe"; Enter sends,
Shift+Enter starts a new line; it can be collapsed), the Alerts strip and a status footer. It uses the FlatLaf look and feel (a Maven
dependency) and falls back to the system look if FlatLaf is missing. Tabs:

- **Pantry**: add, edit, merge and remove items. Each item shows its best-before date and a chip such as
  "1 day · Use soon" (yellow, within 3 days) or "Expired · 2 days" (red). The Add item form has a unit
  list, a best-before date picker (a calendar that opens on click; a week ahead by default, or "No date")
  and shows mistakes inline. Select an item and click **Edit** (or double-click it) to change it.
  To remove several items at once, tick their boxes (or the box in the header for all, or press
  Space on a row) and click **Remove checked (N)**; it lists them and asks first. The Cart and
  History tables have the same boxes.
- **Recipe**: generate a recipe with the AI (time limit, portions, optional "allow ingredients I don't
  have"); Cancel while it is thinking. The recipe card shows "Uses N expiring items", "3 in pantry ·
  2 missing" and a chip for each ingredient (In pantry, Use soon, Expired, Not enough, Missing, Basic).
  When an ingredient has a different name in your pantry, Cook This asks the AI once, so "scallions"
  can count as your "green onions" (shown next to the ingredient).
  **Substitute** asks the AI for replacements and "Use substitute" puts one into the recipe. **Cook This**
  lists what is missing and can add it to the cart; **Mark as Cooked** first shows what it will take
  out of the pantry ("chicken breast: 400 g (you have 500 g, 100 g left)") and asks you to confirm,
  then reduces the amounts (with **Undo**). The first time you cook a recipe it asks for a 1-5 star
  rating, which you can skip for now; **Save to History**. If the AI is unavailable the page says why and offers
  Try Again or Open History.
- **Cart**: your shopping list. Add an item with a quantity and an optional unit, edit it (or
  double-click), remove it (it asks "Are you sure?" first), or clear the cart. Each item shows where it came from: You, Recipe (from
  Cook This) or Chat (added by the agent). New items are checked against this week's flyers by the
  AI; items on sale show the store and price ("No Frills · $5.49") and a sale alert appears.
  **Check for sales** checks the whole cart again.
- **History**: saved recipes, most recently cooked first (tick boxes to remove several). Click a star to rate (1-5) and the heart to
  make a favourite (or press 1-5 and F on the selected row); search by name or ingredient, show
  favourites only, open a recipe or remove it. The footer shows totals. Top-rated recipes are used
  as favourites when a new recipe is generated.
- **Flyers**: tick the stores you shop at, then **Refresh flyers** (needs `FLYER_URL_TEMPLATE`) or
  **Upload flyer…** and choose the store's flyer: a **PDF** downloaded from the store's website
  (up to 32 MB; the AI reads every page, including prices that are only in pictures), or a `.txt`
  file with the flyer page copied into it (saved `.html` pages work too). The AI reads the sale
  items and prices, and the cart is checked for sales. A long PDF is read 4 pages per AI call
  (a 24-page flyer takes 6 calls and a few minutes; the page being read is shown), because one
  reply cannot hold all ~600 items. The PDF is cached by the API, so the later calls cost little.
  Each page costs roughly 1,500-3,000 tokens plus the page image; `claude-haiku-5-5` keeps a big
  flyer to a few cents. Search and filter by store; "In your cart" shows the matches.
  The chat can answer "What's on sale at No Frills?".
- **Profile**: your diet, allergies, dislikes and default portions, used by every recipe. Fill in the
  form (type an allergy or dislike and press Enter; click × to remove it) and click **Save profile**,
  or click **Help Me Set Up** to answer 4 questions from the agent in the Chat panel (diet, allergies,
  dislikes, portions); it saves the
  profile at the end. Without the AI, the page asks you to use the form instead.

The Alerts panel lists expiry alerts when the app starts (and once a day while it stays open);
"Suggest a recipe using expiring food" asks the AI for a recipe that uses them. AI features need
`LLM_API_KEY` in `.env`; everything else works without it. Data is saved in `data/`.

GUI:

```
mvn exec:java
# or
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar
```

CLI (interactive: type commands at the `smart-pantry>` prompt, `help` lists them, `exit` quits):

```
mvn exec:java -Dexec.args="cli"
# or
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar cli
```

Or run one command and exit, e.g. `java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar cli pantry list`
(with Maven: `mvn exec:java -Dexec.args="cli pantry list"`; in PowerShell write
`mvn exec:java "-Dexec.args=cli pantry list"`). Or run the commands in a file, one per line:
`cli run sample-data/setup-commands.txt`. Undo and the profile questions need the interactive CLI
(or one command file), because they are kept for the session. Alerts are printed as `[ALERT] ...` lines.

The GUI and the CLI use the same saved data (`data/`), so changes made in one show up in the other.

| Feature | CLI commands |
|---|---|
| F01 Generate recipe | `recipe generate --time 30 --portions 2 [--use milk,spinach] [--allow-missing] [--save]`, `recipe show <id>`, `recipe save` |
| F02 Cart and sale alerts | `cart add milk --qty 2 [--unit L]`, `cart list`, `cart remove milk`, `cart clear`, `cart sales` |
| F03 Pantry | `pantry add rice --qty 2 --unit kg --location pantry --expires 2027-03-01`, `pantry list`, `pantry edit rice --qty 3`, `pantry remove rice` |
| F04 Profile | `profile set --diet vegetarian --allergy peanuts`, `profile show`, `profile setup` (questions from the agent) |
| F05 Expiry alerts | `pantry expiring`, then `recipe generate --use-expiring` |
| F06 Mark as cooked, undo | `recipe cooked <id>` (the interactive CLI shows what is taken out and asks y/n, then asks for a rating the first time), `undo` |
| F07 Substitutes | `recipe substitute <id> buttermilk [--use 1]` |
| F08 History | `history list [--favourites]`, `history rate <id> 4`, `history fav <id>`, `history search chicken`, `history remove <id>` |
| F09 Chat | `chat "What can I make with chicken?"`, or `chat` for a conversation (then "save this recipe" saves the suggested recipe to history) |
| F10 Flyers | `flyers stores`, `flyers pick "No Frills" Metro`, `flyers refresh`, `flyers upload ./nofrills.pdf --store "No Frills"` (a PDF, .txt or .html file), `flyers list --store "No Frills"` |
| F11 Missing ingredients | `recipe check <id> [--add-missing]` |

`<id>` is a recipe id from `history list` (the first 4 or more characters are enough), or `last` for
the recipe shown last in the session. Dates are `2026-11-20`, `today`, `tomorrow` or `+3` (three days
from today).

### Standalone agent: run the agent on a task (file, standard input or command line)

The jar can also run the agent on a task and exit, without the GUI or the shell. A task is
plain English. The agent decides which of its tools to use, uses them, and prints every step
and its answer. It is the same agent as the Chat (F09) and it uses the same saved data, so the
changes it makes show up in the GUI and CLI too. What the agent can do:

| Area | The agent can |
|---|---|
| Pantry (F03, F05) | list items, find what expires soon, check an item; add, update and remove items ("I bought...", "I used...") |
| Recipes (F01, F11, F07) | generate a recipe with the recipe generator (time, portions, items to use, expiring food); check what is missing and add it to the cart; find substitutes and put one into the recipe |
| Cooking and history (F06, F08) | mark a recipe as cooked (takes the ingredients out of the pantry) and undo it; save, rate and favourite recipes; list saved recipes |
| Cart and sales (F02, F10) | list the cart, add and remove items (new items are checked against this week's flyers); read this week's sale items |
| Profile (F04) | read and save diet, allergies, dislikes and portions |

Uploading or refreshing flyers is done with CLI commands (see `-commands` below). The agent only
changes something when the task asks for it, and every change goes through the same checks as
the GUI (allergies, valid quantities and dates, undo). It can use up to 8 tools per task.

```
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar -LLMAPI claude -input task.txt
```

| Option | Meaning |
|---|---|
| `-input FILE` | Read the task from a text file (`-input -` reads standard input). |
| `-stdin` | Read the task from standard input. |
| `-task "TEXT"` | The task on the command line. |
| `-LLMAPI NAME` | The LLM API: `claude` (the default and the only one this app supports). |
| `-model NAME` | Claude model; overrides `LLM_MODEL` in `.env`, e.g. `claude-haiku-5-5`. |
| `-commands` | The input is CLI commands, one per line (as in `cli run`), not a task. No AI needed for most commands. |
| `-output FILE` | Also write the agent's answers to `FILE`. |
| `-json` | Print the result as JSON (each task, its answer, every tool call with its arguments and result, and what changed), for scripts and automated checks. |
| `-data DIR` | Folder for the saved pantry, cart and history (default `data`, or `DATA_DIR`). Use a separate folder, e.g. `-data demo-data`, to try tasks without changing your own data (`demo-data/` is git-ignored). |
| `-help` | Show the options. |

Use exactly one of `-input`, `-stdin` and `-task`. Options work with one or two dashes
(`-input` or `--input`), and may also follow `cli` (`cli -input task.txt`). The API key always
comes from `.env` (`LLM_API_KEY`), never from the command line. Exit code: 0 done, 1 a task or
command failed (for example no API key), 2 wrong options.

A task file can hold several tasks separated by a line with only `---`; they run in order, and
a later task can refer to an earlier one ("that recipe"). Lines starting with `#` are notes.
Nobody can answer a follow-up question in this mode, so the agent is told to make sensible
choices and say what it assumed. `sample-data/agent-task.txt`:

```
I just bought a dozen eggs and 2 L of milk that is good for a week. Add them to my pantry
(the milk goes in the fridge).
---
Plan a dinner for 2 in 30 minutes that uses the food that expires soon, and add anything I am
missing to my cart.
---
Save that recipe and make it a favourite.
---
What is on my shopping list now, and is any of it on sale this week?
```

Working example on a separate copy of the data (`demo-data/`, so your own pantry is not
changed): the first command fills it with the sample pantry, profile and cart (no AI), the
second runs the four tasks (about 10 AI calls):

```
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar -data demo-data -input sample-data/setup-commands.txt -commands
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar -data demo-data -LLMAPI claude -input sample-data/agent-task.txt -output answers.txt
```

prints, for each task, the task, the steps (for example
`1. used the pantry tool {"action":"add","name":"eggs","quantity":12}`,
`2. used the recipes tool {"action":"generate","minutes":30,"portions":2,"use_expiring":true}`,
`3. used the recipes tool {"action":"check","add_missing":true}`), the agent's answer, what changed
(`Changed: Pantry updated`, `Changed: Cart updated`, ...) and `Done.` The answers are also saved in
`answers.txt`. Add `-json` to get the same as JSON.

Standard input:

```
echo "What can I cook tonight with what I have?" | java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar -stdin
type task.txt | java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar -stdin          # Windows cmd
Get-Content task.txt | java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar -stdin   # PowerShell
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar -stdin < task.txt              # macOS / Linux
```

CLI commands from a file or standard input (every feature, including uploading flyers; no
questions asked; `last` and `undo` work within the file):

```
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar -input sample-data/setup-commands.txt -commands
type my-commands.txt | java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar -stdin -commands
```

## Sample data and example usage

`sample-data/` has safe, made-up data (see `sample-data/README.md`): CLI command files and three
sample flyers with invented prices.

```
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar cli run sample-data/setup-commands.txt     # no AI needed
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar cli run sample-data/ai-demo-commands.txt   # needs the key
```

A short interactive session (AI answers vary):

```
smart-pantry> pantry add milk --qty 1 --unit L --location fridge --expires +2
[ALERT] Expiring: milk (fridge) expires in 2 days
Added milk (1 L, fridge, best before Nov 7, 2026).
smart-pantry> flyers upload sample-data/flyers/sample-flyer-no-frills.txt --store "No Frills"
Reading the No Frills flyer...
Read 13 sale items from No Frills.
smart-pantry> cart add eggs --qty 12
Added eggs (12) to your cart.
[ALERT] On sale: eggs is on sale at No Frills: Large White Eggs, dozen for $3.99
smart-pantry> recipe generate --time 30 --use-expiring --save
Finding a recipe that uses milk...
(the recipe, with a status for each ingredient: use soon, in pantry, missing, basic)
smart-pantry> recipe check last --add-missing
smart-pantry> recipe cooked last
smart-pantry> undo
smart-pantry> chat "What can I make tonight with what I have?"
```

In the GUI the same steps are: **Pantry** > Add item; **Flyers** > Upload flyer… (pick a file in
`sample-data/flyers`); **Cart** > Add; **Recipe** > Generate, then Cook This, Mark as Cooked, Undo;
or type in the **Chat** panel.

## Design patterns

| Pattern | Classes | What it does here |
|---|---|---|
| Facade | `AgentController` | One entry point for the GUI and CLI; hides the managers, the agent and storage. |
| Adapter | `ClaudeClient` (adapter), `AnthropicApi` (adaptee), `LLMClient` (target) | Converts the agent's requests to the Anthropic Messages API format and back. |
| Proxy | `LLMClientProxy` | Stands in for the real `LLMClient`: daily call limit, no-key case, error handling, usage counting. |
| Builder | `PromptBuilder`, `ClaudePromptBuilder`, `PlainPromptBuilder`, `Prompt`; `RecipeAgent` is the director | Builds every prompt from task, context and format parts. |
| Singleton | `NotificationCenter` | One place for expiry and sale alerts; the GUI's Alerts strip and the CLI listen to it. |
| Command | `Command`, `CookRecipeCommand`, `CommandHistory` | Mark as Cooked with Undo (F06). |
| Strategy | `FlyerSource`, `FileFlyerSource`, `OnlineFlyerSource` | Where a store's flyer comes from (uploaded file or web address), chosen per store. |

## Troubleshooting

- **"AI unavailable: No LLM API key is set"**: create `.env` from `.env.example` in the folder you run
  the app from, and set `LLM_API_KEY`.
- **"Anthropic API returned HTTP 401"**: the key is wrong or was revoked. **HTTP 404**: check
  `LLM_MODEL`. **HTTP 429 / 529**: too many requests or the service is busy; the app waits and tries
  once more, then asks you to try again later.
- **"This 24-page PDF needs about 6 AI calls, but only N are left today"**: raise `LLM_DAILY_LIMIT`,
  or save only the pages you need as a smaller PDF.
- **"The daily limit of 100 LLM calls has been reached"**: raise `LLM_DAILY_LIMIT` in `.env`, or wait until tomorrow.
- **A recipe or flyer could not be used**: the app checks every AI answer and asks again once with the
  reason; if the second answer is also unusable it shows the reason. Try again or change the request.
- **Start with an empty app**: close it and delete the `data/` folder.

## Known limitations

- No unit conversion: "500 g" in a recipe is not compared with "1 kg" in the pantry.
- There is no free, public store-flyer API, so flyers are uploaded as PDF, text or HTML files
  (`FLYER_URL_TEMPLATE` works if you have a source). The AI reads them and can make mistakes; the
  results are shown so you can check them. Password-protected PDFs and PDFs over 32 MB (or over
  100 pages) cannot be read. Do not commit real store flyers to the repository.
- Undo works within one session (one GUI run, one interactive CLI or one command file).
- The chat can change the cart and the profile, but it can only read the pantry and the flyers.
- The allergy check is a name match on top of the AI's own rules, so always read the ingredients.

## Project structure

```
src/main/java/com/recipeagent/
  Main.java            entry point (GUI by default, "cli" for the CLI)
  cli/                 CLIApp (all features in a terminal; prints alerts; runs command files),
                       CommandLine, TextTable
  config/AppConfig     reads .env and environment variables
  model/               domain classes (PantryItem, Recipe, CartItem, Flyer, Notification, ...)
  storage/JsonStorage  saves and loads data as JSON files in DATA_DIR
  pantry/PantryManager pantry stock, expiry, deduct/restore, recipe check
  cart/ShoppingCart    shopping list with merge and sale details
  flyer/               FlyerService (stores, sources, flyers), FlyerSource (Strategy) with
                       FileFlyerSource and OnlineFlyerSource
  history/RecipeHistory saved recipes, ratings, favourites, search, cook records
  command/             Command, CookRecipeCommand, CommandHistory (mark as cooked + undo)
  notify/              NotificationCenter (Singleton) and NotificationListener
  agent/AgentTool      interface for tools the agent can call
  agent/RecipeAgent    the AI agent: recipe generation (F01) and substitutes (F07),
                       checks every LLM reply and retries once with feedback;
                       chat with a tool-use loop (F09) and profile questionnaire (F04);
                       flyer reading (F10), sale matching (F02) and ingredient name matching (F11)
  agent/tools/         PantryTool, ProfileTool, CartTool, FlyerTool (what the agent can use)
  llm/                 LLMClient (interface), ClaudeClient (Adapter), AnthropicApi (HTTP client
                       for the Anthropic Messages API), LLMClientProxy (Proxy: daily limit,
                       error handling, usage counting)
  prompt/              Prompt, PromptBuilder, ClaudePromptBuilder, PlainPromptBuilder (Builder)
  controller/AgentController  the Facade used by the GUI and CLI; saves every change
  gui/                 MainWindow, HeaderBar, TabBar, PageHeader, ChatPanel, AlertsStrip, StatusFooter,
                       Theme (colours, buttons), Chip, DatePicker, TagInput, PantryPanel, RecipePanel,
                       CartPanel, HistoryPanel, FlyersPanel, ProfilePanel, table models,
                       InputParser, Background (runs AI calls off the UI thread)
src/test/java/...      JUnit 5 tests
sample-data/           made-up flyers and CLI command files for trying the app
UMLs/                  class, use-case and sequence diagrams (UMLet .uxf + .svg)
```
