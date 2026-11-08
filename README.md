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

Loot Lens's **Ignore Empty Shulkers** setting is enabled by default. It excludes
empty shulker items when checking opened container inventories. When notifications
are enabled, an opened container also reports a short combined summary of the
contents of its filled shulkers. Placed shulker boxes are unaffected. Disable the
setting to count empty shulker items again; configured custom items still
qualify independently.

Illushine's **Block Anomalies** group can highlight horizontally oriented
deepslate in loaded Overworld chunks. The feature is off by default, has its
own chunk radius and color, and updates when blocks or chunks change.

Mobanom's optional **Track Endermites** setting highlights living Overworld
endermites with a separate color and an optional vertical beam. It plays a
detection sound and follows the module's range, ignored-entities, highlight
mode, and chat-notification settings. Existing dimension-anomaly detection for
endermites remains active when the new setting is off.

Safety Net's optional **Air Place Platform** attempts a 5×5 platform below the
player after the warning Y threshold and any configured grace period. Supply
solid, non-falling blocks in its configured hotbar slot. Damage while below
the warning threshold or building disconnects by default; the existing
Y-level disconnect remains independent. Chorus Escape takes precedence.
The warning threshold must leave room inside the world's build height; the
default End threshold of Y 0 is too low for a platform below the player.
Server restrictions, placement delays, falling, and limited reach can prevent
the platform from catching you. It is not guaranteed protection.

Tweaker's inventory-move setting lets you use your movement, jump, sneak, and
sprint bindings in player inventory and container screens. Movement is
suspended while a text field or recipe-book search is focused; creative
inventory and non-container screens are unaffected. Keyboard-symbol and
mouse-button bindings are supported, but scancode bindings are not.
The separate portal-gui setting keeps inventory and container screens open
while standing in a Nether portal. Server-side restrictions or dimension
changes can still close them.
The optional ghost-hands setting attempts to open the first container along
your crosshair through blocks and entities, within normal block reach. Sneak
to interact normally. It replaces containers-through-frames and does not
change mining or attack targeting. Server-side line-of-sight checks,
protection, or blocked chest lids can still prevent opening a container.
The optional air-place setting places held blocks at the crosshair when using
an item while aiming into empty space, within normal reach. Each use-key press
attempts one placement; release the key before placing another. It uses the same
air-placement interaction as Portal Maker; servers may reject it.
Tweaker's Safe Walk group is off by default; its **Safe Walker** setting has
its own toggle key,
which works while no screen is open. It automatically
crouches near an edge, with normal edge clipping as a backup. The
`sneak-edge-distance` setting controls when crouching begins. Existing enabled,
toggle-key, and edge-distance settings are retained; previously selected modes
now use Auto Sneak. Safe Walk operates on the grounded local
player, not while flying, swimming, or riding. It is also paused while a
Tweaker Surface Path or Ender Chest Path is active and resumes when the route
stops; this removes Safe Walk edge protection during the route. Sneaking normally, other
modules, server corrections, and external forces can still affect movement;
this is not a guarantee against falling.

Tweaker's **Attack Guard** setting is off by default and has its own toggle key,
usable while no screen is open. Use the toggle to disable protection when
needed. Selected Types mode starts
with an empty list; add mob types to protect them, or choose All Mobs. The
separate named-entity and tamed-pet settings default to on, protecting
custom-named non-player entities and tamed animals (including horses and
other players' pets), regardless of mode. Players are never blocked. An optional
rate-limited message reports blocked attacks. Attack Guard cancels direct
attacks through Meteor's attack event, including attacks from other modules
using that path. It does not prevent sweeping damage, projectiles, explosions,
or other indirect damage and cannot guarantee mobs will not become hostile.

Tweaker's **Elytra Takeoff** setting is off by default. In Normal trigger mode, bind
`takeoff-start-cancel-key` and press it to launch; Sneak mode launches on a
fresh physical sneak-key press. The same key cancels an active sequence in
either trigger mode. Target Y mode climbs to a configurable absolute Y above
your starting position using the chosen pitch and rocket interval. Jump Glide mode leaves your pitch alone,
jumps, releases jump, presses it again to deploy the elytra, uses one rocket and returns control. Both modes
release sneak during the sequence, use rockets from the offhand or hotbar, and have a
configurable timeout. If sneak remains held or toggled at completion, it stays
suppressed until released or toggled off; pressing it again works normally.
The first rocket is attempted after the client enters glide. When the player
has permission to fly, the initial jump is performed directly to avoid toggling
creative flight with the double press. They need a usable equipped elytra, overhead clearance,
and a grounded player; the assistant does not pathfind or clear blocks. It
refuses to run with Rocket Pilot active and stops on flight failure, collision, focus loss or
an open screen. The sneak trigger supports keyboard-symbol and mouse-button
bindings, not scancodes. Server acceptance, rocket use and safe flight cannot
be guaranteed; keep the cancel key bound and test in a safe area.

Rocket Pilot's auto-takeoff uses the same jump, release, and glide sequence
before firing a rocket. It suppresses sneak during takeoff, requires an
accessible rocket and usable elytra, and disables itself with an error if
takeoff fails. Its separate Ebounce mode retains its own launch behavior.

Tweaker's Surface Path key starts or cancels an Overworld-only Baritone route
to a nearby surface in loaded chunks. The key is ignored outside the Overworld.
It searches up to the configured radius,
preferring nearby open, flat areas of natural ground with clear sky. If none are
found, it also searches for a single clear standing block on natural ground,
including beneath a tree canopy up to 16 blocks high. As a last resort it
attempts a surface above other solid floors, even when the standing space is
obstructed; Baritone decides whether it can reach that position. If no dry
candidate is found it can also target an exposed water surface. It tries up to
16 spaced candidates if paths fail. Candidates can include player-built
structures and hazardous areas; no destination or route is guaranteed safe.
Baritone must be idle. The Pathfinding group contains **Surface (START/STOP)**
and **Ender Chest (START/STOP)** keys, **Surface Search Radius**, and
**E-Chest Search Radius**. Its shared **Allow Break/Placement**
setting (`path-allow-breaking-and-placement`) controls both permissions for both
path features and is off by default. Existing path keys, search radii, and the
shared terrain setting are retained when upgrading to the Pathfinding group.
Previous per-path permissions are not
carried over; enable the shared setting explicitly to allow terrain changes.
The setting is applied when a route starts. While the
route is active Tweaker temporarily changes Baritone's global break and place
settings (including break exceptions and parkour placement), restoring unchanged
settings afterward. Avoid starting other Baritone tasks or changing those
settings during the route. No route is guaranteed safe; supervise movement.
Tweaker's path status messages omit coordinates. It also temporarily disables
Baritone's debug chat, goal outline, and block selection boxes during its routes
and restores the previous settings when the route ends. The path line is unchanged.
Other mods, HUDs, queued messages, server-side logs and world
visuals are outside Tweaker's control and may still reveal location.
Coordinate-free diagnostic messages report whether the player is submerged
and whether terrain changes are enabled. When a
Surface Path route exhausts its candidates, the diagnostics distinguish breach
staging from water or land routing and count invalid candidates, failed Baritone
calculations, and timeouts. These counts do not identify the cause of a failed
calculation or prove that no route exists.
When started submerged in water, or when it becomes submerged during a route,
Surface Path safely pauses Baritone and searches connected, loaded
water for an unobstructed ascent or an eligible overhead breach. Ender Chest
Path uses the same search after safely pausing its chest route. It does not
continue to shore, guarantee enough air or avoid drowning; enclosed water or
impassable terrain can prevent a route. Each bounded search examines at most
4,096 positions within 12 blocks horizontally and 24 vertically. If it cannot
yet verify an exit, it may move upward through reachable water and search again,
for at most four progress segments. An intermediate position is not treated as
a completed escape.
It aims toward each waypoint and requests forward sprint-swimming when the
player has enough food, falling back to ordinary movement, jump and sneak
otherwise; the camera is temporarily controlled. It stops on manual movement, changed terrain, loss
of Baritone control, or stalled movement. It briefly steers back after a
one-block drift from a waypoint. If the drift persists, the player moves farther
away, or the next water space changes, it can replan twice from the current
clear water space; it stops if a safe replacement route cannot be found.
If the swimmer passes one waypoint along a straight section, it continues toward
the next rather than reversing; it does not skip corners.
Both paths use the same final swim and, if ascent slows, can search for up to
two alternative connected-water routes rather than retrying the same column.
Ascent steering checks a short upward and sideways sweep for clear water or
exposed air, but this conservative check cannot guarantee collision-free movement.
The ascent swivel also limits projected horizontal drift to 1.5 blocks from
where that ascent began; currents and momentum can still carry the player farther.
Water currents, narrow spaces,
unloaded chunks, and search limits can prevent recovery.
Minecraft and the server still decide whether sprinting and the swimming pose
actually activate; hunger, status effects, collision, and other restrictions
can prevent them.
For a dry start below an ocean floor, Surface Path can seek an ocean breach site when terrain changes are
enabled. Baritone can excavate dry, permitted blocks to reach the staging space,
then Tweaker mines one seabed block and swims upward. If the staging route fails,
it tries a normal surface route. This can flood the tunnel and cause drowning;
supervise the route and cancel with its key if needed. Sites are restricted to
specific non-falling blocks. When already submerged beneath an eligible roof,
Tweaker can also clear up to four contiguous blocks directly overhead, provided
the entire remaining column is clear water leading to exposed air. It checks
for nearby lava, uses the held suitable tool, and rechecks the blocks and water
as it works. The connected-water search can look for a nearby column, but it
does not excavate a horizontal tunnel, place blocks as part of this recovery,
or excavate through a thicker or irregular roof. After Baritone arrives, Tweaker briefly centers the
player under the breach before mining with the held tool. It holds jump and
stays aligned through the opening, then gently changes direction in clear water
while ascending. In clear water it aims upward and uses vanilla sprint-swimming;
this temporarily controls the camera and its speed depends on game and server
conditions. If ascent slows after clearing the breach, it switches to the
shared connected-water search instead of asking Baritone for water routes;
it stops if no alternative is found.
It warns when air is low; reaching air is not guaranteed.
Manual movement cancels the centering, mining or swimming step; the feature also
stops on a timeout or changed conditions. A suitable or reachable site is not
guaranteed.

Tweaker's Ender Chest Path has its own start/cancel key and search radius. It
works in the Overworld, Nether, and End, searching loaded chunks in the current
dimension for the nearest ender chest by
straight-line distance, then uses Baritone to walk to a horizontally adjacent
block at the same height and stop. It does not open the chest or try a farther
chest if the nearest is unreachable. The target must remain loaded and present.
If the player becomes submerged in the Overworld, Ender Chest Path pauses,
uses the shared connected-water search to reach an accessible water surface,
then swims until the player's head is out of water and resumes toward the same
chest. With terrain changes enabled, it can also use the bounded overhead
escape described above, and then resume toward that same chest. Unobstructed
ascent is available with terrain changes disabled. The chest key
cancels the entire operation, including recovery. Recovery
uses upward sprint-swimming in clear water and temporarily controls the camera;
when ascent slows it can search for up to two alternative connected-water routes.
Recovery stops on manual movement during its final swim, a repeatedly stalled ascent, changed
permissions, lost Baritone control or an unavailable chest. It warns when air
is low and permits at most two recoveries per route to avoid a loop, so chests
that require sustained underwater travel may be unreachable. If recovery is
needed outside the Overworld, or no exposed water surface is found, the route
stops; neither an air supply nor survival is guaranteed.
The shared Pathfinding setting uses the same temporary Baritone settings safeguards
as Surface Path; enabling it permits terrain changes for both path features.
Baritone's disallowed-blocks list temporarily includes ender chests during
the route, even when breaking is enabled.
Baritone must be idle and the player must be on foot.
Surface Path, Ender Chest Path, and Elytra Takeoff have separate bindings;
assign different keys if you want to trigger them independently. Existing path
bindings, search radii, and the shared terrain permission are retained when
upgrading from the previous settings layout.

Manuscript can populate a writable book with custom text, generated symbols,
or UTF-8 text from `config/meteor-client/manuscript.txt`. It can fill pages
automatically when a book is opened or from its module button.

Waypearl persists discovered pearl owners and locations by server and dimension
in `config/tim/waypearl-pearls.json`. Requester hotkey pulls use the configured
cooldown to avoid repeatedly messaging the bot assistant.
The optional messenger connects two Minecraft instances on the **same computer**
over TLS on `127.0.0.1:27841`, without using `/msg`. Enable it and set an
open-messenger key on both clients. In assistant mode, add each requester to
the Waypearl whitelist, then use the messenger panel to save a separate
password of at least 16 characters for that player and start the listener.
Copy the assistant's SHA-256 fingerprint to the requester's panel and enter
that requester's password to connect. Verify the fingerprint through a trusted
local channel: it changes each time the assistant starts its listener. A JDK
with `keytool` is required on the assistant to generate the temporary TLS
certificate. Only one requester can connect at a time.

The assistant stores salted password hashes in
`config/tim/waypearl-messenger-credentials.json`; use unique passwords and
protect the local account and its files. The messenger does not protect against
other processes running as the same OS user. Changing a credential stops the
listener; start it again to reconnect. Connections close when the module is
disabled or the mode changes. The assistant must stay in the Minecraft server;
its listener closes if it disconnects. A requester's messenger connection stays
open when leaving or joining a server. Reconnect manually if the messenger
itself disconnects.

For an offline requester, keep Waypearl active in requester mode and press the
messenger open key on the main menu. Connect to the assistant and choose **Arm
Pull for Server Join**. The assistant waits without a timeout and checks its
server player list each tick. Its automatic confirmation does not disclose
the server address or dimension in messenger messages.
Join that same Minecraft server while keeping the messenger connected. When the
requester appears online, the assistant checks for a loaded pearl with the
matching owner and attempts one pull. The assistant must have auto-pull enabled,
remain on that server and dimension, and have a valid loaded pearl and usable
trapdoor; walking to a distant trapdoor can take time. Servers that hide player
list entries may prevent join detection, and appearing in the list does not
guarantee that the server has finished loading the requester. Test on your
server before relying on this for an emergency escape.

Waypearl still stores the configured server address in its local pearl cache
to distinguish servers. Custom messenger messages can contain anything the
user types; neither this feature nor the messenger hides Minecraft's network
traffic or provides a proxy.

Use **Cancel Armed Pull** before it starts to cancel. The request is also
cancelled if the messenger disconnects, the whitelist or auto-pull changes, the
assistant leaves its server or dimension, or Waypearl is disabled. A normal
messenger pull requested while the requester is offline is rejected rather
than consuming their pearl.

Send custom messages or presets from the panel, or use the requester pull
button or hotkey. Direct pull requests bypass Waypearl's local chat cooldown
and only select a known matching pearl owner; the assistant must enable
auto-pull. A custom message beginning with an assistant trigger phrase also
requests a pull. Normal chat requests retain their existing cooldown. The
optional Waypearl Messenger HUD shows the latest message or connection status.
Its settings include an ender pearl icon, icon position and scale, text and
layout scale, shadow, alignment, wrapping width, padding, colors and background.

For a distant loaded pearl, the assistant walker searches loaded terrain for a
route to its trapdoor, interacts once in range, and, when return-after-pull is
enabled, walks back toward its original position within the configured return
tolerance. It stops and reports a failure if it cannot find or follow a route;
it does not teleport back. Routes are bounded and use a simple block-collision
model, so unusual terrain, changing blocks, server movement rules, or unloaded
chunks can prevent navigation. Test the walker on your server before relying
on it.

Trapdoor interaction requires an empty offhand or hotbar slot and does not
activate while sneaking. The assistant temporarily selects an empty hotbar slot
when necessary, then restores the selected slot. If neither hand nor any hotbar
slot is empty, the pull fails safely instead of using an item. Iron trapdoors
require redstone and cannot be opened by hand.

After an assistant pull, Waypearl can drop one ender pearl from its inventory
and remind the requester to replace their stasis pearl. These options are on by
default and can be changed in the Bot Assistant settings; the reminder delay
defaults to 120 seconds. They work for both chat and direct pulls. The
assistant only treats a pull as confirmed when the original pearl disappears
from a loaded chunk and the requester appears near it. It then waits up to 30
seconds for the requester to be within three blocks of the assistant and drops
one pearl if its player inventory is accessible. Chat may remain open; the
assistant retries pending rotation callbacks until delivery or the deadline.
Other players may pick up the
dropped item; the assistant cannot verify pickup or a server-accepted drop.

A reminder is sent once if no new, loaded stasis pearl with the requester's
known owner and a trapdoor is detected. Pearls outside the scan range or with
unknown owners may not be detected, so a reminder is not proof that no pearl
was placed. Direct reminders wait for that requester to reconnect; normal chat
follow-ups use `/msg` and are spaced by Waypearl's cooldown. Follow-up state is
kept only while Waypearl remains active and connected to the game.

Illumood adds configurable minimum world brightness and biome color moods,
including grass, foliage, water, sky, and fog overrides. Its mixins adapt to
the rendering and environment APIs used by each supported Minecraft version.
Its Night Vision light mode applies vanilla night-vision lighting locally
without adding or removing a status effect.

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
Each target compiles against the corresponding Meteor Client snapshot, so use a
matching Meteor version at runtime.

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
