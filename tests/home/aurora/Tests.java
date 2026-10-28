package home.aurora;

import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;

/**
 * Plain-Java tests for the parts of Aurora Home that don't need a TV:
 * pairing limits, PIN hashing, the morning screen's schedule and calendar,
 * device names and backup types. Run with ./build.sh --test (Gradle's unit tests).
 */
public final class Tests {

    private static int passed, failed;

    @org.junit.Test
    public void everything() {
        TimeZone before = TimeZone.getDefault();
        try {
            org.junit.Assert.assertEquals("failed checks (see output)", 0, run());
        } finally {
            TimeZone.setDefault(before);
        }
    }

    static int run() {
        passed = failed = 0;
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        pairing();
        pins();
        morningSchedule();
        repeats();
        calendarFile();
        names();
        backupTypes();
        System.out.println(passed + " passed, " + failed + " failed");
        return failed;
    }

    private static void check(String what, boolean ok) {
        if (ok) passed++;
        else { failed++; System.out.println("FAIL  " + what); }
    }

    private static void eq(String what, Object want, Object got) {
        check(what + " (want " + want + ", got " + got + ")", want == null ? got == null : want.equals(got));
    }

    // ------------------------------------------------------------------ pairing

    private static void pairing() {
        long t = 1_000_000;
        PairGuard g = new PairGuard(new Random(1));
        String code = g.newCode(t);
        eq("codes have six digits", 6, code.length());
        eq("right code pairs", null, g.check(code, true, t));
        eq("a code works once", "Code expired. Request a new one.", g.check(code, true, t));

        code = g.newCode(t);
        eq("right code, wrong PIN", "Wrong PIN", g.check(code, false, t));

        // Asking for fresh codes must not reset the hourly budget.
        g = new PairGuard(new Random(2));
        String last = null;
        for (int i = 0; i < PairGuard.MISSES_PER_HOUR; i++) {
            if (i % 4 == 0) g.newCode(t + i);
            last = g.check("000000x", true, t + i);
        }
        check("15 misses across many codes pause pairing: " + last, g.paused(t + 20));
        String c = g.newCode(t + 30);
        check("paused pairing refuses even the right code", g.check(c, true, t + 30) != null);
        check("still paused after 59 minutes", g.paused(t + 59 * 60_000));
        check("open again after an hour", !g.paused(t + 61 * 60_000));
        g.unpause();
        c = g.newCode(t + 40);
        eq("the TV lifting the pause lets the right code pair", null, g.check(c, true, t + 40));

        g = new PairGuard(new Random(3));
        g.newCode(t);
        for (int i = 0; i < 4; i++) g.check("x", true, t);
        eq("fifth miss uses up a code", "Too many tries. Request a new code.", g.check("x", true, t));
        g.newCode(t);
        eq("codes expire after two minutes", "Code expired. Request a new one.", g.check("x", true, t + PairGuard.CODE_MS + 1));
    }

    // ------------------------------------------------------------------ PIN

    private static void pins() {
        byte[] salt = new byte[16];
        String stored = Pin.encode("2468", salt, 1000);
        check("stored PIN isn't the digits", !stored.contains("2468"));
        check("right PIN matches", Pin.matches(stored, "2468"));
        check("wrong PIN doesn't", !Pin.matches(stored, "2469"));
        check("garbage doesn't crash or match", !Pin.matches("pbkdf2$x$y", "2468"));
        check("different salt, different hash", !stored.equals(Pin.encode("2468", new byte[]{1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16}, 1000)));
    }

    // ------------------------------------------------------------------ morning screen

    private static Calendar at(int y, int m, int d, int h, int min) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(y, m - 1, d, h, min);
        return c;
    }

    private static void morningSchedule() {
        Calendar fri = at(2026, 10, 30, 8, 0);   // a Friday, after 7:00
        Calendar next = Calendar.getInstance();
        next.setTimeInMillis(Morning.next(fri, 7 * 60, 1));
        eq("weekdays skip the weekend", Calendar.MONDAY, next.get(Calendar.DAY_OF_WEEK));
        next.setTimeInMillis(Morning.next(fri, 7 * 60, 2));
        eq("weekends land on Saturday", Calendar.SATURDAY, next.get(Calendar.DAY_OF_WEEK));
        next.setTimeInMillis(Morning.next(at(2026, 10, 30, 6, 0), 7 * 60, 0));
        eq("later today when it hasn't passed", 30, next.get(Calendar.DAY_OF_MONTH));
        eq("Windows zone names work", "America/Chicago", Morning.zone("Central Standard Time").getID());
        eq("unknown zones fall back to the TV's, not GMT", "America/New_York", Morning.zone("Somewhere/Odd").getID());
    }

    private static void repeats() {
        Calendar tue = at(2026, 10, 27, 0, 0);
        Calendar start = at(2026, 10, 6, 9, 30);   // Tuesday
        check("weekly on Tuesdays", Morning.occursOn(start, "FREQ=WEEKLY;BYDAY=TU,TH", tue));
        check("not on Mondays", !Morning.occursOn(start, "FREQ=WEEKLY;BYDAY=MO", tue));
        check("every 2 weeks: week 3 is off", !Morning.occursOn(start, "FREQ=WEEKLY;INTERVAL=2", tue));
        check("every 3 weeks: week 3 is on", Morning.occursOn(start, "FREQ=WEEKLY;INTERVAL=3", tue));
        check("COUNT=3 ends before the 4th Tuesday", !Morning.occursOn(start, "FREQ=WEEKLY;COUNT=3", tue));
        check("COUNT=4 reaches the 4th Tuesday", Morning.occursOn(start, "FREQ=WEEKLY;COUNT=4", tue));
        check("daily every 3 days", Morning.occursOn(at(2026, 10, 18, 9, 0), "FREQ=DAILY;INTERVAL=3", tue));
        check("daily every 4 days misses", !Morning.occursOn(at(2026, 10, 18, 9, 0), "FREQ=DAILY;INTERVAL=4", tue));
        check("UNTIL in the past", !Morning.occursOn(at(2026, 10, 1, 9, 0), "FREQ=DAILY;UNTIL=20261020T000000Z", tue));
        check("monthly on the 27th", Morning.occursOn(at(2026, 8, 27, 9, 0), "FREQ=MONTHLY", tue));
        check("every other month skips October", !Morning.occursOn(at(2026, 9, 27, 9, 0), "FREQ=MONTHLY;INTERVAL=2", tue));
        check("yearly birthday", Morning.occursOn(at(1990, 10, 27, 0, 0), "FREQ=YEARLY", tue));
        check("events don't happen before they start", !Morning.occursOn(at(2026, 11, 3, 9, 0), "FREQ=WEEKLY", tue));
        check("unknown rules are left out", !Morning.occursOn(start, "FREQ=HOURLY", tue));
    }

    private static void calendarFile() {
        String ics = "BEGIN:VCALENDAR\r\n"
                + "BEGIN:VEVENT\r\nUID:a\r\nSUMMARY:Standup\r\nDTSTART;TZID=America/New_York:20261006T093000\r\nRRULE:FREQ=WEEKLY;BYDAY=TU\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nUID:a\r\nSUMMARY:Standup (moved)\r\nRECURRENCE-ID;TZID=America/New_York:20261027T093000\r\nDTSTART;TZID=America/New_York:20261027T113000\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nUID:b\r\nSUMMARY:Gym\r\nDTSTART;TZID=Eastern Standard Time:20261006T180000\r\nRRULE:FREQ=WEEKLY\r\nEXDATE;TZID=Eastern Standard Time:20261027T180000\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nUID:c\r\nSUMMARY:Pumpkin\\, carving\r\nDTSTART;VALUE=DATE:20261027\r\nEND:VEVENT\r\n"
                + "BEGIN:VEVENT\r\nUID:d\r\nSUMMARY:Dent\r\n ist\r\nDTSTART:20261027T140000Z\r\nEND:VEVENT\r\n"
                + "END:VCALENDAR\r\n";
        List<Morning.Event> today = Morning.eventsOn(ics, at(2026, 10, 27, 7, 0));
        StringBuilder titles = new StringBuilder();
        for (Morning.Event e : today) titles.append(e.title).append(" | ");
        eq("today's events, all-day first, in order", "Pumpkin, carving | Dentist | Standup (moved) | ", titles.toString());
        Calendar dentist = Calendar.getInstance();
        dentist.setTimeInMillis(today.get(1).start);
        eq("UTC times show in local time", 10, dentist.get(Calendar.HOUR_OF_DAY));
    }

    // ------------------------------------------------------------------ small things

    private static void names() {
        eq("iPhone", "iPhone", Security.deviceName("Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X)"));
        eq("Android model", "Pixel 8", Security.deviceName("Mozilla/5.0 (Linux; Android 14; Pixel 8 Build/AP1A) Chrome/120"));
        eq("Chrome's reduced UA", "Android phone", Security.deviceName("Mozilla/5.0 (Linux; Android 10; K) Chrome/120"));
        eq("Mac", "Mac", Security.deviceName("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0)"));
        eq("nothing", "Phone", Security.deviceName(null));
    }

    private static void backupTypes() {
        eq("night hours are ints", "i", Backup.typeOf("night_start"));
        eq("theme is a string", "s", Backup.typeOf("theme"));
        eq("weather position is a float", "f", Backup.typeOf("w_lat"));
        eq("per-app volume", "i", Backup.typeOf("vol:com.netflix.ninja"));
        eq("game scores", "i", Backup.typeOf("best_2048"));
        eq("interface sound volume", "i", Backup.typeOf("ui_sounds_level"));
        eq("unknown keys aren't restored", null, Backup.typeOf("made_up"));
    }
}
