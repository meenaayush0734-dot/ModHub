# ModHub (MVP) for Minecraft 1.21.4 · Fabric

## Requirements to play
- Minecraft Java Edition 1.21.4
- Fabric Loader 0.16.9 or newer
- Fabric API for 1.21.4
- Java 21 (Minecraft 1.21.4 requires it)

## Get the ready-to-use jar (no build tools needed)
1. Create a GitHub repository and upload this whole folder, including the hidden `.github` folder.
2. Open the repository's **Actions** tab and wait for "Build ModHub" to finish (about 2 to 5 minutes).
3. Open that run and download the artifact **modhub-1.21.4-fabric**. It contains `modhub-1.0.0.jar`.

## Install
1. Put `modhub-1.0.0.jar` and the Fabric API jar for 1.21.4 into your `.minecraft/mods` folder.
2. Launch the Fabric 1.21.4 profile.
3. Press **M** in-game to open the dashboard.

## Build locally (optional)
Requires JDK 21 and Gradle 8.10+. Run `gradle build`. The jar appears in `build/libs/`.

## MVP features
- Search Modrinth for Fabric mods compatible with 1.21.4
- One-click install with SHA-1 verification; only `.jar` files are accepted
- Existing files are never overwritten
- Refresh Game reloads resources and shows "Restart Required" when mods changed

## Not in this MVP yet
Dependency detection, download progress with cancel and retry, the Installed Content Manager,
the Download Manager, CurseForge, and the other content categories.
