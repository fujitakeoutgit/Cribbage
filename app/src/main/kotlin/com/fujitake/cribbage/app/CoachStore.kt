package com.fujitake.cribbage.app

import android.content.Context
import com.fujitake.cribbage.engine.HandRecord
import com.fujitake.cribbage.engine.SavedGame
import java.io.File

/** Everything kept between launches: card styles, the game in progress and your hand history. */
class CoachStore(context: Context) {
    private val prefs = context.getSharedPreferences("coach", Context.MODE_PRIVATE)
    private val historyFile = File(context.filesDir, "history.csv")

    fun records(): List<HandRecord> =
        if (historyFile.exists()) HandRecord.parseAll(historyFile.readText()) else emptyList()

    fun addRecord(record: HandRecord) = historyFile.appendText(record.serialize() + "\n")

    fun clearRecords() {
        historyFile.delete()
    }

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
