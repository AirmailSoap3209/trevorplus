# Publishing Trevor+

Releases go to CurseForge and Modrinth through the [mod-publish-plugin](https://github.com/modmuss50/mod-publish-plugin)
(`./gradlew publishMods`), run by `.github/workflows/publish.yml` when a GitHub release is published.

## Where each version goes

| | CurseForge | Modrinth |
|---|---|---|
| **Trevor+** (full, with ESP) | yes | **never**: Modrinth does not allow cheats. The build has no way to send it there. |
| **Trevor+ Lite** (no ESP, no automation) | optional, own project | yes, own project |

Both are built and released together with the same version number.

## One-time setup

### 1. CurseForge: Trevor+

| Field | Value |
|---|---|
| Name | Trevor+ |
| Summary | A pelt-grinding companion for Hypixel SkyBlock's Trevor the Trapper. |
| Description | WYSIWYG editor: Source code button, paste `docs/curseforge-description.html` |
| Icon | `src/main/resources/assets/trevorplus/icon.png` (512×512) |
| Categories | Utility & QoL, Map and Information |
| License | Public Domain (CC0) |
| Source / issues | `https://github.com/AirmailSoap3209/trevorplus` (+ `/issues`) |

Then set `curseforge_id` and `curseforge_slug` in `gradle.properties`.

### 2. Modrinth: Trevor+ Lite

Create a project at modrinth.com (**Create a project**); it starts as a draft.

| Field | Value |
|---|---|
| Name | Trevor+ Lite |
| URL / slug | `trevorplus-lite` |
| Summary | A fair-play companion for Hypixel SkyBlock's Trevor the Trapper: quest stats, Theodolite aid and alerts, no ESP or automation. |
| Description | paste `docs/modrinth-description-lite.md` (it does not mention the full version, on purpose) |
| Icon | `src/main/resources/assets/trevorplus/icon.png` |
| Categories | Utility (primary), Game Mechanics |
| Environment | Client: **required** · Server: **unsupported** |
| License | CC0-1.0 |
| Source / issues | `https://github.com/AirmailSoap3209/trevorplus` and `/issues` |
| Gallery | the quest HUD, the Theodolite blocks, the settings menu, the welcome screen, the accept button |

Copy the project ID (the **⋯** menu > **Copy ID**, 8 characters) into `modrinth_lite_id` in `gradle.properties`.
The Lite update check uses it too, so it must be set before the release is built.

### 3. CurseForge: Trevor+ Lite (optional)

Same as step 1 with `docs/curseforge-description-lite.html`, then set `curseforge_lite_id` and `curseforge_lite_slug`.
Leave them empty to publish Lite on Modrinth only.

### 4. API tokens as GitHub secrets

Repository **Settings > Secrets and variables > Actions**:

- `MODRINTH_TOKEN`: a Modrinth personal access token (modrinth.com/settings/pats) with the **Create versions** scope.
- `CURSEFORGE_TOKEN`: a CurseForge API token (curseforge.com/account/api-tokens). Used for both CurseForge projects.

Never commit these.

### 5. First Modrinth upload and review

Release as usual (below). The workflow uploads the Lite jar to the draft project. Then, on Modrinth, open the project
and press **Submit for review**: Modrinth only reviews projects that have at least one version. Until it is approved,
the page is private and Lite's in-game update notice finds nothing (it stays silent).

## Versioning

Trevor+ uses [semantic versioning](https://semver.org): `MAJOR.MINOR.PATCH`, counted from the last **published**
release, with no numbers skipped.

| Bump | When | Example |
|---|---|---|
| PATCH | Fixes only, nothing new | 2.4.0 → 2.4.1 |
| MINOR | New features or settings; existing settings, commands and saved data keep working | 2.4.1 → 2.5.0 |
| MAJOR | Something stops working the old way: a feature, setting or command is removed or changes meaning, or saved data is not read any more | 2.5.0 → 3.0.0 |

- The version is plain, e.g. `2.4.0`. No `+26.2`: the Minecraft version is stated in `fabric.mod.json` and on each upload.
- Pre-releases are `2.5.0-beta.1`, `2.5.0-rc.1` and so on. They are published as betas, and the in-game update notice
  ignores them.
- The GitHub release tag is always `v` + the version (`v2.4.0`). The publish workflow stops if they differ, and the
  build refuses a `mod_version` that is not semver.
- Porting to a new Minecraft version with no other changes is a MINOR bump.

## Every release

1. Bump `mod_version` in `gradle.properties` following the table above.
2. Replace `docs/RELEASE-NOTES.md` with this release's changes (it becomes the changelog on both sites),
   and add a section to `docs/CHANGELOG.md`.
3. Check locally:
   ```
   ./gradlew clean build
   sh tools/run-tests.sh
   ./gradlew publishMods        # without tokens set: dry run into build/publishMods
   ```
   Load `build/libs/trevorplus-<version>.jar` in a fresh 26.2 instance: the welcome screen should open once on the
   first world join, and `/trevorplus help` should list no `debug` section. Then do the same with
   `trevorplus-lite-<version>.jar`: the mob box shows only while the mob is in sight, no glow, direction line or off-screen pointer, and no "Behind walls" setting or Automatic accept.
4. Commit, push, then create a GitHub release with tag `v<mod_version>` (e.g. `v2.4.0`). The publish workflow uploads to both sites and
   attaches the jars to the release.

To publish from your own machine instead, set `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN` in the environment and run
`./gradlew build publishMods`.

## Porting to a new Minecraft version

Update `minecraft_version`, `fabric_api_version` and `loader_version` in `gradle.properties` and `"minecraft"` in
`fabric.mod.json`. The publish config picks up the new game version automatically.