# Tim - Trail Investigator Module

**Meteor Client addon** providing utilities focused on **stash hunting**, **base hunting**, and general survival/QoL on the 2b2t anarchy server (and similar environments).

## Modules

- Baromine
- Chambers Assistant
- City Assistant
- Datamine
- Dog Tag
- Dungeon Assistant
- Eight To One
- Elytra Assistant
- Gatekeeper
- Graveyard
- Groundwork
- Handhold
- Handmold
- Illumood
- Illushine
- Inspector Gadget
- Inventory101
- Lava Marker
- Loot Lens
- Manor Assistant
- Manuscript
- Mendbot
- Mobanom
- Neighbourhood Watch
- Penpal
- Portal Maker
- Raidar
- Ringmaster
- Rocket Pilot
- Safety Net
- Server Healthcare System
- Sign Scanner
- Skipper
- Third Sight
- Threaturn
- Time Throttle
- Total Disposal
- Tunnelers
- Tweaker
- Waypearl

## HUDs

- Baromine
- Motance
- Portal Stock
- Chambers Assistant
- City Assistant
- Dungeon Assistant
- Dura Panel
- Eight To One
- End Assistant
- Gatekeeper
- Info Assistant
- Last Seen Player
- Loot Lens
- Neighbourhood Watch
- Position
- Rocket Pilot
- Second Life
- Server Report
- Statistics Information
- Time Throttle
- Timmy Icon

The Timmy Icon HUD displays Timmy from South Park, the namesake of the Tim
addon, using `assets/tim/textures/hud/timmy.png`. Its `icon-scale` and `opacity`
settings control the image's size and transparency. `padding-horizontal` and
`padding-vertical` independently add space around the image, and the optional
`background` setting adds a configurable color behind it.

To enable the optional HUD sound, add your OGG audio file as
`src/main/resources/assets/tim/sounds/timmy.ogg`. The Sound settings let you
choose a randomized seconds/minutes/hours interval or a daily in-game time, and
adjust volume. Settings are organized into Appearance, Blink, Sound, and Sound
Schedule groups. Sleep schedule mode plays the sound when Timmy wakes after a
configurable real-time or in-game-tick sleep duration.

The Timmy Icon HUD can also blink using a closed-eyes image at
`src/main/resources/assets/tim/textures/hud/timmy-blink.png`. Match its aspect
ratio to the regular `timmy.png`; the Blink settings control the random interval
and blink duration.

When Timmy's sound plays, the HUD briefly displays the transparent
`timmy-shout.png` image from the same folder.

Angry expressions use transparent `timmy-angry-one.png` and
`timmy-angry-two.png` images in the same folder. The Angry settings let you
choose either expression or random selection, set random appearance intervals
and display duration, and enable angry expressions when damage interrupts
Sleep Mode.

More modules and HUDs are planned.

## Requirements

- Minecraft **1.21.4, 1.21.5, 1.21.8, 1.21.11, or 26.1**
- Java **21 or newer** for 1.21.x; Java **25 or newer** for 26.1
- [Fabric Loader](https://fabricmc.net/use/installer/)
- [Meteor Client](https://meteorclient.com/) compatible with your Minecraft version
- **[Baritone](https://github.com/cabaletta/baritone/releases)** Fabric mod compatible with your Minecraft version, installed in your `mods` folder
- Tim addon JAR built for the same Minecraft version

## Installation

1. Download the latest release JAR from [Releases](https://github.com/rithsgit/HuntingUtilitiesBETA/releases) (or build from source — see below).
2. Make sure Fabric Loader, Meteor Client, and the Fabric version of Baritone are compatible with the same Minecraft version as the addon.
3. Place the Tim, Meteor Client, and **Baritone mod JARs** in your Minecraft `mods` folder. Baritone is required; the API JAR used to build Tim is not a substitute for the runtime mod.
4. Launch Minecraft using the Fabric profile.
5. Open Meteor's GUI (default key: `Right Shift`) and find the modules under the **Tim** category.

## Building from Source

This project uses Gradle and supports building a separate addon JAR for each supported Minecraft version.
Each target compiles against the corresponding Meteor Client snapshot, so use a
matching Meteor version at runtime. The 26.1 port uses a separate Mojang-named
source tree and Gradle wrapper; changes to shared features may need to be
ported to both source trees.

### Setup

- Clone the repo:  
  ```bash
  git clone https://github.com/rithsgit/HuntingUtilitiesBETA.git
  cd HuntingUtilitiesBETA
  ```

### Build

Use Java 21 and select a 1.21.x Minecraft target at build time:

```bash
./gradlew build -Pminecraft_version=1.21.4
./gradlew build -Pminecraft_version=1.21.5
./gradlew build -Pminecraft_version=1.21.8
./gradlew build -Pminecraft_version=1.21.11
```

In Windows PowerShell, quote the property to pass the entire version:

```powershell
.\gradlew.bat build "-Pminecraft_version=1.21.11"
```

For 26.1, use Java 25 for Gradle and build the separate project:

```bash
bash versions/26.1/gradlew -p versions/26.1 build
```

In Windows PowerShell:

```powershell
.\versions\26.1\gradlew.bat -p versions\26.1 build
```

The 26.1 output is in `versions/26.1/build/libs`; 1.21.x outputs are in
`build/libs`. The 26.1 build uses the included Baritone 1.18.0 API JAR at
compile time, and still requires the compatible Baritone Fabric mod at runtime.
The 26.1 JAR has been confirmed to start, but its modules have not all been
verified in-game.

The Minecraft target is included in both the addon version and output JAR name
(for example, `tim-1.2+mc1.21.4.jar` and `tim-1.2+mc26.1.jar`), so builds
for different Minecraft versions remain distinct.
