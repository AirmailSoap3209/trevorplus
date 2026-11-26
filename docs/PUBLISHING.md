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

## Every release

1. Bump `mod_version` in `gradle.properties` (the published version becomes `<mod_version>+26.2`).
2. Replace `docs/RELEASE-NOTES.md` with this release's changes (it becomes the changelog on both sites),
   and add a section to `docs/CHANGELOG.md`.
3. Check locally:
   ```
   ./gradlew clean build
   sh tools/run-tests.sh
   ./gradlew publishMods        # without tokens set: dry run into build/publishMods
   ```
   Load `build/libs/trevorplus-<version>.jar` in a fresh 26.2 instance: the welcome screen should open once on the
   first world join, and `/trevorplus help` should list no `debug` section.
4. Commit, push, then create a GitHub release with tag `v<mod_version>`. The publish workflow uploads to both sites and
   attaches the jars to the release.

To publish from your own machine instead, set `MODRINTH_TOKEN` / `CURSEFORGE_TOKEN` in the environment and run
`./gradlew build publishMods`.

## Porting to a new Minecraft version

Update `minecraft_version`, `fabric_api_version` and `loader_version` in `gradle.properties` and `"minecraft"` in
`fabric.mod.json`. The publish config picks up the new game version automatically.