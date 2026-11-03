package com.fujitake.cribbage.app

import android.content.Context
import com.fujitake.cribbage.engine.SavedGame

/** Everything kept between launches: the card styles and the game in progress. */
class CoachStore(context: Context) {
    private val prefs = context.getSharedPreferences("coach", Context.MODE_PRIVATE)

    var face: FaceStyle
        get() = FaceStyle.entries.firstOrNull { it.name == prefs.getString(FACE, null) } ?: FaceStyle.IVORY
        set(value) = prefs.edit().putString(FACE, value.name).apply()

    var back: BackStyle
        get() = BackStyle.entries.firstOrNull { it.name == prefs.getString(BACK, null) } ?: BackStyle.GRAPHITE
        set(value) = prefs.edit().putString(BACK, value.name).apply()

    var savedGame: SavedGame?
        get() = SavedGame.parse(prefs.getString(GAME, null))
        set(value) = prefs.edit().putString(GAME, value?.serialize()).apply()

    private companion object {
        const val FACE = "face"
        const val BACK = "back"
        const val GAME = "game"
    }
}
