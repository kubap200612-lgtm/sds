# Rynek Analiza - dodatek do BK-Rynek 3.0 (Minecraft 1.21.4, Fabric)

Osobny mod, który analizuje rynek i wpisuje **ceny sprzedaży** prosto w listę przedmiotów BK-Rynek:
`sprzedaż = średnia 3 najtańszych ofert za sztukę x ilość sprzedaży z BK`. **Ceny skupu ustalasz sam** - mod ich nie liczy
i nie zmienia (chyba że sam wpiszesz skup w polu w panelu).

Nowość: **autopilot** (domyślnie wyłączony) - sam pilnuje ceny sprzedaży (tuż pod najtańszą cudzą ofertą), z niej liczy skup
i sam dobiera marżę. Po włączeniu najpierw działa w **trybie próbnym**: wszystko liczy, ale niczego nie wpisuje do BK. Opis niżej.

Nowość w 0.2.1: **znaczek "Rynek Analiza: działa"** obok okna rynku - zielona kropka miga przy każdym oknie, które mod odczytał,
gdy skanuje BK (opis w "Podgląd na rynku").

Wszystko było kompilowane i testowane tylko na atrapach API Minecrafta i na symulowanym rynku (prawdziwy build zrobi dopiero
GitHub Actions); mod w tej wersji nie był uruchomiony w grze.

## Jak zbudować
1. Repo z plikami budowania (`build.gradle`, `gradle.properties`, `settings.gradle`, `.github/workflows/build.yml`)
   i folderem `src/` z tej paczki. Build leci w zakładce Actions, jar jest w artefakcie "rynek-analiza".
2. Jar wrzuć do `mods/` razem z BK-Rynek (stary jar tego moda usuń).

## Panel (GUI)
- Klawisz **prawy Shift** (domyślnie) otwiera/zamyka panel. Klawisz **F8** (domyślnie) = start/stop analizy.
  Oba klawisze zmienisz w panelu, w zakładce **Ustawienia** (kliknij przycisk i naciśnij klawisz; Delete/Backspace = brak, Esc = anuluj),
  albo w Sterowaniu Minecrafta (kategoria "Rynek Analiza").
- Zakładka **Przedmioty**: wszystkie przedmioty z aktywnego profilu BK na **jednej długiej liście**, którą przewijasz kółkiem myszy,
  paskiem po prawej (uchwyt można przeciągać, klik w tor przeskakuje) albo klawiszami **PageUp / PageDown / Home / End**
  (gdy nie piszesz w polu tekstowym). Nad listą jest wyszukiwarka i przyciski **Start analizy** (z wpisaną frazą analizuje tylko pasujące) i **Stop**.
  W każdym wierszu: nazwa (z ikoną przy szerszym oknie), mały wykres **Trend** (ostatnie skany, przy szerokim oknie),
  **Śr. 3** (średnia 3 najtańszych ofert za sztukę z ostatniej analizy), **Sprzedaż** (cena sprzedaży w BK, wpisuje ją analiza;
  "551$/16" = 551$ za wystawkę 16 szt.), pole **Skup ($)** (cena skupu w BK za sztukę - patrz niżej) i przycisk **Wykres**.
- Przycisk **Wykres** rozwija wykres tego przedmiotu **pod wierszem** (lista się wydłuża, możesz mieć rozwiniętych kilka naraz).
  Zwijasz go przyciskiem **Ukryj** (w wierszu albo w nagłówku wykresu - ten drugi jest zawsze w zasięgu, nawet gdy wiersz wyjedzie z widoku).
  Po rozwinięciu lista sama przewija się tak, żeby wykres był widoczny. Osobnej zakładki z wykresami już nie ma.
- W rozwiniętym bloku, pod wykresem, jest **pasek autopilota**: przełącznik **Auto: TAK/NIE** (czy autopilot może zmieniać ceny tego
  przedmiotu), pole **Min. sprzedaż** (cena całej wystawki, poniżej której autopilot nie zejdzie), pole **Max skup** (za sztukę, powyżej
  którego autopilot nie podniesie skupu) i linia stanu: marża, ceny wyliczone przez autopilota i powód ostatniej decyzji.
  Na wykresie decyzje autopilota to przerywane linie: zielona = sprzedaż, fioletowa = skup (za sztukę).
  W małym oknie blok bywa wyższy niż lista - wtedy po rozwinięciu widać nagłówek i wykres, a pasek po przewinięciu w dół.
- Zakładka **Autopilot**: włączanie, tryb (próba / na żywo), statystyki dnia i dziennik decyzji (opis niżej).
- Zakładka **Ustawienia**: klawisze, szybkość analizy (Bezpieczna / Szybka / Maksymalna), **minimalna liczba ofert do ceny** (1-3, domyślnie 3),
  podatek od sprzedaży (0.97 - tylko do pokazania zysku na wykresie),
  pauza na ostatniej stronie, podgląd na rynku, wznawianie BK, ścisłe sprawdzanie komponentów,
  historia cen (zapis, obserwacja rynku podczas pracy BK, odstęp zapisów), **"Wykresy: też obserwacje BK"** oraz liczby autopilota
  (co ile minut skan, ile przedmiotów na skan, budżet, limit niesprzedanych zakupów, minimalny zysk, marże, próg "drogich").
  Ustawienia są na kilku stronach (przyciski < >); przy wąskim oknie niektóre etykiety są skrócone.
- Podczas analizy panel nie otwiera się (komunikat w czacie) - zatrzymaj analizę klawiszem start/stop.
- Ostatnie pomiary i ustawienia są zapisywane w `config/rynek-analiza.json`, stan autopilota w `config/rynek-analiza-autopilot.json`.

## Autopilot
**Włączanie.** Zakładka Autopilot -> "Włącz autopilota". Startuje w trybie **PRÓBNYM**. Przejście na **NA ŻYWO**: przycisk "Tryb: PRÓBNY"
i drugi klik w ciągu 5 s (pierwszy tylko pyta "Na żywo? Kliknij ponownie"). Powrót do próby i wyłączenie - jednym klikiem; wtedy kupowanie
wstrzymane przez autopilota (budżet / limit) wraca w BK.

**Kiedy skanuje.** Co 12 minut (ustawienie) sprawdza do 6 przedmiotów naraz - najdłużej niesprawdzane, na żywo najpierw te z niesprzedanymi
zakupami. Tylko gdy **BK pracuje** i nie masz otwartego innego okna (np. ekwipunku albo tego panelu). Na czas skanu BK jest zatrzymywany
i potem zawsze wznawiany (zwykle kilkanaście-kilkadziesiąt sekund). Skan jest cichy: na czat idą tylko zmiany wpisane do BK, podejrzane oferty,
budżet i krótkie podsumowanie próby; reszta trafia do logu gry (`latest.log`, linie `[rynek-analiza] autopilot:`).
Dodatkowo autopilot czyta okna rynku, które otwiera sam BK (nic nie klika) - stąd szybciej zauważa, że ktoś go podciął.

**Tryb próbny.** Liczy ceny sprzedaży i skupu, ale nic nie wpisuje do BK. Gdy w oknie BK pojawi się oferta tańsza niż wyliczony skup,
zapisuje "wirtualny zakup", a po 2 h sprawdza, czy sprzedaż po aktualnej cenie dałaby zysk - na tym uczy się marży.
W zakładce Autopilot: wirtualne zakupy, ich wynik i liczba zmian cen z dzisiaj. Zwykła analiza (Start / F8) działa w próbie jak dotąd (średnia 3).

**Na żywo.** Dla przedmiotów z **Auto: TAK**:
- cenę sprzedaży wpisuje tylko tym, które mają w BK włączone "Sprzedawanie" (mod niczego w BK nie włącza),
- skup wpisuje tylko tym, które mają w BK włączone kupowanie i **min. 5 analiz z ostatnich 7 dni**,
- przedmioty **drogie** (od 10 000$/szt.) dostają skup dopiero, gdy autopilot ma 5 zyskownych sprzedaży i jest na plusie z 7 dni,
- zwykła analiza (Start / F8) też oddaje decyzję autopilotowi (zamiast średniej 3),
- nowa cena sprzedaży dotyczy **kolejnych** wystawek BK - oferty, które już wiszą na rynku, zostają po starej cenie.

**Cena sprzedaży** (za całą wystawkę, jak w BK):
- cel = najtańsza **cudza** oferta minus 1$, ale nie niżej niż **podłoga**,
- podłoga = najwyższa z: koszt zakupu / 0.97 x 1.03 (min. zysk 3%), 85% zwykłej ceny (mediana analiz z 7 dni, gdy jest ich min. 5),
  Twoje "Min. sprzedaż". Bez tego wszystkiego - pierwsza analiza przedmiotu. Jeśli autopilot nie prowadzi skupu, kosztem jest Twój skup z BK,
- oferta dużo tańsza od następnej albo od zwykłej ceny (poniżej 65%) to pomyłka - jest pomijana, a na czacie pojawia się ostrzeżenie,
- tylko **jedna** tańsza oferta = czeka (zwykle szybko schodzi); od **2** tańszych obniża (ustawienie "spadek = ofert pod Tobą"),
- spadek o ponad 20% musi się powtórzyć w 2 skanach z rzędu; po spadku pamięta poprzednią cenę i wraca do niej od razu, gdy rynek wróci,
- w górę tylko po pełnym skanie, po 2 potwierdzeniach i najwyżej o 10% naraz; różnice poniżej 1,5% pomija,
- **Twoje oferty** (rozpoznane po nicku w opisie oferty) nie są brane pod uwagę. Gdy nick nie rozpozna żadnej, oferty dokładnie po Twoich
  cenach z ostatniej doby też są pomijane (żeby autopilot nie podcinał sam siebie).

**Cena skupu** (za sztukę): `min(sprzedaż, zwykła cena) x (0.97 - marża)`, nie wyżej niż Twoje "Max skup". W dół od razu,
w górę po 2 potwierdzeniach i najwyżej o 5% naraz.

**Marża** (start 12%, zakres 3-25%, wszystko w ustawieniach), osobno dla każdego przedmiotu:
- brak zakupów przy aktywnym skupie przez 1 h (pierwsze 5 transakcji) albo 3 h (później), choć oferty na rynku były: -1 / -0,5 pkt,
- sprzedaż ze stratą: +2 / +1 pkt; mały zysk (poniżej połowy marży): +0,5 pkt; zakup niesprzedany od 6 h: +1 / +0,5 pkt.

**Zabezpieczenia** (na żywo):
- **budżet dzienny** 25 000$ na zakupy po cenach autopilota - po jego wyczerpaniu kupowanie tych przedmiotów jest wyłączane w BK do jutra
  (budżet **0 = autopilot nic nie kupuje**, tylko ustawia ceny),
- **hamulec strat**: strata dnia ponad 20% budżetu = to samo,
- **max 3 niesprzedane zakupy** na przedmiot - kupowanie tego przedmiotu wstrzymane, aż coś zejdzie,
- zakupy rozpoznaje z historii BK, sprzedaże z liczników BK (przedmiot po kwocie: cena wystawki albo cena x 0.97).

**Dziennik** (zakładka Autopilot): ostatnie decyzje, najnowsze na górze. Zielone = wpisane do BK, szare = próba,
czerwone = skan nieudany (np. rynek się nie otworzył). Starsze niż dzisiejsze mają datę.

**Ograniczenia.** Autopilot nie był sprawdzany w grze. Rozpoznawanie własnych ofert zakłada, że opis oferty na rynku zawiera Twój nick.
Dwie sprzedaże między odczytami (co sekundę) są liczone, ale bez przypisania do przedmiotu; dwa przedmioty z tą samą ceną wystawki
mogą się pomylić przy przypisaniu sprzedaży. Podatek 0.97 bierze z ustawień ("Podatek od sprzedaży").

## Cena sprzedaży (wpisuje analiza)
Analiza (Start / F8) dla każdego przedmiotu liczy średnią 3 najtańszych ofert **za sztukę** i wpisuje do BK `sellPrice`:
`sprzedaż = floor( średnia 3 najtańszych za sztukę x ilość sprzedaży )`.
- **Ilość sprzedaży** to pole "Ilość sprzedaży" wpisu w BK (brak = 1). BK wystawia przedmiot komendą `/wystaw {quantity} {price}`,
  gdzie `{price}` to cena **całej wystawki** - dlatego np. złote jabłka z ilością 64 dostają cenę za 64 szt. (64 x średnia za sztukę).
- Cena skupu (`maxPrice`) **nie jest zmieniana** przez analizę.
- Mod **nie włącza sprzedaży** - w BK przy przedmiocie musi być włączone "Sprzedawanie", a w ustawieniach BK "Automatyczna sprzedaż"
  (w Twoim profilu oba są teraz wyłączone). Sprawdź najpierw na jednym przedmiocie.
- Mniej ofert niż "Min. ofert do ceny" (domyślnie 3) = cena sprzedaży zostaje bez zmian (komunikat w czacie mówi, ile ofert było).
  Przy rzadkich przedmiotach możesz zejść do 2 albo 1 (wtedy jedna oferta wyznacza cenę - ostrożnie).
- Cena sprzedaży zmienia się tylko po analizie. Jeśli zmienisz w BK ilość sprzedaży, uruchom analizę tego przedmiotu ponownie
  (wpisz jego nazwę w wyszukiwarce panelu i kliknij "Start (filtr)").

## Cena skupu (ustalasz sam)
Skup ustawiasz w BK jak dotąd albo w polu **Skup ($)** w panelu (cena za sztukę, jak "Limit kupna" w BK).
Wyjątek: autopilot na żywo sam ustawia skup przedmiotów z **Auto: TAK** (patrz wyżej) - przy nich wpisana ręcznie wartość
zostanie nadpisana przy następnym skanie; żeby ustalać skup sam, przełącz przedmiot na **Auto: NIE**.
- wpisana liczba trafia do BK **ok. 1 s po ostatnim wpisanym znaku** (żeby przy wpisywaniu "14000" nie poszły ceny 1, 14, 140...)
  albo od razu po zamknięciu panelu; dolna linia panelu pokazuje "skup 13490$ -> 14000$",
- puste pole albo 0 niczego nie zmienia, przecinek działa jak kropka,
- samo otwarcie i zamknięcie panelu niczego nie zapisuje.

## Wykresy (pod wierszem przedmiotu)
- Każdy przedmiot ma własny wykres ze **wszystkich analiz** (od pierwszej do ostatniej). Jeden punkt = jeden skan: wysokość = **średnia 3 najtańszych ofert za sztukę**.
  Na wykresie: oś cen z siatką (okrągłe wartości), delikatne wypełnienie pod linią, przerywana **linia średniej** ze skanów,
  pogrubiona linia z zaokrąglonymi punktami, nad punktami wartości, pod nimi **godzina skanu** (HH:mm) i data (dd.MM), gdy się zmienia.
  Najnowszy skan to większy biały punkt z niebieską "pigułką" z wartością. Gdy punktów jest dużo, a okno wąskie, podpisy są pokazywane
  co któryś punkt (zawsze ostatni), a przy bardzo wielu analizach zostaje sama linia bez kropek - szczegóły każdego punktu i tak pokaże najechanie myszą.
- Najechanie myszą na wykres pokazuje linię pionową i kartę z szczegółami najbliższego punktu: pełna data i godzina,
  źródło (skan z analizy / obserwacja BK), średnia 3, najtańsza oferta i liczba ofert.
- Pod wykresem cztery karty: **Średnia** (ze średnich 3), **Najniższa**, **Najwyższa** i **Ostatni** (z procentową zmianą względem poprzedniego skanu),
  a pod nimi linia szczegółów: `sprzedaż w BK` (z ilością, gdy > 1), `skup w BK`, `zysk/szt.` (sprzedaż za sztukę x podatek 0.97 - skup),
  `rozstęp`, `najtańsza oferta` (najtańsza pojedyncza oferta ze wszystkich analiz) i `ostatni skan` (ile minut/godzin temu).
  Karty i rozstęp też liczą się ze wszystkich analiz.
  Przy wąskim oknie linia pokazuje tyle, ile się mieści (ceny w BK i zysk są pierwsze).
- Domyślnie punkty pochodzą **tylko ze skanów z analizy**. Przełącznik **"Wykresy: też obserwacje BK"** (zakładka Ustawienia)
  dodaje zaufane pomiary z okien rynku otwieranych przez BK (patrz niżej). Wybór jest zapamiętywany.
- Przedmiot bez skanów pokazuje krótki komunikat zamiast wykresu. Wykresy zapełniają się po kolejnych analizach (Start / F8).

## Wzór
**Cena sprzedaży** (wpisywana do BK jako `sellPrice`) = `floor( średnia 3 najtańszych ofert za sztukę x ilość sprzedaży )`.
**Zysk na sztuce** (tylko pokazywany) = `sprzedaż / ilość x 0.97 - skup`.

## Co dzieje się podczas analizy
BK-Rynek jest zatrzymywany (i wznawiany po analizie, jeśli działał). Dla każdego przedmiotu:
`/ah search <nazwa>` -> PPM w repeater dwa razy ("Najniższej ceny") -> skan -> cena za sztukę z każdej pasującej oferty ->
cena sprzedaży -> `sellPrice` wpisu w BK -> na końcu zapis configu BK i przeładowanie cen. Pomiar (Śr. 3, najtańsza oferta, liczba ofert, czas)
trafia do historii (wykresy).
Mniej ofert niż minimum = cena sprzedaży nie zostaje ustawiona i pomiar nie jest zapisywany.

**Zapasowe wyszukiwanie.** Gdy po `/ah search <pełna nazwa>` na rynku nie było **żadnego** przedmiotu o tym id (albo rynek się nie otworzył),
mod próbuje krótszej frazy: bez numeru na końcu, a potem samo pierwsze słowo - np. "Anarchiczny hełm II" -> "Anarchiczny hełm" -> "Anarchiczny".
Dopasowanie BK i tak wybiera właściwy przedmiot (hełm II, nie hełm I). Gdy przedmioty tego id były, ale nie pasowały, mod nie szuka dalej,
tylko pokazuje diagnostykę (niżej). Własna fraza w `search` w configu wyłącza zapasowe wyszukiwanie dla tego przedmiotu.

**Przedmioty o tej samej nazwie** (np. 5 x "Zaklęta książka", 2 x "Diamentowy miecz") są rozróżniane po pierwszej linii lore
(potem CMD / enchanty) - w panelu widać np. "Zaklęta książka [Regeneracji I]", a każdy ma osobny wykres i pomiar.
Wcześniej ich pomiary się mieszały; ta wspólna stara historia nie jest już pokazywana, nowe wykresy zapełnią się od następnej analizy.
Przedmioty z unikalną nazwą zachowują swoją historię.

Dopasowanie ofert jak w BK: najpierw zgodne id przedmiotu (BK wybiera wpisy po id, np. `enchanted_golden_apple`),
potem `AutoMarketScanner.matchesEntry` (nazwa, enchanty, lore, custom model data...).
Zwykłe złote jabłko o nazwie "Zaklęte złote jabłko" nie przejdzie.

Zasięg skanu zależy od przedmiotu (na stałe, bez ustawienia): przedmioty niestackowalne (po 1 szt., np. hełmy) - skan kończy się,
gdy znaleziono 3 oferty, bo rynek jest posortowany od najniższej ceny; przedmioty stackowalne (np. złote jabłka) -
przeglądane są wszystkie strony, bo większy stack na dalszej stronie może mieć niższą cenę za sztukę.

## Podgląd na rynku
- Nad rynkiem pasek z ikoną i nazwą analizowanego przedmiotu, numerem w kolejce, stroną, liczbą ofert i trybem skanu.
- 3 najtańsze oferty (idą do ceny sprzedaży) mają **żółtą** ramkę. Ostatnia strona skanu z podświetleniem jest chwilę trzymana
  (`highlightPauseTicks`, domyślnie 10 ticków = 0,5 s; 0 = bez pauzy).
- Po policzeniu w czacie: "Użyte oferty: str.1 r1 k1 = 950/szt (id "nazwa" xN); ...". To ostateczny wybór;
  w trybie pełnego skanu podświetlenie na kolejnych stronach pokazuje stan chwilowy.
- Wyłączenie podglądu: zakładka Ustawienia albo `"showOverlay": false`.

### Znaczek "Rynek Analiza: działa"
Pokazuje, że mod naprawdę czyta rynek, gdy skanuje go BK (tylko rysuje - niczego nie klika).
- Przy każdym oknie rynku, w lewym górnym rogu ekranu (obok okna, nie na ofertach):
  - **zielona kropka i "działa"** - mod odczytał okno; kropka mignie przy każdym nowym odczycie. Pod spodem: ile cudzych ofert
    przedmiotu z Twojej listy BK zobaczył (i nazwa), najtańsza za sztukę, "zapisano do wykresu", "autopilot sprawdził",
    licznik odczytów od startu gry i ile było Twoich ofert. Na wąskim ekranie część opisu jest skrócona.
  - **żółty "czytam okno..."** - nowe okno jeszcze nie odczytane. Mod czyta okno najwyżej co 3 s, więc przy szybkim
    przełączaniu stron część pomija; dopóki ostatni odczyt jest sprzed najwyżej 10 s, znaczek zostaje zielony.
  - **czerwony** - mod nie widzi BK albo odczyt się nie udał (z treścią błędu).
  - **szary "nie obserwuje"** - wyłączone "Obserwuj rynek, gdy działa BK" albo "Zapisuj historię cen" (a autopilot też wyłączony).
  - "bez zapisu: nie od najtańszych" jest normalne: okno nie pokazuje najtańszych ofert (np. dalsza strona albo inne
    sortowanie), więc nie idzie do wykresu. "brak przedmiotów z listy BK" = w oknie nie ma niczego z Twojej listy.
  - W trakcie analizy (Twojej albo skanu autopilota) znaczek mówi tylko "analiza trwa" / "skan autopilota" - szczegóły są nad oknem.
- Gdy BK pracuje, a okno rynku jest zamknięte: jedna linia w lewym górnym rogu ekranu gry - kiedy był ostatni odczyt
  i za ile skan autopilota (gdy włączony). Chowa się pod F1.
- Wyłączenie: zakładka Ustawienia, "Znaczek 'działa'" (albo `"showBadge": false`).

## Komendy (opcjonalnie)
`/analiza start [fragment]`, `/analiza stop`, `/analiza lista` (nazwa, skup, sprzedaż i ilość każdego przedmiotu).

## Do sprawdzenia przy pierwszym teście autopilota
1. Włącz autopilota (tryb próbny) i zostaw BK włączony. Po pierwszym skanie (zaraz po zamknięciu panelu, potem co 12 min) w zakładce
   Autopilot pojawią się decyzje, a przy przedmiotach - linia stanu w pasku pod wykresem. W BK nic się nie powinno zmienić.
2. W logu analizy (i na znaczku przy rynku: "Twoich: N") powinno być widać Twoje oferty, jeśli masz coś wystawione. Jeśli masz oferty, a tego dopisku brak - nick nie jest
   rozpoznawany: daj znać, zanim przełączysz na żywo.
3. Po dniu próby sprawdź wynik wirtualnych zakupów. Na żywo zacznij od niskiego budżetu i kilku przedmiotów (resztę przełącz na Auto: NIE).

## Do sprawdzenia przy pierwszym teście
1. Prawy Shift otwiera panel i widać w nim przedmioty z BK (jeśli nie - komunikat błędu w panelu).
2. Analiza jednego przedmiotu (wpisz nazwę w wyszukiwarce panelu, "Start (filtr)"): w czacie "średnia 3 najtańszych = X/szt. -> sprzedaż Y$ za N szt.",
   w kolumnie "Sprzedaż" ta sama cena, a w BK "Cena sprzedaży" tego przedmiotu też. Skup w BK się nie zmienił.
3. Złote jabłka (ilość sprzedaży 64): cena sprzedaży = 64 x średnia za sztukę. **Zanim włączysz w BK sprzedawanie, sprawdź na jednym przedmiocie,
   że wystawka na rynku ma właściwą cenę.**
4. Anarchiczny hełm II: jeśli dalej "nie znaleziono żadnej pasującej oferty", w czacie pojawią się linie "BK: ..." z powodem odrzucenia,
   a pełny raport BK będzie w `config/rynek-analiza-diagnostyka.txt` - przyślij go. Jeśli "na rynku nie było żadnego przedmiotu o id..." także po
   zapasowych frazach - na rynku po prostu nie ma ofert albo serwer szuka inną frazą (wpisz ją w `search` w configu).
5. Fraza po `/ah search` to domyślnie nazwa przedmiotu bez kolorów. Jeśli serwer szuka inaczej, wpisz własną w `search` w configu.
6. Cena z lore: używany jest `lore_regex` z profilu BK, a gdy go brak - pierwsza liczba po słowie "Koszt".
7. BK porównuje cenę za sztukę tylko przy włączonym trybie "Unit" w profilu.

## Szybkość analizy
Zamiast stałych opóźnień bot czeka na faktyczną odpowiedź serwera: po każdym kliknięciu obserwuje okno rynku i idzie dalej,
gdy dane przestały się zmieniać (kilka ticków). Dodatkowo: nie klika sortowania, jeśli "Najniższej ceny" jest już ustawione,
pauza z podświetleniem jest tylko raz na przedmiot, a przerwa między przedmiotami jest krótka.
Poziomy (zakładka Ustawienia): Bezpieczna (4 ticki między kliknięciami, 3 ticki spokoju), Szybka - domyślna (2 / 2), Maksymalna (1 / 1).
Jeśli serwer zacznie odrzucać komendy lub klikanie, wybierz niższy poziom. Rzeczywisty czas zależy od pingu i serwera.

## Dopasowanie i cena - jak w BK
- Cena oferty jest czytana funkcją BK (`AutoMarketScanner.extractPrice`, ten sam skaner co w silniku BK): pomija m.in. linie
  "koszt użycia" w lore przedmiotów customowych. Własny odczyt ("Koszt" / `lore_regex`) działa tylko jako zapas.
- Liczba komponentów NIE jest sprawdzana domyślnie (BK sam jej nie sprawdza w swojej ścieżce dopasowania, więc dodatkowe
  sprawdzenie mogło odrzucać prawidłowe oferty). Włączysz je w Ustawieniach ("Ścisłe sprawdzanie komponentów").
- Gdy dla przedmiotu nie znaleziono dość ofert, w czacie pojawia się linia "Diagnostyka": ile przedmiotów o tym id było w oknie,
  ile odrzuciło dopasowanie BK (z przykładową nazwą), ile liczba komponentów, ile nie miało odczytanej ceny - albo że przedmiotów
  tego id w ogóle nie było.
- Gdy BK odrzucił oferty, mod prosi BK o jego własny raport dopasowania (`CleanMarketEngine.debug`) dla pierwszej odrzuconej oferty:
  kilka linii z sekcji FAILURES trafia do czatu ("BK: ..."), a cały raport do `config/rynek-analiza-diagnostyka.txt` (dopisywany na końcu).

## Historia cen
Plik: `config/rynek-analiza-history.json`. Zapis w tle co kilka minut, przy końcu analizy i przy zamykaniu gry.
Skany z analizy nigdy nie są usuwane przez przerzedzanie (do ok. 1000 punktów na przedmiot przerzedzane są tylko stare obserwacje).

Źródła punktów:
- **Skan z analizy** (Start / F8): po każdej analizie przedmiotu zapisywany jest pomiar (najtańsza oferta i średnia 3 najtańszych, za sztukę,
  liczba ofert, czas). To jedyne źródło wykresu w ustawieniu domyślnym i najpewniejsze dane.
- **Obserwacja podczas pracy BK** (Ustawienia -> "Obserwuj rynek, gdy działa BK"): gdy BK otworzy okno rynku, mod tylko je czyta
  (nie klika, nie zatrzymuje BK) i zapisuje ceny przedmiotów z listy - ale tylko wtedy, gdy widać prawdziwe minimum (rynek ma jedną stronę
  albo to strona 1 posortowana rosnąco po cenie). Okna przy innym sortowaniu (BK często używa "Najnowsze") nie są już zapisywane.
  Zapis nie częściej niż raz na minutę na przedmiot. Te punkty widać na wykresie po przełączeniu "skany + obserwacja BK".
- Stare punkty-próbki z poprzedniej wersji moda są w pliku, ale nie są pokazywane ani liczone.

## Czego już nie ma
- **Marża i liczenie ceny skupu** (pole "Marża", "Domyślna marża", komendy `/analiza marza` i `/analiza marza_domyslna`) - analiza liczy
  teraz cenę sprzedaży, a skup ustalasz sam. Stare pola `margins` / `defaultMargin` w configu są pomijane i znikają przy zapisie.
- **Sugerowana marża** (z rozrzutu cen, z historii, przycisk "Użyj") - usunięta w całości razem z mnożnikiem w ustawieniach i pomarańczowymi
  ramkami 4.-5. oferty. Stare pola `suggested` / `suggestFactor` w configu są ignorowane.
- **Profil rynku i podgląd decyzji BUY / WATCH / REJECT** - usunięty razem ze starym wykresem (liczył się z zakresów czasu i zaufanych pomiarów,
  których nowy wykres już nie używa).
