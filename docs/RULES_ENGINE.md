# Rules engine (port exactly; prototype function names in brackets)

Data: docs/exercise_library.json (56 exercises) and docs/plan_tables.json.
An exercise has: id, name, needs (subset of "db", "bench", "adj"), bodyweight flag, startKg (dumbbell moves),
primary muscles, secondary muscles. Equipment check [has]: db = at least one dumbbell owned; bench = flat or adjustable;
adj = adjustable bench. Muscles: chest, shoulders, reardelts, lats, biceps, triceps, quads, hams, glutes, abs.

## 1. Per-exercise state [st]
{ weightKg, targetReps (default 12), sets (default 3) } per exercise id, persisted.
Initial weight for dumbbell moves: the largest owned dumbbell <= startKg x levelFactor
(beginner 1.0, intermediate 1.25, advanced 1.5); if none, the lightest owned. If the stored weight is no longer owned,
snap to the largest owned weight <= it (else lightest).

## 2. Weekly schedule [wkSeq, WEEK, DI, occ]
Auto: WEEK[days] from plan_tables.json (preset labels F, A, B, U1, L1, U2, L2, X, Z).
Other splits cycle a list over the training days: full body (A,B), upper/lower (U1,L1,U2,L2),
push/pull/legs and body-part (muscle labels "M:chest,triceps"), custom (per-day muscle list).
When the same muscle label repeats in a week, append "#n" so the second occurrence varies its exercises.

## 3. Building a day [build -> buildRaw -> applySw]
Preset labels: PLAN[label] is an ordered map slot -> candidate ids. For each slot, take the first candidate that is
equipment-available, not excluded, and not already used. A slot with no candidate goes into "miss" (UI: "nothing in your
equipment covers X").
Muscle labels [buildM]:
  - count of exercises per muscle = max(1, round(weeklyTarget / frequencyThisWeek / 3)),
    weeklyTarget = TGT[level] direct sets: big muscles beginner 9, intermediate 13, advanced 16;
    small muscles (shoulders, reardelts, biceps, triceps, abs) 6, 9, 12. frequency = number of days that muscle appears.
  - cap exercises per day: 6 for beginner, 7 otherwise. While over the cap, decrement the muscle with the most exercises (never below 1).
  - per muscle, walk PREF[muscle] groups in order (rotate the group list by occurrence index n for repeats) and take the first
    available id of each group until the count is reached. Big muscles are ordered first.
Swaps [applySw]: S.sw maps baseId -> chosenId, applied after build if chosen is available, not excluded, and not already in the day.
Alternatives for the swap sheet: same first primary muscle, available, not excluded, not already in the day.

## 4. Exclusions from concerns [derive, KW]
Free text is lowercased and matched against KW (regex per exercise id, in plan_tables.json). Matching ids are excluded.
Pain reports add ids to a permanent excluded list. Replace with an LLM later but keep this as the offline fallback.

## 5. Volume accounting
Weighted exercise volume = sum over sets of reps x weightKg. Bodyweight exercises count reps only (never kg).
Per muscle, only the primary muscle gets the kg or reps credit. (Weekly "sets per muscle" counts secondary at half.)

## 6. Live adjustment inside a workout [cur, adjust]
After each saved set (no pain flag):
  - reps < current target: next set unchanged.
  - effort not easy: unchanged.
  - effort easy and target < top (12 weighted, 15 bodyweight): target += 1 (capped at top), same weight.
  - effort easy at the top: move to the next heavier owned dumbbell, new target = min(top, max(6, floor(top x oldW / newW) + 1)).
  - nothing heavier: unchanged.
Each set stores the weight and target that applied to it. Reps chips start at the target and count down 5 values.

## 7. Next session [progress]
Inputs: planned {sets, target, weight}, actual reps per set, actual weight per set.
  plannedVol = sets x target x plannedWeight (bodyweight: sets x target).
  actualVol  = sum(reps_i x weight_i).
  - actualVol < plannedVol: keep the same plan (state reset to planned values).
  - else baseWeight = heaviest weight used in the session (bodyweight: n/a).
    targetVol = actualVol x 1.10. Candidate plans: for reps r in 8..top (top 12 weighted, 15 bodyweight):
      (sets, r, baseWeight), (sets, r, next heavier weight, penalty 0.02), (sets+1, r, baseWeight, penalty 0.08) if sets < 4.
    Keep candidates with volume > actualVol. Choose the one minimising |vol - targetVol| / targetVol + penalty.
    If none, keep the plan at baseWeight.
  Message: "Volume was X (plan was Y), so next time aims for about 10% more than that: Z (+n%)". Rounding can land a few percent off 10 (dumbbell steps).
Progression must stay slow. Never jump weight just because a single set was easy; the live rule above handles that inside a session.

## 8. Workout summary [sessCard]
Per exercise: Hit if all planned sets done and (actual volume >= planned, or every set met its own live target for weighted moves).
Short otherwise; Stopped if pain. Header percent = actualVol / plannedVol for weighted (reps basis if only bodyweight).
Personal record: a set whose weight x reps (bodyweight: reps) beats all earlier sets of that exercise [withPR].

## 9. AI layer (later, behind interfaces)
- SetParser: text -> SetRecord. Default implementation = the keyword parser [parse]. Later an on-device LLM must return the
  same JSON schema and be validated; on failure fall back to the keyword parser. Always show the one-line readback.
- ConcernMapper: text -> excluded exercise ids. Same pattern.
- CoachNotes: produce short explanatory text from numbers the engine already computed. Never change loads.
- Safety: pain/dizziness/injury words trigger a fixed message (stop, see a professional). No diagnosis.
