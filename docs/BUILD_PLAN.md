# Build plan for Claude Code

## Phase 0: project skeleton
- Create /android (Kotlin, Compose, Material 3). Modules: `app` and `engine` (pure Kotlin/JVM library).
- Load docs/exercise_library.json and docs/plan_tables.json as assets (copy into engine resources; keep originals).
- App name string in one place.

## Phase 1: engine + unit tests (no UI)
Port in this order, each with tests that mirror the prototype:
1. Equipment checks and exercise availability
2. st(): starting weights per level; snapping to owned dumbbells
3. Week schedules (all 7 day counts, all splits) and occurrence suffix
4. Preset day builder; muscle-day builder (counts, cap, rotation); swaps
5. Concern keyword mapping
6. Live per-set adjustment
7. Next-session progression (10% of actual volume)
8. Volume and summary calculations, PR detection
Acceptance examples (from prototype tests):
- plan 3x12 at 5 kg, did 15,12,12 at 10 kg (390 kg) -> next 3 x 12 at 12 kg (432 kg, +11%)
- plan 3x12 at 5 kg, did 12,12,12 at 5 kg (180) -> next 3 x 11 at 6 kg (198, +10%)
- plan 3x12 at 5 kg, did 12 at 5, 11 at 6, 12 at 6 (198) -> next 3 x 12 at 6 kg (216, +9%)
- did 10,9,8 at 5 kg (135 < 180) -> same plan again
- bodyweight pushup 3x12, did 15,14,14 (43 reps) -> next 3 x 15
- no bench, no dumbbells, body-part split 4 days: chest day = push-up, wide push-up, close-grip push-up; biceps reported missing
- every combination of bench x dumbbell sets x days 1..7 x split builds without error and without duplicate exercises in a day
- beginner day never exceeds 6 exercises, others 7

## Phase 2: data layer
Room entities: Workout (date, label, plan json, reps json, per-set weights json, log json, pain ids), ExerciseState,
Swap, Settings (DataStore). Repositories with in-memory fakes for tests.

## Phase 3: UI, matching the prototype
Setup -> This week -> Workout -> Workout done -> History/Profile -> Progress -> How it works.
Use the prototype for layout, wording and spacing. Monochrome tokens; Light / Dark / Auto.
Edge to edge, safe areas, large touch targets (min 48dp), no horizontal scroll at 360dp width.

## Phase 4: voice
SpeechRecognizer to text, keyword parser, one-line readback, Save set. Locale en-IN first; Tamil and Hindi later.

## Phase 5: release hygiene
Crash-free offline use, data export, wipe data, privacy text, Play Console listing (title "RepPal: Home Workout Coach" if the name stays).

## Phase 6: later
Baseline / find-my-weights step (calibration set per main movement, seed related lifts by ratio),
Google sign-in + cloud backup, on-device LLM for SetParser and ConcernMapper, coach notes, readiness check-in,
plateau and deload suggestions, weekly review with accept/decline suggestions, local-language voice.

## Open questions for the owner
- Final brand name (RepPal is a placeholder; an app with the same name exists on iOS)
- Allow editing or deleting past workouts?
- Heaviest-weight chart toggle in Progress?
- Should moving up a dumbbell need two sessions at the top of the range?
- Name and photo from the Google profile on the profile screen?
