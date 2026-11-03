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
- Illushine
- Inspector Gadget
- Inventory101
- Lava Marker
- Loot Lens
- Manor Assistant
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

More modules and HUDs are planned.

## Requirements

- Minecraft **1.21.4, 1.21.5, 1.21.8, or 1.21.11**
- Java **21 or newer**
- [Fabric Loader](https://fabricmc.net/use/installer/)
- [Meteor Client](https://meteorclient.com/) compatible with your Minecraft version
- Tim addon JAR built for the same Minecraft version

## Installation

1. Download the latest release JAR from [Releases](https://github.com/rithsgit/HuntingUtilitiesBETA/releases) (or build from source — see below).
2. Make sure Fabric Loader and Meteor Client are installed for the same Minecraft version as the addon.
3. Place the addon JAR in your Minecraft `mods` folder alongside Meteor Client.
4. Launch Minecraft using the Fabric profile.
5. Open Meteor's GUI (default key: `Right Shift`) and find the modules under the **Tim** category.

## Building from Source

This project uses Gradle and supports building a separate addon JAR for each supported Minecraft version.

### Setup

- Clone the repo:  
  ```bash
  git clone https://github.com/rithsgit/HuntingUtilitiesBETA.git
  cd HuntingUtilitiesBETA
  ```

### Build

Select the Minecraft target at build time:

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

The Minecraft target is included in both the addon version and output JAR name
(for example, `tim-1.1+mc1.21.4.jar` and `tim-1.1+mc1.21.11.jar`), so builds
for different Minecraft versions remain distinct.
