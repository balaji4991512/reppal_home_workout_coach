# How to do each exercise (exercise guide)

Every exercise in the library (all 56) has a guide page: "howto/{id}".

- **Drawn figure.** A simple figure in the app's colours loops through the movement (`engine/Figure.kt`, `engine/Motions.kt`).
  Most moves are drawn from the side; lateral raises and side bends from the front. The worked muscles glow on the
  figure: main muscles in coral, helpers lighter. Tap to pause. Positions are tuned so feet and hands stay on the floor
  or bench and the figure stays in its box (checked by tests; `MOTION_SHEETS=<dir>` writes contact sheets to look at).
- **Muscle map.** Front and back outlines with each group filled: main strong, helpers light, the rest plain.
- **Steps and tips.** Three to four steps in plain words and one to three "watch out" tips (`engine/Guides.kt`),
  ending with "Stop if anything hurts".
- **Where to open it.** The exercise name on the Week screen ("How to do it" under each name), and "How to do it" under
  the exercise name while logging a workout. From a workout the guide opens on top; Back returns with the sets kept,
  and leaving for another tab from the guide asks first, as leaving the workout does.
- Nothing is downloaded; it all works offline.
