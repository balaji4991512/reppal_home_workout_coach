# Talking to Wick (draft, defaults chosen; change anything)

Asked for: say "Wick, set a timer for 25 minutes for study" and have it start, even with the phone locked.

## How it listens
- Settings has **Listen for "Wick"**, off by default. Turning it on asks for the microphone, then keeps a quiet
  notification up ("Listening for Wick") for as long as it is on. Android shows its microphone dot all that time.
- Speech is recognised on the phone by Vosk with the small Indian English model (`vosk-model-small-en-in`, about 36 MB,
  inside the app). No audio and no text leave the phone; nothing is recorded or saved.
- It only listens for a short list of words: "wick", the command words below, numbers and the focus tags. Anything else
  is heard as unknown and ignored. Tags with words the model does not know cannot be said.
- It keeps listening with the screen off and the phone locked. If Android stops it (the phone restarted, or the app
  was cleared), it starts again the next time Wick opens, and after a restart.
- It does not listen during a phone call, or while another app is recording.
- Costs, said on the switch: battery, the microphone dot, and the odd false start when someone says "wick".

## What it understands
A command is one sentence that starts with "Wick". Saying just "Wick" plays a short beep, then the command can follow
within 5 seconds.

| Say | Does |
|---|---|
| "Wick, set a timer for 25 minutes for study" / "Wick, study for an hour" / "Wick, focus 45" | starts a focus session |
| "Wick, pause" | pauses the running session |
| "Wick, resume" / "continue" / "carry on" | resumes it |
| "Wick, stop" / "end" / "finish" / "cancel" | ends it; time focused so far is kept, as with End session |

- **Length:** minutes ("25 minutes", "twenty five", a bare number), hours ("an hour", "two hours", "an hour and a half",
  "half an hour") or both ("one hour thirty minutes"). No length means 25 minutes. It must be 1 to 180 minutes.
- **Tag:** any tag in the Focus list, said anywhere in the sentence ("studying" counts as Study). No tag means none.
- Starting while a session already runs does nothing; Wick says so with the "no" sound.

## What you hear
- Understood and done: a short rising chime, and the Focus notification shows the session (as now, with its end time).
- Not understood, or not possible (nothing running to pause, length out of range, a session already running): a short
  low tone. Nothing changes.
- Sounds play on the alarm volume, like the focus end, so they are heard on vibrate.

## Built in this order
1. The command parser in the engine (text to command), with tests. Pure Kotlin, no Android.
2. The listening service (microphone foreground service, Vosk, the word list, the sounds), and the commands acting on
   the existing focus sessions (start, pause, resume, end), so alarms and history work as they do from the screen.
3. The Settings switch, the permission, and starting again after a restart.

## Not in this spec
Commands for workouts, habits or tasks; Google Assistant; any spoken replies; any network speech.
