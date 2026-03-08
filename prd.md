# Technical Product Requirements Document (PRD) & Execution Manual
**Project:** Pacman Autonomous Search Agent Optimization & Enhancement
**Target AI:** Cursor (GPT-5.4 or equivalent advanced autonomous agent)

## 📌 Context & Objective
You are an advanced, autonomous AI coding assistant. Your task is to optimize and enhance an existing Pacman agent codebase written in Java. The user has successfully implemented a baseline agent that finds correct paths but suffers from performance bottlenecks (fails high-tier efficiency tests) and currently ignores Ghosts. 

**Your primary directive:** Achieve high efficiency and implement ghost evasion with the **absolute minimum number of changes** to the original codebase. You must preserve the user's original logic structure, coding style, and variable naming conventions.

---

## 🛠️ Phase 1: Backend Efficiency Optimization
**Goal:** Pass the efficiency autograder (beat 8.6s execution time for 100+ mazes) by eliminating redundant calculations.

### Constraints & Rules
* **DO NOT** rewrite the `graphSearch` or `getHeuristic` methods from scratch.
* **DO** inject specific caching and pre-calculation blocks into the user's existing logic.
* **Required Commenting:** Every single line you add or change must include a detailed comment explaining the *syntax*, the *logic*, and the *reasoning* for why it makes the code faster.

### Required Implementations
1.  **Pre-Calculation Matrix (`ThriftyPelletRouter.java -> graphSearch`)**
    * *Current State:* The agent uses "lazy loading," calculating physical paths via BFS during the A* search.
    * *Action:* Before the `PriorityQueue` is initialized, write a nested loop to iterate through all `initial.getRemainingPelletCoordinates()` plus the starting `PacmanCoordinate`. Call `boardRouter.graphSearch` for every pair and store the `TrueCost` in `sessionParams.cache` for *both* directions.
2.  **MST Cache (`ThriftyPelletRouter.java -> getHeuristic`)**
    * *Current State:* The agent recalculates the Minimum Spanning Tree (MST) from scratch every time a node is visited.
    * *Action:* Wrap the existing MST logic (the two `while`/`for` loops) in a `HashMap` lookup (e.g., `mstCache.computeIfAbsent`). Key it by the set of remaining pellets. Clear this cache at the start of `graphSearch`.
3.  **Maze-Aware Heuristic (Fixing the Datatype Issue)**
    * *Current State:* The MST uses `DistanceMetric.manhattanDistance`, which is optimistic and causes A* to explore too many nodes.
    * *Action:* Replace `manhattanDistance` with `getEdgeWeight`. 
    * *Datatype Fix:* Because `getEdgeWeight` requires `PelletVertex` objects, but the loop uses `Coordinate` objects, use the factory method `src.removePellet(coordinate)` to generate the required vertices on the fly. 
    * *Example Injection:* `getEdgeWeight(src.removePellet(start), src.removePellet(p), params);`

---

## 👻 Phase 2: Ghost Evasion (Risk Implementation)
**Goal:** Modify the agent to win games in the presence of ghosts.
* Target 1: Win 25% of games with 1 ghost (2 lives).
* Target 2: Win 20% of games with 2 ghosts (3 lives).
* Target 3: Win 10% of games with 4 ghosts (5 lives).

### Constraints & Rules
* **Maintain Admissibility & Consistency:** When adding "risk" to edge weights, ensure $h(n) \le true\_cost$. If you artificially inflate the cost of a path because a ghost is near, you must carefully balance the heuristic so A* doesn't break.
* **Minimal Intrusion:** Do not rewrite the routing architecture. Modify existing traversal loops to factor in ghost locations.

### Required Implementations
1.  **Risk-Aware Board Routing (`ThriftyBoardRouter.java`)**
    * Read the `GameView` to get ghost locations and their `GhostChaseRadius`.
    * Modify `getOutgoingNeighbors` or the BFS queue logic: If a coordinate is within the chase radius or directly adjacent to a non-scared ghost, dramatically increase its transition cost or temporarily treat it as a wall (unless Pacman is trapped).
2.  **Scared Ghost Exploitation**
    * If ghosts are scared (e.g., `ghost.getIsScared()`), the risk weight should invert, potentially rewarding Pacman for eating them if it aligns with the pellet path.

---

## 🧠 Phase 3: Codebase Indexing & Frontend Preparation
**Goal:** Establish a deep structural understanding of the entire repository to facilitate future frontend/UI overhauls and modular backend additions.

### Instructions for Cursor AI
1.  **Deep Indexing:** Autonomously read and index all `.java` files, `.html` Javadoc files (specifically `index-all.html`), and project configurations.
2.  **Mental Model Mapping:** Build a persistent contextual map of how the Rendering engine (Frontend) connects to the Game engine and Agents (Backend).
3.  **Scalability Standard:** For any changes made in Phase 1 and 2, ensure the code is modular. If a new heuristic or a UI tracker needs to be added later, the injection points must be clearly marked.

---

## ⚙️ Autonomous Operating Directives for Cursor
You are expected to operate with **zero-prompting execution** once this document is read. Follow this exact workflow:

1.  **Analyze & Plan:** Read the specified files (`PacmanAgent.java`, `ThriftyBoardRouter.java`, `ThriftyPelletRouter.java`). Map out the exact line numbers where you will inject the Phase 1 optimizations.
2.  **Self-Reasoning:** Before writing code, output a brief internal monologue block `[INTERNAL REASONING]` validating that your proposed change matches the user's exact coding style and does not break existing logic.
3.  **Execute Phase 1:** Write the efficiency updates. Thoroughly comment on the code.
4.  **Execute Phase 2:** Implement the Ghost risk logic.
5.  **Autonomous Testing Loop:**
    * Mentally trace the execution of the code. 
    * Check for infinite loops (e.g., verify `curr = curr.getParentPath()` is present in path reconstruction).
    * Check for null pointers in the cache lookups.
    * If you detect a logical flaw, fix it immediately without asking for permission.
6.  **Final Output:** Present the finalized code blocks ready to be saved, with an explanation of how the UI/Frontend indexing has been stored in your context for the next phase.