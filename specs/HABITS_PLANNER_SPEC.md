# Habits, planner, routines and Today (draft for review)

Status: DRAFT. Nothing here is built. Approve or change it, then we build step by step.
The original docs in `docs/` are left as they are; this adds to them and does not replace them.

## Why
RepPal already has one daily habit: the workout. This adds the rest of a person's day (habits, tasks, routines) next to
it, for one person on one phone, made for users in India. Later steps (focus timer, journal, goals, character, AI) build
on the data this records. They are NOT part of this spec.

## Principles
- For me only. Everything stays on the phone. No sharing, no accounts needed for any of this.
- Gentle. A missed day loses nothing. No broken streaks, no red marks.
- Quick. Checking something off takes one tap.
- Same look as the rest of the app: minimal, light/dark/auto.
- Rules in the pure Kotlin engine with unit tests first, then the screens (as for the workout planner).

## 1. Habits
**A habit** has: name, icon (picked from a small set), how it is tracked, a schedule, and a start date.

| How it is tracked | Example | Counts as done when |
|---|---|---|
| Done / not done | Meditated | ticked |
| Number | 8 glasses of water | number reaches the target |
| Time | 30 min reading | minutes reach the target |

**Schedule:** every day, chosen weekdays (Mon..Sun), or N times a week (any days).

**Screens**
- *Habits list*: today's habits first, one-tap tick; a number or time habit opens a small box to add to today's amount.
- *Habit page*: month calendar (days fill in when done), this week "4 of 5", this month and all-time counts. Past days
  can be edited.
- *Add / edit habit*: name, icon, tracking type, target, schedule. Starting suggestions (water, sleep, reading, yoga,
  meditation, walking) that the user can ignore.
- *Archive* a habit to hide it and keep its history; *Delete* removes its history too (asks first).

**Rules (engine)**
- A habit is due on a day if its schedule says so (N-times-a-week habits are due every day until the week's N is met).
- Week and month progress count only the days the habit was due; a day not yet reached is never counted as missed.
- The week starts Monday.

## 2. Daily planner
**A task** has: title, date, optional time, optional note, done or not.
- *Day view*: pick a day (today by default), tasks listed by time then in the order added, add with one line of text.
- Tap to tick; tap a task to edit; swipe or button to delete.
- *Move to tomorrow*: one tap for a task not done. Unfinished tasks from earlier days show once, in a "Carried over"
  group on today, with Move to today and Delete. They are never nagged about.
- Not in this step: repeating tasks, reminders, sub-tasks, tags.

## 3. Routines
**A routine** is a named, ordered checklist (e.g. Morning, Evening) with a part of the day (morning / afternoon /
evening).
- *Run a routine*: a full-screen list, tick items in order or any order; finishing marks the routine done for the day.
- *Edit*: add, remove and reorder items (drag and drop, as in Rearrange days).
- An item can optionally be linked to a habit: ticking it ticks the habit for the day.
- Starting suggestions: Morning, Evening (the user edits them).

## 4. Today screen
The new home screen, one scroll:
1. Greeting and date.
2. **Workout**: today's planned workout (or "Rest day"), with Start. Tapping goes to the existing workout screens.
3. **Routines** for the current part of the day.
4. **Habits** due today.
5. **Tasks** for today (plus Carried over).

A line at the top, such as "5 of 9 done", shows the day at a glance. Nothing is flagged red.

**Menu:** Today, Habits, Planner, Week, Workout, Progress, Body, Profile, Plan settings, How it works.
The Week and Workout screens stay exactly as they are. In the code the workout screen's route is currently called
`Today`, so it is renamed to avoid a clash with the new Today screen.

## Data
- Same database (Room, next version) with new tables: habit, habit_entry (habit, date, amount), task, routine,
  routine_item, routine_done (routine, date).
- Dates are stored as local calendar days, not timestamps, so a day does not shift with time zones.
- **Export and restore** include the new data (export format version goes up; older files still restore).
- **Delete account / wipe** clears it all.
- **Privacy page** is updated to say what is now stored (habits, tasks, routines, all on the phone only).

## Not in this spec
Sharing, reminders and notifications, home-screen widget, repeating tasks, focus timer, journal, goals, character,
AI, Indian-language text, festival and fasting days. Each gets its own short spec when its turn comes.

## Build order and checks
1. Engine: habit schedule and progress rules, task carry-over, routine completion, with unit tests.
2. Data layer: tables, repositories, migration test, export/restore/wipe tests.
3. Habits screens, then Planner, then Routines, then Today. Check each on the emulator.
4. Update the privacy page; install on the phone.

**Acceptance (examples)**
- A weekdays-only habit is not due on Saturday and does not count against the week.
- A "3 times a week" habit with 3 ticks by Wednesday shows done for the week and is not due Thursday.
- Water at 5 of 8 glasses is not done; adding 3 makes it done.
- A task left undone yesterday shows once under Carried over today, and does not return after Move to tomorrow.
- Export, wipe, restore gives back every habit, task and routine.
- Old exports (before this change) still restore.

## Questions for you
1. **Colour:** the app is black and white. Allow one accent colour per habit, or keep everything monochrome?
2. **Home screen:** should Today replace the Week page as the first screen when the app opens?
3. **Habit suggestions:** is the starting list (water, sleep, reading, yoga, meditation, walking) right, or what else?
