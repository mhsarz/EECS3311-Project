# Sample data

Safe, made-up data for trying the app. Nothing here is real store data, and no file contains an API key.

| File | What it is |
|---|---|
| `setup-commands.txt` | CLI commands that fill the pantry (some food expires in 1-3 days, one item is already expired), set a profile (peanut allergy, dislikes olives) and add a few items to the cart. No AI needed. |
| `ai-demo-commands.txt` | CLI commands that go through the AI features: read the sample flyers, sale alerts, generate a recipe, missing ingredients, substitutes, mark as cooked and undo, history, chat. Needs `LLM_API_KEY` and uses about 15 AI calls. |
| `flyers/sample-flyer-no-frills.txt` | A made-up flyer in plain text (dotted price lists, "2 for $9", "/lb"). |
| `flyers/sample-flyer-metro.txt` | A made-up flyer in another layout (milk is dearer than at No Frills, so the cart shows the cheaper store). |
| `flyers/sample-flyer-freshco.html` | A made-up flyer saved as a web page, to show that HTML, styles and scripts are removed before the AI reads it. |

The flyers say at the top that they are samples with invented prices. The store names only tell the app which store the flyer belongs to.

## How to use

From the project folder, after `mvn clean package`:

```
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar cli run sample-data/setup-commands.txt
java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar cli run sample-data/ai-demo-commands.txt
```

With Maven: `mvn exec:java -Dexec.args="cli run sample-data/setup-commands.txt"`.

Then open the GUI (`java -jar target/recipe-agent-0.1.0-jar-with-dependencies.jar`) to see the same data: the CLI and GUI share the `data/` folder.

In the GUI you can also upload the flyers yourself: **Flyers** tab, **Upload flyer…**, pick a file from `sample-data/flyers` (the store is guessed from the file name).

Best-before dates in the command files are relative (`--expires +2` means two days from today), so the expiry alerts work whenever you run them. To start again from an empty app, delete the `data/` folder.
