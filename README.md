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
src/test/java/...      JUnit 5 tests
UMLs/                  class, use-case and sequence diagrams (UMLet .uxf + .svg)
```
