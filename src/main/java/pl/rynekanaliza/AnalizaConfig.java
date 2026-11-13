package pl.rynekanaliza;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ustawienia w config/rynek-analiza.json (własne frazy wyszukiwania, ostatnie wyniki analizy, opcje).
 * Pola ze starszych wersji (marże: "margins", "defaultMargin") są przy wczytywaniu pomijane.
 */
public final class AnalizaConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Klucz = "nazwa bez kolorów|material" (małe litery; przy powtórzonych nazwach z dopiskiem), wartość = fraza po "/ah search ". */
    public Map<String, String> search = new LinkedHashMap<>();
    /** Ostatni pomiar z analizy każdego przedmiotu (klucz jak wyżej). */
    public Map<String, ScanRecord> lastScan = new LinkedHashMap<>();
    /** Wykresy: oprócz skanów z analizy pokazuj też zaufane pomiary z obserwacji rynku, gdy działa BK. */
    public boolean chartObserved = false;
    /** Podatek od sprzedaży (mnożnik) - tylko do pokazania zysku na sztuce w panelu. */
    public double taxFactor = 0.97;
    public int listingsUsed = 3;
    /** Minimalna liczba pasujących ofert, żeby policzyć cenę (1-3). */
    public int minListings = 3;
    public int maxPages = 20;
    /** Szybkość analizy: 0 = bezpieczna, 1 = szybka (domyślnie), 2 = maksymalna. */
    public int speed = 1;
    /** Wymagaj tej samej liczby komponentów co we wzorcu z BK (BK sam tego nie sprawdza; włącz tylko, gdy coś się myli). */
    public boolean strictComponents = false;
    /** Zapisuj historię cen (z analiz i z obserwacji rynku) do wykresów. */
    public boolean historyEnabled = true;
    /** Obserwuj okna rynku otwierane przez BK (bez klikania) i zapisuj z nich ceny. */
    public boolean passiveObserve = true;
    /** Minimalny odstęp między zapisami z obserwacji dla jednego przedmiotu (sekundy). */
    public int historyIntervalSec = 60;
    /** Czy po analizie włączyć z powrotem BK-Rynek, jeśli działał przed startem. */
    public boolean resumeBk = true;
    /** Żółte podświetlenie najtańszych ofert + pasek z nazwą i ikoną nad rynkiem. */
    public boolean showOverlay = true;
    /** Znaczek "Rynek Analiza: działa" obok okna rynku (i na ekranie gry, gdy pracuje BK). */
    public boolean showBadge = true;
    /** Ile ticków (20 = 1 s) trzymać stronę z podświetlonymi ofertami, żeby dało się je obejrzeć. 0 = bez pauzy. */
    public int highlightPauseTicks = 10;

    // ----- autopilot cen (patrz Autopilot) -----
    /** Autopilot włączony (sam ustala ceny sprzedaży, skupu i marżę). */
    public boolean autopilot = false;
    /** Tryb próbny: wszystko liczy, ale nic nie wpisuje do BK. */
    public boolean apTrial = true;
    /** Co ile minut skan autopilota (BK zatrzymuje się na chwilę). */
    public int apIntervalMin = 12;
    /** Ile przedmiotów sprawdza jeden skan autopilota. */
    public int apItemsPerCycle = 6;
    /** Ile autopilot może wydać na zakupy w ciągu dnia ($). */
    public double apBudget = 25000;
    /** Ile niesprzedanych zakupów jednego przedmiotu, zanim autopilot wstrzyma jego skup. */
    public int apMaxStock = 3;
    /** Minimalny zysk ponad koszt zakupu przy sprzedaży (%). */
    public double apMinProfitPct = 3;
    /** Marża: startowa, minimalna i maksymalna (% ceny sprzedaży). */
    public double apStartMarginPct = 12;
    public double apMinMarginPct = 3;
    public double apMaxMarginPct = 25;
    /** Przedmioty od tej ceny za sztukę dostają automatyczny skup dopiero, gdy autopilot zarabia na tańszych. */
    public double apExpensive = 10000;
    /** Ile cudzych ofert pod naszą ceną oznacza prawdziwy spadek (jedna tańsza oferta = czekamy). */
    public int apDropOffers = 2;

    /** Pomiar z analizy: średnia 3 najtańszych (za sztukę), najtańsza oferta, liczba ofert, czas. */
    public static final class ScanRecord {
        public double avg3;
        public double cheapest;
        public int offers;
        public long t;

        public ScanRecord() {}

        public ScanRecord(double avg3, double cheapest, int offers, long t) {
            this.avg3 = avg3;
            this.cheapest = cheapest;
            this.offers = offers;
            this.t = t;
        }
    }

    private static Path file() {
        return FabricLoader.getInstance().getConfigDir().resolve("rynek-analiza.json");
    }

    public static AnalizaConfig load() {
        try {
            Path f = file();
            if (Files.exists(f)) {
                AnalizaConfig c = GSON.fromJson(Files.readString(f, StandardCharsets.UTF_8), AnalizaConfig.class);
                if (c != null) {
                    if (c.search == null) c.search = new LinkedHashMap<>();
                    if (c.lastScan == null) c.lastScan = new LinkedHashMap<>();
                    return c;
                }
            }
        } catch (Exception e) {
            System.err.println("[rynek-analiza] nie udało się wczytać configu: " + e);
        }
        AnalizaConfig c = new AnalizaConfig();
        c.save();
        return c;
    }

    public void save() {
        try {
            Files.writeString(file(), GSON.toJson(this), StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[rynek-analiza] nie udało się zapisać configu: " + e);
        }
    }

    /** Przenosi ustawienia do silnika analizy. */
    public void applyToAnalyzer() {
        MarketAnalyzer.TAX_FACTOR = taxFactor;
        MarketAnalyzer.LISTINGS_USED = Math.max(1, listingsUsed);
        MarketAnalyzer.MIN_LISTINGS = Math.max(1, Math.min(Math.max(1, listingsUsed), minListings));
        MarketAnalyzer.MAX_PAGES = Math.max(1, maxPages);
        switch (speed) {
            case 0 -> { MarketAnalyzer.MIN_CLICK_GAP_TICKS = 4; MarketAnalyzer.STABLE_TICKS = 3; MarketAnalyzer.BETWEEN_TARGETS_TICKS = 20; }
            case 2 -> { MarketAnalyzer.MIN_CLICK_GAP_TICKS = 1; MarketAnalyzer.STABLE_TICKS = 1; MarketAnalyzer.BETWEEN_TARGETS_TICKS = 2; }
            default -> { MarketAnalyzer.MIN_CLICK_GAP_TICKS = 2; MarketAnalyzer.STABLE_TICKS = 2; MarketAnalyzer.BETWEEN_TARGETS_TICKS = 6; }
        }
        MarketAnalyzer.HIGHLIGHT_PAUSE_TICKS = Math.max(0, highlightPauseTicks);
        PriceHistory.ENABLED = historyEnabled;
        PriceHistory.PASSIVE_INTERVAL_SEC = Math.max(5, historyIntervalSec);
        AnalysisOverlay.enabled = showOverlay;
    }
}
