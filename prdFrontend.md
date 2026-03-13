# UI/UX Product Requirements Document (PRD) & Autonomous Execution Manual
**Project:** Pacman "Internship Hunt" Edition (Java Swing Frontend Overhaul)
**Target AI:** Cursor (GPT-5.4 or equivalent advanced autonomous agent)
**End Goal:** A highly visual, interactive, and LinkedIn-ready Java Swing game, structured for future integration into a web portfolio.

## 📌 Context & Vision
The backend algorithms (A* routing, MST heuristics) have been successfully optimized. The current objective is entirely focused on the **Frontend (UI/UX)**. 
The user wants to transform this standard Pacman game into a highly relatable, viral "CS Student Internship Hunt" theme for a LinkedIn showcase. 
* **Pacman** = The CS Student.
* **Pellets** = Tech Company Internships (Logos of varying companies).
* **Ghosts** = Cool, themed adversaries (e.g., "Bugs", "Rejection Letters", or sleek neon designs).

**Future Scalability:** The user plans to eventually integrate this onto their personal website (`vedantkejariwal.com/projects`) with a simulation UI. Therefore, all rendering logic must be strictly decoupled, modular, and cleanly documented so it can be ported to WebAssembly (CheerpJ) or a JS-canvas wrapper in the future.

---

## 🛑 STRICT GUARDRAILS (The "Do Not Touch" List)
Under **NO CIRCUMSTANCES** are you to modify the logic, routing, or structure of the following backend files. They are perfectly optimized and locked:
1. `src/pas/pacman/routing/ThriftyBoardRouter.java`
2. `src/pas/pacman/routing/ThriftyPelletRouter.java`
3. `src/pas/pacman/agents/PacmanAgent.java`

Your playground is strictly limited to the **Rendering, View, and Game Loop UI files** (e.g., files extending `JPanel`, `JFrame`, or handling `Graphics2D` painting).

---

## 🛠️ Phase 1: Codebase Indexing & Asset Architecture
**Goal:** Establish a modular pipeline for custom image rendering.

### 1. Indexing Command
* Scan the entire `/Users/vedantkejariwal/Downloads/Code/cs440 copy/` directory.
* Locate the exact files responsible for drawing the grid, pellets, ghosts, and Pacman (likely containing `Graphics g`, `paintComponent`, or `paint`).

### 2. Asset Management System
* Create a centralized `assets/` folder in the root directory (if it doesn't exist).
* Create a configuration class (e.g., `AssetConstants.java` or `ThemeManager.java`) that loads these images using `ImageIO.read()`.
* **CRITICAL REQUIREMENT:** The user must be able to change a graphic simply by replacing a `.png` file in the `assets/` folder. The code must be completely dynamic and agnostic to the actual image content. Document the exact file paths in a comment block at the top of the Theme Manager.

---

## 🎨 Phase 2: The "Internship Hunt" Visual Overhaul
**Goal:** Replace basic shapes with high-quality, dynamic image assets.

### Feature A: Directional Pacman (The CS Student)
* **Current State:** Usually a static yellow circle or a simple arc.
* **New Implementation:** Load a custom image of a face/character. 
* **Interactive Rotation:** Read the entity's current `Direction` state. Use Java's `Graphics2D` and `AffineTransform` to dynamically rotate the image so the character's face always looks in the direction it is moving (Up = 270°, Down = 90°, Left = 180°, Right = 0°). 

### Feature B: Randomized Tech Logo Pellets
* **Current State:** White/Yellow dots.
* **New Implementation:** The pellets should render as small icons of tech companies (e.g., `logo_a.png`, `logo_b.png`, `logo_c.png`).
* **Randomization Logic:** When the maze is initialized, assign a random logo to each pellet coordinate. Store this mapping (e.g., `HashMap<Coordinate, Image>`) so that the logo doesn't constantly flicker/change every frame. It should look like a map scattered with distinct internship opportunities.

### Feature C: Themed Ghosts & Aesthetics
* Load custom image files for the Ghosts. Ensure the background grid looks modern (e.g., dark mode aesthetic, sleek neon borders) to make the tech logos and character pop.

---

## ⚙️ Autonomous Operating Directives for Cursor

You are an autonomous agent. Do not ask for permission to write code. Follow this exact zero-prompting workflow:

**Step 1: Discover & Map**
* Search the codebase for UI rendering files. Identify where `fillOval`, `fillRect`, or standard drawing methods are currently used for Pacman and Pellets.
* Output an `[INTERNAL REASONING]` block summarizing which files you will modify to inject image rendering.

**Step 2: Scaffold the Asset Manager**
* Write the `ThemeManager` class. Add placeholder fallbacks (e.g., draw a colored circle if `logo_a.png` throws an `IOException`) so the game doesn't crash while the user is downloading their custom PNGs.

**Step 3: Implement Rendering Logic**
* Apply the `Graphics2D.drawImage()` logic.
* Implement the `AffineTransform` rotation for Pacman based on his velocity/action direction.
* Implement the random seed mapping for the Pellet logos.

**Step 4: Autonomous Testing & Failsafes**
* Compile and run the game using the standard run command.
* **Self-Correction:** If you get a `NullPointerException` because an image file doesn't exist yet, ensure your fallback `try/catch` logic handles it gracefully by rendering a standard shape, printing a warning to the console, and allowing the game to continue.

**Step 5: Handover Documentation**
* Once the UI code is successfully injected and tested, output a clear, stylized Markdown guide for the user.
* This guide MUST include:
    1. The exact folder path where they should drop their `.png` files.
    2. The exact filenames the code is looking for (e.g., `pacman_face.png`, `ghost_1.png`, `logo_1.png` to `logo_5.png`).
    3. The dimensions you recommend for the images (e.g., 32x32 pixels) to prevent weird scaling issues in the Swing grid.