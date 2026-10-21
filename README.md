# Blocks per sometimes (Fabric)

Blocks / crops / fish per second, 10 minutes and hour, plus juice boxes per hour, shown top-left.
Open the settings with the **O** key (rebindable in Controls) or `/bpsmod`. `/bpsmod reset` clears stats.

Build it with GitHub Actions: upload everything, open the Actions tab, download the jar artifacts.

## Contracts
Tracks the two contract slots from the server's `/contracts` menu:
- reads the chat lines "You've activated <Tier> Contract in slot N!" and "Your <Tier> Contract is complete!"
- reads the task of a contract when you right-click it (e.g. FARM 22,500 CROPS) and counts crops / ores / blocks / fish since activation (estimate)
- shows `Contract 1 / Contract 2` on the overlay (green = running, red = EMPTY, grey = unknown)
- chat message + ding while a slot is empty (repeat interval in the Contracts tab)
- open `/contracts` once after installing so both slots sync
