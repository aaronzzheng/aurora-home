# Aurora Home

A home screen (launcher) for Android TV and Google TV, built for a 1080p TV and a D-pad remote. Each look is a complete home screen with its own layout, sounds and games, not just a recolour.

## Looks

| Look | What it is |
|---|---|
| **Aurora** | An Apple TV-style home: full-bleed wallpaper, rounded app icons that lift toward you, glass buttons |
| **Retro** | The whole screen is the front of a 1960s Japanese TV: cream enamel, piano-key channel presets, a chrome tuning dial |
| **Classic** | A Windows 95 desktop, drawn pixel for pixel |
| **Art** | A famous painting in a gilded frame on a museum wall |
| **Space** | Looking out of a spacecraft porthole at Earth, with backlit flight-system buttons |
| **Cozy** | An open fire, with smoked-glass controls |
| **Minimal** | Paper and ink by day, black stock at night |
| **Computer Science** | A green phosphor hacker terminal |
| **Day & Night** | A sunny room by day, the fire after 7 pm |

Each look brings its own sound set. The nine built-in games (Tic-Tac-Toe, Connect 4, Checkers, Chess, Dots & Boxes, Hangman, 2048, Minesweeper, Solitaire) are drawn to match: in Classic they are Windows 95 games, in Computer Science they are ASCII, and so on.

## Other features

- **Phone remote:** a web page on your phone with a D-pad, volume, typing, apps, timers and settings. It pairs with a one-time code and takes the TV's current look.
- **Sleep timer, bedtime and wind-down**, a **night mode** that warms and dims the screen, and a **morning screen**.
- **Kid lock**, a **settings PIN**, a **security panel**, and **settings backup and restore**.
- **Gentle volume:** holding volume changes it at a steady pace.

## Building

Requirements: JDK 17 and the Android SDK (with `local.properties` pointing at it, as Android Studio creates it). Then:

```bash
./build.sh            # AuroraHome.apk (release)
./build.sh --preview  # AuroraHome-preview.apk (debuggable, for an emulator)
./build.sh --test     # unit tests
```

Install on a TV with network debugging turned on:

```bash
adb connect <tv-ip>:5555
adb install -r AuroraHome.apk
```

**Signing.** Without a `keystore.properties`, builds use Android's debug key. To sign with your own key, create `keystore.properties` next to `build.gradle.kts`. It's ignored by git; keep the key backed up, because the TV only accepts updates signed with the same key. The file looks like this:

```properties
storeFile=your.keystore
storePassword=…
keyAlias=…
keyPassword=…
```

**Accessibility service.** Bedtime, the sleep timer, night mode and the phone remote need Aurora's accessibility service ("Aurora Remote & Night Mode"). Turn it on in the TV's Accessibility settings.

**Aurora wallpapers.** The original build uses macOS wallpapers, which are Apple's and aren't in this repository. Without them the Aurora look uses the NASA photos instead. Add your own as `res/drawable-nodpi/wp_<name>.webp` plus a blurred `wp_<name>_blur.webp`.

## Credits

Every picture in this repository is public domain, CC0, or drawn for this project.

| Files | Source | Licence |
|---|---|---|
| `sp_*.webp`, `space_earth.webp` | NASA (Hubble and Webb images with ESA, CSA and STScI); `space_earth` is the ISS Expedition 20 photo "Thunderstorms on the Brazilian Horizon" | NASA public domain |
| `cozy_fire.webp` | "Fire in the Winter" by Sweet Ice Cream Photography, via [Wikimedia Commons](https://commons.wikimedia.org/wiki/File:Fire_in_the_Winter_(Unsplash).jpg) | CC0 |
| `light_room.webp` | [Poly Haven](https://polyhaven.com/a/brown_photostudio_05) HDRI "brown_photostudio_05" | CC0 |
| `tx_walnut.webp` | Poly Haven texture [walnut_veneer](https://polyhaven.com/a/walnut_veneer) | CC0 |
| `art_damask.webp`, `art_parquet.webp` | Poly Haven textures [floral_jacquard](https://polyhaven.com/a/floral_jacquard) and [herringbone_parquet](https://polyhaven.com/a/herringbone_parquet) | CC0 |
| `cs_bezel.webp`, `space_cabin.webp`, `minimal_paper.webp` | Rendered for this project | — |
| `res/raw/*.wav` | Synthesized by `tools/sounds.py` and `sounds.py` | — |
| Art look paintings | Downloaded at runtime from the [Art Institute of Chicago API](https://api.artic.edu/docs/) (public-domain works only) | CC0 |
