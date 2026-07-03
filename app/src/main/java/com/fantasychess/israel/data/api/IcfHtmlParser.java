package com.fantasychess.israel.data.api;

import com.fantasychess.israel.data.model.GameResult;
import com.fantasychess.israel.data.model.Gender;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.WeekGame;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fallback scraper for the classic chess.org.il pages.
 *
 * The classic site renders plain HTML tables (club roster, player card with a
 * games history table), so we parse defensively: find any table whose text
 * looks like the expected one, and skip rows we cannot understand.
 */
public final class IcfHtmlParser {

    private static final Pattern ISO_DATE = Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})");
    private static final Pattern IL_DATE = Pattern.compile("^(\\d{1,2})[./](\\d{1,2})[./](\\d{4})");

    private IcfHtmlParser() {
    }

    /** Parses a club page into a roster. Rows: name / player id / rating. */
    public static List<Player> parseClubRoster(String html) {
        Document doc = Jsoup.parse(html);
        List<Player> players = new ArrayList<>();
        for (List<String> cells : tableRows(doc, "שחקן", "מדד", "דירוג")) {
            String name = firstTextCell(cells);
            Integer id = null;
            Integer rating = null;
            for (String cell : cells) {
                Integer number = parseInt(cell);
                if (number == null) continue;
                if (number > 100 && id == null) id = number;
                if (number >= 1000 && number <= 2900) rating = number;
            }
            if (name == null || id == null || rating == null) continue;
            players.add(new Player(id, name.trim(), Gender.BOY, rating, null, null,
                    IcfApiConfig.CLUB_NAME));
        }
        return players;
    }

    /**
     * Parses the games table of a player-card page (כרטיס שחקן) and keeps only
     * games inside the [fromIsoDate, toIsoDate] window.
     */
    public static List<WeekGame> parsePlayerGames(String html, int playerId,
                                                  String fromIsoDate, String toIsoDate) {
        Document doc = Jsoup.parse(html);
        List<WeekGame> games = new ArrayList<>();
        for (List<String> cells : tableRows(doc, "יריב", "תוצאה", "תאריך")) {
            String date = null;
            GameResult result = null;
            Integer opponentRating = null;
            String opponentName = null;
            for (String cell : cells) {
                if (date == null) date = parseIsoDate(cell);
                if (result == null) result = parseResult(cell);
                Integer number = parseInt(cell);
                if (opponentRating == null && number != null && number >= 1000 && number <= 2900) {
                    opponentRating = number;
                }
                if (opponentName == null && hasLetter(cell) && !cell.contains(":")
                        && parseResult(cell) == null) {
                    opponentName = cell.trim();
                }
            }
            if (date == null || result == null || opponentRating == null || opponentName == null) {
                continue;
            }
            if (date.compareTo(fromIsoDate) < 0 || date.compareTo(toIsoDate) > 0) continue;
            games.add(new WeekGame(playerId, date, opponentName, "", opponentRating,
                    true, result, "ליגה"));
        }
        return games;
    }

    /** Returns cell texts of every row in the first table matching any hint. */
    private static List<List<String>> tableRows(Document doc, String... hints) {
        List<List<String>> rows = new ArrayList<>();
        Element match = null;
        for (Element table : doc.select("table")) {
            String text = table.text();
            for (String hint : hints) {
                if (text.contains(hint)) {
                    match = table;
                    break;
                }
            }
            if (match != null) break;
        }
        if (match == null) return rows;
        for (Element tr : match.select("tr")) {
            List<String> cells = new ArrayList<>();
            for (Element td : tr.select("td")) {
                cells.add(td.text());
            }
            if (!cells.isEmpty()) rows.add(cells);
        }
        return rows;
    }

    /** Accepts {@code dd/MM/yyyy}, {@code dd.MM.yyyy} or ISO and returns ISO. */
    public static String parseIsoDate(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        Matcher iso = ISO_DATE.matcher(trimmed);
        if (iso.find()) return iso.group();
        Matcher il = IL_DATE.matcher(trimmed);
        if (il.find()) {
            return String.format(Locale.ROOT, "%s-%02d-%02d",
                    il.group(3), Integer.parseInt(il.group(2)), Integer.parseInt(il.group(1)));
        }
        return null;
    }

    /** Accepts {@code 1-0}, {@code 0-1}, {@code 1:0}, {@code ½-½}, Hebrew words. */
    public static GameResult parseResult(String raw) {
        if (raw == null) return null;
        String r = raw.trim();
        if (r.startsWith("1-0") || r.startsWith("1:0") || r.equals("1") || r.contains("נצח")) {
            return GameResult.WIN;
        }
        if (r.startsWith("0-1") || r.startsWith("0:1") || r.equals("0") || r.contains("הפסד")) {
            return GameResult.LOSS;
        }
        if (r.contains("½") || r.contains("0.5") || r.contains("תיקו")) {
            return GameResult.DRAW;
        }
        return null;
    }

    public static Gender guessGender(String raw) {
        if (raw == null) return Gender.BOY;
        String r = raw.trim().toLowerCase(Locale.ROOT);
        return (r.startsWith("f") || r.contains("נ")) ? Gender.GIRL : Gender.BOY;
    }

    private static Integer parseInt(String cell) {
        try {
            return Integer.parseInt(cell.replace(",", "").trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) return true;
        }
        return false;
    }

    private static String firstTextCell(List<String> cells) {
        for (String cell : cells) {
            if (hasLetter(cell)) return cell;
        }
        return null;
    }
}
