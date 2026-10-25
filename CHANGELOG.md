# Changelog

What's new in each version of White Bag Pup, newest first.
Download the latest from [Releases](../../releases/latest).

## 2.6.7

- Titles read exactly like in game, using the game's own title data: your name, then the prefix, then the suffix
  ("Sophie the Wanderer", or just "Sophie the"). An empty title slot shows nothing.

## 2.6.6

- Empty title slots (the game's "the Blank Suffix") aren't shown: no title just shows your name.

## 2.6.5

- The friends leaderboard can show everyone's all-time totals: the pup now sends all-time bags (with colours),
  boss kills, realm events, fame and play time too.

## 2.6.4

- **"I only play one account"** on Home: everything goes to one account, and the account buttons above the tabs
  go away (for when the pup thinks a reconnect is a second account).

## 2.6.3

- Titles are the right way round: "Sophie the Wanderer", not "the Wanderer Sophie".

## 2.6.2

- **Titles:** your title shows with your name ("Sophie the Wanderer") in About Me and badge posts. The words come
  from the game files; what was found is written to titles-debug.txt. The friends leaderboard keeps just the name.

## 2.6.1

- Fixed player names showing the game's extra codes ("Kiinguu,a19d,fe3") in badge posts, recaps, About Me and the
  friends leaderboard. Names saved before are cleaned up too.

## 2.6

- The dry streak badges (Never Give Up, Survivor, Unbreakable, Cursed and Cured) are gone: they were close to
  impossible.
- **25 Prismatic badges** with rainbow medals, the hardest of all: Eternal Winter (1,000 whites), Royal Flush
  (4 whites in one run), Lucky Day (10 whites in one day), Divine Collector (50 Divines), Legendary Hoarder,
  Bag Hoarder (10,000 bags), Dungeon Legend (5,000 runs), Living There,
  Been Everywhere (75 dungeons), Speedrun Pup (50 runs in a day), Boss Nightmare (10,000 bosses), Unstoppable, Alpha,
  Event Horizon (1,000 realm events), Ascended (10,000,000 fame), The Realm Is Home (2,000 hours), A Whole Year
  (365 days played), Grand Museum (600 collection items), Archivist (complete 20 dungeons), Shiny Legend (100 shinies),
  Puzzle Prodigy, Puzzle Machine, Merchant Prince (500 trades) and Spoiled Beyond Belief (10,000 pets).
- **Updates itself:** from now on the pup checks GitHub (when it opens and every 6 hours), downloads a new version in
  the background, backs up your files, and installs it when you close the pup. The button at the top of Home
  restarts onto it straight away. The old version is kept as WhiteBagPup.jar.old. Turn it off with "Install updates
  by itself" on Home.
- **Backups** (Home): back up now, restore, and a weekly backup by itself. The last 8 are kept in the backups folder.
- **Friends leaderboard:** with a Femcore bot recap link, tick "Join the friends leaderboard" (Webhooks tab). The bot
  keeps a daily board (resetting at midnight your time) of white bags, all bags (with each colour), runs, boss
  kills, realm events, fame and play time, and posts the day's winners.
- **Wardrobe** (Me tab): dress up the pup with 10 hats (party hat, pink bow, flower crown, top hat, Viking helmet,
  halo, crown, plus the witch and Santa hats any time) and 7 outfits (bow tie, red bandana, cosy scarf, hero cape,
  gold collar, royal cape). Most unlock with badges; badge posts say what they unlock.
- **21 harder badges** (pink), like Avalanche and Ice Age (250 / 500 whites), Jackpot (3 whites in one run),
  Blessed (10 Divines), Dungeon Master (1,000 runs), Obsessed, World
  Traveller (60 dungeons), Boss Exterminator, One Pup Army, Storm Chaser, Mythic (1,000,000 fame), Realm Resident,
  Best Friends Forever, Museum, Master Completionist, Shiny Hunter, Puzzle Master, Puzzle Addict, Turbo Zoomies and
  Most Loved Pup.
- **Webhooks tab:** every Discord webhook in one place: bag posts (with ping, test post and game pictures), recaps
  and badges. Home keeps the bags to watch and the start button.
- **Badge webhook:** badges can post to their own channel, with a test post. Leave it empty to keep posting them
  with your bags.
- Realm event and realm boss kills count on Boss Kills as soon as they die, instead of when you leave the realm
  (closing the game while still in the realm used to lose them). Events from the RealmStock list are recognised even
  if it loaded after the app started.
- A realm event made of several big enemies (a god and its tentacles, a boss and its towers) counts as one kill,
  not one per part. Every kill of the boss itself still counts, including several in one realm visit.
- **Puzzle log** on the Puzzle tab: how many practice puzzles you've solved (and how many perfectly), and how many
  in game.
- **15 puppy badges**, with paw and bone medals: Good Pup, Very Good Pup, Best in Show (practice puzzles), Belly Rubs
  and Spoiled Rotten (pet the pup at the top of the app), Fetch! and Fetch Champion (bags of any colour), Sniffer Dog
  and Bone Collector (shiny items), Zoomies (10 runs in a day), Midnight Walkies, Walkies and Long Walkies (different
  dungeons), Pack Leader and Loyal Companion (30 days played).
- Opening the Badges page unlocks anything you've earned straight away. Badges you'd already earned before they
  existed unlock quietly instead of all posting to Discord at once.
- **Dungeons** page under Loot: every dungeon side by side (scroll sideways), with its difficulty, what drops the
  portal, and the UT and ST items you can get there. Items you've collected are in colour with a heart; hover over
  one to see if it's in your collection and when you first found it. Search by dungeon, enemy or item. A Shiny items
  row at the bottom shows the shiny versions of each dungeon's items.
- Retro items are gone from Collection and Dungeons: they came from past events and don't drop any more.
- **Shiny only** in the Discord filter on the Home tab: only bags with a shiny item get posted, from every bag
  colour (even ones not ticked under Bags to Watch).
- Shiny items are marked "✨ Shiny" in bag posts.
- **Cyan bags** are tracked too (shinies can drop in them): tick Cyan and Boosted Cyan under Bags to Watch, and
  there's a Cyan box in the recap bag filter. Shiny only includes them.
- **Sound boxes** under each bag in Bags to Watch: untick one to turn off the pup's built-in sound for that bag.
  Your own sound file for it (in the sounds folder) still plays.

## 2.5

- **Tidier tabs:** 14 tabs are now 7, on one row. Related pages share a tab, with a switch along the top:
  - **Loot**: Bag Log, Stats (the old Loot tab) and Collection
  - **Fights**: DPS and Boss Kills
  - **Me**: About Me, Fame, Vault and Badges
  - **Chat**: Chat and Trades
  - **Events** and **Puzzle** stay as they were.

## 2.4

- **Game pictures only ever show the game.** The picture now comes from the RotMG window itself, so a chat or
  browser in front of the game can't end up in your Discord post. If Windows can't give the game's own picture,
  the pup only copies the screen while the game is the window in front; otherwise that bag gets no picture.

## 2.3

- The Collection tab has a second page, **Shiny items**: every shiny UT and ST item in the game, filed under the
  dungeons their normal versions drop in, greyed out until you loot one. It has the same filters, search and
  Share on Discord button. A shiny you've never had before gets the "NEW to your collection" banner on Discord too.
- A shiny no longer also ticks off the normal item (they're separate in your collection).
- Dungeons with no items of their own are left out of the Collection (The Trials of Cronus and others whose bosses
  are other dungeons' bosses); their items are under the dungeons they really come from, or **Quest chests**.
- Badge medals with a heart or star show them properly.
- The Puzzle tab no longer draws targets over the towers; the list beside the board says which to shoot.

## 2.2

- The Collection tab counts everything you've looted again: before the pup had seen which account you're playing,
  it showed nothing. It now counts all your accounts unless you pick one.
- The Collection is sorted properly: the "Elsewhere" group is gone. Old (Retro) and seasonal versions of dungeon
  items go with their dungeon, and event-only items are in **Events and seasonal**.
- Items the game files don't have (removed, or named differently on the wiki) are left out of the book instead of
  showing a blank "UT" box. They're listed in `collection-debug.txt`.
- Collection tooltips stay small: items that drop almost everywhere list a few places and "and 53 more places".

## 2.1

- **New to your collection!** When a UT or ST you've never looted before drops, its Discord post says so, and recaps
  list the new items for your collection.
- **Share on Discord** on the Collection tab posts a picture of your collection: the total and each dungeon's progress
  with the items you've found.
- **Badges** tab: 29 pup badges to unlock (white bags, Legendary and Divine items, runs, dry streaks, boss kills, fame,
  play time, your collection, trades and the Shatters puzzle). New ones get announced in the app and on Discord (you
  can turn that off). Badges you'd already earned unlock quietly, so your history doesn't flood Discord.
- **Exalt tracker** on the About Me tab: every class you've loaded, how many of its 40 exaltation levels it has, the
  stat closest to its next level, and about how many dungeon runs that is at your pace.
- Fixed pages jumping to the bottom when they open.
- The app now has its own font, Nunito, built in, so text looks the same on every PC and every letter matches
  (Windows 11's font made some letters, like W, look out of place).

## 2.0

- **Puzzle** tab: a solver for The Shatters' Puzzle Room (nine towers, make them all green). Set the towers to match
  the game and the ones to shoot get a pink target, with the fewest shots. "Click to shoot" lets you follow along,
  and "Random practice" gives you a puzzle to try. In the Puzzle Room the pup also tries to read the towers from the
  game by itself (anything it notices goes in `puzzle-debug.txt`). The towers use the game's own pictures once the
  pup knows which objects they are (from the game files, or from seeing them in the Puzzle Room).
- Tabs squeeze a little when that saves a whole row.

## 1.9

- The Collection tab now ticks off items from every bag the pup has ever logged. It used to miss them when the tab
  was drawn before the game's item names had loaded. Dungeons where you've had bags now show under **My dungeons**,
  even without a logged run.
- Dry streaks only count dungeons: time in the realm (and realm event whites) no longer counts as a run.
- Wide tables on the Loot tab fit the page (no more cut-off columns).

## 1.8

- **Boss Kills** tab: every boss you help kill, with totals for this session, today and the last 7 days, and
  per boss: kills, where, your best % of the damage, how often you did the most damage, and your fastest kill.
  Switch between bosses, minibosses, realm events and everything, or search. Realm events (Cube God, Grand Sphinx,
  Lord of the Lost Lands...) are matched against RealmStock's event list and count as bosses.
  **Pick bosses** lets you tick which bosses are shown and counted. Fights saved before 1.8 count as kills.

## 1.7

- **Collection** tab: a book of every UT and ST item that can drop, by dungeon, greyed out until you loot it.
  Shows how many of each dungeon's items you've found, with a search box and Missing / Found filters.
  Hover an item to see where it drops and when you first got it.
- **Dry streaks** on the Loot tab: runs since your last white bag, overall and per dungeon, with your longest
  ever streak. Recaps mention a long dry streak, or one you broke.

## 1.6

- **Play time** on the About Me tab: this session, today, this week and in total.
- Recaps count play time properly: all your time in game, not just finished trips to an area.
- No more scrolling through empty space: each tab is only as tall as it needs to be (they were all as tall as Home).

## 1.5

- When a post can't be sent, it tries once more after 5 seconds and then stops, with one message saying what
  probably went wrong (instead of four "couldn't reach Discord" lines).
- Clearer errors for bot recap links (wrong key, bot not answering, wrong channel).
- Recap pictures are kept under Discord's upload limit.
- Recaps no longer include top DPS and trades.

## 1.4

- Recaps can go to a Discord bot instead of a webhook: paste the bot's recap link (`https://.../recap?key=...`)
  into **Recap webhook URL**. The bot posts the recap with ◀ ▶ buttons to flip through the loot pictures.
- The "luckiest dungeon" line no longer shows odd numbers like "1 white per 0.5 runs".

## 1.3

- Recaps now say your in-game name, and which classes you played and for how long.
- Pick which bags go in the recap (**Bags in recap** on the Home tab; White, Red and Orange to start with).
- All the recap's loot comes in one post as pictures, one bag per picture. Click a picture and use the arrows to
  flip through them. With lots of bags, several share a picture (up to 10 pictures and 60 bags, best first).

## 1.2

- Tabs stay in the same order when you click them. They squeeze onto one row when they can,
  and otherwise split evenly over two rows.

## 1.1

- **Recaps**: daily and/or weekly summaries for each account, posted to their own Discord channel
  (bags, best drops, runs, luckiest dungeon, fame, top DPS, trades, time played).
  Set them up under **Recaps** on the Home tab.
- Fame and DPS are now saved between sessions, so recaps can add them up.

## 1.0

First release! 🐶

- Discord posts for the bags you watch, with item tiers, rarities and an optional game picture
- Tabs for Bag Log, Chat, DPS, About Me, Fame, Vault, Loot (with drop rates), Trades and Events
- Works with two game accounts at once
