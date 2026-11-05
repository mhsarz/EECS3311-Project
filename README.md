# AI Smart Recipe and Grocery Shopping List Agent

EECS 3311 course project. A desktop app (GUI and CLI) with an AI agent that tracks your pantry, warns about food close to expiring, generates recipes from what you have, and checks weekly store flyers for sales on your shopping list.

The design (features F01–F11, UML diagrams, and design patterns) is in `AI Smart Recipe and Grocery shopping list Agent.docx` and the `UMLs/` folder.

> Status: in development (Stage 2). This README is updated as features are added.

## Prerequisites

- Java 17 or newer (`java -version`)
- Maven 3.8 or newer (`mvn -version`)
- An Anthropic API key (only needed for the AI features)

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

   `.env` is listed in `.gitignore`, so it is never committed. Never put a real key in `.env.example`, the source code, screenshots, or videos. You can also set `LLM_API_KEY` as an environment variable instead of using `.env`.

### Configuration options

| Variable | Default | Meaning |
|---|---|---|
| `LLM_API_KEY` | (none) | Anthropic API key. Without it, only the deterministic features work. |
| `LLM_MODEL` | `claude-opus-5-5` | Model name sent to the API. |
| `LLM_DAILY_LIMIT` | `100` | Maximum LLM calls per day (enforced by `LLMClientProxy`). |
| `DATA_DIR` | `data` | Folder where pantry, cart, history and profile are saved. |

## Build and test

```
mvn clean package
```

This compiles the code, runs the tests, and builds `target/recipe-agent-0.1.0-jar-with-dependencies.jar`.

Run only the tests:

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
  **Substitute** asks the AI for replacements and "Use substitute" puts one into the recipe. **Cook This**
  lists what is missing and can add it to the cart; **Mark as Cooked** takes the amounts out of the
  pantry (with **Undo**); **Save to History**. If the AI is unavailable the page says why and offers
  Try Again or Open History.
- **History**: saved recipes with 1-5 star ratings, favourites and search. Top-rated recipes are used
  as favourites when a new recipe is generated.

The Alerts panel lists expiry alerts when the app starts (and once a day while it stays open);
"Suggest a recipe using expiring food" asks the AI for a recipe that uses them. AI features need
`LLM_API_KEY` in `.env`; everything else works without it. Data is saved in `data/`.

GUI:

```
mvn exec:java
# or
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar
```

CLI:

```
mvn exec:java -Dexec.args="cli"
# or
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar cli
```

## Project structure

```
src/main/java/com/recipeagent/
  Main.java            entry point (GUI by default, "cli" for the CLI)
  config/AppConfig     reads .env and environment variables
  model/               domain classes (PantryItem, Recipe, CartItem, Flyer, Notification, ...)
  storage/JsonStorage  saves and loads data as JSON files in DATA_DIR
  pantry/PantryManager pantry stock, expiry, deduct/restore, recipe check
  cart/ShoppingCart    shopping list with merge and sale flags
  history/RecipeHistory saved recipes, ratings, favourites, search, cook records
  command/             Command, CookRecipeCommand, CommandHistory (mark as cooked + undo)
  notify/              NotificationCenter (Singleton) and NotificationListener
  agent/AgentTool      interface for tools the agent can call
  agent/RecipeAgent    the AI agent: recipe generation (F01) and substitutes (F07),
                       checks every LLM reply and retries once with feedback;
                       chat with a tool-use loop (F09) and profile questionnaire (F04)
  agent/tools/         PantryTool, ProfileTool, CartTool (what the agent can use)
  llm/                 LLMClient (interface), ClaudeClient (Adapter), AnthropicApi (HTTP client
                       for the Anthropic Messages API), LLMClientProxy (Proxy: daily limit,
                       error handling, usage counting)
  prompt/              Prompt, PromptBuilder, ClaudePromptBuilder, PlainPromptBuilder (Builder)
  controller/AgentController  the Facade used by the GUI and CLI; saves every change
  gui/                 MainWindow, HeaderBar, TabBar, PageHeader, ChatPanel, AlertsStrip, StatusFooter,
                       Theme (colours, buttons), Chip, DatePicker, PantryPanel, RecipePanel, HistoryPanel, table models,
                       InputParser, Background (runs AI calls off the UI thread)
src/test/java/...      JUnit 5 tests
UMLs/                  class, use-case and sequence diagrams (UMLet .uxf + .svg)
```
