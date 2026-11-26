# Publishing Trevor+

Releases go to Modrinth and CurseForge through the [mod-publish-plugin](https://github.com/modmuss50/mod-publish-plugin)
(`./gradlew publishMods`), run by `.github/workflows/publish.yml` when a GitHub release is published.

## One-time setup

### 1. Create the projects

| | Modrinth | CurseForge |
|---|---|---|
| Name / slug | Trevor+ / `trevorplus` | Trevor+ / `trevorplus` |
| Summary | A pelt-grinding companion for Hypixel SkyBlock's Trevor the Trapper. | same |
| Description | paste `docs/DESCRIPTION.md` | WYSIWYG editor: Source code button, paste `docs/curseforge-description.html` |
| Icon | `src/main/resources/assets/trevorplus/icon.png` (512×512) | same |
| Categories | Utility, Game Mechanics | Utility & QoL, Map and Information |
| Environment | Client: required · Server: unsupported | (set per file: client) |
| License | CC0-1.0 | Public Domain (CC0) |
| Source / issues | `https://github.com/AirmailSoap3209/trevorplus` (+ `/issues`) | same |

Add a few gallery screenshots: the quest HUD during a quest, the mob box, the Theodolite block highlight, the welcome screen
and the settings menu. Both sites review new projects by hand, and screenshots make the review faster.

### 1b. Trevor+ Lite

Lite is a separate project on each site (it is a separate mod: id `trevorplus-lite`). Create it the same way, with
`docs/DESCRIPTION-LITE.md` (Modrinth) or `docs/curseforge-description-lite.html` (CurseForge) as the description, and
link the two projects to each other in their descriptions or "relations". Then fill in `modrinth_lite_id`,
`curseforge_lite_id` and `curseforge_lite_slug`. The same tokens are used for both; a lite ID left empty is skipped.

### 2. Fill in the project IDs

In `gradle.properties`:

```
modrinth_id=<Modrinth project ID, the 8-character one under the project's "..." menu>
curseforge_id=<CurseForge numeric project ID, from the "About Project" box>
```

A platform whose ID is left empty is skipped.

### 3. Add the API tokens as GitHub secrets

Repository **Settings > Secrets and variables > Actions**:

- `MODRINTH_TOKEN`: a Modrinth personal access token with the *Create versions* scope (modrinth.com/settings/pats).
- `CURSEFORGE_TOKEN`: a CurseForge API token (curseforge.com/account/api-tokens).

Never commit these.

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