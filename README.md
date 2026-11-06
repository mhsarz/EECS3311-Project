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
```

## Prerequisites

- Java 17 or newer (`java -version`)
- Maven 3.8 or newer (`mvn -version`)
- Internet access for the first build (Maven downloads the dependencies) and for the AI features
- An Anthropic API key (only needed for the AI features; see below)

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
| `LLM_API_KEY` | (none) | Anthropic API key. Without it, only the deterministic features work. |
| `LLM_MODEL` | `claude-opus-5-5` | Model name sent to the API. |
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
**Chat** panel on the right (ask in plain language, e.g. "Add a dozen eggs to my cart"; Enter sends,
Shift+Enter starts a new line; it can be collapsed), the Alerts strip and a status footer. It uses the FlatLaf look and feel (a Maven
dependency) and falls back to the system look if FlatLaf is missing. Tabs:

- **Pantry**: add, edit, merge and remove items. Each item shows its best-before date and a chip such as
  "1 day · Use soon" (yellow, within 3 days) or "Expired · 2 days" (red). The Add item form has a unit
  list, a best-before date picker (a calendar that opens on click; a week ahead by default, or "No date")
  and shows mistakes inline. Select an item and click **Edit** (or double-click it) to change it.
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
- **History**: saved recipes, most recently cooked first. Click a star to rate (1-5) and the heart to
  make a favourite (or press 1-5 and F on the selected row); search by name or ingredient, show
  favourites only, open a recipe or remove it. The footer shows totals. Top-rated recipes are used
  as favourites when a new recipe is generated.
- **Flyers**: tick the stores you shop at, then **Refresh flyers** (needs `FLYER_URL_TEMPLATE`) or
  **Upload flyer…** and choose a text file with a store's flyer (copy the flyer page from the store's
  website into a `.txt` file; saved `.html` pages work too). The AI reads the sale items and prices,
  and the cart is checked for sales. Search and filter by store; "In your cart" shows the matches.
  The chat can answer "What's on sale at No Frills?".
- **Profile**: your diet, allergies, dislikes and default portions, used by every recipe. Fill in the
  form (type an allergy or dislike and press Enter; click × to remove it) and click **Save profile**,
  or click **Help Me Set Up** to answer about 6 questions from the agent in the Chat panel; it saves the
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
| F09 Chat | `chat "What can I make with chicken?"`, or `chat` for a conversation |
| F10 Flyers | `flyers stores`, `flyers pick "No Frills" Metro`, `flyers refresh`, `flyers upload ./nofrills.txt --store "No Frills"`, `flyers list --store "No Frills"` |
| F11 Missing ingredients | `recipe check <id> [--add-missing]` |

`<id>` is a recipe id from `history list` (the first 4 or more characters are enough), or `last` for
the recipe shown last in the session. Dates are `2026-11-20`, `today`, `tomorrow` or `+3` (three days
from today).

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
- **"The daily limit of 100 LLM calls has been reached"**: raise `LLM_DAILY_LIMIT` in `.env`, or wait until tomorrow.
- **A recipe or flyer could not be used**: the app checks every AI answer and asks again once with the
  reason; if the second answer is also unusable it shows the reason. Try again or change the request.
- **Start with an empty app**: close it and delete the `data/` folder.

## Known limitations

- No unit conversion: "500 g" in a recipe is not compared with "1 kg" in the pantry.
- There is no free, public store-flyer API, so flyers are uploaded as text or HTML files
  (`FLYER_URL_TEMPLATE` works if you have a source). The AI reads them and can make mistakes; the
  results are shown so you can check them.
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
