# Pacman Project Overview

This project has two main parts:

- a backend that decides how Pacman should plan and move
- a frontend that decides how the game is drawn

The backend is where the search logic lives. The frontend is where the visuals, assets, and rendering live.

## Main Backend Files

- `src/pas/pacman/agents/PacmanAgent.java`
  - the coordinator
  - asks the pellet router what to target next
  - asks the board router how to walk there
  - refreshes plans when ghosts become dangerous

- `src/pas/pacman/routing/ThriftyPelletRouter.java`
  - the high-level planner
  - chooses the order of pellets to eat
  - uses caching, greedy reduction, MST heuristic, and weighted A*

- `src/pas/pacman/routing/ThriftyBoardRouter.java`
  - the low-level pathfinder
  - finds safe board paths
  - prefers safer tiles when ghosts are nearby

## Frontend Files

- `edu/bu/pas/pacman/rendering/ThemeManager.java`
  - loads images from `assets/`
  - provides fallback visuals if images are missing

- `edu/bu/pas/pacman/rendering/GameRenderer.java`
  - draws the board, pellets, Pacman, and ghosts
  - handles Pacman rotation and themed rendering

- `edu/bu/pas/pacman/rendering/GamePanel.java`
  - Swing panel that calls the renderer

- `assets/`
  - put custom frontend images here
  - replace files here to change the visuals without changing Java code

## How To Change The Frontend

1. Replace images in `assets/`
   - `pacman_face.png`
   - `ghost_1.png` to `ghost_4.png`
   - `ghost_scared.png`
   - `logo_1.png` to `logo_5.png`

2. If you change frontend Java code, rebuild it:

```bash
javac -cp "lib/*:." -d build/frontend @frontend.srcs
jar uf "lib/pas-pacman-jar-1.0.0.jar" -C "build/frontend" edu/bu/pas/pacman/rendering/GamePanel.class -C "build/frontend" edu/bu/pas/pacman/rendering/GameRenderer.class -C "build/frontend" edu/bu/pas/pacman/rendering/ThemeManager.class
```

3. Run the game:

```bash
java -cp "lib/*:." edu.bu.pas.pacman.Main -a src.pas.pacman.agents.PacmanAgent -x 13 -y 13 -d MEDIUM -p 3 -g 2 --hz 5
```

Useful options:

- `-x`, `-y`: board size
- `-d`: difficulty
- `-p`: Pacman lives
- `-g`: number of ghosts
- `--hz`: render speed
- `-s`: run without GUI

## Game Logic In Simple Terms

The game logic is split into layers:

```text
PacmanAgent
   |
   | chooses the next target
   v
ThriftyPelletRouter
   |
   | estimates good pellet order
   v
ThriftyBoardRouter
   |
   | finds the actual path on the maze
   v
Game movement
```

## Algorithms Used

- `BFS`
  - used for board-level pathfinding
  - good for shortest paths on an unweighted grid

- `A*`
  - used for pellet planning
  - combines path cost so far with a heuristic estimate

- `Weighted A*`
  - late-game optimization in pellet search
  - faster than plain A* when many states are possible

- `Greedy nearest-neighbor`
  - used in the early pellet phase to shrink the search space quickly

- `Prim's algorithm`
  - used to estimate MST cost in the heuristic
  - helps A* reason about the remaining pellets

## Data Structures Used

- `HashMap`
  - caches pairwise distances
  - caches MST values

- `PriorityQueue`
  - used in A*, weighted A*, and Prim's algorithm

- `HashSet`
  - tracks visited states and visited coordinates

- `Stack`
  - stores the current movement plan so Pacman can pop the next step

- `ArrayDeque`
  - used as the BFS queue

## Techniques Used For Game Functionality

- `distance caching`
  - avoids recomputing the same board distances many times

- `MST heuristic caching`
  - avoids rebuilding the same heuristic tree repeatedly

- `hybrid search`
  - greedy early game, weighted A* late game

- `ghost-aware routing`
  - safer neighbors are preferred before risky ones

- `reactive replanning`
  - if a ghost gets close, Pacman refreshes the path

- `asset-driven rendering`
  - frontend visuals come from files in `assets/`

- `fallback rendering`
  - if an image is missing, the game still runs using built-in drawing

## Codebase Structure

```text
cs440 copy/
|
|-- src/
|   `-- pas/pacman/
|       |-- agents/
|       |   `-- PacmanAgent.java
|       `-- routing/
|           |-- ThriftyBoardRouter.java
|           `-- ThriftyPelletRouter.java
|
|-- edu/
|   `-- bu/pas/pacman/rendering/
|       |-- GamePanel.java
|       |-- GameRenderer.java
|       `-- ThemeManager.java
|
|-- assets/
|   `-- frontend images
|
|-- lib/
|   `-- game jars
|
|-- doc/
|   `-- Javadocs
|
|-- frontend.srcs
|-- pacman.srcs
|-- routing.srcs
`-- README.md
```

## Quick Mental Model

- Backend decides what Pacman should do.
- Frontend decides how that decision looks on screen.
- `assets/` changes appearance.
- routing and agent files change behavior.

That is the main idea of the project.
