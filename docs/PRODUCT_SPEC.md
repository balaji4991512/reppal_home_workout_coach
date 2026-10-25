# Product spec

## Audience and promise
First users are gym beginners training at home with little equipment. Promise: a plan that fits what you own,
that moves up slowly and only when you have earned it, and logs every set with almost no typing.

## Onboarding / setup (one screen, editable later under "Equipment and plan")
1. Equipment
   - Bench: none / flat / adjustable
   - Dumbbells: tick the weights owned, 1 to 24 kg (steps 1,2,4,5,6,8,10,12,14,16,20,24; see plan_tables.json)
   - Bodyweight moves are always available
2. Training level: beginner / intermediate / advanced (choose from options, or derived from "how long have you lifted")
3. Days per week: 1 to 7. Weeks run Monday to Sunday. Workout weekdays per count are in plan_tables.json (workoutDayIndexes).
4. Training split: Auto, Full body, Upper/lower, Push/pull/legs, Body-part (chest+triceps, back+biceps, legs, shoulders+abs), Choose my own.
   - Choose my own: per training day, tick muscles. Show a coverage hint (muscles not trained, trained only once).
5. Concerns: ONE FREE-TEXT box ("any injury or exercise you cannot do"). No pick lists.
   Text is mapped to excluded exercises (keyword table now, LLM later). Show what was left out and why.
6. Sign in with Google, or continue as guest (data local to device).
Planned later: a baseline step to find starting weights (see BUILD_PLAN.md, phase 6).

## Screens
- This week: hero card (day, workout name, exercise count, minutes, "n of N workouts done"), Start workout,
  then Monday to Sunday list. Each day expands to its exercises with prescription ("3 x 12 at 5 kg").
  Upcoming exercises have a small swap icon at the right end; tapping lists alternatives for the same primary muscle.
  Finished days show the summary card. A collapsed "Planned volume this week" section lists kg per muscle (weighted) and reps (bodyweight).
- Workout (one exercise at a time): progress dots, big target ("12 reps at 5 kg"), set pills, text/voice box + Mic,
  "Reps you did" chips (start at the target and count down 5 values), "How it felt" chips Easy / Just right / Hard / Felt pain.
  Tapping chips builds the set record at once. Typed/voiced text needs "Check my note". Then one line summary
  ("12 reps at 5 kg, just right") and Save set. Raw JSON only under a collapsed "Show saved data".
  After each set a one-line note says what the next set will be.
- Workout done: percent of planned volume with Target reached / Below target, one-line totals, then one row per exercise
  (reps and the weight used per set, Hit / Short / Stopped). Planned figure shown only for rows that fell short.
  Then "what changes next time" per exercise.
- Progress: exercise list, then per-exercise chart (range tabs, line chart with nice-step axis), recent sets with PR badge.
- History and profile: account, level, days, bench, dumbbells, working-around list, saved workouts (expandable), wipe data (confirm inline).
- Equipment and plan, How it works, theme switch (Light / Dark / Auto), sign in / out.
- Side menu: This week, Today's workout, Progress, History and profile, Equipment and plan, How it works. Logo is a dumbbell mark in a rounded square.

## Set record (what voice or chips produce)
{ "reps": int, "weight_kg": number|null, "effort": "easy|just right|hard", "flags": ["pain"]?, "comment": string }
More fields later.

## Pain
Any pain flag: finish nothing further for that exercise, remove it from the plan (kept in excluded list), tell the user to
see a doctor or physio. Never suggest pushing through.

## Persistence
Local: settings, plan state per exercise, swaps, history (date, plan, reps, per-set weights, logs, pain), theme.

## Non-goals (for now)
Social, nutrition, camera form checking, autonomous agents, medical advice.
