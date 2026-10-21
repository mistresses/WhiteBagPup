# Changelog

What's new in each version of White Bag Pup, newest first.
Download the latest from [Releases](../../releases/latest).

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
