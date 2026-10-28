package home.aurora;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.List;
import java.util.Random;
import java.util.TimeZone;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Plain-Java tests for the parts of Aurora Home that don't need a TV:
 * pairing limits, PIN hashing, the morning screen's schedule and calendar,
 * device names and backup types. Run with ./build.sh --test (Gradle's unit tests).
 */
public final class Tests {

    private static int passed, failed;

    // One test per area, so a failure names the area; the checks inside print what failed.
    @org.junit.Test public void pairingLimits() { section(Tests::pairing); }
    @org.junit.Test public void pinHashing() { section(Tests::pins); }
    @org.junit.Test public void morningSchedule() { section(Tests::schedule); }
    @org.junit.Test public void calendarRepeats() { section(Tests::repeats); }
    @org.junit.Test public void calendarFile() { section(Tests::icsFile); }
    @org.junit.Test public void deviceNames() { section(Tests::names); }
    @org.junit.Test public void backupTypes() { section(Tests::backup); }
    @org.junit.Test public void backupKnowsEverySetting() { section(Tests::everyKey); }

    private static void section(Runnable checks) {
        TimeZone before = TimeZone.getDefault();
        passed = failed = 0;
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
            checks.run();
        } finally {
            TimeZone.setDefault(before);
        }
        System.out.println(passed + " passed, " + failed + " failed");
        org.junit.Assert.assertEquals("failed checks (see output)", 0, failed);
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

    private static void schedule() {
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
        // UNTIL in UTC: 01:30Z on the 28th is still the evening of the 27th in New York.
        Calendar evening = at(2026, 10, 1, 21, 30);
        check("UTC UNTIL keeps the local last day", Morning.occursOn(evening, "FREQ=DAILY;UNTIL=20261028T013000Z", tue));
        check("UTC UNTIL doesn't spill into the next day", !Morning.occursOn(evening, "FREQ=DAILY;UNTIL=20261028T013000Z", at(2026, 10, 28, 0, 0)));
        Calendar fourthTue = at(2026, 9, 22, 9, 0);   // the 4th Tuesday of September
        check("monthly on the 4th Tuesday", Morning.occursOn(fourthTue, "FREQ=MONTHLY;BYDAY=4TU", tue));
        check("monthly on the last Tuesday", Morning.occursOn(fourthTue, "FREQ=MONTHLY;BYDAY=-1TU", tue));
        check("not the 2nd Tuesday", !Morning.occursOn(fourthTue, "FREQ=MONTHLY;BYDAY=2TU", tue));
        check("monthly BYDAY with COUNT=1 is over", !Morning.occursOn(fourthTue, "FREQ=MONTHLY;BYDAY=4TU;COUNT=1", tue));
        check("monthly BYDAY with COUNT=2 reaches October", Morning.occursOn(fourthTue, "FREQ=MONTHLY;BYDAY=4TU;COUNT=2", tue));
    }

    private static void icsFile() {
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

    private static void backup() {
        eq("night hours are ints", "i", Backup.typeOf("night_start"));
        eq("theme is a string", "s", Backup.typeOf("theme"));
        eq("weather position is a float", "f", Backup.typeOf("w_lat"));
        eq("per-app volume", "i", Backup.typeOf("vol:com.netflix.ninja"));
        eq("game scores", "i", Backup.typeOf("best_2048"));
        eq("interface sound volume", "i", Backup.typeOf("ui_sounds_level"));
        eq("unknown keys aren't restored", null, Backup.typeOf("made_up"));
        eq("arrows in every app survives a restore", "b", Backup.typeOf("remote_full"));
    }

    /**
     * Every setting the app reads or writes must be either restored or skipped on purpose:
     * a key missing from Backup's list is wiped by a restore (as "Arrows in every app" once was).
     */
    private static void everyKey() {
        Pattern p = Pattern.compile("(?:get|put)(?:Boolean|Int|Long|Float|String|StringSet)\\(\"([a-zA-Z0-9_:]+)\"");
        TreeSet<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Paths.get("src"))) {
            for (Path f : (Iterable<Path>) files.filter(x -> x.toString().endsWith(".kt") || x.toString().endsWith(".java"))::iterator) {
                Matcher m = p.matcher(new String(Files.readAllBytes(f)));
                while (m.find()) keys.add(m.group(1));
            }
        } catch (IOException e) {
            check("read the sources: " + e, false);
            return;
        }
        keys.remove("image_id");   // a field of the paintings' JSON, not a setting
        check("found the settings in the sources", keys.size() > 30);
        for (String k : keys) check("backup handles setting " + k, Backup.handles(k));
    }
}
