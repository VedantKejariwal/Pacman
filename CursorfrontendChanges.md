# Cursor Frontend Changes

## Files Changed

- `edu/bu/pas/pacman/rendering/ThemeManager.java`
- `edu/bu/pas/pacman/rendering/GameRenderer.java`
- `edu/bu/pas/pacman/rendering/GamePanel.java`
- `lib/pas-pacman-jar-1.0.0.jar`
- `assets/README.md`
- `frontend.srcs`

## Commands to rerender front end

cd "/Users/vedantkejariwal/Downloads/Code/cs440 copy"
javac -cp "lib/*:." -d build/frontend @frontend.srcs
jar uf "lib/pas-pacman-jar-1.0.0.jar" -C "build/frontend" edu/bu/pas/pacman/rendering/GamePanel.class -C "build/frontend" edu/bu/pas/pacman/rendering/GameRenderer.class -C "build/frontend" edu/bu/pas/pacman/rendering/ThemeManager.class
java -cp "lib/*:." edu.bu.pas.pacman.Main -a src.pas.pacman.agents.PacmanAgent -x 13 -y 13 -d MEDIUM -p 3 -g 2 --hz 5

## Quick Overview

The frontend work was implemented without touching the locked backend search files.

The main idea was:

1. Keep the real game logic exactly where it already was.
2. Replace only the Swing rendering layer that lives inside the pacman jar.
3. Add a small asset manager so visuals can be swapped by changing PNG files.
4. Keep safe fallbacks so the game still runs even when no custom assets exist yet.

This means the backend pathfinding, pellet planning, and ghost avoidance still come from your earlier Java files, while the frontend now has a separate theme/render layer that changes how the same game state is drawn.

## High-Level Changes By File

### `edu/bu/pas/pacman/rendering/ThemeManager.java`

High level:
Centralized all asset filenames, asset loading, warning messages, and stable logo selection logic.

Detailed changes:

- Added one place that defines the expected filenames inside `assets/`.
- Added image loading through `ImageIO.read(...)`.
- Added one-time warning logging so missing files do not spam every frame.
- Added stable ghost image selection based on ghost id.
- Added stable pellet-logo selection based on coordinate.
- Added generated badge-style fallback logo images (`AI`, `ML`, `JS`, `DB`, `OS`) so the board still feels themed even before real logo PNGs are added.

What this file logically does:

- It does not know anything about pathfinding or game decisions.
- It only answers questions like:
  - "Which Pacman image should I use?"
  - "Which ghost image matches this ghost?"
  - "Which logo image should this pellet coordinate keep using?"

Why it matters:

- It keeps asset concerns out of the drawing methods.
- It creates a clean seam for later replacing Swing with another renderer.

### `edu/bu/pas/pacman/rendering/GameRenderer.java`

High level:
Replaced the default shape renderer with a theme-aware renderer that draws a dark board, stable pellet badges/logos, directional Pacman, and themed ghosts.

Detailed changes:

- Rebuilt the renderer around the same public methods used by the original jar class:
  - `drawCellBackgroundColors(...)`
  - `drawPellets(...)`
  - `drawPacman(...)`
  - `drawGhosts(...)`
  - `drawEntities(...)`
  - `draw(...)`
- Preserved the original board sizing convention using the same rendering constants from the game jar.
- Added a dark-mode board palette with grid lines for better contrast.
- Added stable pellet image mapping stored in a `Map<Coordinate, BufferedImage>`.
- Added directional Pacman rotation using `AffineTransform`.
- Because the public game API does not expose Pacman direction directly, inferred direction from the change between Pacman's previous and current coordinates.
- Added image-based ghost drawing when PNGs exist.
- Kept shape-based ghost fallback when PNGs are missing.
- Upgraded the Pacman fallback shape so it still visibly rotates even without a custom sprite.
- Enabled antialiasing and bilinear interpolation for cleaner scaled drawing.

What this file logically does:

- Reads the current `Game` and `Board` state.
- Converts tile/entity positions into pixel coordinates.
- Delegates asset lookup to `ThemeManager`.
- Draws the full current frame.

Why it matters:

- This is the real frontend implementation layer.
- It changes appearance without changing gameplay behavior.

### `edu/bu/pas/pacman/rendering/GamePanel.java`

High level:
Kept the Swing panel wrapper minimal and used it only to forward paint calls into the new renderer.

Detailed changes:

- Kept the class intentionally small.
- Preserved the original public constructor shape so the existing `Main` flow still works.
- Set panel size from `GameRenderer.TOTAL_WIDTH` and `GameRenderer.TOTAL_HEIGHT`.
- Forwarded `paintComponent(...)` into `GameRenderer.draw(...)`.

What this file logically does:

- It is the Swing bridge between the window and the renderer.
- It does not make visual decisions itself.

Why it matters:

- This keeps `GameRenderer` focused on rendering and `GamePanel` focused on Swing lifecycle.

### `lib/pas-pacman-jar-1.0.0.jar`

High level:
Patched the active pacman jar so the existing `edu.bu.pas.pacman.Main` entrypoint now uses the new renderer classes.

Detailed changes:

- Recompiled:
  - `GamePanel`
  - `GameRenderer`
  - `ThemeManager`
- Updated the jar so the active runtime classes match the new frontend source.

Why this was necessary:

- The checked-in repo did not include editable source for `GamePanel` and `GameRenderer`.
- Those classes existed only inside the jar.
- Because the run command loads from `lib/*`, patching the jar was the least invasive way to keep the normal launch flow working.

### `assets/README.md`

High level:
Added a user-facing asset guide.

Detailed changes:

- Listed exact filenames.
- Listed recommended sprite sizes.
- Explained fallback behavior.
- Explained that replacing PNGs changes visuals without code edits.

### `frontend.srcs`

High level:
Added a simple source list for rebuilding the frontend renderer classes.

Detailed changes:

- Listed the three frontend source files in one place.
- Makes future compile commands simpler and more repeatable.

## How The Frontend Pieces Fit Together

```text
Game state / logic (already existing)
    |
    v
Main -> GamePanel -> GameRenderer -> ThemeManager
                         |
                         v
                 assets/*.png or fallback drawing
```

More detailed flow:

```text
PacmanAgent / ghost logic / board state
        |
        v
      Game
        |
        +--> Board / Tiles / Entities
        |
        v
   GameRenderer
        |
        +--> background colors
        +--> pellet logos
        +--> Pacman sprite rotation
        +--> ghost sprites
        |
        v
     Swing window
```

## What Each Part Does In Plain English

- `Game` is still the truth for what is happening.
- `Board` tells the renderer what is in each cell.
- `Entity`, `Pacman`, and `Ghost` tell the renderer where moving actors are.
- `ThemeManager` decides which image or fallback asset should represent those things.
- `GameRenderer` converts game coordinates into pixel drawing.
- `GamePanel` plugs that renderer into Swing.

So the frontend now sits on top of the backend rather than mixing with it.

## Code And Syntax Notes

### Why a `Map<Coordinate, BufferedImage>` for pellets?

- A map is the cleanest way to say:
  - "this exact pellet coordinate should keep this exact logo"
- That prevents flickering or reshuffling across repaints.
- `Coordinate` is a natural key because each pellet lives on one board cell.

### Why use `Math.floorMod(...)` instead of `%`?

- `%` can produce a negative result for negative numbers.
- `Math.floorMod(...)` guarantees a valid non-negative index.
- That makes it safer when mapping ids or hashed coordinates into a fixed image list.

### Why use `AffineTransform`?

- Swing images do not automatically rotate.
- `AffineTransform` lets the renderer:
  - move the drawing origin to the center of a cell
  - rotate the sprite
  - draw the image centered
- This is the cleanest way to make one Pacman sprite face multiple directions.

### Why infer Pacman direction from coordinates?

- The public game API exposed position, but not a direct "facing" field.
- The renderer stores Pacman's previous coordinate and compares it to the current one.
- That gives a simple direction estimate:
  - `+x` means right
  - `+y` means down
  - `-x` means left
  - `-y` means up

### Why separate `ThemeManager` from `GameRenderer`?

- If asset loading were mixed into every draw method, the renderer would become harder to read and harder to port.
- By separating them:
  - `ThemeManager` handles asset lookup
  - `GameRenderer` handles drawing logic
- This mirrors good separation-of-concerns design and is better for future web migration.

## Asset Guide

Asset folder:

- `assets/`

Expected filenames:

- `pacman_face.png`
- `ghost_1.png`
- `ghost_2.png`
- `ghost_3.png`
- `ghost_4.png`
- `ghost_scared.png`
- `logo_1.png`
- `logo_2.png`
- `logo_3.png`
- `logo_4.png`
- `logo_5.png`

Recommended sizes:

- Pacman / ghost sprites: about `64x64`
- Pellet logos: about `32x32`

Fallback behavior:

- Missing Pacman or ghost PNGs:
  - the game falls back to Swing shapes
- Missing logo PNGs:
  - the game falls back to generated badge-style mini-images
- Missing assets do not crash the app
- Missing-asset warnings print once per filename

## Rebuild / Update Workflow

If you change the frontend renderer source later, rebuild with:

```bash
javac -cp "lib/*:." -d build/frontend @frontend.srcs
jar uf "lib/pas-pacman-jar-1.0.0.jar" -C "build/frontend" edu/bu/pas/pacman/rendering/GamePanel.class -C "build/frontend" edu/bu/pas/pacman/rendering/GameRenderer.class -C "build/frontend" edu/bu/pas/pacman/rendering/ThemeManager.class
```

Then run with the normal game command:

```bash
java -cp "lib/*:." edu.bu.pas.pacman.Main -a src.pas.pacman.agents.PacmanAgent -x 13 -y 13 -d MEDIUM -p 3 -g 2 --hz 5
```

## Validation Performed

The frontend was validated by:

- compiling the new renderer classes successfully
- patching the jar successfully
- launching `edu.bu.pas.pacman.Main` successfully
- confirming the game still runs to completion
- confirming missing asset warnings appear once and do not crash the app

Observed runtime result during validation:

- the app launched successfully
- the game completed normally with exit code `0`
- fallback rendering stayed active when PNGs were missing

## Final Result

The frontend now has:

- a modular theme/asset layer
- image-driven rendering support
- stable logo assignment
- directional Pacman rendering
- themed board colors
- safe fallbacks
- a documented rebuild path

And importantly, the locked backend search files were left unchanged.
