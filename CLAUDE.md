# RepPal (working name): Home Workout Coach

Android app for beginners that builds home workout plans from the equipment the user owns,
tracks every set, and adds load slowly using a deterministic rules engine. AI is a thin layer on top
(parsing speech, explaining, coaching text). AI never decides loads.

Store title idea: "RepPal: Home Workout Coach" (name is NOT final; keep it in one place, e.g. strings.xml).

## Read first
1. docs/PRODUCT_SPEC.md   what the app does and every screen
2. docs/RULES_ENGINE.md   planner and progression rules (the core; port exactly, test heavily)
3. docs/BUILD_PLAN.md     order of work and acceptance tests
4. docs/exercise_library.json and docs/plan_tables.json   data to load as app assets
5. prototype/reppal-prototype.html   working web prototype. It is the reference behaviour.
   Open it in a browser. All logic is in the single <script>. Functions of interest:
   build, buildM, applySw, progress, adjust, sessCard, volCard, wkSeq, derive.

## Stack (proposed)
- Kotlin, Jetpack Compose, Material 3, single activity, navigation-compose
- minSdk 26, current stable AGP / Kotlin
- Room for history and per-exercise state; DataStore for settings
- Rules engine in a pure Kotlin module (no Android imports) so it is unit-testable on the JVM
- Android SpeechRecognizer for voice (on-device where available)
- Auth later: Credential Manager + Google sign-in via Firebase. Start as guest-only, local data.
- AI layer later, behind an interface (see RULES_ENGINE.md section 9). Start with a keyword parser.

## Working rules
- Write the rules engine and its unit tests BEFORE the UI. The prototype behaviour is the spec.
- Keep UI minimal: monochrome, light default plus Light / Dark / Auto switch, lots of white space.
- Beginners first. Slow progression. Never push through pain.
- Volume counts external weight (kg) only; bodyweight moves are counted in reps.
- Do not add features not in the spec without asking.
- Android project goes in /android (create it). Keep docs and prototype untouched.
- Commit small, run unit tests before each commit.
