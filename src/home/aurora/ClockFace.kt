package home.aurora

/** Anything on the home screen that can show the time, given as "0942" or " 942". */
fun interface ClockFace { fun setTime(hhmm: String) }
