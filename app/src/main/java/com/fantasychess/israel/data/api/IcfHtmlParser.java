package com.fantasychess.israel.data.api;

import com.fantasychess.israel.data.model.GameResult;
import com.fantasychess.israel.data.model.Gender;
import com.fantasychess.israel.data.model.Player;
import com.fantasychess.israel.data.model.WeekGame;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

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

    /** Parses a player-card page into a Player object. */
    public static Player parsePlayerDetails(String html, int playerId) {
        Document doc = Jsoup.parse(html);
        String text = doc.text();
        
        // Find name: usually inside an <h1> or a specific label
        String name = "שחקן " + playerId;
        Element h1 = doc.selectFirst("h1");
        if (h1 != null) name = h1.text().trim();

        Integer rating = null;
        Integer fide = null;
        Integer fideId = null;
        String club = "";
        String title = null;

        // Scrape for labels
        for (Element row : doc.select("tr")) {
            String rowText = row.text();
            Elements tds = row.select("td");
            if (tds.isEmpty()) continue;
            String val = tds.last().text();
            if (rowText.contains("מדד ישראלי")) {
                rating = parseInt(val);
            } else if (rowText.contains("מדד בינלאומי") || rowText.contains("FIDE")) {
                if (rowText.contains("ID")) {
                    fideId = parseInt(val);
                } else {
                    fide = parseInt(val);
                }
            } else if (rowText.contains("מועדון")) {
                club = val.trim();
            } else if (rowText.contains("תואר")) {
                title = val.trim();
            }
        }

        if (rating == null) {
            // fallback: try finding a 4-digit number in the text
            Matcher m = Pattern.compile("(\\d{4})").matcher(text);
            if (m.find()) {
                String g = m.group(1);
                if (g != null) rating = Integer.parseInt(g);
            }
            if (rating == null) rating = 1200;
        }

        return new Player(playerId, name, guessGender(name), rating, fide, fideId, title, club, null, null);
    }

    /** Parses a club page into a roster. Rows: name / player id / rating. */
    public static List<Player> parseClubRoster(String html) {
        Document doc = Jsoup.parse(html);
        List<Player> players = new ArrayList<>();
        
        // Find the player table
        Element table = null;
        for (Element t : doc.select("table")) {
            String text = t.text();
            if (text.contains("שם") && (text.contains("מדד") || text.contains("דירוג"))) {
                table = t;
                break;
            }
        }
        if (table == null) return players;

        for (Element tr : table.select("tr")) {
            Elements tds = tr.select("td");
            if (tds.isEmpty()) continue;

            String name = null;
            Integer id = null;
            Integer rating = null;
            String club = "";

            // Try to find the ID in a link first (best way)
            for (Element a : tds.select("a")) {
                String href = a.attr("href");
                if (href.contains("Id=")) {
                    try {
                        String idStr = href.substring(href.indexOf("Id=") + 3);
                        if (idStr.contains("&")) idStr = idStr.substring(0, idStr.indexOf('&'));
                        id = Integer.parseInt(idStr.trim());
                        name = a.text().trim();
                    } catch (Exception ignored) {}
                }
            }

            for (Element td : tds) {
                String cell = td.text().trim();
                Integer number = parseInt(cell);
                if (number != null && number >= 1000 && number <= 2900) {
                    rating = number;
                } else if (name == null && hasLetter(cell) && cell.length() > 2) {
                    name = cell;
                } else if (hasLetter(cell) && !cell.equals(name)) {
                    club = cell;
                }
            }

            if (name == null || id == null || rating == null) continue;
            players.add(new Player(id, name, Gender.BOY, rating, null, null,
                    null, club.isEmpty() ? IcfApiConfig.CLUB_NAME : club, null, null));
        }
        return players;
    }

    /**
     * Parses the games or tournaments table of a player-card page.
     * Includes past games/tournaments and upcoming ones.
     */
    public static List<WeekGame> parsePlayerGames(String html, int playerId) {
        Document doc = Jsoup.parse(html);
        List<WeekGame> games = new ArrayList<>();
        
        // 1. Try finding individual games table first
        List<List<String>> gameRows = tableRows(doc, "יריב", "תוצאה", "תאריך");
        if (!gameRows.isEmpty()) {
            for (List<String> cells : gameRows) {
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
                            && parseResult(cell) == null && !cell.contains("/") && !cell.contains(".")) {
                        opponentName = cell.trim();
                    }
                }
                if (date != null && opponentName != null) {
                    if (result == null) result = GameResult.UPCOMING;
                    games.add(new WeekGame(playerId, date, opponentName, "",
                            opponentRating == null ? 0 : opponentRating,
                            true, result, "ליגה"));
                }
            }
        }

        // 2. If no individual games, or in addition, look for the Tournament Participation table
        // This is where "next games" (upcoming tournaments) usually appear.
        List<List<String>> tournamentRows = tableRows(doc, "תחרות", "משחקים", "תוצאה");
        for (List<String> cells : tournamentRows) {
            String date = null;
            String tournamentName = null;
            String resultSummary = null;
            for (String cell : cells) {
                if (date == null) date = parseIsoDate(cell);
                if (tournamentName == null && hasLetter(cell) && !cell.contains(":") && !cell.contains("/") && !cell.contains("+")) {
                    tournamentName = cell.trim();
                }
                if (cell.contains("+") && cell.contains("-")) {
                    resultSummary = cell.trim();
                }
            }
            if (date != null && tournamentName != null) {
                // If it's already in games (by date and name), skip
                boolean exists = false;
                for (WeekGame g : games) {
                    if (g.dateIso.equals(date) && g.competition.equals(tournamentName)) {
                        exists = true;
                        break;
                    }
                }
                if (exists) continue;

                GameResult result = resultSummary != null ? parseResult(resultSummary) : GameResult.UPCOMING;
                // Use the tournament summary as the "opponent name" if no individual games found
                games.add(new WeekGame(playerId, date, tournamentName, "", 0,
                        true, result, resultSummary != null ? "Summary: " + resultSummary : "אירוע"));
            }
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

    /** Accepts {@code 1-0}, {@code 0-1}, {@code 1:0}, {@code ½-½}, Hebrew words, and summaries like {@code +6-1=0}. */
    public static GameResult parseResult(String raw) {
        if (raw == null) return null;
        String r = raw.trim();
        
        // Handle summary format +W-L=D (e.g. +6-1=0)
        if (r.startsWith("+") && r.contains("-")) {
            int plus = r.indexOf('+');
            int minus = r.indexOf('-');
            int eq = r.indexOf('=');
            try {
                int wins = Integer.parseInt(r.substring(plus + 1, minus));
                int losses = Integer.parseInt(r.substring(minus + 1, eq != -1 ? eq : r.length()));
                if (wins > losses) return GameResult.WIN;
                if (losses > wins) return GameResult.LOSS;
                return GameResult.DRAW;
            } catch (Exception e) {
                // fall through
            }
        }

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
