package pl.rynekanaliza;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Analiza rynku dla BK-Rynek.
 *   Klawisz "otwórz panel" (domyślnie prawy Shift) - panel z listą przedmiotów, cenami, wykresami i ustawieniami
 *   Klawisz "start/stop" (domyślnie F8)            - start/stop analizy wszystkich przedmiotów
 *   Oba klawisze zmienisz w panelu (zakładka Ustawienia) albo w Sterowaniu Minecrafta.
 *
 * Analiza wpisuje do BK CENĘ SPRZEDAŻY (średnia 3 najtańszych ofert za sztukę x ilość sprzedaży z BK).
 * Ceny skupu ustawia gracz (w BK albo w polu "Skup" w panelu) - chyba że włączy autopilota (Autopilot), który
 * sam ustala sprzedaż (pod najtańszą ofertę), skup i marżę.
 *
 * Komendy (opcjonalnie):
 *   /analiza start [fragment nazwy]   /analiza stop   /analiza lista
 */
public class RynekAnalizaMod implements ClientModInitializer {
    private static AnalizaConfig cfg;
    private static PriceHistory history;
    private static Autopilot autopilot;
    private static boolean bkWasRunning;
    private static int apTicker;
    private static long nextCycleAt;
    private static boolean warnedAutopilot;

    public static KeyBinding openKey;
    public static KeyBinding toggleKey;

    public static AnalizaConfig config() { return cfg; }

    public static PriceHistory history() { return history; }

    public static Autopilot autopilot() { return autopilot; }

    @Override
    public void onInitializeClient() {
        cfg = AnalizaConfig.load();
        cfg.applyToAnalyzer();
        history = PriceHistory.load();
        autopilot = Autopilot.load(cfg);
        MarketAnalyzer.quietSink = line -> autopilot.scanLine(line);
        MarketAnalyzer.init();
        ClientTickEvents.END_CLIENT_TICK.register(MarketObserver::tick);
        ClientTickEvents.END_CLIENT_TICK.register(RynekAnalizaMod::autopilotTick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
            history.saveBlocking();
            autopilot.save();
        });

        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof HandledScreen<?>) {
                ScreenEvents.afterRender(screen).register((scr, ctx, mouseX, mouseY, tickDelta) -> {
                    AnalysisOverlay.render(scr, ctx);
                    StatusBadge.renderScreen(scr, ctx); // znaczek "Rynek Analiza: działa" obok okna rynku
                });
            }
        });
        // ten sam znaczek na ekranie gry, gdy BK pracuje, a okno rynku jest zamknięte
        HudRenderCallback.EVENT.register((ctx, tickCounter) -> StatusBadge.renderHud(ctx));

        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rynek_analiza.open_gui", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.rynek_analiza"));
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.rynek_analiza.start", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_F8, "category.rynek_analiza"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleKey.wasPressed()) toggleAnalysis();
            while (openKey.wasPressed()) openGui(client);
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) ->
                dispatcher.register(ClientCommandManager.literal("analiza")
                        .then(ClientCommandManager.literal("start")
                                .executes(ctx -> { startAnalysis(""); return 1; })
                                .then(ClientCommandManager.argument("fragment", StringArgumentType.greedyString())
                                        .executes(ctx -> {
                                            startAnalysis(StringArgumentType.getString(ctx, "fragment"));
                                            return 1;
                                        })))
                        .then(ClientCommandManager.literal("stop")
                                .executes(ctx -> { MarketAnalyzer.stop(); return 1; }))
                        .then(ClientCommandManager.literal("lista")
                                .executes(ctx -> { printList(ctx.getSource()); return 1; }))));
    }

    // ---------- panel i start/stop ----------
    public static void openGui(MinecraftClient client) {
        if (client.currentScreen != null) return;
        if (MarketAnalyzer.isRunning()) {
            MarketAnalyzer.log.accept(MarketAnalyzer.isCycle()
                    ? "Trwa skan autopilota (zwykle kilkanaście sekund) - panel otworzysz po nim albo przerwij go klawiszem start/stop."
                    : "Analiza trwa - zatrzymaj ją klawiszem start/stop, żeby otworzyć panel.");
            return;
        }
        if (!BkBridge.available()) {
            MarketAnalyzer.log.accept("Nie widzę BK-Rynek 3.0 (" + BkBridge.error() + ").");
            return;
        }
        client.setScreen(new AnalizaScreen(cfg));
    }

    public static void toggleAnalysis() {
        if (MarketAnalyzer.isRunning()) MarketAnalyzer.stop();
        else startAnalysis("");
    }

    public static void startAnalysis(String filter) {
        if (MarketAnalyzer.isRunning()) {
            MarketAnalyzer.log.accept("Analiza już trwa (klawisz start/stop albo /analiza stop, żeby przerwać).");
            return;
        }
        if (!BkBridge.available()) {
            MarketAnalyzer.log.accept("Nie widzę BK-Rynek 3.0 (" + BkBridge.error() + ").");
            return;
        }
        try {
            List<BkBridge.BkTarget> targets = BkBridge.targets(filter, cfg);
            if (targets.isEmpty()) {
                MarketAnalyzer.log.accept("Brak przedmiotów na liście BK" + (filter.isBlank() ? "." : " pasujących do: " + filter));
                return;
            }
            if (!BkBridge.usesBkMatcher()) {
                MarketAnalyzer.log.accept("Uwaga: używam własnego dopasowania (nie BK): " + BkBridge.error());
            }
            startTargets(targets, false);
        } catch (Throwable t) {
            MarketAnalyzer.log.accept("Błąd startu analizy: " + t);
        }
    }

    /** Analiza podanych przedmiotów; cycle = skan autopilota (cichy, bez wpisywania średniej 3, BK zawsze wznawiany). */
    static void startTargets(List<BkBridge.BkTarget> targets, boolean cycle) {
        cfg.applyToAnalyzer();
        MarketAnalyzer.PRICE_REGEX = BkBridge.priceRegex();

        bkWasRunning = BkBridge.isRunning();
        if (bkWasRunning) BkBridge.stopBk();

        MarketAnalyzer.cycle = cycle;
        if (cycle) autopilot.beginCycle();
        MarketAnalyzer.onFinished = () -> {
            MarketAnalyzer.cycle = false;
            cfg.save();        // ostatnie pomiary
            history.save();    // historia cen
            if (cycle) autopilot.endCycle();
            else autopilot.save();
            BkBridge.commit(); // ceny do BK
            if (bkWasRunning && (cycle || cfg.resumeBk)) BkBridge.startBk();
        };
        if (!MarketAnalyzer.start(targets)) {
            MarketAnalyzer.cycle = false;
            MarketAnalyzer.log.accept("Nie udało się wystartować analizy.");
            if (bkWasRunning && (cycle || cfg.resumeBk)) BkBridge.startBk();
        }
    }

    // ---------- autopilot: zakupy/sprzedaże z BK, nauka, skany co kilka minut ----------
    private static void autopilotTick(MinecraftClient mc) {
        if (cfg == null || autopilot == null || !cfg.autopilot) return;
        if (++apTicker < 20) return; // raz na sekundę
        apTicker = 0;
        long now = System.currentTimeMillis();
        try {
            if (mc.player == null || mc.world == null || !BkBridge.available()) return;
            List<BkBridge.BkTarget> ts = MarketObserver.targets(cfg);
            for (BkBridge.Purchase p : BkBridge.pollPurchases()) autopilot.onPurchase(ts, p.stack, p.total, p.count, now);
            double[] sc = BkBridge.salesCounters();
            if (sc != null) autopilot.onSalesCounters(ts, (int) sc[0], sc[1], now);
            autopilot.tick(ts, now);
            autopilot.flush();
            maybeStartCycle(mc, now);
        } catch (Throwable t) {
            if (!warnedAutopilot) {
                warnedAutopilot = true;
                MarketAnalyzer.log.accept("Autopilot: błąd (" + t + ") - sprawdź log gry.");
            }
            t.printStackTrace();
        }
    }

    /**
     * Skan autopilota co apIntervalMin minut: tylko gdy BK pracuje (wtedy i tak stoi na rynku) i gracz nie ma otwartego
     * innego okna (np. ekwipunku albo tego panelu). BK jest zatrzymywany na czas skanu i wznawiany po nim.
     */
    static void maybeStartCycle(MinecraftClient mc, long now) throws Exception {
        if (MarketAnalyzer.isRunning() || now < nextCycleAt) return;
        long interval = Math.max(2, cfg.apIntervalMin) * 60_000L;
        if (!BkBridge.isRunning()) { nextCycleAt = now + 60_000L; return; }
        if (mc.currentScreen != null && !(mc.currentScreen instanceof HandledScreen<?> hs && MarketAnalyzer.isMarket(hs))) {
            nextCycleAt = now + 30_000L;
            return;
        }
        List<BkBridge.BkTarget> pick = autopilot.pickTargets(BkBridge.targets("", cfg), now, Math.max(1, cfg.apItemsPerCycle));
        nextCycleAt = now + interval;
        if (!pick.isEmpty()) startTargets(pick, true);
    }

    /** Za ile ms następny skan autopilota (0 = przy najbliższej okazji). */
    public static long msToNextCycle() {
        return Math.max(0, nextCycleAt - System.currentTimeMillis());
    }

    private static void printList(FabricClientCommandSource src) {
        try {
            if (!BkBridge.available()) { src.sendFeedback(Text.literal("[Analiza] Brak BK-Rynek: " + BkBridge.error())); return; }
            for (String line : BkBridge.describe(cfg)) src.sendFeedback(Text.literal("[Analiza] " + line));
        } catch (Throwable t) {
            src.sendFeedback(Text.literal("[Analiza] Błąd: " + t));
        }
    }
}
