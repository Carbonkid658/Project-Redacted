# Project [REDACTED]

A Fabric mod for Minecraft 26.3. The world has ended; you found and run your own SCP Foundation site.
This is the core mod from the design doc (`design/game-design.md`) **without the individual SCPs**. Every system that touches
anomalies is generic and goes through `dev.projectredacted.api.ScpApi`, so SCPs can be added later.

## Building

Needs JDK 25.

```
./gradlew build
```

The jar lands in `build/libs/`. It builds against Minecraft 26.3, Fabric Loader 0.19.5, Loom 1.18 (from the official Fabric 26.3 example mod template) and Fabric API 0.162.0+26.3.
`.github/workflows/build.yml` builds the jar on every push and attaches it to the run as `project-redacted-jar`.
If the build fails, the compiler errors are also published as a "report: Errors" check on the commit.

The code compiles, but it has not been play-tested in a running game yet.

## How to play

1. **Found a site.** Craft Plastic (iron ingot + 4 nuggets), Plastic Blocks, then a **Mainframe** (plastic blocks, iron blocks, a redstone block) and place it.
   Your claim is 5x5 chunks around it. You get a **Level 4 keycard**.
   - If a ruined site is inside the claim, you're asked whether to move into it. You can also repair a **Ruined Mainframe** with 3 Repair Kits.
2. **Place the Site Director's Computer** (plastic, glass pane, redstone) and use it. Pick a site type (Underground, Surface-Flat, Surface-Tall)
   and generate the **Level 0 facility**: Entrance, Barracks (4 beds) and Director's Office. The Mainframe and Terminal move into the office.
3. **Recruit staff** from **Foundation Bunkers** (Level 4 keycard door, 1 to 4 survivors). Some want diamonds. Each staff member needs a bed.
4. **Build rooms** with the **Room Configuration Device**: use it on two blocks to mark the corners (floor and ceiling), then use it in the air.
   Pick a room, see a ghost-particle preview, rotate, reroll, then build (or register a room you built yourself). Construction just happens.
5. **Power rooms** at a **Circuit Breaker** (or from the terminal). Rooms stay dormant until connected. Generators make power, Water Pumps make water,
   Circuit Breaker rooms add breaker slots.
6. **Assign tasks** with the **Clipboard** on a staff member: room numbers, an SCP's cell, a checkpoint or an entrance. Sneak-use a security guard
   with an empty hand to have them follow you.
7. **Upgrade** at the terminal. Facility Level 1 needs 1 SCP, 1 Researcher and 1 Security.

### What's in

- **Site and claims**: one site per player, claim protection, members with clearance and permissions, transferable directorship (`/site ...`).
- **26 room types** from the catalog, procedurally furnished in three palettes (modern clinical, retro office, mixed). Datapacks add more
  (see `examples/datapack`).
- **Power and water** per room; Mainframe-down stops the site; demolition blocked while an SCP is inside.
- **Staff**: six roles plus D-Class, names, named staff with skills, levels from experience, clearance, promotion, food/sleep/morale,
  abandonment, defection, permadeath. Security fights; medical heals; engineers repair; janitors lift morale; administration cuts upgrade
  costs and brings in coins; researchers produce Research Documents.
- **Raids**: GOC, Serpent's Hand, Chaos Insurgency, including multi-faction raids. Each faction follows its design behavior (GOC cuts power and hunts
  SCPs, the Hand hacks C.A.S.S.I.E. in four stages, the Insurgency sabotages the Mainframe and steals SCPs). Raiders break in through the entrance.
  Cooldowns, scaling with players, repairs afterwards.
- **C.A.S.S.I.E.**: early warning, announcements, hackable, repairable, keeps working when the Mainframe is down.
- **MTF**: upgrade; arrive 10 minutes into a raid, recontain after raids, can be called after a breach.
- **Factions to trade with**: GOC outposts, Dr. Wondertainment stalls (restock daily), roaming MC&D contacts (sell SCPs as delivery flares),
  Ways to the Wanderers' Library (trade SCPs for Library Tomes and dollars). Coins craft into Dollars.
- **Keycards and keycard doors**, Reinforced Doors/Glass, Containment Concrete, the **Alpha Warhead**, lore documents in ruins and bunkers.
- **UI**: Foundation-themed button screens for everything (terminal with map, rooms, staff, upgrades, personnel, security and log tabs; staff cards,
  recruiting and the clipboard roster; shops and the Librarian; room picker and preview with R/G rotate and reroll keys; circuit breaker;
  Mainframe, C.A.S.S.I.E., warhead and keycard reader panels; documents). The server builds each view and the client draws it
  (`gui/` and `client/screen/`), so the mod is needed on both sides. Keycard swipes, delivery flares and repair kits stay instant.

### Adding an SCP

```java
ScpApi.register(new ScpDefinition("scp_173", "SCP-173", ObjectClass.EUCLID)
        .entity(MyEntities.SCP_173)
        .require(ContainmentRequirement.cellAtLeast("medium"))
        .require(ContainmentRequirement.observed())
        .breachChance(0.25)
        .mcdPrice(60));
```
Add the entity to `data/projectredacted/tags/entity_type/scp.json` (or an item to `tags/item/scp_items.json`). The site then counts it,
labels its cell, weighs it for raids, checks its containment, opens its cell on a breach, and lets MTF recontain it.
The creative-only **Unclassified Anomaly** item is a stand-in for testing: put it in a cell's barrel and it counts as a contained SCP.

## Defaults picked for open questions

| Question | Default in the code |
|---|---|
| Level 2 and 3 unlocks | L2: 3 SCPs, 3 researchers, 3 security, 1 engineer, 1 medical. L3: 6/6/6/2/2 plus 1 administration (`FacilityLevels`). |
| C.A.S.S.I.E. cost | Upgrade first; crafting uses diamond blocks, netherite and a redstone block; repair takes 2 Repair Kits. |
| Staff count and GOC raids | GOC raids weigh SCP count only; other factions also weigh staff count. |
| D-Class source | A powered Prison takes in one D-Class per day while it has free beds. |
| Water shortage | Rooms that don't get water stop working. |
| Mainframe down | Staff work, construction, room power and upgrade progress stop. C.A.S.S.I.E. and doors keep working. |
| SCP classes for the Hand's hack | Light cells open at stage 2 (Safe), Heavy cells at stage 3 (Keter), all cells at stage 4. |
| Testing rewards and risks | Not implemented yet (on hold in the design). Researchers assigned to an occupied cell research faster. |

## Notes for 26.3

- World features are data-driven: each feature type is a codec registered in `registry/ModWorldgen`, and the feature
  instances live in `data/projectredacted/worldgen/feature` (there is no `configured_feature` folder any more).
- Dyed and copper blocks are collections in 26.3 (`Blocks.CONCRETE.white()`, `Blocks.COPPER_BLOCK.asList()`).
- Day time comes from `Level#getOverworldClockTime` (wrapped in `util/Time`).
- 26.3 has no per-block render layer map. Reinforced Glass and the Reinforced Door rely on texture transparency.
  If they render opaque in game, that is the first thing to look at.

## Placeholder art

Textures were drawn by a script and are simple. Sounds point at vanilla sounds until real C.A.S.S.I.E. audio exists.
