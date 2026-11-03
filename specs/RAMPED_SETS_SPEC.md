# Ramped sets (a change to docs/RULES_ENGINE.md sections 6 and 7 for weighted moves)

docs/RULES_ENGINE.md plans every set of an exercise at one weight and moves up from the heaviest weight used. That
made a session done as 15 reps at 10 kg, 12 at 13.5 and 12 at 16 plan the next one as 3 x 12 at 16 kg for every set.
Weighted (dumbbell) moves are now planned as a ramp. Bodyweight moves, and workouts saved before this change, keep the
rules in docs/RULES_ENGINE.md as they were. The docs are left as they were; this file is the rule for weighted moves.

## The plan
- The exercise state is still { top weight, target reps, sets }. The top weight is the last set.
- Each earlier set is the owned dumbbell below the next one, and at least 10% lighter (so a fine-stepped set still
  ramps). The lightest owned dumbbell is a floor. Reps are the same in every set.
  With the 15 settings of an adjustable dumbbell, a top of 16 kg over 3 sets is 11.5, 13.5, 16; over 4 sets, 10, 11.5, 13.5, 16.
- Planned volume is the sum of reps x each set's weight. The plan keeps the weight of every set (`setWeights`), so a
  saved workout still reads the same if the rule changes later. An empty list means every set at the top weight.

## In the workout
- Set 1 is the lightest weight. After a set that met its target, the next set steps up to the next planned weight, one
  dumbbell at a time, with the planned reps. A set short of its target, or a pain report, does not step up.
- Everything else in the live rule is as before (the next set follows what was done; easy at the top of the range moves up).

## Next session (top set leads)
- Work short of the plan (volume below the ramp's): the same plan again.
- A weight lifted above the plan's top is the new top, with the reps it was lifted for (8 to 12).
- Otherwise the top set (best set at the top weight) decides: below its target reps, the same plan; at or above the
  target but below 12, one more rep; at 12, the top moves to the next dumbbell for min(12, max(6, floor(12 x old / new) + 1)) reps.
- At 12 reps with nothing heavier, one set is added (up to 4), with the reps that make the ramp about 10% more work.
- Nothing heavier and 4 sets: the same plan again.

## Where it shows
Week and profile plans ("3 x 12 at 11.5, 13.5, 16 kg"), the set being logged, the workout summary (planned work is the
ramp, so starting light is not "short"), and the Done screen's notes. Export format 14 carries `setWeightsKg`.
