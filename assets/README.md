# Frontend Assets

Place your replacement PNG files directly in this `assets/` folder.

Required filenames:

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

- Pacman / ghost sprites: around `64x64`
- Pellet logo sprites: around `32x32`

Behavior:

- If a PNG is missing, the game falls back to simple built-in Swing drawing.
- Replacing a PNG changes the visuals without needing code changes.
- Pellet logos stay stable for a coordinate during a run, so they do not flicker between frames.
- If the logo PNGs are missing, the renderer still creates stable text badge images such as `AI`, `ML`, and `DB` so the board keeps the internship-hunt feel before custom assets are added.
