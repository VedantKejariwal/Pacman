# Cursor Changes Summary

This document explains the changes made to the Pacman project in a way that is useful for:

- someone who wants a quick high-level summary
- someone trying to navigate the codebase
- someone trying to learn the algorithms and Java syntax used in the code

---

## Commands to run

- java -cp "lib/*:." edu.bu.pas.pacman.Main -a src.pas.pacman.agents.PacmanAgent -x 13 -y 13 -d MEDIUM -p 3 -g 2 --hz 5

## Files Changed

The changes were made in these files:

- `src/pas/pacman/routing/ThriftyPelletRouter.java`
- `src/pas/pacman/routing/ThriftyBoardRouter.java`
- `src/pas/pacman/agents/PacmanAgent.java`

---

## Quick Read: High-Level Summary

If you only read one section, read this one.

### What changed overall

The Pacman bot was improved in three major ways:

1. **Pellet planning became much faster**
   - The pellet-order planner now avoids recomputing many distances from scratch.
   - It also avoids trying to solve the full "eat every pellet in the best possible order" problem exactly when the board is still very large.

2. **Movement became ghost-aware**
   - The board router now prefers safe paths away from dangerous ghosts.
   - If no safe path exists, it can still fall back to a less-safe route so Pacman does not freeze.

3. **The agent replans when danger changes**
   - If a non-scared ghost gets close, Pacman refreshes the board path to the current target.

### Why these changes were needed

On larger boards, the original pellet search behaved like a hard Traveling Salesperson Problem (TSP). Even a strong A* heuristic becomes too slow when the state space becomes huge. The new design solves this by:

- caching useful path distances
- caching MST heuristic results
- using a hybrid strategy:
  - greedy early game
  - weighted A* late game

That gives a practical speedup while still keeping good route quality.

---

## Project-Level Mental Model

These three files work together like this:

```text
PacmanAgent
   |
   | asks "What pellet should I go to next?"
   v
ThriftyPelletRouter
   |
   | asks "How far apart are these important points on the board?"
   v
ThriftyBoardRouter
```

You can think about the code in two layers:

- **High-level planning**: choose the order of pellets
- **Low-level movement**: choose how to safely walk through the maze

### Responsibility split

- `PacmanAgent.java`
  - the coordinator
  - decides when to ask for a new pellet plan
  - decides when to rebuild the current walking route

- `ThriftyPelletRouter.java`
  - the strategic planner
  - decides pellet order
  - uses heuristics, caching, greedy reduction, and weighted A*

- `ThriftyBoardRouter.java`
  - the tactical mover
  - finds actual board paths
  - avoids dangerous ghosts when possible

---

## File 1: `ThriftyPelletRouter.java`

### What this file does logically

This file answers:

> "Given Pacman's position and the remaining pellets, what order should Pacman eat them in?"

This is the hardest part of the problem, because the number of possible pellet orders grows extremely quickly as the number of pellets increases.

### High-level changes made

- Added a distance cache so board distances between important coordinates are reused.
- Added an MST cache so the heuristic does not recompute the same tree repeatedly.
- Added helper methods to make the logic easier to reuse and easier to understand.
- Added a hybrid strategy:
  - greedy nearest-pellet reduction while many pellets remain
  - weighted A* on the smaller remaining tail

### Detailed description of the changes

#### 1. Pairwise distance caching

The code now stores distances between:

- Pacman's current coordinate
- every remaining pellet coordinate

This avoids repeatedly calling board search for the same pair of locations.

Conceptually:

```text
distance(A, B) is expensive once
distance(A, B) is cheap forever after that
```

This is stored in:

- `HashMap<Coordinate, HashMap<Coordinate, Float>> cache`

This acts like a 2D lookup table:

```text
cache.get(c1).get(c2)
```

#### 2. MST heuristic caching

The heuristic uses:

- distance from Pacman to the nearest remaining pellet
- plus MST weight over the remaining pellets

The MST only depends on the set of remaining pellets, not on the exact path used to reach that state.

So the code stores:

- `Set<Coordinate>` -> `Float`

This is the role of:

- `HashMap<Set<Coordinate>, Float> mstCache`

This avoids recomputing Prim's algorithm over and over for the same pellet set.

#### 3. Hybrid greedy + weighted A*

This was the biggest scaling improvement.

When many pellets remain, solving the exact pellet-order search is too expensive.

So the router now does:

1. **Greedy phase**
   - repeatedly pick the nearest pellet
   - stop when the number of remaining pellets becomes small

2. **Weighted A* phase**
   - run a stronger search on the remaining tail
   - use `g + W * h` instead of ordinary `g + h`

This trades a little optimality for a large speed gain.

### Why this algorithm was chosen

A plain exact A* search is elegant, but it does not scale well enough when the number of pellets is large.

The final hybrid strategy was chosen because:

- greedy nearest-neighbor is extremely cheap
- weighted A* keeps some lookahead quality
- the hybrid avoids the worst part of the state-space explosion
- cached board distances make both phases faster

### Useful learning notes for this file

#### Why `HashMap` was used

`HashMap` is used because lookup time is usually very fast on average.

That makes it a good fit for:

- repeated distance lookups
- repeated MST lookups

#### Why `PriorityQueue` was used

`PriorityQueue` is the standard structure for:

- A*
- weighted A*
- Prim's algorithm

because it quickly returns the smallest-scoring candidate.

#### Why helper methods were added

Methods like:

- `getCachedDistance(...)`
- `calculateMstWeight(...)`
- `getNearestPelletCoordinate(...)`
- `buildGreedyPrefix(...)`

make the algorithm easier to read and reduce duplicate logic.

### Important syntax patterns used in this file

#### Diamond operator

```java
new Path<>(initial)
```

The `<>` tells Java to infer the generic type automatically.

#### Ternary operator

```java
(path == null) ? Float.POSITIVE_INFINITY : path.getTrueCost()
```

This is a compact `if/else` expression:

- if `path == null`, use infinity
- otherwise use the path cost

#### `computeIfAbsent`

```java
mstCache.computeIfAbsent(key, k -> calculateMstWeight(...))
```

Meaning:

- if the key already exists, return the stored value
- otherwise compute it once, store it, and return it

This is a clean caching pattern.

### Visual view of the algorithm

```text
Start state
   |
   v
Precompute important pairwise board distances
   |
   v
Many pellets left?
   |
   +-- yes --> Greedily remove nearest pellets
   |              |
   |              v
   |        Smaller remaining problem
   |
   +-- no ----------------------------+
                                      |
                                      v
                            Weighted A* on reduced state space
                                      |
                                      v
                                Pellet order path
```

---

## File 2: `ThriftyBoardRouter.java`

### What this file does logically

This file answers:

> "If Pacman wants to go from coordinate A to coordinate B, which board path should he take?"

This is the lower-level movement router.

### High-level changes made

- Added ghost-aware tile classification
- Kept BFS as the basic search strategy
- Changed neighbor preference so safe tiles are explored first
- Added a fallback mode that ignores ghost danger if no safer path exists

### Detailed description of the changes

#### 1. Ghost-aware helper methods

The file now includes helper methods for:

- walkability checks
- immediate ghost danger
- risky ghost proximity
- scared-ghost opportunity

These break the logic into smaller, readable pieces.

#### 2. Safe / risky / forced neighbor buckets

Instead of treating all legal neighbors equally, the router now separates neighbors into three groups:

- `xyz`
  - safe neighbors
- `risky`
  - near ghosts but not immediate danger
- `forced`
  - immediate danger, used only if nothing else exists

This changes the behavior without needing a more complicated weighted shortest-path algorithm.

#### 3. BFS with safety preference

The file still uses BFS because BFS is simple and reliable when all move costs are effectively uniform.

The improvement is:

- safe neighbors are returned first
- risky ones are used second
- forced-danger moves are only used as a fallback

#### 4. Fallback path if ghost avoidance blocks everything

The router first tries a ghost-aware search.

If that fails, it does another BFS where:

- `ignoreGhosts = true`

This prevents Pacman from getting stuck forever just because every path looks dangerous.

### Why this algorithm was chosen

BFS was kept because:

- movement on the grid is simple
- all steps have equal cost in the normal board path model
- the existing code structure already fit BFS well

Instead of replacing BFS with something much more complicated, the safer design choice was:

- keep BFS
- improve neighbor ordering and filtering

That is a smaller, easier-to-debug change.

### Useful learning notes for this file

#### Why `instanceof` is used

The code checks:

```java
if(!(entity instanceof Ghost))
```

This makes sure the object is actually a `Ghost` before casting it.

#### Why `ArrayDeque` is used as a queue

`ArrayDeque` is a good modern Java choice for queue behavior:

- fast push/pop from ends
- lighter than some older queue classes

#### Why Chebyshev distance is used

Ghost chase behavior in the framework is based on Chebyshev distance, so this file uses:

```text
max(dx, dy)
```

instead of Manhattan distance.

### Visual view of the board-routing logic

```text
Neighbors of current tile
   |
   v
Classify each as:
   - safe
   - risky
   - forced-danger
   |
   v
Return safe neighbors if any exist
   |
   +-- otherwise risky
   |
   +-- otherwise forced-danger
```

---

## File 3: `PacmanAgent.java`

### What this file does logically

This file is the main controller for the Pacman bot.

It answers:

- "Do I need a new pellet-order plan?"
- "Do I need a new board route?"
- "Which action should I take this turn?"

### High-level changes made

- Connected the new routing logic cleanly
- Added replan logic when nearby ghosts make the current board route unsafe
- Kept path reconstruction logic clear and correct
- Added more explanation comments to make the control flow easier to study

### Detailed description of the changes

#### 1. Replanning when ghosts get close

The method:

- `shouldRefreshPlan(...)`

checks if a live, non-scared ghost is near Pacman.

If so, `makeMove(...)` calls:

- `makePlan(game)`

This recomputes the **board path** to the current target using the latest board situation.

#### 2. Separation between pellet planning and board planning

This file now clearly acts as the coordinator between:

- pellet-level strategy from `ThriftyPelletRouter`
- coordinate-level pathfinding from `ThriftyBoardRouter`

That separation is important because those are different problems.

#### 3. Path reconstruction

The file reconstructs paths by walking parent pointers:

- pellet-level path reconstruction uses `curr = curr.getParentPath();`
- board-level path reconstruction uses `x = x.getParentPath();`

This is the standard pattern when a search returns a linked chain of parent pointers instead of a ready-made list.

### Why this design was chosen

The agent should not mix all planning details into one giant method.

It is cleaner to let:

- pellet router decide strategic targets
- board router decide movement routes
- agent decide when to call each one

This makes the code easier to debug and easier to extend later.

### Useful learning notes for this file

#### Why a `Stack<Coordinate>` is used

The board path is reconstructed backward from target to source.

A stack is useful because:

- push the nodes while walking backward
- pop them later in forward move order

#### Why `add(0, value)` is used in the pellet-order list

When reconstructing the pellet path:

```java
pelletOrder.add(0, curr.getDestination().getPacmanCoordinate());
```

inserting at index `0` reverses the backward parent chain into forward order.

#### Why the target pellet is index `1`

After reconstructing the pellet-order path:

- index `0` is usually Pacman's current location
- index `1` is the next actual pellet target

### Visual view of the agent flow

```text
makeMove(game)
   |
   v
Is a ghost nearby?
   |
   +-- yes --> rebuild board route to current target
   |
   +-- no --> keep current route if still usable
   |
   v
No current route?
   |
   +-- yes --> ask pellet router for pellet-order plan
   |             |
   |             v
   |        choose next target pellet
   |             |
   |             v
   |        call makePlan(game)
   |
   v
Pop next coordinate from plan
   |
   v
Convert coordinate difference into Action
```

---

## How the Three Files Relate to Each Other

This relationship is the most important thing to understand in the codebase.

### Connection summary

- `PacmanAgent.java`
  - owns the routers
  - decides when to call them

- `ThriftyPelletRouter.java`
  - works at the "pellet order" level
  - depends on `ThriftyBoardRouter` for real board distances

- `ThriftyBoardRouter.java`
  - works at the "single path through the maze" level
  - is used both directly by the agent and indirectly by the pellet router

### Dependency diagram

```text
PacmanAgent
   |
   +----> ThriftyPelletRouter
   |           |
   |           +----> ThriftyBoardRouter
   |
   +----> ThriftyBoardRouter
```

### Why this relationship matters

The pellet router does not know how to move through walls and ghosts by itself.
It asks the board router for true path distances.

So:

- pellet planning is strategic
- board routing is tactical
- the agent ties them together

---

## Why These Coding Decisions Were Reasonable

This section is about learning from the design, not just memorizing the code.

### Why not keep exact A* for everything?

Because exact A* over all pellet-order states becomes too expensive as the board grows.

The problem behaves like:

- a combinatorial ordering problem
- similar to TSP

So exact search is elegant, but not practical enough on bigger boards.

### Why greedy + weighted A*?

Because it gives a strong compromise:

- greedy is fast
- weighted A* keeps some lookahead quality
- the hybrid is much cheaper than solving the entire search exactly

### Why use caching?

Because board distances between important points are reused many times.

If those distances are recomputed repeatedly, the total cost becomes very large.

Caching turns:

- repeated expensive search

into:

- repeated cheap lookup

### Why keep BFS in board routing?

Because the board routing problem is much smaller and simpler than the pellet-order problem.

BFS is:

- easy to reason about
- reliable
- already close to the original structure

The smarter change was to keep BFS and improve the neighbor selection.

### Why separate helper methods?

Because helper methods improve:

- readability
- reuse
- debugging
- commenting

They also make it easier for a learner to understand one idea at a time.

---

## Java Syntax Reference From These Files

Here are some important syntax patterns that appear in the code.

### `final`

```java
public final Coordinate coord;
```

Meaning:

- the field is assigned once
- after assignment, it cannot point to a different object

### Generic types

```java
Path<Coordinate>
HashMap<Coordinate, HashMap<Coordinate, Float>>
Set<Coordinate>
```

Meaning:

- `Path<Coordinate>` stores coordinates
- `Set<Coordinate>` stores a unique collection of coordinates
- nested `HashMap` acts like a lookup table

### `instanceof`

```java
if(entity instanceof Ghost)
```

Meaning:

- check the real runtime type before casting

### Lambda expression

```java
key -> this.calculateMstWeight(src, ep)
```

Meaning:

- a small anonymous function
- used here to compute a cache entry only when needed

### Ternary operator

```java
condition ? a : b
```

Meaning:

- if condition is true, use `a`
- otherwise use `b`

### `PriorityQueue`

```java
PriorityQueue<Path<PelletVertex>> q = new PriorityQueue<>(...)
```

Meaning:

- keeps items ordered by priority
- very useful for A*, weighted A*, and Prim's algorithm

---

## Final Takeaway

The core lessons from these changes are:

1. **Separate strategy from movement**
   - pellet order is a different problem from maze walking

2. **Use caching whenever the same expensive computation repeats**
   - especially path distances and heuristic subproblems

3. **Hybrid algorithms are often more practical than pure optimal algorithms**
   - exact search is nice, but sometimes too slow

4. **Keep the architecture understandable**
   - agent coordinates
   - pellet router plans
   - board router moves

5. **Comments should explain intent, not just repeat the code**
   - good comments tell you why the code exists

---

## Short Version

If you want the shortest summary possible:

- `ThriftyPelletRouter.java` chooses pellet order using cached distances, cached MST values, and a greedy + weighted A* hybrid.
- `ThriftyBoardRouter.java` finds ghost-aware board paths using BFS with safe/risky/danger neighbor preference.
- `PacmanAgent.java` coordinates both routers, rebuilds routes when ghosts get close, and turns planned coordinates into moves.

