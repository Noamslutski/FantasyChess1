# פנטזי שחמט ישראל · Fantasy Chess Israel ♟️

A Sorare-inspired **fantasy game for Israeli chess players**, built around the
**Hapoel Petah Tikva chess club (מועדון שחמט הפועל פתח תקווה)** and the player
database of the Israeli Chess Federation website
([chess.org.il — אתר איגוד השחמט הישראלי](https://chess.org.il)).

Collect player cards, build a squad of 5, and earn fantasy points from the
real games your players play over the board every week.

![App preview](docs/screenshots/app-mockup.png)

## Features

- **Card packs** — you can only field a player whose card you own. You get
  **3 free packs when you install the app** and **1 free pack every
  game-week**. Each pack contains 3 random cards from the club roster.
- **Scarcity tiers** (Sorare-style): Limited · Rare · Super Rare · Unique,
  with pack odds of 70% / 20% / 8% / 2% and score bonuses of
  +0% / +5% / +10% / +20%.
- **Squad of 5** — a 2-1-2 formation with one **captain** who scores x1.5.
- **1–100 card rating** derived from the player's **Israeli national rating
  (מדד)**: a linear map where 1000 → 1 and 2800 → 100.
- **Weekly games** — every player page shows the games they played during the
  current game-week (Sunday–Saturday, the Israeli week) with the fantasy
  points earned per game.
- **Avatars** — one shared picture for every boy and one for every girl,
  exactly as in the classic collectible-sticker style.
- **Hebrew-first UI** with full RTL support (English translation included).

## Fantasy scoring

| Event | Points |
|---|---|
| Win | 60 |
| Draw | 30 |
| Loss (participation) | 10 |
| Opponent-strength adjustment (win) | ±(rating diff × 0.05), capped −15…+25 |
| Opponent-strength adjustment (draw) | ±(rating diff × 0.03), capped −10…+15 |
| Upset bonus (beat someone rated 150+ above you) | +20 |
| Unbeaten week (2+ games, no losses) | +10 |
| Captain | ×1.5 |
| Rarity bonus | Limited +0% · Rare +5% · Super Rare +10% · Unique +20% |

## Building & running

The app is written in **pure Java** with classic Android Views (no Kotlin).

1. Open the project in **Android Studio** (Hedgehog or newer).
2. Let Gradle sync (AGP 8.5.2, Gradle 8.7 wrapper, `compileSdk 34`,
   `minSdk 26`).
3. Run on any device/emulator with Android 8.0+.

Command line: `./gradlew assembleDebug`
Unit tests: `./gradlew test` (scoring + rating-mapper tests).

## Israeli Chess Federation (chess.org.il) integration

All federation access lives in `app/src/main/java/.../data/api/`:

| File | Role |
|---|---|
| `IcfApiConfig.java` | **Every endpoint/URL in one place** — base URLs, the club id, player-card URLs |
| `IcfApiClient.java` | OkHttp client: tries JSON endpoints first, then falls back to HTML |
| `IcfHtmlParser.java` | Jsoup parser for the classic player-card (כרטיס שחקן) and club pages |

The app loads data in this order, so it **always runs smoothly**:

1. **Live JSON API** on chess.org.il;
2. **HTML scraping** of the classic federation pages;
3. **Bundled sample roster** (`app/src/main/assets/hapoel_pt_players.json`) —
   used offline or whenever the site is unreachable. The home screen shows an
   honest "demo mode" banner whenever sample data is displayed, and a tap on
   it retries the live API.

> **Important — verify the endpoints once:** the federation does not publish
> official API documentation, and this project was developed in a sandbox
> whose network policy blocks chess.org.il, so the exact endpoint paths in
> `IcfApiConfig.java` could not be verified against the live site and are
> best-effort. Verifying takes two minutes: open chess.org.il in Chrome,
> press **F12 → Network**, open a club/player page, and copy the request URL
> the site itself uses into `IcfApiConfig.java`. Also set `CLUB_ID` to Hapoel
> Petah Tikva's real id from the club page URL. Nothing else in the app needs
> to change.
>
> For the same reason, the bundled sample roster uses **placeholder names**,
> not the real registered members of the club; live federation data replaces
> it automatically once the endpoints respond.

## Architecture

```
app/src/main/java/com/fantasychess/israel/
├── data/
│   ├── api/        IcfApiConfig · IcfApiClient · IcfHtmlParser
│   ├── local/      LocalStore (SharedPreferences + Gson persistence)
│   ├── model/      Player · WeekGame · OwnedCard · Squad · Rarity · …
│   ├── repo/       FantasyRepository (single source of truth, LiveData)
│   └── sample/     SampleDataSource · SampleWeekGenerator (Elo-based demo games)
├── domain/         RatingMapper · FantasyScoring · PackGenerator · GameWeek
└── ui/
    ├── MainViewModel
    ├── PlayerCardBinder (one card layout reused everywhere)
    ├── adapters/   CardGridAdapter · LeadersAdapter
    └── fragments/  Home · Squad · Packs · Collection · PlayerDetail
```

- **MVVM**: `FantasyRepository` owns an immutable `State` snapshot published
  through LiveData; all mutations run on a single background executor, so the
  UI thread never blocks (smooth scrolling, no jank).
- Networking never throws into the UI — every failure falls back one level.
- Persistence is plain SharedPreferences + Gson (no annotation processors),
  which keeps builds fast and dependency-light.

---

### עברית — בקצרה

משחק פנטזי לשחקני השחמט של **הפועל פתח תקווה**: פותחים חבילות קלפים
(3 חבילות מתנה בהתחלה וחבילה חינם בכל מחזור), מרכיבים סגל של 5 שחקנים עם
קפטן, וצוברים נקודות לפי המשחקים האמיתיים של השחקנים במהלך השבוע. דירוג כל
קלף (1–100) נגזר מהמדד הלאומי באתר איגוד השחמט הישראלי, ובעמוד כל שחקן
מוצגים משחקי השבוע שלו. אם אתר האיגוד אינו זמין, האפליקציה עוברת אוטומטית
למצב הדגמה עם נתוני דוגמה — כך שהיא תמיד עובדת חלק.
