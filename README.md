# NITRO (new build, part 1)

Fabric client mod for Minecraft 1.21.4. Contains: ClickGUI, Light Detector, Base Finder (with Sus Chunk Finder).
AutoBuild is **not** in this drop yet.

## Build
Push this folder to a GitHub repo. The workflow in `.github/workflows/build.yml` builds on every push; download
the jar from the run's **Artifacts** (`nitro-jar`). If the build fails, send me the red error lines from the log.
If `fabric_version` can't be resolved, set it to the newest `+1.21.4` Fabric API version in `gradle.properties`.

## Use
Right Shift opens the ClickGUI. Rail on the left (star = favourites), modules in the middle, settings on the right.
In the settings pane: Enabled, Shortcut (click, then press a key; Backspace clears), Favourite, then module settings.
Settings are saved to `config/nitro.json` when the GUI closes and on game exit.

## Light Detector
Highlights loaded block light below Y=0 that is brighter than the sky light in the same cell. Denoise filter, colour by
light level (blue = dim, red = bright) or a custom colour, render radius 1-16 chunks, minimum light 1-15, opacity.
Scans a few chunks per tick nearest-first. Clears when disabled or when the world/dimension changes.

## Base Finder modes
Because I did not have your old Base Finder or the Amethyst Scanner source, the modes below are my own definitions:
- Classic: weighted block-clue score per chunk.
- ANBS+ Scan: clue + man-made-material score per 4x4x4 cell; chunk is flagged when enough cells pass (min-cell filter).
- Activity Clues: re-scans chunks and flags those whose clue/light signature changes between scans.
- Amethyst: Bypass: palette-only scan (does a section contain a clue block at all?), no per-block walk. Fast, coarse.
- Amethyst: Light: lit-above-sky samples below the surface, grouped into cells, min-cell filter.
- Amethyst: ANBS+ Scan: man-made blocks only, cell based.
Clue categories: containers, machinery, redstone, geodes, growth (toggle each in Filters). Natural geodes and generated
structures (mineshafts, villages, bastions) also match some clues, so expect false positives; use sensitivity and filters.

## Added in this drop
Categories: Render (Freecam, Storage ESP), HUD (Target HUD, Spotify HUD). All are toggleable in the ClickGUI, have a
Shortcut (keybind) row, and save their settings to `config/nitro.json`.

- **Freecam**: detaches the camera. WASD / Space / Shift fly, Sprint boosts, mouse looks. Your real player does not move
  or turn. Switches off on damage (setting), death, or world change. Uses two mixins (`nitro.mixins.json`).
- **Storage ESP**: outlines loaded chests, trapped chests, ender chests, barrels, shulker boxes (and optionally hoppers,
  dispensers/droppers, furnaces, brewing stands, chiseled bookshelves, decorated pots). Per-type toggles and colours, fill,
  tracers. Only block entities of chunks your client has loaded.
- **Target HUD**: name, head, health (+absorption), distance, armor and held items of the player under your crosshair, or
  the nearest player in range. Stays for a configurable hold time after you look away.
- **Spotify HUD**: current song, artist, album, artwork and a progress bar, read from the local Spotify app (no login).
  Windows: PowerShell + media session. macOS: AppleScript (allow the automation prompt). Linux: needs `playerctl`.
