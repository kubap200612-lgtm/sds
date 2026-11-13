package pl.rynekanaliza;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reguły autopilota bez Minecrafta i bez BK (łatwe do przetestowania). Wszystkie ceny są ZA SZTUKĘ;
 * do BK idzie cena całej wystawki = cena za sztukę x ilość sprzedaży.
 *
 * Sprzedaż (nastawienie: szybko schodzi):
 *  - cel = najtańsza CUDZA oferta minus 1$ (od ceny całej wystawki), ale nie niżej niż podłoga,
 *  - oferta dużo tańsza od następnej / od zwykłej ceny = pomyłka, pomijana,
 *  - tylko jedna oferta pod nami = czekamy (zejdzie, a my będziemy pierwsi); od dropMinOffers ofert = obniżka,
 *  - spadek o więcej niż bigDrop wymaga potwierdzenia w kolejnych skanach,
 *  - po spadku zapamiętujemy cenę sprzed spadku i wracamy do niej od razu, gdy rynek wróci,
 *  - wyżej niż zwykle: dopiero po raiseConfirm skanach i najwyżej o maxRaiseStep naraz,
 *  - podgląd okien BK (nie pełny skan) może tylko obniżać.
 * Skup: z ceny, za którą realnie sprzedasz: min(sprzedaż, zwykła cena) x (podatek - marża);
 *  w dół od razu, w górę powoli (potwierdzenie i najwyżej +buyMaxRaiseStep naraz), nie więcej niż ręczny max.
 */
public final class AutoPricing {
    private AutoPricing() {}

    public static final class Params {
        public double tax = 0.97;
        public double undercut = 1;          // o ile $ (od ceny całej wystawki) taniej od najtańszej cudzej oferty
        public double outlierRatio = 0.65;   // tańsza niż 65% następnej oferty / zwykłej ceny = pomyłka
        public int dropMinOffers = 2;        // ile cudzych ofert pod nami = prawdziwy spadek
        public double bigDrop = 0.20;        // większy spadek czeka na potwierdzenie
        public int bigDropConfirm = 2;       // w ilu skanach z rzędu
        public double deadBand = 0.015;      // mniejsze podwyżki pomijamy (bez ciągłego przestawiania)
        public int raiseConfirm = 2;
        public double maxRaiseStep = 0.10;
        public double histFloorFactor = 0.85;
        public int minHistory = 5;           // tyle analiz z 7 dni, żeby ufać "zwykłej cenie"
        public double minProfit = 0.03;      // minimalny zysk ponad koszt zakupu przy sprzedaży
        public double buyDeadBand = 0.01;
        public int buyRaiseConfirm = 2;
        public double buyMaxRaiseStep = 0.05;
    }

    /** Liczniki potwierdzeń między skanami (zapisywane w stanie przedmiotu). */
    public static final class SellState {
        public int raiseSeen, bigDropSeen;
        public double levelBeforeDrop;
        public long levelBeforeDropAt;
    }

    public static final class BuyState {
        public int raiseSeen;
    }

    public static final class Filtered {
        public final List<Double> kept = new ArrayList<>();
        public final List<Double> outliers = new ArrayList<>();
    }

    public static final class SellDecision {
        public double pp;          // nowa (albo dotychczasowa) cena za sztukę
        public boolean changed;
        public String reason = "";
        public double floor;
        public List<Double> outliers = List.of();
    }

    public static final class BuyDecision {
        public double pp;
        public boolean changed;
        public String reason = "";
    }

    /**
     * Odrzuca pomyłki z dołu listy: najtańsza oferta jest pomyłką, gdy kosztuje mniej niż ratio x następna oferta
     * albo mniej niż ratio x zwykła cena (usual > 0). Sprawdza kolejno, aż najtańsza wygląda normalnie.
     */
    public static Filtered filterOutliers(List<Double> perPiece, double usual, double ratio) {
        Filtered f = new Filtered();
        List<Double> s = new ArrayList<>();
        for (Double v : perPiece) if (v != null && v > 0 && !v.isNaN() && !v.isInfinite()) s.add(v);
        Collections.sort(s);
        int i = 0;
        while (i < s.size()) {
            double c = s.get(i);
            boolean vsNext = i + 1 < s.size() && c < ratio * s.get(i + 1);
            boolean vsUsual = usual > 0 && c < ratio * usual;
            if (!vsNext && !vsUsual) break;
            f.outliers.add(c);
            i++;
        }
        f.kept.addAll(s.subList(i, s.size()));
        return f;
    }

    /**
     * Podłoga ceny sprzedaży (za sztukę): najwyższa z - koszt zakupu / podatek x (1 + min. zysk), 85% zwykłej ceny
     * (gdy jest min. minHistory analiz), ręczne minimum. Gdy nic z tego nie jest znane - pierwsza analiza przedmiotu.
     */
    public static double floor(double costPP, double usual, int histCount, double manualMin, double firstAnalysis, Params p) {
        double f = 0;
        if (costPP > 0) f = Math.max(f, costPP / p.tax * (1 + p.minProfit));
        boolean hist = histCount >= p.minHistory && usual > 0;
        if (hist) f = Math.max(f, usual * p.histFloorFactor);
        if (manualMin > 0) f = Math.max(f, manualMin);
        if (f <= 0 && !hist && firstAnalysis > 0) f = firstAnalysis;
        return f;
    }

    /** Cena całej wystawki zaokrąglona w górę (żeby nie zejść poniżej podłogi). */
    static double ceilTotal(double pp, int count) {
        return Math.ceil(pp * count - 1e-6);
    }

    static double floorTotal(double pp, int count) {
        return Math.floor(pp * count + 1e-6);
    }

    /**
     * Decyzja o cenie sprzedaży.
     * @param othersPP   ceny za sztukę cudzych pasujących ofert (bez własnych)
     * @param fullScan   true = pełny skan posortowany od najtańszych; false = podgląd okien BK (wtedy tylko obniżki)
     * @param currentPP  obecna cena sprzedaży za sztukę (0 = brak)
     * @param count      ilość sprzedaży (sztuk w wystawce)
     * @param floorPP    podłoga za sztukę (0 = brak)
     * @param usual      zwykła cena (mediana analiz z 7 dni; 0 = nieznana) - do wykrywania pomyłek
     */
    public static SellDecision decideSell(List<Double> othersPP, boolean fullScan, double currentPP, int count, double floorPP,
                                          double usual, SellState st, Params p) {
        SellDecision d = new SellDecision();
        int c = Math.max(1, count);
        d.pp = currentPP;
        d.floor = floorPP;
        Filtered f = filterOutliers(othersPP, usual, p.outlierRatio);
        d.outliers = f.outliers;

        // podłoga wyżej niż obecna cena (np. drożej kupione) - od razu w górę
        if (currentPP > 0 && floorPP > 0 && currentPP < floorPP - 1e-9) {
            d.pp = ceilTotal(floorPP, c) / c;
            d.changed = true;
            d.reason = "podniesiona do podłogi " + money(d.pp * c);
            st.raiseSeen = 0;
            st.bigDropSeen = 0;
            return d;
        }
        if (f.kept.isEmpty()) {
            st.bigDropSeen = 0;
            if (fullScan) st.raiseSeen = 0;
            d.reason = f.outliers.isEmpty() ? (currentPP > 0 ? "brak cudzych ofert - bez zmian" : "brak danych") : "same pomyłki - bez zmian";
            return d;
        }
        double cheapest = f.kept.get(0);
        double target = (Math.ceil(cheapest * c - 1e-6) - p.undercut) / c;
        boolean atFloor = false;
        if (floorPP > 0 && target < floorPP - 1e-9) {
            target = ceilTotal(floorPP, c) / c;
            atFloor = true;
        }
        if (target <= 0) {
            d.reason = "cena wyszłaby <= 0 - bez zmian";
            return d;
        }
        if (currentPP <= 0) {
            d.pp = target;
            d.changed = true;
            d.reason = atFloor ? "start na podłodze " + money(target * c) : "start: najtańsza " + money(cheapest * c) + " - " + money(p.undercut);
            st.raiseSeen = 0;
            st.bigDropSeen = 0;
            return d;
        }

        if (target < currentPP - 1e-9) { // w dół
            st.raiseSeen = 0;
            int below = 0;
            for (double v : f.kept) if (v < currentPP - 1e-9) below++;
            if (below < Math.max(1, p.dropMinOffers)) {
                st.bigDropSeen = 0;
                d.reason = below == 0 ? "jesteś najtańszy - bez zmian" : below + " tańsza oferta - czekam, aż zejdzie";
                return d;
            }
            if (target < currentPP * (1 - p.bigDrop)) {
                st.bigDropSeen++;
                if (st.bigDropSeen < Math.max(1, p.bigDropConfirm)) {
                    d.reason = String.format(Locale.ROOT, "duży spadek (%.0f%%) - czekam na potwierdzenie %d/%d",
                            (1 - target / currentPP) * 100, st.bigDropSeen, Math.max(1, p.bigDropConfirm));
                    return d;
                }
            }
            st.bigDropSeen = 0;
            if (st.levelBeforeDrop <= 0) st.levelBeforeDrop = currentPP;
            d.pp = target;
            d.changed = true;
            d.reason = atFloor ? "w dół do podłogi " + money(target * c) + " (" + below + " ofert taniej)"
                    : "podcięty: " + below + " ofert taniej, najtańsza " + money(cheapest * c);
            return d;
        }
        st.bigDropSeen = 0;
        if (target > currentPP + 1e-9) { // w górę
            if (!fullScan) {
                d.reason = "bez zmian (podwyżki tylko po pełnym skanie)";
                return d;
            }
            if ((target - currentPP) / currentPP < p.deadBand) {
                st.raiseSeen = 0;
                d.reason = "różnica poniżej " + pct(p.deadBand) + " - bez zmian";
                return d;
            }
            if (st.levelBeforeDrop > currentPP + 1e-9) {
                double back = floorTotal(Math.min(target, st.levelBeforeDrop), c) / c;
                if (back > currentPP + 1e-9) {
                    d.pp = back;
                    d.changed = true;
                    d.reason = "powrót do ceny sprzed spadku";
                    if (back >= st.levelBeforeDrop - 1e-9) { st.levelBeforeDrop = 0; st.levelBeforeDropAt = 0; }
                    st.raiseSeen = 0;
                    return d;
                }
            }
            st.raiseSeen++;
            if (st.raiseSeen < Math.max(1, p.raiseConfirm)) {
                d.reason = "wzrost - czekam na potwierdzenie " + st.raiseSeen + "/" + Math.max(1, p.raiseConfirm);
                return d;
            }
            st.raiseSeen = 0;
            double up = floorTotal(Math.min(target, currentPP * (1 + p.maxRaiseStep)), c) / c;
            if (up <= currentPP + 1e-9) {
                d.reason = "bez zmian";
                return d;
            }
            d.pp = up;
            d.changed = true;
            d.reason = String.format(Locale.ROOT, "w górę o %.1f%% (najtańsza %s)", (up / currentPP - 1) * 100, money(cheapest * c));
            return d;
        }
        st.raiseSeen = 0;
        d.reason = "bez zmian";
        return d;
    }

    /**
     * Decyzja o cenie skupu (za sztukę).
     * @param sellPP    cena sprzedaży za sztukę, którą autopilot właśnie ustalił (0 = brak)
     * @param usual     zwykła cena (mediana analiz z 7 dni)
     * @param histCount liczba analiz z 7 dni
     * @param margin    marża jako ułamek ceny sprzedaży (np. 0.12)
     * @param manualMax ręczny maksymalny skup za sztukę (0 = brak)
     * @param currentPP obecny skup za sztukę (0 = brak)
     */
    public static BuyDecision decideBuy(double sellPP, double usual, int histCount, double margin, double manualMax,
                                        double currentPP, BuyState st, Params p) {
        BuyDecision d = new BuyDecision();
        d.pp = currentPP;
        if (histCount < p.minHistory || usual <= 0) {
            st.raiseSeen = 0;
            d.reason = "skup: za mało analiz (" + histCount + "/" + p.minHistory + ")";
            return d;
        }
        double basis = sellPP > 0 ? Math.min(sellPP, usual) : usual;
        double target = Math.floor(basis * (p.tax - margin) * 100 + 1e-6) / 100;
        if (manualMax > 0) target = Math.min(target, manualMax);
        if (target <= 0) {
            d.reason = "skup wyszedłby <= 0";
            return d;
        }
        if (currentPP <= 0) {
            d.pp = target;
            d.changed = true;
            d.reason = "skup start";
            st.raiseSeen = 0;
            return d;
        }
        if (target < currentPP * (1 - p.buyDeadBand)) {
            st.raiseSeen = 0;
            d.pp = target;
            d.changed = true;
            d.reason = "skup w dół";
            return d;
        }
        if (target > currentPP * (1 + p.buyDeadBand)) {
            st.raiseSeen++;
            if (st.raiseSeen < Math.max(1, p.buyRaiseConfirm)) {
                d.reason = "skup: wzrost - czekam " + st.raiseSeen + "/" + Math.max(1, p.buyRaiseConfirm);
                return d;
            }
            st.raiseSeen = 0;
            d.pp = Math.min(target, Math.floor(currentPP * (1 + p.buyMaxRaiseStep) * 100 + 1e-6) / 100);
            d.changed = d.pp > currentPP + 1e-9;
            d.reason = d.changed ? "skup w górę" : "skup bez zmian";
            return d;
        }
        st.raiseSeen = 0;
        d.reason = "skup bez zmian";
        return d;
    }

    /** Mediana (0 dla pustej listy). */
    public static double median(List<Double> v) {
        if (v.isEmpty()) return 0;
        List<Double> s = new ArrayList<>(v);
        Collections.sort(s);
        int n = s.size();
        return n % 2 == 1 ? s.get(n / 2) : (s.get(n / 2 - 1) + s.get(n / 2)) / 2;
    }

    static String money(double v) {
        return AnalizaScreen.fmtR(v) + "$";
    }

    static String pct(double f) {
        return String.format(Locale.ROOT, "%.1f%%", f * 100);
    }
}
