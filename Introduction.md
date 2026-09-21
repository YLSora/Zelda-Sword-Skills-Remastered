# Zelda Sword Skills Remastered

This mod is a port of the classic Minecraft 1.8.9 mod [Zelda Sword Skills](https://www.minecraftforum.net/forums/mapping-and-modding-java-edition/minecraft-mods/1293190-zelda-sword-skills-1-8-9-v3-4-and-1-7-10-v2-5-1-03) to version 1.20.1, featuring a modernized remaster based on my personal understanding. If your childhood holds memories of playing in a blocky world wearing a green hat, then this mod will now return to you with a traditional yet entirely fresh look.

*This mod is dedicated to wishing a happy 40th anniversary to the Legend of Zelda franchise, and a smooth release for the Legend of Zelda: Ocarina of Time remake. Thanks again to the predecessors for their contributions to the Zelda modding community.*

---

## ⭐ Overview

Zelda Sword Skills Remastered is a remake of the classic Zelda Sword Skills mod for modern Minecraft versions. Centered around the classic "Legend of Zelda" style of adventure and combat, this mod integrates sword combat and combos, dungeon challenges, magical items, song playing, and NPC quests into the Minecraft exploration progression.

- **Sword Skill System Remastered**: Players can learn different sword skills to challenge more powerful enemies with brand-new combat styles. You can also continuously strengthen your attributes during the game using Skill Orbs, Heart Pieces, and Magic Containers.
- **Dungeons and Adventure**: Improved various different Temples that generate in the world. Players need to collect keys and defeat temple bosses, gradually progressing through the adventures of the Water, Desert, and Ice Temples, followed by the Forest, Earth, Fire, and End Temples.
- **Classic Elements Ported**: The remastered version ports original characters like Navi, Zelda, and Biggoron, as well as original questlines like Master Sword upgrading, Mask Trading, and the Biggoron's Sword Trading Sequence. In addition, song playing, items, and masks have been fully ported, with some of them being optimized and improved.

### 📌 Operating Environment
> - **Game Version**: Minecraft JE 1.20.1
> - **Java Version**: 17 and above
> - **Loader Version**: Forge 47.*
> - **Recommended Prerequisite**: [Configured](https://www.curseforge.com/minecraft/mc-mods/configured)
> - **Installation Requirement**: Needs to be installed on both the client and the server

⚠️ **Note**: Some game content is still in the testing and adjustment phase, and you may encounter incomplete content during gameplay. Before adding this mod to your save, **please back up your world first**.

For the related wiki explanation of the original work, please refer to [here](https://www.dropbox.com/s/ccj6a1xo76mj50x/ZSS_Manual.pdf?dl=1). The following only introduces the remastered parts of the mod.

---

## 🏕️ Getting Started

At the very beginning of entering the game, you will receive a "Link's House" seed item and a **Basic Sword Technique skill orb**, just like at the start of the original mod. Find a clear, open area, right-click the floor with the Link's House item, and it will generate a temporary shelter for you.
Now, this shelter feels more like home: it recreates Link's house from the opening of Ocarina of Time and is equipped with some starter gear: the **full Kokiri Clothing Set, the Kokiri Sword, the Deku Shield, and a bottle containing Navi**.

When you are fully prepared, you can set off on your adventure. In the wild, you can see Ceramic Jars everywhere containing various simple supplies. And at night, in addition to the vanilla MC monsters, many monsters from the Zelda series have been added, so please handle them with care!

## ⚔️ Combat and Sword Skills

Combat in the wild is always brutal and ever-changing, and in the early stages, aside from low-damage weapons in your hands, you can only rely on your own sword skills to survive!

Using a Basic Sword Technique skill orb will increase your **Basic Sword Technique** level by one. As your adventure unfolds, more types of skill orbs will become available to obtain. While the maximum level for the Basic Sword Technique is 10, the max level for all other skills is 5.

Combat and sword skills are the most heavily modified parts of this remastered mod. Below is a detailed introduction to the sword skills:

### 🔹 Basic and Advanced Sword Skills
- **Basic Sword Technique**: When facing an enemy, pressing the default `Z` key will lock onto the target. Now, there will be a clear targeting indicator when locked on. As the level increases, the lock-on distance will also increase. When a target is defeated, it will automatically lock onto another nearby enemy.
  - **Combo System**: Locking onto a target activates a "Combo" state. The more you attack an enemy within a short period, the more your combo will accumulate, gradually increasing the damage dealt. The higher your Basic Sword Technique level, the higher the maximum combo size (max 20 times). Taking damage no longer interrupts the combo, but missing an attack or not attacking for a long time will still break it. You can use advanced sword skills to extend your combo.
- **Spin Attack**: The most fundamental advanced sword skill. Hold the attack key to charge up. Once charging is complete, you unleash a spinning attack, dealing AoE damage in a circle.
- **Dash Attack**: While locked on, double-tap `W` then attack. Dash forward to the locked enemy and attack, dealing additional damage to all enemies in your path. Grants invulnerability during the dash.
- **Rising Cut**: While locked on, press `Sneak + Space`, then attack. Perform a double jump and launch the enemy into the air, granting yourself invulnerability during the process.
- **Leaping Blow** (modified from the original): While locked on, perform a double jump in the air and land a hit on an enemy while falling. Deals AoE damage to enemies within range upon landing and briefly stuns them.
- **Double Jump** (New): Press the spacebar again while in the air to perform a double jump. At max level, you can jump 3 blocks high.
- **Dodge**: Triggered by double-tapping `A` or `D`, rapidly moving about 4 blocks to the left or right, granting 1 second of invulnerability. While locked on, you will move in a circular motion around the locked target for an arc distance of about 4 blocks.
- **Parry**: While locked on and holding a sword weapon, hold the right mouse button to enter a blocking stance. If you successfully block an opponent's attack, the opponent will be briefly stunned. It can also be used to parry a small number of projectiles.
- **Sword Beam**: When at full health and locked onto a target, sneak and left-click to unleash a ranged sword beam attack.

### 🔸 Higher-Tier Advanced Sword Skills
- **Sword Break**: When successfully parrying an enemy, follow up with a normal attack to empower this strike, further extending the enemy's stun duration and dealing armor-piercing damage.
- **Helm Splitter** (modified from "Armor Break"): When successfully parrying an enemy, follow up with a jumping slash to leap above the enemy's head, land behind them, and unleash a powerful armor-piercing attack. You are invulnerable during the jump, and it further extends the enemy's stun duration.
- **Flash Assault** (modified from "Back Slice"): If you successfully dodge an enemy's instantaneous attack while dodging, you will enter a "Successful Dodge" state. You can follow up with a Dash Attack to unleash a Flash Assault. Dash behind the enemy and deal continuous, multi-hit, high armor-piercing damage. You are completely invulnerable from the perfect dodge until the Flash Assault is successfully executed.
- **I.A.I Slash**: When a relatively close enemy is in your line of sight and you are empty-handed, you can cancel the lock-on state. Shortly after, the enemy will be "marked." If you draw a weapon and attack the marked enemy within a short time, it will deal a powerful armor-piercing attack and lock onto the enemy.
- **Fatal Strike**: When a locked enemy's health is below 10%, there is a chance for a prompt below the combo system saying "It says, you can go for it" (It's time to strike). Performing a jumping slash on the enemy at this moment will deal extremely high damage. A successful execution consumes some of your hunger.

### 🌟 Ultimate Skills (Learned from Orca)
- **Super Spin Attack**: Can be unleashed when at full health. Continuously clicking the left mouse button during a Spin Attack will keep you spinning and releasing Spin Attacks. Each spin consumes 2 points of magic power (MP).
- **Flash Assault Burst** (New): After unleashing a Dash Attack, continuously double-click the left mouse button to release another Dash Attack. Keeps the target locked on and provides full invulnerability coverage. Each extra Dash Attack released consumes 10 MP.

*Note: Except for the Ending Blow, none of the other skills consume hunger, but most have a cooldown period.*

## 🏛️ Dungeons

The changes to the temples mainly include the arena size, boss types, and the order of conquest:
- **Combat Experience**: Larger arenas are more conducive to executing sword skills. Temple bosses have been adjusted to better fit their temple's theme.
- **Order of Conquest**: You prioritize the order of Desert -> Water -> Ice to obtain the three Pendants of Virtue: Courage, Wisdom, and Power. Then, after accepting Zelda's quest, you will get the Forest Temple Key (which may also be obtained earlier). Find a Forest Temple containing the Master Sword, place the three pendants, and you can pull out the **Master Sword**.
- **Advanced Challenges**: Afterward, you can further challenge the Earth Temple high in the mountains, the Fire Temple in the Nether, and the End Temple in the End to obtain the power of the three Sacred Flames respectively, further upgrading the Master Sword or other equipment.
- **Fairy System**: The original Secret Rooms have been replaced with "Fairy Pools" and "Fairy Roots", which are easier to find but fewer in number. Here, you can capture Fairies or upgrade equipment, with functions identical to the original Secret Rooms.

## 🪈 Items and Songs

- **Tool Optimization**: Mainly undergone functional optimization, including various exploration items, magic items, etc., making them easier to use. All ZSS equipment will **not consume durability**. Some tools have been modified based on personal understanding, granting them brand-new and better functional effects.
- **Ocarina Playing**: Basically follows the old version, but some functions have been added and optimized. All learned songs will be recorded in the achievement system for easy reference.
  - *Command: Type `/zss song learn/forget` to learn/forget a song (requires cheat permissions).*

## 📜 Quests and NPCs

The quest and NPC systems have only been simply ported. Some functions may be incomplete, but they currently do not significantly affect the main gameplay progression.

- **Complete Quest Systems Include**: The Pendants and Master Sword quest, the Biggoron's Sword Trading Sequence, the Mask Trading Sequence, Orca the Skill Trainer's Quest, and the Gold Skulltula Tokens.
  - *Command: Use the `/zss quests` command to view and complete quests (requires cheat permissions).*
- **NPC Naming Transformation**: By naming a villager, they will transform into the corresponding original NPC. Some important NPCs will transform irreversibly after being named. Additionally, there are minor changes to the content of some shop trades.

## 🧚 Navi

The Navi system has been completely revamped, making her the best companion to accompany you on your journey:
- **Combat and Guidance**: During combat, Navi will fly towards enemies as a guide; when idle, you can also `Sneak + Right-click` Navi to chat with her.
- **Portability and Invulnerability**: Navi can be captured in a bottle at any time and released at any time. She will not be attacked by any creatures and will not take any damage. Each player's Navi data is fixed.
- *Command: Type `/zss navi` to recall or reset your Navi.*

## ⚙️ Configuration

It is recommended to install the Configured mod to configure the mod in-game.
- **Client Configuration** (stored in `config/zeldaswordskills_remastered-client.toml`): You can configure Navi's state or toggle the display of the Magic Meter.
- **Server Configuration** (stored in `saves/<your save>/serverconfig`): You can enable or disable various world features, as well as enable the more challenging "Master Mode". (*Note: Server configurations require a world restart to take effect.*)

## ❓ Q&A

**Q1: Will new features be added?**
**A1**: Maybe, but not many. The primary goal of this remaster is to port the original mod to a higher version and improve the imperfect parts of the original. Later on, I might focus on fleshing out the quest system.

**Q2: Why did you decide to remaster this mod?**
**A2**: Actually, at first, I just wanted to bring items like the Master Sword back into my own modpack, thinking I would just throw the features together casually. But as I created more and more things with the help of AI and gained a deeper understanding of the original mod, I gradually started implementing a variety of features.

**Q3: Will there be a Fabric or other version?**
**A3**: Probably not for now, as just making this one Forge version has already burned through a lot of my Tokens (laughs). But since both the source code and the original mod are open source, experts are more than welcome to take over.

**Q4: I found some issues with the sword skills/quest system that got me stuck at a certain stage, what should I do?**
**A4**: Please submit an issue on GitHub describing the problem in detail, or provide a crash report, log, or screenshot. See the README file for more details.

**Q5: Can I add the mod to a modpack or server?**
**A5**: You are free to add it, but _**please do not use it for commercial purposes or on commercial servers (such as for pay-to-win gear, restricted levels, etc.)**_ . All the context and resources of this mod are always free to everyone.