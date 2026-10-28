# Journal, goals, focus timer and character (draft, defaults chosen; change anything)

Built in this order, one at a time, each with rules and tests first, then storage, then screens. Same principles as
`HABITS_PLANNER_SPEC.md`: for me only, everything on the phone, gentle (no streak loss, nothing red), AI never decides
anything.

## 1. Journal
- One entry per day with two short lines: **"One win today"** and **"One thing to do better"** (each up to 200
  characters; either may be empty, but not both).
- Screen **Journal**: today's entry at the top (Save), then past entries, newest first. Tap a past entry to change it.
- The **Journal habit** ticks itself on days with an entry (new habit kind of self-tick; Journal habits made before this
  stay tick-by-hand).
- **Today** shows a Journal line: "Write two lines" or "Written today".
- Data: `journal_entry` (day, win, improve). In export, restore and Delete.

## 2. Goals
A goal has a title, a kind, a target, a start day and an end day.

| Kind | Target | Progress counts |
|---|---|---|
| Workouts | N workouts | workouts finished from start to end |
| Habit | N days | days the chosen habit was done from start to end |
| Weight | a weight in kg | the latest weigh-in against the first one (the first weigh-in of each day counts) |

- Progress is shown as "12 of 20" and a thin bar, with days left. Gentle wording: ahead / on track / "a little behind,
  still time". Never red.
- Reached goals show "Reached" and stay in a Completed list. A goal whose end day has passed shows "Ended: 17 of 20".
- Screens: **Goals** list, Add/Edit form (kind, title, target, habit, end day with quick choices: this week, this month,
  30 days). Delete asks first. Menu item **Goals**.
- Data: `goal`. In export, restore and Delete.

## 3. Focus timer
- Choose 15, 25, 45 or 60 minutes (or type 1 to 180), optionally pick a planner task to work on, Start. A big countdown,
  Pause is not offered (stop ends the session; time done so far is kept).
- The timer runs from an end time, so it keeps counting with the screen off or the app closed. When it ends the phone
  vibrates and shows a notification ("Focus session finished"). If notifications are not allowed, the finished state
  shows next time the app opens.
- Every session is saved: when it started, planned and actual minutes, and the task's title. The Focus screen shows
  today's total, this week's total and recent sessions.
- Menu item **Focus**. Data: `focus_session` (and the running timer in settings). In export, restore and Delete.

## 4. Character (and the AI layer)
No points, no levels. The app shows **evidence** from what you actually did this week, for a few qualities:

| Quality | Evidence this week |
|---|---|
| Consistency | workouts finished of those planned; habit days done of those due |
| Discipline | routines finished; tasks done of tasks planned for the days that passed |
| Comeback | times you came back (any habit, workout or routine) after two or more quiet days |
| Listening to your body | sets you stopped for pain and kept notes on; rest days you took |
| Focus | focus sessions and minutes |
| Reflection | days you wrote in your journal |

- Each quality shows one plain line of evidence and "up from / about the same as / quieter than last week".
- **This week's focus**: one quality, picked for you (the one that was quietest last week); tap another to change it.
- **Weekly reflection**: two questions ("What went well this week?" "What will you do differently?"), saved per week.
- **A summary in plain words** of the week, written by a `CoachText` interface. The built-in version fills in a
  template from the evidence (no network, nothing leaves the phone). A model-based version can be plugged in later;
  it may only word what the numbers say and never changes any plan or load.
- Menu item **Character**. Data: `weekly_reflection`, and the chosen focus in settings. In export, restore and Delete.

## Not in this spec
Notifications for habits, widgets, sharing, accounts, any network AI, points or streaks.
