package home.aurora

import android.content.Context
import android.content.SharedPreferences

/** The one settings file everything shares (the games use it too, for their scores). */
object Prefs {

    const val NAME = "aurora"

    @JvmStatic
    fun of(c: Context): SharedPreferences = c.getSharedPreferences(NAME, Context.MODE_PRIVATE)
}
