package com.homeworkoutcoach.app.ui.habits

import com.homeworkoutcoach.engine.Habits

/** One picture a habit can have. [key] is what is stored and never changes, so a saved habit keeps its look. */
data class HabitIcon(val key: String, val emoji: String, val color: String, val group: String)

/** Every icon to pick from, in the order they are listed, grouped by what they are about. */
object HabitIcons {
    const val HEALTH = "Health"
    const val MOVEMENT = "Movement"
    const val SPORT = "Sport"
    const val FOOD = "Food and drink"
    const val MIND = "Mind and learning"
    const val CREATIVE = "Creative"
    const val HOME = "Home and pets"
    const val WORK = "Work and money"
    const val PEOPLE = "People"
    const val OUTDOORS = "Outdoors"
    const val GENERAL = "General"

    val groups = listOf(GENERAL, HEALTH, MOVEMENT, SPORT, FOOD, MIND, CREATIVE, HOME, WORK, PEOPLE, OUTDOORS)

    val all: List<HabitIcon> = listOf(
        // general
        HabitIcon("check", "✅", "blue", GENERAL), HabitIcon("star", "⭐", "amber", GENERAL), HabitIcon("heart", "❤️", "red", GENERAL),
        HabitIcon("bolt", "⚡", "orange", GENERAL), HabitIcon("fire", "🔥", "orange", GENERAL), HabitIcon("rocket", "🚀", "purple", GENERAL),
        HabitIcon("crown", "👑", "amber", GENERAL), HabitIcon("gem", "💎", "teal", GENERAL), HabitIcon("flag", "🚩", "red", GENERAL),
        HabitIcon("trophy", "🏆", "amber", GENERAL), HabitIcon("medal", "🏅", "amber", GENERAL), HabitIcon("list", "📋", "blue", GENERAL),
        HabitIcon("sun", "☀️", "amber", GENERAL), HabitIcon("moon", "🌙", "purple", GENERAL), HabitIcon("target", "🎯", "red", GENERAL),
        // health
        HabitIcon("drop", "💧", "blue", HEALTH), HabitIcon("pill", "💊", "pink", HEALTH), HabitIcon("tooth", "🪥", "teal", HEALTH),
        HabitIcon("floss", "🦷", "teal", HEALTH), HabitIcon("shower", "🚿", "teal", HEALTH), HabitIcon("soap", "🧼", "teal", HEALTH),
        HabitIcon("lotion", "🧴", "pink", HEALTH), HabitIcon("stethoscope", "🩺", "teal", HEALTH), HabitIcon("bandage", "🩹", "pink", HEALTH),
        HabitIcon("thermometer", "🌡️", "red", HEALTH), HabitIcon("eye", "👁️", "blue", HEALTH), HabitIcon("sleep", "😴", "purple", HEALTH),
        HabitIcon("bed", "🛏️", "purple", HEALTH), HabitIcon("alarm", "⏰", "orange", HEALTH), HabitIcon("posture", "🧍", "blue", HEALTH),
        // movement
        HabitIcon("walk", "🚶", "green", MOVEMENT), HabitIcon("run", "🏃", "orange", MOVEMENT), HabitIcon("steps", "👟", "orange", MOVEMENT),
        HabitIcon("dumbbell", "🏋️", "orange", MOVEMENT), HabitIcon("pushup", "💪", "orange", MOVEMENT), HabitIcon("lotus", "🧘", "pink", MOVEMENT),
        HabitIcon("yoga", "🧘‍♀️", "pink", MOVEMENT), HabitIcon("stretch", "🤸", "orange", MOVEMENT), HabitIcon("dance", "💃", "pink", MOVEMENT),
        HabitIcon("bike", "🚴", "orange", MOVEMENT), HabitIcon("swim", "🏊", "blue", MOVEMENT), HabitIcon("hike", "🥾", "green", MOVEMENT),
        HabitIcon("climb", "🧗", "orange", MOVEMENT), HabitIcon("stopwatch", "⏱️", "blue", MOVEMENT), HabitIcon("rowing", "🚣", "blue", MOVEMENT),
        // sport
        HabitIcon("boxing", "🥊", "red", SPORT), HabitIcon("martial", "🥋", "red", SPORT), HabitIcon("soccer", "⚽", "green", SPORT),
        HabitIcon("basketball", "🏀", "orange", SPORT), HabitIcon("tennis", "🎾", "green", SPORT), HabitIcon("badminton", "🏸", "teal", SPORT),
        HabitIcon("cricket", "🏏", "amber", SPORT), HabitIcon("golf", "⛳", "green", SPORT), HabitIcon("surf", "🏄", "blue", SPORT),
        HabitIcon("skate", "⛸️", "blue", SPORT), HabitIcon("ski", "⛷️", "blue", SPORT), HabitIcon("volleyball", "🏐", "amber", SPORT),
        // food and drink
        HabitIcon("apple", "🍎", "red", FOOD), HabitIcon("fruit", "🍇", "purple", FOOD), HabitIcon("banana", "🍌", "amber", FOOD),
        HabitIcon("salad", "🥗", "green", FOOD), HabitIcon("carrot", "🥕", "orange", FOOD), HabitIcon("broccoli", "🥦", "green", FOOD),
        HabitIcon("avocado", "🥑", "green", FOOD), HabitIcon("egg", "🥚", "amber", FOOD), HabitIcon("meal", "🍽️", "orange", FOOD),
        HabitIcon("cook", "🍳", "orange", FOOD), HabitIcon("lunchbox", "🍱", "teal", FOOD), HabitIcon("soup", "🍲", "orange", FOOD),
        HabitIcon("fasting", "⏳", "amber", FOOD), HabitIcon("tea", "🍵", "green", FOOD), HabitIcon("coffee", "☕", "amber", FOOD),
        HabitIcon("milk", "🥛", "blue", FOOD), HabitIcon("juice", "🧃", "orange", FOOD), HabitIcon("candy", "🍬", "pink", FOOD),
        HabitIcon("soda", "🥤", "red", FOOD), HabitIcon("burger", "🍔", "red", FOOD), HabitIcon("wine", "🍷", "purple", FOOD),
        // mind and learning
        HabitIcon("brain", "🧠", "purple", MIND), HabitIcon("book", "📖", "green", MIND), HabitIcon("books", "📚", "green", MIND),
        HabitIcon("pen", "✍️", "teal", MIND), HabitIcon("notebook", "📓", "teal", MIND), HabitIcon("breath", "🌬️", "teal", MIND),
        HabitIcon("gratitude", "🙏", "amber", MIND), HabitIcon("pray", "🛐", "purple", MIND), HabitIcon("smile", "😊", "amber", MIND),
        HabitIcon("idea", "💡", "amber", MIND), HabitIcon("puzzle", "🧩", "purple", MIND), HabitIcon("chess", "♟️", "purple", MIND),
        HabitIcon("language", "🗣️", "blue", MIND), HabitIcon("globe", "🌍", "teal", MIND), HabitIcon("graduation", "🎓", "blue", MIND),
        HabitIcon("code", "💻", "blue", MIND), HabitIcon("laptop", "🖥️", "blue", MIND), HabitIcon("flashcards", "🗂️", "blue", MIND),
        HabitIcon("podcast", "🎧", "purple", MIND), HabitIcon("news", "📰", "blue", MIND), HabitIcon("couch", "🛋️", "purple", MIND),
        // creative
        HabitIcon("music", "🎵", "pink", CREATIVE), HabitIcon("guitar", "🎸", "orange", CREATIVE), HabitIcon("piano", "🎹", "purple", CREATIVE),
        HabitIcon("mic", "🎤", "pink", CREATIVE), HabitIcon("art", "🎨", "pink", CREATIVE), HabitIcon("draw", "✏️", "amber", CREATIVE),
        HabitIcon("camera", "📷", "teal", CREATIVE), HabitIcon("sewing", "🧵", "pink", CREATIVE), HabitIcon("film", "🎬", "purple", CREATIVE),
        // home and pets
        HabitIcon("broom", "🧹", "green", HOME), HabitIcon("sponge", "🧽", "amber", HOME), HabitIcon("laundry", "🧺", "teal", HOME),
        HabitIcon("trash", "🗑️", "green", HOME), HabitIcon("recycle", "♻️", "green", HOME), HabitIcon("plant", "🌱", "green", HOME),
        HabitIcon("flower", "🌸", "pink", HOME), HabitIcon("home", "🏠", "orange", HOME), HabitIcon("dog", "🐶", "amber", HOME),
        HabitIcon("cat", "🐱", "amber", HOME), HabitIcon("fishtank", "🐠", "blue", HOME), HabitIcon("cart", "🛒", "orange", HOME),
        HabitIcon("box", "📦", "amber", HOME), HabitIcon("tools", "🔧", "orange", HOME),
        // work and money
        HabitIcon("money", "💰", "green", WORK), HabitIcon("chart", "📈", "green", WORK), HabitIcon("bank", "🏦", "teal", WORK),
        HabitIcon("card", "💳", "blue", WORK), HabitIcon("receipt", "🧾", "amber", WORK), HabitIcon("briefcase", "💼", "blue", WORK),
        HabitIcon("calendar", "📅", "red", WORK), HabitIcon("inbox", "📥", "teal", WORK), HabitIcon("email", "📧", "blue", WORK),
        HabitIcon("dnd", "🔕", "purple", WORK), HabitIcon("phone", "📵", "red", WORK), HabitIcon("social", "📱", "red", WORK),
        HabitIcon("tv", "📺", "red", WORK), HabitIcon("game", "🎮", "purple", WORK), HabitIcon("nosmoke", "🚭", "red", WORK),
        // people
        HabitIcon("call", "📞", "green", PEOPLE), HabitIcon("letter", "💌", "pink", PEOPLE), HabitIcon("family", "👪", "orange", PEOPLE),
        HabitIcon("friends", "👫", "pink", PEOPLE), HabitIcon("couple", "💑", "red", PEOPLE), HabitIcon("kid", "🧒", "amber", PEOPLE),
        HabitIcon("gift", "🎁", "red", PEOPLE), HabitIcon("hug", "🤗", "amber", PEOPLE), HabitIcon("party", "🎉", "pink", PEOPLE),
        HabitIcon("kindness", "🤲", "amber", PEOPLE), HabitIcon("volunteer", "🙋", "teal", PEOPLE), HabitIcon("donate", "💝", "pink", PEOPLE),
        // outdoors
        HabitIcon("tree", "🌳", "green", OUTDOORS), HabitIcon("leaf", "🍃", "green", OUTDOORS), HabitIcon("mountain", "⛰️", "teal", OUTDOORS),
        HabitIcon("sunlight", "🌞", "amber", OUTDOORS), HabitIcon("sunrise", "🌅", "amber", OUTDOORS), HabitIcon("stars", "🌌", "purple", OUTDOORS),
        HabitIcon("beach", "🏖️", "teal", OUTDOORS), HabitIcon("camp", "⛺", "green", OUTDOORS), HabitIcon("fishing", "🎣", "blue", OUTDOORS),
        HabitIcon("bird", "🐦", "blue", OUTDOORS), HabitIcon("compass", "🧭", "teal", OUTDOORS), HabitIcon("map", "🗺️", "teal", OUTDOORS),
    )

    private val byKey = all.associateBy { it.key }

    /** What to show before anything is typed. */
    val starters = listOf("check", "drop", "dumbbell", "book", "sleep")

    fun find(key: String): HabitIcon? = byKey[key]
    fun inGroup(group: String): List<HabitIcon> = all.filter { it.group == group }

    /**
     * Icons to try first for a typed name: the ones its suggestion uses, then icons whose key appears in the name
     * ("morning walk" gives walk). Fills up from [fallback] so there are always [count] to choose from.
     */
    fun quickFor(name: String, fallback: List<String>, count: Int = 5): List<HabitIcon> {
        val q = name.trim().lowercase()
        val fromName = if (q.isEmpty()) emptyList() else {
            val suggested = Habits.suggest(q, limit = 5).map { it.icon }
            val inWord = all.filter { it.key.length > 2 && q.contains(it.key) }.map { it.key }
            (suggested + inWord).mapNotNull(::find)
        }
        return (fromName + fallback.mapNotNull(::find)).distinctBy { it.key }.take(count)
    }
}
