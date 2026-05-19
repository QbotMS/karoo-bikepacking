# AGENTS.md — Karoo 3 LIVE Field / QBot


## 2026-05-19 — aktualizacja nadrzędna / aktualny stan projektu

Ta sekcja ma pierwszeństwo przed starszymi fragmentami pliku, jeśli pojawi się sprzeczność.

### Aktualny układ pól

`LIVE 3×2` pozostaje bez zmian:

```text
Speed | Power | HR
Cad   | Grade | Gear
```

Aktualny `DYN` to faktycznie `DYN 4×2`, mimo historycznej nazwy klasy `BpDyn3x2DataType.kt`:

```text
D    | IF10 | HRD | W'
DTD  | Vśr  | T   | W
```

Zmiany względem starszych wersji:

- `IF10` w DYN zastąpione przez `IF10`;
- `HRD` wypada jako slot DYN;
- w miejscu `NP10` wchodzi `HRD` / HR Drift-Strain;
- `HRD` może pozostać metryką wewnętrzną, jeśli jest użyteczne dla algorytmów, ale nie jest już stałym slotem DYN.

### HRD / HR Drift-Strain

`HRD` to lokalny wskaźnik ekonomii wysiłku: czy za tę samą moc organizm płaci coraz wyższym tętnem.

To nie jest klasyczny końcowy Pw:Hr decoupling z raportu treningowego i nie wolno traktować go jako diagnozy medycznej.

Stany UI / modelu:

```text
WAIT
BASE
OK
+
++
HOT
INVALID
```

Model MVP:

```text
0–20 min: WAIT
20–35 min: BASE
35+ min: current 10–15 min vs baseline
postój >8–10 min: WAIT/REBASE
```

Liczyć tylko stabilny wysiłek:

- moving=true;
- power > około 55–60% FTP;
- cadence > 0;
- brak freewheel/zjazdu;
- brak sprintu;
- względnie stabilna moc;
- HR smoothing.

Logować osobno:

- `driftDayPct`;
- `driftLocalPct`;
- `baselineAgeMin`;
- `postStopWarmup`;
- `validSamples`.

### LIVE UI — aktualna zasada kolorów

Domyślnie tła slotów są neutralne / native-looking.
Status kolorystyczny idzie przede wszystkim na wartość/liczbę, nie na całe tło kafla.
Nie dublować informacji przez jednoczesne mocne tło i mocno kolorowaną cyfrę.

Zasady szczególne:

- `Speed`, `Power`, `W′` kolorują głównie wartość;
- tło pozostaje neutralne / transparentne;
- niska moc nie może być czerwona ani alarmowa;
- `Power below target` = neutral / muted / chłodny kolor;
- czerwony/bursztynowy dla `Power` tylko przy realnym przepalaniu kosztu.

### Grade — aktualny priorytet runtime

Fallback UI Grade pokazujący `--` nie rozwiązuje problemu źródła danych.
Trzeba zweryfikować runtime, czy extension realnie dostaje bieżące nachylenie z Karoo.

Wymagania:

- znaleźć właściwy stream/field Karoo SDK: `grade`, `slope`, `gradient`, `elevation grade`, `current grade` albo aktualną nazwę w SDK;
- podpiąć do `RideEngine` / live state;
- dodać freshness dla `grade`;
- logger ma zapisywać `live.grade.value`, `live.grade.display`, `live.grade.freshness`;
- valid → np. `+3.2%`;
- missing/stale → `--`;
- nie używać `climb.avgGradePct` jako fallbacku dla live grade.

Po ostatniej zmianie Code dodano próby subskrypcji grade i freshness. To trzeba potwierdzić runtime logiem z Karoo.

### DYN MSG — brak fake placeholderów

`message == null` nie oznacza komunikatu testowego.
Nie pokazywać fałszywego `PODJAZD` ani żadnego statycznego komunikatu demo.

Zasada:

```text
message == null -> normalny DYN albo pusty/neutralny stan
```

### Wind

Wind pochodzi z `karoo-headwind`.

Aktualna logika UI:

- headwind = czerwony;
- tailwind = zielony;
- crosswind / wpływ około `-1/+1 km/h` = neutralny;
- `windArrow()` ma uwzględniać poprawną / inwertowaną orientację względem jazdy.

### W′ / RSRV

`W′Bal` w zakresie `15–29` nie wymusza automatycznie `WARN`.
Status zależy od kontekstu, trendu spadku, HRD/strain i dalszej trasy.
UI koloruje wartość, tło pozostaje neutralne.

### HR zones + setup switch

Dodać w konfiguracji:

```text
HR display mode: bpm / zone
```

Strefy:

```text
Z1 / Z2 / Z3 / Z4 / Z5
```

Na MVP bez rozbudowanego setup wizard, ale tryb `bpm / zone` ma być zaplanowany w SETUP.

### Runtime JSONL logger

Runtime logger JSONL jest źródłem prawdy po jeździe.

Ścieżka na Karoo:

```text
/storage/emulated/0/Android/data/com.bikepacking.karoo/files/qbot_logs
```

Tick powinien zawierać:

- `event`;
- `ts`;
- `elapsedSec`;
- `live`;
- `dyn`;
- `stats`;
- `climb`;
- `message`;
- `freshness`.

Nie usuwać loggera przy refaktorach.

### Aktualny checkpoint

Ostatni raportowany stan po zmianach:

```text
Build OK
493 tests passed
APK gotowy do testowania
```

### Aktualna kolejność prac

1. Zweryfikować runtime Grade po nowej subskrypcji.
2. Usunąć `NP10` ze slotu DYN i dodać `HRD`.
3. Dodać `HR display mode: bpm / zone`.
4. Dopiero potem pełna integracja Climb SDK.
5. Dopiero potem pełny DYN message engine.

---

## Cel projektu

Budujemy własne pole danych LIVE dla Hammerhead Karoo 3, działające na ekranie mapy jako mały, bardzo czytelny HUD.

Nie projektujemy pełnoekranowego dashboardu.
Nie robimy dynamicznych komunikatów w polu `LIVE`.
Dynamiczne komunikaty będą osobną warstwą projektowaną później.

ETA nie należy do `LIVE`. ETA może być pokazane w polu `DYN`, ponieważ `DYN` jest polem kontekstowym, a nie głównym polem sterowania wysiłkiem.

Priorytetem są dwa stałe pola HUD na ekranie mapy:

1. `DYN 3×2` — górne pole kontekstowe, drugorzędne.
2. `LIVE 3×2` — dolne pole główne, decyzyjne.

Oba pola mają układ 3 kolumny × 2 rzędy. `DYN` nie zastępuje `LIVE`; jest wyświetlany nad nim.

## Układ LIVE

Pole LIVE ma mieć dokładnie 6 subpól:

Górny rząd:
1. Speed
2. Power
3. HR

Dolny rząd:
4. Cadence
5. Grade
6. Gear

Układ:

```text
┌────────┬────────┬────────┐
│ 28.4   │ 186    │ 139    │
│ SPD    │ W      │ BPM    │
├────────┼────────┼────────┤
│  72    │ +3%    │ 40×15  │
│ RPM    │ GRD    │ GEAR   │
└────────┴────────┴────────┘
```

Etykiety mają być bardzo małe albo ikonowe. Wartości liczbowe muszą dominować.

## Zasady UI

- projektować pod realny mały obszar pola na ekranie mapy Karoo 3;
- nie robić pełnoekranowych wizualizacji;
- cyfry mają zajmować około 70–80% wysokości subpola;
- brak dużych nagłówków;
- etykiety tylko jako małe ikony albo minimalne skróty;
- wartości nie mogą skakać przy zmianie liczby cyfr;
- każde z 6 subpól ma mieć własne tło/cieniowanie/kolor wynikające z logiki parametru;
- UI ma być natywne, zwarte, techniczne i czytelne podczas jazdy gravelowej;
- nie przeładowywać kolorem — kolor jest drugim poziomem informacji, wartość jest pierwszym.

## Wzorzec graficzno-techniczny

Barberfish traktować jako wzorzec techniczno-graficzny, nie funkcjonalny.

Nie kopiować funkcjonalności Barberfish. Budować własne pole LIVE.

Repo Barberfish:
- ikony: `app/src/main/res/drawable`
- layouty: `app/src/main/res/layout`

Istotne ikony/referencje:
- `ic_avg_hr.xml`
- `ic_avg_power.xml`
- `ic_cadence.xml`
- `ic_col_hr.xml`
- `ic_col_power.xml`
- `ic_col_speed.xml`
- `ic_grade.xml`
- `ic_speed_average.xml`
- `ic_time_to_dest.xml`

Dla Gear trzeba przygotować własną ikonę.

Istotne layouty do analizy:
- `barberfish_hud.xml`
- `barberfish_hud_four.xml`
- `barberfish_field.xml`
- `barberfish_sparkline.xml`

Z Barberfish przejąć zasady:
- równe sloty;
- duże stabilne cyfry;
- małe etykiety/ikony;
- native-looking kontener;
- kontrolowany kolor;
- stabilne renderowanie tekstu;
- auto-fit tekstu, żeby wartości typu `40×15` nie wychodziły poza slot;
- brak wizualnego chaosu.

## Pole STATS 3×6 — statystyki jazdy / podsumowanie kontekstowe

`STATS` to osobne pole danych, które ma być wizualnie spójne z `LIVE 3×2` i `DYN 3×2`, ale ma większą gęstość informacji.

`STATS` nie jest pełnoekranowym dashboardem.
`STATS` nie jest polem alertów.
`STATS` nie zastępuje `LIVE` ani `DYN`.

Rola pól:

```text
LIVE  3×2 -> Czy teraz jadę dobrze?
DYN   3×2 -> Jaki jest szerszy kontekst jazdy?
STATS 3×6 -> Jak wygląda statystyczny i logistyczny przebieg jazdy?
```

### Priorytet STATS

`STATS` ma być gęstym panelem statystycznym, ale nadal czytelnym na realnym ekranie mapy Karoo.

Priorytety:

- czytelność na małym polu mapy Karoo;
- układ 3 kolumny × 6 wierszy;
- 18 jednowartościowych slotów;
- brak pełnoekranowego dashboardu;
- brak alertów;
- brak ikon;
- krótkie, ale bardziej opisowe etykiety;
- duże wartości liczbowe;
- jednostki mniejsze od wartości;
- stabilne formatowanie;
- fallback `--` przy braku danych;
- logika odporna na pauzy tam, gdzie to wymagane;
- brak wartości demo w produkcyjnym `STATS`.

`STATS` ma wyglądać jak członek tej samej rodziny UI co `LIVE` i `DYN`, ale może mieć własną gęstszą strukturę.

### Finalny układ STATS 3×6

Aktualny roboczo-finalny układ `STATS`:

```text
┌─────────────┬─────────────┬─────────────┐
│ NP          │ IF          │ VI          │
├─────────────┼─────────────┼─────────────┤
│ CARB IN     │ FLUID IN    │ KCAL        │
├─────────────┼─────────────┼─────────────┤
│ TSS         │ DRIFT       │ RSRV        │
├─────────────┼─────────────┼─────────────┤
│ UP          │ UP LEFT     │ ETA         │
├─────────────┼─────────────┼─────────────┤
│ V AVG TOTAL │ TIME TOTAL  │ TIME STOP   │
├─────────────┼─────────────┼─────────────┤
│ BURN BAT    │ BAT LEFT    │ RD BAT      │
└─────────────┴─────────────┴─────────────┘
```

Znaczenie pól:

| Etykieta | Znaczenie | Format przykładowy |
|---|---|---|
| `NP` | Normalized Power | `196W` |
| `IF` | Intensity Factor | `.78` |
| `VI` | Variability Index | `1.05` |
| `CARB IN` | rekomendowane węgle na godzinę | `60g` |
| `FLUID IN` | rekomendowane płyny na godzinę | `0.7L` |
| `KCAL` | zużyte kilokalorie | `920` |
| `TSS` | Training Stress Score | `84` |
| `DRIFT` | decoupling / dryf HR-power | `+4%` |
| `RSRV` | rezerwa organizmu / ride reserve | `72%` |
| `UP` | przewyższenie zrobione | `420m` |
| `UP LEFT` | przewyższenie do końca trasy | `680m` |
| `ETA` | przewidywana godzina dojazdu | `18:35` |
| `V AVG TOTAL` | średnia prędkość brutto, z postojami | `22.4` |
| `TIME TOTAL` | łączny czas od startu | `2:18` |
| `TIME STOP` | łączny czas pauzy | `0:18` |
| `BURN BAT` | zużycie baterii Karoo/head unit na godzinę | `8%/h` |
| `BAT LEFT` | szacowany czas pracy baterii Karoo/head unit | `7:20` |
| `RD BAT` | bateria tylnej przerzutki AXS | `83%` |

### Typografia STATS

Aktualne założenia typografii:

- wartość główna: `22sp`;
- jednostka: `18sp`, osobny `TextView`;
- etykieta: `9sp`;
- font: `sans-serif-condensed`;
- wartości liczbowe pogrubione;
- jednostki bez pogrubienia albo optycznie lżejsze;
- `includeFontPadding=false`;
- fallback `--` bez jednostki;
- nie używać `android:autoSizeTextType`;
- nie używać `RemoteViews.setAutoSizeTextTypeWithDefaults`.

`RemoteViews` na Karoo okazał się zawodny dla auto-size i spanów. Jednostki muszą być bezpiecznie renderowane jako osobny `TextView`, a nie przez `Spannable`, jeśli powoduje to brak efektu albo ryzyko czarnego pola.

### Jednostki w STATS

Jednostki mają być mniejsze o około 20% od liczby.

Przykłady:

```text
196 + W
60  + g
0.7 + L
72  + %
420 + m
8   + %/h
83  + %
```

Dla wartości bez jednostki unit view ma być ukryty:

- `IF`;
- `VI`;
- `TSS`;
- `KCAL`;
- `ETA`;
- `V AVG TOTAL`;
- `TIME TOTAL`;
- `TIME STOP`;
- `BAT LEFT`.

### Tło i separatory STATS

STATS używa pasmowania rzędów:

- rzędy 1, 3, 5: czarne albo prawie czarne tło;
- rzędy 2, 4, 6: ciemnogranatowe tło;
- separatory subtelne, zgodne z rodziną `LIVE` / `DYN`;
- kolor tła nie może dominować nad wartością.

Cel pasmowania: łatwiejsze czytanie gęstego panelu 3×6.

### Visual test STATS

Musi istnieć osobne pole testowe / debugowe do oceny czytelności, np.:

```text
QBot STATS 0000 TEST
```

albo aktualna nazwa zgodna z projektem.

Pole testowe:

- nie korzysta z realnych danych;
- nie korzysta z produkcyjnych fallbacków;
- nie zastępuje produkcyjnego `STATS`;
- służy tylko do sprawdzania czytelności layoutu na Karoo;
- ma takie same etykiety jak produkcyjne `STATS`.

Wartości testowe dla docelowego układu:

```text
196W  | .78  | 1.05
60g   | 0.7L | 920
84    | +4%  | 72%
420m  | 680m | 18:35
22.4  | 2:18 | 0:18
8%/h  | 7:20 | 83%
```

Dla testu szerokości można czasowo używać `0000` w każdym slocie, ale tylko w polu testowym, nigdy w produkcyjnym `STATS`.

### Produkcyjny fallback STATS

W produkcyjnym `STATS`:

- brak danych zawsze pokazuje `--`;
- nie pokazywać `0` jako braku danych;
- fallback `--` nie ma jednostki;
- nie zgadywać danych, których SDK nie udostępnia;
- wartości demo nie mogą wejść do produkcyjnego działania.

### Logika pauz w STATS

Parametry treningowe mają ignorować pauzy.
Parametry logistyczne mają uwzględniać pauzy.

Podział:

| Pole | Pauzy mają wpływać? | Zasada |
|---|---:|---|
| `NP` | Nie | tylko aktywne próbki mocy |
| `IF` | Nie | dziedziczy z NP |
| `VI` | Nie | NP / średnia moc z aktywnych próbek |
| `TSS` | Nie | aktywny wysiłek, nie elapsed standing time |
| `KCAL` | Nie | energia z aktywnych próbek mocy |
| `CARB IN` | Nie bezpośrednio | moving time, nie elapsed time |
| `FLUID IN` | Częściowo | temperatura + intensywność, nie postój jako wysiłek |
| `DRIFT` | Nie | tylko sensowne próbki HR + power |
| `RSRV` | Nie przez trasę/pauzę | rezerwa organizmu, nie route budget |
| `UP` | Nie | suma przewyższenia |
| `UP LEFT` | Zależne od trasy | fallback `--`, jeśli brak trasy |
| `ETA` | Tak | logistycznie uwzględnia postoje |
| `V AVG TOTAL` | Tak | dystans / elapsed time |
| `TIME TOTAL` | Tak | elapsed time od startu |
| `TIME STOP` | Tak | elapsed time - moving time |
| `BURN BAT` | Tak | bateria schodzi także na postoju |
| `BAT LEFT` | Tak | z realnego zużycia baterii |
| `RD BAT` | Nie dotyczy | stan baterii AXS |

### Filtr aktywnych próbek mocy

`StatsCalculator.update()` musi odrzucać próbki pauzy z obliczeń treningowych.

Przyjęty model:

```kotlin
val movingAdvanced = movingSec > lastMovingSec
val hasPower = powerWatts > 0
val isActivePowerSample = movingAdvanced && hasPower
```

Tylko gdy `isActivePowerSample == true`, próbka może wejść do:

- NP;
- rolling power;
- totalPowerSum;
- totalPowerCount;
- calories;
- statystyk mocy zależnych od aktywnego wysiłku.

Pauza z `power=0` nie może obniżać NP.
Pauza ze stale wartością mocy z sensora nie może zwiększać kcal ani średniej mocy.
Zjazd bez pedałowania (`movingSec` rośnie, `power=0`) nie jest aktywną próbką mocy dla NP/VI.

`reset()` musi zerować także `lastMovingSec`.

### DRIFT / Decoupling

`DRIFT` oznacza decoupling HR-power.

Zasady:

- używać tylko sensownych próbek HR + power;
- pauzy mają być pomijane;
- próbki z `power=0` albo brakiem HR mają być odrzucane;
- wynik `0%` jest poprawną wartością, nie brakiem danych;
- fallback `--` tylko wtedy, gdy danych jest za mało;
- DRIFT nie powinien być traktowany jako wiarygodny zbyt wcześnie.

Docelowo:

- `--` przez pierwsze minimum około 20 minut aktywnej jazdy;
- sensowna interpretacja po około 45 minutach aktywnej jazdy;
- mocniejszy wpływ na `RSRV` dopiero po zebraniu wystarczającej liczby próbek.

### RSRV — obecny model MVP

`RSRV` oznacza rezerwę organizmu / ride reserve.

`RSRV` NIE oznacza:

- procentu ukończenia trasy;
- route budget;
- ile zostało dystansu;
- ile zostało przewyższenia.

`RSRV` nie może być skalowany głównie względem `remainingKm`, `avgNetKph` albo długości wczytanej trasy.

Poprzedni model był błędny, bo zachowywał się jak route budget:

```text
RSRV = funkcja TSS / projected_TSS_from_remaining_route
```

Na krótkiej trasie testowej mogło to dawać absurdalnie niską rezerwę, np. około 37% po kilku kilometrach spokojnej jazdy.

Obecny model MVP:

```text
RSRV = baseToday
       - TSS penalty
       - IF10 penalty
       - DRIFT penalty
```

Zasady MVP:

- `remainingKm`, `avgNetKph`, `hasRoute` nie są główną skalą `RSRV`;
- brak trasy nie może dawać `RSRV = 0`;
- krótka trasa nie może obniżać `RSRV`;
- `TSS` obniża `RSRV` jako koszt całkowity;
- `IF10` obniża `RSRV`, jeśli intensywność jest wysoka;
- `DRIFT` obniża `RSRV`, jeśli są dane i dryf jest wysoki;
- wynik clamp `0..100`.

Przykładowy MVP:

```text
baseToday = todayFactor * 100, fallback 100
reserve -= TSS * 0.6
if IF10 > 0.75 -> penalty
if DRIFT > 5% and hasDecouplingData -> penalty
```

### RSRV v2 — kierunek docelowy

`RSRV v2` ma być bardziej zaawansowaną miarą rezerwy organizmu.

Definicja:

```text
RSRV = ile rezerwy ma organizm dzisiaj
Route Risk / Finish Risk = czy ta rezerwa wystarczy na pozostałą trasę
```

`remainingKm`, `UP LEFT` i trudność trasy mogą wpływać na przyszły `Route Risk`, ale nie powinny bezpośrednio obniżać `RSRV`.

Docelowe wejścia `RSRV v2`:

- Today Factor / freshness z QBot, Garmin, Xert albo profilu dnia;
- TSS / accumulated training load;
- IF10 / ostatnia intensywność;
- NP10 / krótszy koszt intensywności;
- W′bal / anaerobic reserve, jeśli dostępne;
- DRIFT / decoupling po wystarczająco długiej aktywnej jeździe;
- HR strain względem power;
- temperatura i warunki;
- deficyt węgli i płynów, jeśli użytkownik zacznie logować intake.

Model docelowy:

```text
RSRV = baseToday
       - loadPenalty
       - intensityPenalty
       - anaerobicPenalty
       - driftPenalty
       - heatHydrationPenalty
```

Zasady `RSRV v2`:

- pauzy nie mogą sztucznie obniżać `RSRV`;
- krótka trasa testowa nie może obniżać `RSRV`;
- brak trasy nie może dawać `RSRV = 0`;
- `RSRV` powinno działać bez wczytanej trasy;
- `remainingKm` może być tylko kontekstem dla osobnego przyszłego `Route Risk`;
- `RSRV` powinno być stabilne, z histerezą/smoothingiem;
- `RSRV` powinno być konserwatywne przy wysokim IF, dużym DRIFT i niskim W′bal.

### CARB IN

`CARB IN` oznacza rekomendowany intake węgli w g/h.

To nie jest faktycznie zjedzona ilość.
Nie mieszać rekomendacji z logowaniem spożycia.

Aktualny model powinien używać:

```kotlin
carbsGPerH(
    if10: Float,
    movingSec: Long,
    vi: Float,
    tempCelsius: Float?,
    bodyWeightKg: Float
)
```

Zasady:

- używać `movingSec`, nie `elapsedSec`;
- pauza nie może podnosić rekomendacji tylko dlatego, że płynie czas brutto;
- wynik ma rosnąć monotonicznie z IF10;
- wynik ma łagodnie rosnąć z aktywnym czasem jazdy;
- wynik ma łagodnie rosnąć z masą ciała;
- wynik nie powinien spadać w upale;
- wynik ma być zaokrąglany do 5 g;
- clamp około `20..110 g/h`.

Przykładowe oczekiwane wyniki dla 75 kg, 15°C, VI 1.05, 2h aktywnej jazdy:

| IF10 | CARB IN |
|---:|---:|
| 0.50 | około `40g` |
| 0.65 | około `55g` |
| 0.75 | około `65g` |
| 0.85 | około `75g` |
| 0.95 | około `85g` |

Dla IF10 0.70, 15°C, VI 1.05, 75 kg:

| Moving time | CARB IN |
|---:|---:|
| 30 min | około `55g` |
| 1 h | około `55g` |
| 2 h | około `60g` |
| 4 h | około `65g` |

### FLUID IN

`FLUID IN` oznacza rekomendowany intake płynów w L/h.

To nie jest faktycznie wypita ilość.
Nie mieszać rekomendacji z logowaniem spożycia.

Aktualny model powinien używać:

- IF10;
- temperatura;
- wilgotność, jeśli jest dostępna lub ustawiona w konfiguracji;
- masa ciała;
- fallback dla braku temperatury.

Zasady:

- wynik ma rosnąć z temperaturą;
- wynik ma rosnąć z intensywnością;
- wynik ma łagodnie rosnąć z masą ciała;
- brak temperatury ma dawać sensowną bazę, nie `0`;
- clamp około `0.30..1.50 L/h`;
- wynik zaokrąglać do `0.05 L/h` albo stabilnego formatu display.

Przykładowe oczekiwane wyniki dla IF10 0.70, humidity 50%, 75 kg:

| Temp | FLUID IN |
|---:|---:|
| 0°C | około `0.40L` |
| 10°C | około `0.45L` |
| 20°C | około `0.60L` |
| 30°C | około `0.80L` |
| 35°C | około `0.90L` |

Dla 20°C, humidity 50%, 75 kg:

| IF10 | FLUID IN |
|---:|---:|
| 0.50 | około `0.45L` |
| 0.70 | około `0.60L` |
| 0.90 | około `0.85L` |

### Bateria Karoo i RD BAT

Rząd 6 ma rozdzielić baterię Karoo od baterii tylnej przerzutki.

```text
BURN BAT | BAT LEFT | RD BAT
```

Zasady:

- `BURN BAT` = spadek baterii Karoo/head unit na godzinę;
- `BAT LEFT` = szacowany czas pracy baterii Karoo/head unit;
- `RD BAT` = bateria tylnej przerzutki AXS;
- nie wolno używać baterii Karoo jako wartości `RD BAT`;
- jeśli brak danych AXS, `RD BAT` pokazuje `--`;
- jeśli Karoo SDK daje procent, pokazywać np. `83%`;
- jeśli SDK daje tylko status, pokazywać krótki status, np. `OK`, `LOW`, `CRIT`.

Dane `RD BAT` mają pochodzić z Karoo SDK shifting battery stream:

```text
DataType.Type.SHIFTING_BATTERY
DataType.Field.SHIFTING_BATTERY_STATUS_REAR_DERAILLEUR
```

albo aktualnych nazw w używanej wersji SDK.

### Testy wymagane dla STATS

Minimalne testy jednostkowe / integracyjne:

1. `NP` ignoruje zera z pauzy.
2. `VI` nie rośnie sztucznie po pauzie.
3. `average power` nie zawiera zer z pauzy.
4. `KCAL` nie rośnie podczas pauzy z `0W`.
5. `KCAL` nie rośnie od stale wartości mocy przy stojącym `movingSec`.
6. `reset()` zeruje `lastMovingSec`.
7. `TSS` nie jest zaniżany przez pauzę.
8. `DRIFT` ignoruje pauzy.
9. `DRIFT` pokazuje `0%` jako prawdziwą wartość, nie `--`.
10. `RSRV` nie zależy od długości krótkiej trasy.
11. `RSRV` działa bez trasy.
12. `RSRV` spada z TSS.
13. `RSRV` spada z wysokim IF10.
14. `RSRV` spada z wysokim DRIFT.
15. `RSRV` clamp `0..100`.
16. `CARB IN` rośnie monotonicznie z IF10.
17. `CARB IN` używa `movingSec`, nie `elapsedSec`.
18. `CARB IN` nie rośnie podczas pauzy.
19. `CARB IN` rośnie łagodnie z aktywnym czasem jazdy.
20. `CARB IN` nie spada w upale.
21. `CARB IN` rośnie łagodnie z masą ciała.
22. `CARB IN` clamp około `20..110`.
23. `FLUID IN` rośnie z temperaturą.
24. `FLUID IN` rośnie z IF10.
25. `FLUID IN` rośnie łagodnie z masą ciała.
26. `FLUID IN` clamp około `0.30..1.50`.
27. `FLUID IN` z brakiem temperatury daje sensowną wartość.
28. `UP LEFT` pokazuje `--`, jeśli brak trasy.
29. `ETA` pokazuje `--`, jeśli brak trasy albo brak możliwości obliczenia.
30. `TIME STOP = elapsed - moving`, bez wartości ujemnych.
31. `V AVG TOTAL = distance / elapsed time`.
32. `BURN BAT` fallback przy braku danych startowych.
33. `BURN BAT` liczy realny spadek baterii na godzinę.
34. `BAT LEFT` fallback, jeśli brak `BURN BAT`.
35. `BAT LEFT` liczy czas z aktualnej baterii i zużycia.
36. `RD BAT` pokazuje `--`, jeśli brak danych AXS.
37. `RD BAT` pokazuje procent, jeśli dane AXS są dostępne.
38. `RD BAT` nie używa baterii Karoo/head unit.
39. Każdy z 18 slotów ma fallback `--`.

### Czego nie robić w STATS

- Nie robić pełnoekranowego dashboardu.
- Nie wracać do układu 3×2 dla `STATS`.
- Nie zmieniać `LIVE` ani `DYN` przy poprawianiu `STATS`.
- Nie używać auto-size w `STATS`.
- Nie używać wartości demo w produkcyjnym `STATS`.
- Nie pokazywać `0` jako braku danych.
- Nie mieszać baterii Karoo z baterią przerzutki.
- Nie mieszać rekomendowanego intake z faktycznie zjedzonym/wypitym intake.
- Nie liczyć statystyk treningowych z zer z pauzy.
- Nie używać długości trasy jako podstawy `RSRV`.
- Nie liczyć `CARB IN` z `elapsedSec`.

### Definicja gotowości STATS

`STATS` można uznać za gotowe do testów terenowych, gdy:

- ma układ 3×6;
- pokazuje 18 ustalonych pól;
- wartości mają `22sp`, jednostki około `18sp`, etykiety około `9sp`;
- tła rzędów są pasmowane czarne / ciemnogranatowe;
- jednostki są renderowane osobnym `TextView`;
- fallback `--` działa bez jednostki;
- `RD BAT` jest oddzielone od baterii Karoo;
- pauzy nie psują `NP`, `IF`, `VI`, `TSS`, `KCAL`, `DRIFT`;
- `RSRV` nie zależy od długości trasy;
- `CARB IN` i `FLUID IN` skalują się zgodnie z intensywnością, czasem aktywnej jazdy, temperaturą i masą;
- `LIVE` i `DYN` pozostają nietknięte;
- testy jednostkowe przechodzą.

## Pole DYN 3×2 — stały HUD kontekstowy

`DYN` to drugie stałe pole danych, wyświetlane nad `LIVE 3×2`.

`DYN` nie jest polem alertów. W MVP nie nadpisuje treści komunikatami i nie zastępuje `LIVE`.

Układ ekranowy:

```text
[DYN  3×2]   ← dane kontekstowe / drugorzędne
[LIVE 3×2]   ← główne dane jazdy / sterowanie wysiłkiem
```

Rola pól:

```text
LIVE 3×2 -> Czy teraz jadę dobrze?
DYN  3×2 -> Jaki jest szerszy kontekst jazdy?
```

### Układ DYN

Pole `DYN` ma mieć dokładnie 8 slotów w układzie 4×2:

```text
┌──────┬──────┬──────┬──────┐
│  D   │ IF10 │ HRD  │  W'  │
├──────┼──────┼──────┼──────┤
│ DTD  │ Vśr  │  T   │  W   │
└──────┴──────┴──────┴──────┘
```

Uwaga: klasa `BpDyn3x2DataType.kt` ma historyczną nazwę 3×2, ale faktyczny layout to **DYN 4×2** (8 slotów).

### Slot 1 — IF10 / HRD

Jedno subpole pokazuje dwie wartości:

- `IF10`
- `HRD`

Przykład:

```text
IF .78
NP 196
```

Etykiety mają być minimalne:

```text
IF10
NP10
```

Znaczenie:

- IF10: koszt intensywności z ostatnich 10 minut;
- HRD: lokalny dryf HR-power / koszt fizjologiczny;
- informacja kontekstowa, nie główny alarm;
- nie może dominować nad `Power` z pola `LIVE`.

Formatowanie:

- IF: 2 miejsca po przecinku, np. `.78` albo `0.78`;
- NP: liczba całkowita w watach, np. `196`;
- jednostka `W` może być pominięta, jeśli układ jest ciasny.

### Slot 2 — Temperatura

Pokazuje aktualną temperaturę.

Przykład:

```text
12°C
```

Etykieta:

```text
TMP
```

Kolorystyka spokojna, informacyjna:

- zimno: subtelny niebieskawy tint;
- neutralnie: ciemny grafit / neutral;
- ciepło: subtelny żółty lub pomarańczowy tint.

W MVP nie robić z temperatury alertu.

### Slot 3 — Wiatr

Pokazuje wiatr tak jak w starym `LIVE`:

- strzałka kierunku względem kierunku jazdy;
- siła wiatru w m/s.

Przykład:

```text
↗ 4.2
```

albo:

```text
↗
4.2
```

Jednostka `m/s` ma być bardzo mała albo pominięta, jeżeli wartość zawsze oznacza m/s.

Logika:

- strzałka pokazuje kierunek wiatru względem jazdy;
- liczba pokazuje siłę wiatru;
- kolor/tint zależny od wpływu na jazdę:
  - tailwind / korzystnie: subtelny zielony;
  - boczny: neutralny;
  - headwind: bursztyn lub czerwony tint.

To pole ma być informacyjne, nie alarmowe.

### Slot 4 — Średnia prędkość netto / brutto

Jedno subpole pokazuje dwie średnie:

- średnia netto / moving average;
- średnia brutto / elapsed average, czyli z postojami.

Preferowany przykład:

```text
24.8
22.1
```

Małe etykiety:

```text
MOV
ALL
```

Alternatywnie, jeśli zmieści się czytelnie:

```text
24.8/22.1
```

Preferowana wersja MVP: układ dwuliniowy, bo jest stabilniejszy i czytelniejszy w małym polu.

Znaczenie:

- pokazuje, jak postoje obniżają realną średnią całej wyprawy;
- pomaga oceniać tempo logistyczne, a nie chwilową jazdę.

### Slot 5 — Dystans przejechany / dystans do celu

Jedno subpole pokazuje:

- dystans przejechany;
- dystans pozostały do celu.

Preferowany przykład:

```text
42
118
```

Małe etykiety:

```text
DONE
LEFT
```

Alternatywnie:

```text
42/118
```

Preferowana wersja MVP: układ dwuliniowy.

Znaczenie:

- pierwsza wartość = dystans już przejechany;
- druga wartość = dystans do celu;
- jednostka km może być pominięta, jeśli nie ma miejsca.

### Slot 6 — ETA

Pokazuje przewidywany czas przyjazdu.

Przykład:

```text
18:35
```

Etykieta:

```text
ETA
```

Na MVP można użyć:

- ETA z Karoo, jeśli dostępne;
- albo prostego ETA liczonego z dystansu do celu i średniej brutto;
- albo prostego ETA liczonego z dystansu do celu i średniej netto.

Preferencja projektowa:

- do turystyki / bikepackingu bardziej użyteczne jest ETA po średniej brutto;
- później można dodać własny algorytm uwzględniający postoje.

### Styl graficzny DYN

`DYN` musi być mniej dominujący niż `LIVE`.

Zasady UI:

- układ 3 kolumny × 2 rzędy;
- takie same ogólne wymiary jak `LIVE 3×2`;
- cyfry duże i czytelne, ale mniejsze lub spokojniejsze wizualnie niż w `LIVE`;
- bez dużych nagłówków;
- etykiety tylko jako małe skróty;
- tła ciemne, spokojne, native-looking;
- subtelne cieniowanie;
- stabilna szerokość cyfr;
- wartości nie mogą skakać przy zmianie liczby cyfr;
- unikać agresywnych pełnych kafli kolorystycznych;
- kolory mają być informacyjne, nie alarmowe;
- `DYN` ma być czytelny jednym spojrzeniem, ale nie może odciągać uwagi od `LIVE`.

### Hierarchia względem LIVE

`LIVE 3×2`:

- główne pole decyzyjne;
- największe cyfry;
- mocniejsza semantyka kolorów;
- dane: Speed, Power, HR, Cadence, Grade, Gear.

`DYN 3×2`:

- drugorzędne pole kontekstowe;
- spokojniejszy wygląd;
- mniejsze napięcie wizualne;
- dane: IF/NP, temperatura, wiatr, średnie, dystans, ETA.

### Dwa tryby DYN

DYN działa w dwóch trybach:

```text
Tryb 1: normal mode — DYN 4×2 / 8 stałych slotów
Tryb 2: message override — dwuliniowy dynamiczny komunikat
```

Zasady:

- `LIVE` **nigdy** nie jest nadpisywane komunikatami;
- DYN **normalnie** pokazuje 8 danych kontekstowych (4×2);
- DYN **może być czasowo nadpisane** dwuliniowym komunikatem dynamicznym;
- wszystkie komunikaty muszą przechodzić przez `MessagePriorityEngine`;
- po `minDisplayMs` i braku ważniejszego komunikatu DYN **wraca** do normalnego 4×2;
- komunikat nie może przerywać ważniejszego komunikatu;
- `MessagePriorityEngine` decyduje, który komunikat jest aktywny.

Implementacja:

- `BpDyn3x2DataType.kt` — historyczna nazwa klasy; faktyczny layout to **DYN 4×2** (8 slotów);
- `bindNormal()` — renderuje 8 stałych slotów;
- `bindMessage()` — ukrywa 7 slotów, recyklinguje `slot_dist` jako kontener komunikatu;
- `RideState.activeRideMessage` — źródło komunikatu z `RideMessageCoordinator`;
- `MessagePriorityEngine` — priorytetyzacja, cooldown, `minDisplayMs`.

### Technical debt — message overlay

Obecnie `bindMessage()` recyklinguje `slot_dist` (pierwszy slot wiersza 1) jako kontener komunikatu dynamicznego.

To działa, ale:

- `slot_dist` traci swoją normalną funkcję (dystans) w trybie message;
- `tv_dist` / `tv_label_dist` są reuse'owane jako line1 / line2 komunikatu;
- layout nie ma osobnego kontenera dedykowanego pod komunikaty.

Docelowo:

- dodać osobny `message_container` w `field_dyn_3x2.xml`;
- `bindMessage()` powinien renderować w `message_container`, nie w `slot_dist`;
- w trybie message wszystkie 8 slotów pozostaje `VISIBLE` ale przyciemnione, albo `GONE` — decyzja projektowa;
- komunikat nie powinien zabierać miejsca żadnemu slotowi danych.

### MVP implementacji DYN

Kolejność prac:

1. Utworzyć layout `DYN 3×2`.
2. Zachować takie same ogólne proporcje jak `LIVE 3×2`.
3. Dodać 6 slotów:
   - IF10 / HRD
   - TEMP
   - WIND
   - AVG netto/brutto
   - DIST done/left
   - ETA
4. Na początku użyć wartości demo.
5. Ustawić stabilne formatowanie liczb.
6. Dodać subtelne tła/cieniowanie.
7. Dopiero później podłączyć realne źródła danych.
8. Nie implementować alertów w tym etapie.

Uwaga: aktualna implementacja ma już **DYN 4×2** (8 slotów) z message override. Powyższa lista to historyczny plan MVP.

### Przykład docelowego wyglądu tekstowego

```text
┌──────────┬──────────┬──────────┐
│ IF .78   │          │    ↗     │
│ NP 196   │   12°C   │   4.2    │
├──────────┼──────────┼──────────┤
│ 24.8     │ 42       │          │
│ 22.1     │ 118      │ 18:35    │
└──────────┴──────────┴──────────┘
```

Lub z małymi etykietami:

```text
┌──────────┬──────────┬──────────┐
│ IF10 .78 │ TMP      │ WIND ↗   │
│ NP10 196 │ 12°C     │ 4.2      │
├──────────┼──────────┼──────────┤
│ MOV 24.8 │ DONE 42  │ ETA      │
│ ALL 22.1 │ LEFT118  │ 18:35    │
└──────────┴──────────┴──────────┘
```

### Definicja gotowości DYN

DYN MVP jest gotowy, gdy:

- wyświetla się nad `LIVE 3×2`;
- ma układ 4×2 (8 slotów);
- ma takie same ogólne proporcje jak `LIVE`;
- pokazuje 8 danych kontekstowych;
- wartości są czytelne na małym polu mapy;
- cyfry nie skaczą;
- `DYN` wygląda spokojniej niż `LIVE`;
- nie zasłania ani nie zastępuje `LIVE`;
- message override działa przez `MessagePriorityEngine`;
- po `minDisplayMs` DYN wraca do normalnego 4×2.

## Kolory i cieniowanie

Przyjęty kierunek:
- logika stref/statusów inspirowana Zwift;
- estetyka subtelna jak Barberfish;
- nie agresywne pełne kafle jak VinHKE.

Domyślnie styl `subtle`:
- ciemny bazowy kafel;
- delikatny tint/status;
- duże jasne cyfry;
- opcjonalnie delikatna ramka/glow.

Nie robić pełnego mocnego zalania kolorem dla wszystkich 6 subpól, bo na ekranie mapy zrobi się wizualny chaos.

## Architektura logiki

Nie pisać 6 niezależnych algorytmów. Najpierw stworzyć wspólny model kontekstu jazdy, a pola mają tylko interpretować ten sam kontekst.

Docelowy podział:

```text
RideContext
├─ RiderState
├─ RouteContext
├─ SurfaceContext
├─ EffortContext
├─ GearContext
└─ DisplayState
```

Moduły logiczne:

```text
TodayProfile
RouteProfile
SurfaceProfile
ClimbProfile
PowerAdvisor
HrStrainModel
CadenceModel
GearAdvisor
ColorEngine
```

Każde pole korzysta ze wspólnego kontekstu:

```text
Speed   -> speed vs reference/expected speed
Power   -> current power vs sustainable power range
HR      -> HR strain vs expected HR
Cadence -> cadence vs expected cadence
Grade   -> current grade / terrain state
Gear    -> gear fit score
```

Dzięki temu późniejsze dodanie nawierzchni, W’bal, Today Factor albo AI supportu nie powinno rozwalić UI ani logiki pól.

## Źródła danych

### HR

HR brać z Karoo SDK jako gotowy strumień `HEART_RATE`.

Nie czytać pasa HR bezpośrednio.

Obsłużyć stany:
- brak sensora;
- searching;
- streaming;
- stara wartość/stale data.

### Gear

Gear brać z danych Karoo/AXS drivetrain, nie zgadywać z prędkości i kadencji.

Format wartości:
- `40×15`
- fallback: `--×--`

Nie mieszać indeksu biegu z liczbą zębów bez pewnej konfiguracji kasety/tarczy.

### Surface / nawierzchnia

Docelowo użyć podejścia jak Timklge / RouteGraph:
- poprosić o dostęp do offline maps Karoo;
- po załadowaniu trasy analizować segmenty;
- wyciągać surface z map;
- zapisać lokalnie profil nawierzchni po dystansie;
- w czasie jazdy robić lookup: aktualna pozycja / dystans -> surface.

W architekturze od razu zostawić miejsce na:

```text
SurfaceContextProvider
currentSurface
upcomingSurface
surfaceDifficultyFactor
```

Nawierzchnia ma wpływać na:
- Speed;
- Power;
- Cadence;
- Gear.

## Logika pól

### Speed

Speed odnosić do średniej jazdy, średniej planowanej albo wartości oczekiwanej.

Na start:
- neutralny zakres: ±15% względem średniej referencyjnej;
- poniżej −15%: czerwony tint/cieniowanie;
- powyżej +15%: zielony tint/cieniowanie;
- w zakresie ±15%: neutralne/brak wyraźnego cieniowania.

Speed nie ma krzyczeć alarmowo przy chwilowym spadku na piachu, zakręcie albo podjeździe. W przyszłości uwzględnić nawierzchnię i grade.

### Power

Power nie jest statycznym kolorem wg stref FTP.

Power ma być lokalnym `sustainable power advisor`.

System powinien znać:
- FTP/CP;
- Today Factor/freshness;
- W’bal;
- cel jazdy;
- kontekst trasy.

Power ma dynamicznie oceniać, czy aktualna moc jest rozsądna w danym kontekście:
- płasko / brak trudności przed Tobą;
- krótki podjazd;
- długi podjazd;
- remaining distance/time;
- remaining elevation;
- W’bal;
- typ jazdy/cel;
- czas jazdy;
- HR drift;
- temperatura;
- nawierzchnia;
- kadencja;
- trend mocy/HR/W’bal;
- wiatr.

Na podjazdach:
- krótki podjazd może tolerować wyższą moc;
- długi podjazd lub dużo remaining UP powinno obniżać target i szybciej ostrzegać.

W’bal jako override:
- szybki lub głęboki spadek W’bal powoduje żółte/czerwone cieniowanie niezależnie od chwilowego kontekstu.

Wiatr:
- przy długim headwind system ma pilnować, czy użytkownik nie próbuje utrzymywać średniej zbyt wysoką mocą;
- przy tailwind nie interpretować wysokiej prędkości jako niskiego kosztu.

Semantyka kolorów:
- za lekko / poniżej sensownego zakresu: chłodny niebieski/szary tint;
- sustainable / sweet: neutralny lub zielony tint;
- koszt rośnie: żółty/bursztynowy tint;
- przepalanie przyszłości jazdy: czerwony/bordowy tint.

MVP Power:
- flat target z Today Factor;
- short climb target + tolerancja;
- long climb target konserwatywny;
- W’bal override.

### HR

HR nie jest prostym wyświetlaczem stref tętna.

Power pokazuje koszt mechaniczny.
HR pokazuje reakcję organizmu na ten koszt.

HR ma odpowiadać na pytanie:

```text
jak organizm reaguje na aktualny wysiłek?
```

Nie tylko:

```text
w której strefie HR jestem?
```

HR powinno oceniać:
- HR vs expected HR dla danej mocy;
- HR drift;
- czas jazdy;
- temperaturę;
- fatigue / Today Factor;
- trend HR;
- recovery po wysiłku;
- pośrednio możliwe nawodnienie.

Kolor/cieniowanie HR ma być wolniejsze i stabilniejsze niż Power:
- większy smoothing;
- histereza;
- brak nerwowego skakania.

Semantyka kolorów:
- niski/ekonomiczny koszt: chłodny grafit/zieleń;
- normalny wysiłek: zielony/neutralny;
- strain rośnie: bursztyn;
- HR drift/gotowanie: czerwony.

### Cadence

Cadence nie ma być oceniana szosowo według uniwersalnego targetu 90 rpm.

Użytkownik preferuje niższą kadencję około 60–70 rpm.

Cadence ma oceniać:
- czy obecna kadencja jest efektywna i ekonomiczna w danym kontekście.

Cadence powinno być powiązane z:
- Gear;
- Power;
- Grade;
- Fatigue;
- Surface.

Sama liczba rpm jest wskaźnikiem pomocniczym.

Docelowe stany:
- za nisko;
- sweet spot;
- za wysoko.

Target kadencji może być dynamiczny:
- inny na podjeździe;
- inny na płaskim;
- inny przy zmęczeniu;
- inny na gravelu.

Cadence i Gear mają działać razem:
- Cadence pokazuje rytm pedałowania;
- Gear interpretuje mechaniczny koszt tego rytmu.

### Grade

Grade ma pokazywać nachylenie dodatnie i ujemne.

Pole Grade ma pozostać względnie proste: informuje, co robi teren, nie dubluje inteligencji Power/HR/Gear.

Przyjąć skalę i kolory możliwie zgodne z natywną logiką Karoo/Climber.

Rozróżnienia:
- mocny zjazd;
- zjazd;
- neutral/płasko;
- lekki podjazd;
- mocny podjazd;
- stromo/bardzo stromo.

Grade musi mieć:
- smoothing;
- histerezę;
- brak migotania przy chwilowych zmianach nachylenia.

### Gear

Gear to najbardziej interpretacyjne pole.

Nie tylko pokazuje przełożenie.
Ma oceniać, czy obecne przełożenie ma sens.

Rola Gear:

```text
mechanical efficiency advisor
```

Gear musi oceniać:
- aktualne przełożenie;
- cadence;
- power;
- grade;
- speed;
- surface;
- preferowany styl jazdy;
- fatigue.

Stany:
- za twardo;
- sweet spot;
- za lekko;
- brak danych.

Przykłady:

Za twardo:
- niska kadencja;
- wysoka moc;
- podjazd;
- HR rośnie;
- niska prędkość;
- trudna nawierzchnia.

Za lekko:
- bardzo wysoka kadencja;
- niska moc;
- płasko/zjazd;
- małe obciążenie.

Sweet spot:
- kadencja zgodna z preferencją;
- moc sustainable;
- HR stabilny;
- teren sensownie obsłużony.

Gear ma być stabilniejsze niż Cadence:
- większa histereza;
- wolniejsze zmiany statusu;
- brak migania przy pojedynczej zmianie biegu.

## AI i komunikaty

W tym polu LIVE nie robimy dynamicznych komunikatów.

AI online nie może być głównym systemem podczas jazdy z powodu:
- baterii;
- opóźnień;
- niezawodności;
- wcześniejszych złych doświadczeń.

Podczas jazdy kluczowa logika musi działać lokalnie na Karoo.

AI na Raspberry Pi / QBot może być używany:
- przed jazdą;
- po jeździe;
- na żądanie;
- do analizy formy;
- do konfiguracji zaleceń;
- jako dodatkowy support.


AI nie zastępuje lokalnej logiki LIVE.

## Dynamiczne komunikaty — Climb Pacing Messages

Pierwszy etap dynamicznych komunikatów ograniczyć do `Climb Pacing Messages`.

Nie projektować ogólnego systemu alertów dla wszystkich danych.
Nie dublować cieniowania pól `LIVE`.
Nie generować komunikatów motywacyjnych ani coachingowych bez konkretnej decyzji.

Rola komunikatów:

```text
pomóc dobrać moc na aktualnym i następnym segmencie podjazdu,
żeby nie ugotować się przed końcem podjazdu i dalszej trasy
```

Komunikaty mają wynikać z całego kontekstu jazdy, nie z zawartości pola `DYN`.
`DYN` albo osobne pole dynamiczne jest tylko rendererem komunikatu.

### Źródło danych o podjazdach

Informacje o podjazdach brać z Karoo SDK / danych Climber, jeśli są dostępne.

Założenie projektowe:

```text
Karoo SDK udostępnia dane o podjazdach / segmentach podjazdu,
bo korzystają z nich aplikacje typu 7Climbs.
```

Nie budować własnej detekcji podjazdów jako głównego źródła, jeśli SDK udostępnia dane Climber/climb.
Własny `ClimbProfile` może być warstwą pomocniczą lub fallbackiem, ale nie powinien zastępować danych Karoo SDK bez potrzeby.

Potrzebne dane z SDK / profilu podjazdu:

- numer aktualnego lub nadchodzącego podjazdu, np. `3/7`;
- długość podjazdu;
- przewyższenie podjazdu;
- średnie nachylenie podjazdu;
- aktualny segment podjazdu;
- średnie nachylenie aktualnego / następnego segmentu;
- dystans do końca segmentu;
- dystans i przewyższenie do końca podjazdu;
- pozostałe przewyższenie na całej trasie, jeśli dostępne.

### Format pola dynamicznego

Pole dynamiczne ma mieć dwie linie.

Czcionka ma być taka jak wartości w `DYN`.
Zakładamy, że komunikaty zmieszczą się w dwóch liniach, więc nie projektować bardzo małej typografii ani długich zdań.

Format ogólny:

```text
LINIA 1: status / kontekst
LINIA 2: konkretna decyzja / liczby
```

Przykłady:

```text
PODJAZD 3/7
850m · 6%
```

```text
SEG 7%
TRZYMAJ 210–230W
```

```text
ODPUŚĆ!
CEL 210–220W
```

```text
PODKRĘĆ
DO 230W
```

```text
3/7 OK
+54m · zostało 420m
```

### Kolory dynamicznych komunikatów

Na etapie testów stosować prostą i jednoznaczną kolorystykę.

Komunikat informacyjny:

```text
ciemne tło
żółta czcionka
```

Komunikat alarmowy:

```text
czerwone tło
biała czcionka
```

Nie stosować wielu wariantów kolorystycznych w pierwszej wersji.
Nie mieszać kolorystyki dynamicznych komunikatów z subtelnym cieniowaniem pól `LIVE`.

### Typy komunikatów MVP

W MVP obsłużyć tylko pięć typów:

1. `CLIMB_AHEAD` — zbliża się podjazd;
2. `SEGMENT_TARGET` — zalecana moc na aktualny / następny segment;
3. `EASE_OFF` — użytkownik jedzie za mocno i przepala zasób;
4. `PUSH_UP` — użytkownik może bezpiecznie podkręcić moc;
5. `CLIMB_SUMMARY` — krótkie podsumowanie po zakończeniu podjazdu.

### CLIMB_AHEAD

Komunikat o zbliżającym się podjeździe ma pokazywać:

- numer podjazdu w trasie, np. `3/7`;
- długość podjazdu;
- średnie nachylenie;
- opcjonalnie przewyższenie, jeśli zmieści się czytelnie.

Preferowany format:

```text
PODJAZD 3/7
850m · 6%
```

Wersja z przewyższeniem, jeśli mieści się bez utraty czytelności:

```text
PODJAZD 3/7
850m · +54m · 6%
```

Moment pokazania komunikatu:

- krótki / lekki podjazd: około `150–250 m` wcześniej;
- normalny podjazd: około `300–500 m` wcześniej;
- długi / stromy / ważny podjazd: około `600–900 m` wcześniej;
- alternatywnie czasowo: około `30–90 s` przed podjazdem, zależnie od trudności.

MVP może użyć hybrydy:

```text
trigger = distanceToClimb <= advanceDistance
       OR timeToClimb <= advanceTime
```

Gdzie `advanceDistance` i `advanceTime` zależą od trudności podjazdu.

### SEGMENT_TARGET

Komunikat w trakcie podjazdu ma pomagać dobrać moc do segmentu.

Preferowany format:

```text
SEG 7%
TRZYMAJ 210–230W
```

Target mocy musi uwzględniać:

- średnie nachylenie segmentu;
- długość segmentu;
- dystans / przewyższenie do końca podjazdu;
- pozostały dystans trasy;
- pozostałe przewyższenie trasy;
- W′bal;
- TodayFactor / świeżość;
- HR strain / dryf;
- aktualną moc i trend mocy.

### EASE_OFF

Komunikat alarmowy, gdy użytkownik jedzie za mocno.

Preferowany format:

```text
ODPUŚĆ!
CEL 210–220W
```

Warunek nie może być tylko chwilowym przekroczeniem mocy.
Komunikat pokazywać, gdy przekroczenie trwa wystarczająco długo i ma znaczenie dla dalszej jazdy.

Przykładowy warunek:

```text
power > targetHigh przez 15–25 s
AND W′bal spada szybko albo jest niski
AND do końca podjazdu / trasy zostało jeszcze istotne obciążenie
```

### PUSH_UP

Komunikat informacyjny, gdy użytkownik może bezpiecznie podkręcić moc.

Preferowany format:

```text
PODKRĘĆ
DO 230W
```

Pokazywać ostrożnie, tylko gdy:

- moc jest poniżej targetu przez dłuższy czas;
- HR strain jest OK;
- W′bal jest stabilny;
- dalszy przebieg podjazdu i trasy pozwala na mocniejszą jazdę.

Nie pokazywać `PUSH_UP`, jeśli po tym segmencie czeka trudny dalszy podjazd, dużo przewyższenia albo użytkownik powinien oszczędzać zasoby.

### CLIMB_SUMMARY

Po zakończeniu podjazdu pokazać krótkie podsumowanie.

Komunikat ma zawierać:

- numer podjazdu, np. `3/7`;
- ocenę pacingu: `ZA MOCNO`, `OK` albo `OSZCZĘDNIE`;
- zrobione przewyższenie na podjeździe;
- pozostałe przewyższenie na trasie.

Preferowany format:

```text
3/7 OK
+54m · zostało 420m
```

Przykłady:

```text
3/7 ZA MOCNO
+54m · zostało 420m
```

```text
3/7 OSZCZĘDNIE
+54m · zostało 420m
```

Nie używać agresywnie oceny `ZA SŁABO`, bo przy długiej jeździe oszczędzanie może być świadomą i dobrą decyzją.
Lepszy status to `OSZCZĘDNIE`.

Moment pokazania:

- po zakończeniu podjazdu;
- po krótkiej stabilizacji, np. `10–20 s`;
- albo po przejechaniu `100–200 m` od końca podjazdu;
- nie natychmiast przy szumnej zmianie grade.

### Ocena pacingu po podjeździe

`ZA MOCNO`, jeśli:

- znacząca część podjazdu była powyżej `targetHigh`;
- W′bal spadł za mocno;
- HR strain wzrósł bardziej niż oczekiwano;
- koszt podjazdu jest zbyt wysoki względem pozostałej trasy.

`OK`, jeśli:

- większość czasu była w zakresie targetu;
- W′bal spadł akceptowalnie;
- HR strain jest zgodny z oczekiwaniem;
- koszt podjazdu pasuje do dalszej trasy.

`OSZCZĘDNIE`, jeśli:

- moc była poniżej targetu;
- HR strain i W′bal były spokojne;
- ale oszczędzanie mogło być sensowne przy dalszej trasie / przewyższeniu.


### Zasady anty-chaos dla Climb Pacing Messages

- Jeden komunikat naraz.
- Dwie linie tekstu.
- Krótki, konkretny przekaz.
- Minimum kilka sekund widoczności.
- Cooldown dla tego samego typu komunikatu.
- Brak migania.
- Brak karuzeli komunikatów.
- Brak dublowania oczywistych kolorów z `LIVE`.
- Komunikat ma się pojawiać tylko wtedy, gdy pomaga podjąć decyzję.

## Dynamiczne komunikaty — Route Horizon Pacing

Drugim modułem dynamicznych komunikatów po `Climb Pacing Messages` ma być `Route Horizon Pacing`.

Ten moduł nie wykrywa długiego kosztu jazdy po fakcie.
Jego zadaniem jest patrzeć przed siebie na najbliższy sensowny horyzont trasy i podać ekonomiczny target mocy.

Rola modułu:

```text
na najbliższym sensownym fragmencie trasy
podpowiedzieć, jaką moc trzymać,
żeby nie przepalać jazdy między podjazdami / manewrami / waypointami
```

`Route Horizon Pacing` nie zastępuje `Climb Pacing`.
Jeśli w horyzoncie znajduje się podjazd z Karoo SDK / Climber, analizę należy uciąć do początku tego podjazdu, a dalej sterowanie przejmuje `Climb Pacing`.

### Horyzont decyzyjny

Nie planować mocy naiwnie do następnego waypointu.
Waypoint może być:

- za blisko, np. `500 m`, więc odcinek jest za krótki na sensowne planowanie mocy;
- za daleko, np. `20 km`, więc predykcja byłaby zbyt ogólna i mało wiarygodna.

Moduł ma wybierać najbliższy sensowny horyzont decyzyjny.

Priorytet końca horyzontu:

1. początek następnego podjazdu z Karoo SDK / Climber;
2. następny istotny manewr nawigacyjny, np. skręt / zmiana drogi;
3. następny waypoint / cue użytkownika;
4. koniec trasy;
5. limit roboczy, domyślnie około `5 km`.

Roboczy model:

```text
horizonEnd = min(
    nextClimbStart,
    nextNavigationManeuver,
    nextWaypoint,
    routeEnd,
    currentDistance + maxPlanningDistance
)
```

### Minimalny i maksymalny dystans planowania

Przyjąć robocze progi:

```text
minUsefulDistance = 1.5–2.0 km
optimalDistance   = 3.0–5.0 km
maxPlanningDistance = 5.0 km
```

Zasady:

- jeśli horyzont jest krótszy niż około `1.5–2.0 km`, nie pokazywać `Route Horizon Pacing`;
- jeśli horyzont ma `3–5 km`, można wygenerować komunikat z targetem mocy;
- jeśli następny waypoint / manewr jest dalej niż `5 km`, analizować tylko najbliższe `5 km`;
- przy długich odcinkach ponawiać ocenę co około `5 km` albo wcześniej, jeśli zmieni się sytuacja.

Wyjątek:

```text
jeśli za krótkim odcinkiem zaczyna się istotny podjazd,
to nie jest Route Horizon Pacing, tylko CLIMB_AHEAD
```

Przykład:

```text
do podjazdu 700 m -> pokazuje Climb Pacing / CLIMB_AHEAD
nie pokazuje: DO PODJAZDU 0.7km / TRZYMAJ 180W
```

### Manewr nawigacyjny jako koniec horyzontu

Następny manewr nawigacyjny może być lepszym końcem horyzontu niż waypoint.

Manewr, np. skręt, może oznaczać:

- zmianę kierunku względem wiatru;
- zmianę nawierzchni;
- zmianę drogi;
- zmianę ekspozycji;
- początek innego rytmu jazdy.

Dlatego, jeśli do skrętu jest sensowny dystans, np. `3.2 km`, komunikat może dotyczyć właśnie tego horyzontu.

Przykład:

```text
DO SKRĘTU 3.2km
TRZYMAJ ~180W
```

Jeśli do skrętu jest np. `400–800 m`, nie generować targetu mocy, bo odcinek jest za krótki.

### Długie odcinki między waypointami

Jeśli kolejny waypoint / cue jest bardzo daleko, np. `20 km`, nie planować całych `20 km`.

W takim przypadku dzielić analizę na krótsze horyzonty:

```text
0–5 km   -> nowa ocena i komunikat
5–10 km  -> nowa ocena i komunikat
10–15 km -> nowa ocena i komunikat
15–20 km -> nowa ocena albo przejście do manewru / waypointu
```

Przykładowe komunikaty:

```text
NAST. 5km
TRZYMAJ ~180W
```

```text
NAST. 5km
ZEJDŹ 160–175W
```

```text
NAST. 5km
MOŻESZ ~190W
```

### Źródła danych

Target mocy na horyzont ma wynikać z całego kontekstu jazdy, nie z samego dystansu.

Uwzględniać:

- FTP / CP;
- TodayFactor / świeżość;
- RSRV;
- W′bal;
- HR strain / dryf;
- IF10;
- NP10;
- dotychczasowy koszt jazdy;
- pozostały dystans trasy;
- pozostałe przewyższenie trasy;
- nadchodzące podjazdy z Karoo SDK / Climber;
- dotychczasową średnią z trasy jako kontekst, nie jako cel sam w sobie.

Średnia prędkość może być punktem odniesienia, ale komunikat nie ma mówić użytkownikowi, żeby gonił średnią.
Główna decyzja ma dotyczyć mocy.

### Nawierzchnia

Nawierzchnia może być opcjonalnym korektorem, ale nie jest warunkiem MVP.

Założenie:

```text
czy po piachu, czy po asfalcie,
użytkownik nadal może próbować trzymać zadaną moc;
zmieni się głównie prędkość, a nie sama decyzja o koszcie w watach
```

Dlatego MVP może działać bez rozpoznania nawierzchni.
Jeśli `SurfaceContext` będzie dostępny, można później korygować target i komunikat, ale nie blokować modułu brakiem danych o nawierzchni.

### Format komunikatów

Pole dynamiczne nadal ma mieć dwie linie i czcionkę jak wartości w `DYN`.

Przykłady:

```text
DO SKRĘTU 3.2km
TRZYMAJ ~180W
```

```text
DO PKT 4.0km
TRZYMAJ ~180W
```

```text
NAST. 5km
TRZYMAJ ~180W
```

```text
NAST. 5km
ZEJDŹ 160–175W
```

```text
NAST. 5km
MOŻESZ ~190W
```

Nie pokazywać komunikatów typu:

```text
DO PKT 20km
TRZYMAJ ~180W
```

Taki komunikat udaje zbyt dużą precyzję.
Dla długiego odcinka używać `NAST. 5km` i ponawiać ocenę.

### Kiedy generować komunikat

Generować `Route Horizon Pacing` tylko wtedy, gdy zmienia decyzję użytkownika.

Dobre momenty:

- po minięciu waypointu / cue;
- po minięciu istotnego manewru;
- po zakończeniu podjazdu i komunikacie `CLIMB_SUMMARY`;
- na długim odcinku bez manewrów co około `5 km`;
- gdy target mocy zmieni się istotnie, np. o `15–20 W`;
- gdy RSRV, W′bal albo HR strain wymusza korektę;
- gdy przed użytkownikiem pojawia się początek podjazdu i trzeba zakończyć horyzont.

Nie generować:

- dla odcinków krótszych niż około `1.5–2.0 km`;
- co kilkaset metrów bez zmiany sytuacji;
- jeśli aktywny jest `Climb Pacing`;
- jeśli komunikat tylko powtarza cieniowanie `LIVE`;
- jeśli target nie różni się znacząco od aktualnego zachowania.

### Priorytet względem Climb Pacing

Hierarchia:

```text
Climb Pacing Messages
> Route Horizon Pacing Messages
> przyszłe pozostałe komunikaty
```

Jeśli aktywny jest podjazd z Karoo SDK / Climber albo zbliża się istotny podjazd w progu `CLIMB_AHEAD`, `Route Horizon Pacing` ma milczeć.

### Typy komunikatów MVP

MVP może mieć trzy typy:

1. `HORIZON_HOLD` — trzymaj ekonomiczną moc;
2. `HORIZON_EASE` — zejdź z mocy, bo koszt jest za wysoki względem dalszej trasy;
3. `HORIZON_PUSH` — możesz podkręcić moc, jeśli stan organizmu i dalsza trasa na to pozwalają.

Przykłady:

```text
NAST. 5km
TRZYMAJ ~180W
```

```text
DO SKRĘTU 3.2km
ZEJDŹ 160–175W
```

```text
DO PKT 4.0km
MOŻESZ ~190W
```

### Zasady anty-chaos dla Route Horizon Pacing

- Jeden komunikat naraz.
- Dwie linie tekstu.
- Nie planować zbyt krótkich odcinków.
- Nie planować długich odcinków jako jednej predykcji.
- Ucinać analizę do początku podjazdu z Karoo SDK / Climber.
- Używać manewrów nawigacyjnych jako sensownych punktów końca horyzontu.
- Ponawiać ocenę na długich odcinkach co około `5 km` albo przy istotnej zmianie stanu.
- Komunikat ma dotyczyć mocy, nie gonienia średniej prędkości.
- Nie dublować oczywistych kolorów z `LIVE`.





## Dynamiczne komunikaty — Global Message Priority / MessagePriorityEngine

Wszystkie dynamiczne komunikaty muszą przechodzić przez wspólny `MessagePriorityEngine`.

Nie implementować priorytetów wyłącznie lokalnie w pojedynczych modułach, bo doprowadzi to do konfliktów między komunikatami.

Rola `MessagePriorityEngine`:

```text
wybrać jeden aktywny komunikat,
uwzględnić priorytet, severity, cooldown, aktualny kontekst jazdy,
oraz to, czy użytkownik już skorygował zachowanie
```

### Globalna hierarchia robocza

Robocza kolejność priorytetów:

```text
1. Critical sensor / data loss / setup critical
2. Climb Pacing alarm
3. HR / Power / W′bal alarm
4. Light / Twilight alarm after dusk
5. Finish / Route Risk
6. Climb Pacing info
7. Route Horizon Pacing
8. Intake Reminders
9. Weather Shift / Environmental Risk
10. Stop / Pause Discipline
11. Light / Twilight informational reminder
12. Ride Trend informational messages
13. Reserve Available
```

### Zasady wyboru komunikatu

Komunikat może zostać pokazany tylko wtedy, gdy:

- moduł jest włączony w SETUP extension;
- komunikaty globalnie są włączone;
- komunikat nie jest zablokowany cooldownem;
- nie istnieje ważniejszy aktywny komunikat;
- komunikat może realnie zmienić decyzję użytkownika;
- komunikat nie dubluje oczywistego cieniowania `LIVE`;
- komunikat mieści się w dwuliniowym formacie dynamicznego pola.

### Severity

Każdy komunikat powinien mieć severity:

```kotlin
enum class MessageSeverity {
    INFO,
    WARNING,
    ALARM,
    CRITICAL
}
```

Kolory testowe:

```text
INFO / WARNING = ciemne tło + żółta czcionka
ALARM / CRITICAL = czerwone tło + biała czcionka
```

### Higiena globalna

Globalne zasady higieny:

- jeden komunikat naraz;
- minimum kilka sekund widoczności;
- brak migania;
- brak karuzeli komunikatów;
- cooldown per typ komunikatu;
- limit komunikatów informacyjnych na godzinę;
- alarm może przerwać komunikat informacyjny;
- komunikat informacyjny nie powinien przerywać alarmu;
- nie powtarzać tego samego komunikatu, jeśli użytkownik skorygował zachowanie;
- nie generować komunikatów z modułów wyłączonych w SETUP extension.

### Centralny model komunikatu

Docelowy model może wyglądać podobnie:

```kotlin
data class RideMessage(
    val type: RideMessageType,
    val module: RideMessageModule,
    val severity: MessageSeverity,
    val priority: Int,
    val line1: String,
    val line2: String,
    val minDisplayMs: Long,
    val cooldownMs: Long,
    val createdAtMs: Long
)
```

Wszystkie moduły dynamiczne generują kandydatów `RideMessage`, a `MessagePriorityEngine` wybiera jeden komunikat do pokazania.

## Dynamiczne komunikaty — Audio Cue

Audio cue ma zwrócić uwagę użytkownika, gdy pojawia się dynamiczny komunikat, bo użytkownik nie patrzy stale w ekran.

### Zasady podstawowe

- audio cue jest powiązane z `RideMessage`, nie z DYN jako takim;
- dźwięk gra **tylko** przy pojawieniu się nowego aktywnego komunikatu wybranego przez `MessagePriorityEngine`;
- nie gra co tick;
- nie gra przy każdym re-renderze DYN;
- nie gra przy przedłużeniu tego samego komunikatu (`minDisplayMs` extend);
- musi mieć cooldown, np. 10–15 s;
- musi mieć możliwość wyłączenia w SETUP;
- implementacyjnie można rozważyć Android `ToneGenerator`, ale musi nie crashować, jeśli audio jest niedostępne;
- logować `AUDIO_CUE played/skipped`.

### Trigger — gdzie podpiąć

Audio cue musi być wyzwalane przy **zmianie aktywnego komunikatu**, nie w `bindMessage()`.

Poprawne miejsce:

- `MessagePriorityEngine.select()` — gdy zwraca **nowy** komunikat (inny `messageKey` niż poprzedni `activeMessage`);
- albo `RideMessageCoordinator.selectMessage()` — po porównaniu z `currentActiveMessage()`;
- **nie** w `BpDyn3x2DataType.bindMessage()` — to jest renderer, nie decydent;
- **nie** w `RideEngine.updateState()` — za często, co tick.

Model triggera:

```kotlin
val previousKey = MessagePriorityEngine.messageKey(previousActive)
val newKey = MessagePriorityEngine.messageKey(newActive)
if (newKey != null && newKey != previousKey) {
    audioCueEngine.playIfAllowed(newActive.severity)
}
```

### Tryby ustawienia audio

W SETUP extension:

```text
Audio cue mode: OFF / IMPORTANT / ALL
```

Znaczenie:

| Tryb | Kiedy gra |
|---|---|
| `OFF` | nigdy |
| `IMPORTANT` | `WARNING`, `ALARM`, `CRITICAL` |
| `ALL` | `INFO`, `WARNING`, `ALARM`, `CRITICAL` |

Domyślnie preferowane: **IMPORTANT**.

Zasady:

- `INFO` może być bez dźwięku w trybie `IMPORTANT`;
- `WARNING`/`ALARM`/`CRITICAL` powinny dawać krótki sygnał;
- cooldown 10–15 s dotyczy każdego play, niezależnie od trybu;
- jeśli cooldown nie minął, logować `AUDIO_CUE skipped (cooldown)`.

### Implementacja — wskazówki

- użyć `ToneGenerator` z krótkim tonem (np. `TONE_PROP_BEEP`, 150–200 ms);
- otoczyć `try/catch` — audio może być niedostępne (brak uprawnień, hardware, muted);
- jeśli `ToneGenerator` nie jest dostępny, fallback na `MediaPlayer` z zasobem `.ogg`/`.wav`;
- jeśli oba zawiodą, logować `AUDIO_CUE failed` i nie crashować;
- audio cue nie może blokować głównego wątku;
- audio cue nie może opóźniać renderowania DYN.

### Logowanie

```text
AUDIO_CUE played: CLIMB_AHEAD (ALARM)
AUDIO_CUE skipped: same message (cooldown)
AUDIO_CUE skipped: mode=IMPORTANT, severity=INFO
AUDIO_CUE failed: ToneGenerator unavailable
```

### Czego nie robić

- Nie grać dźwięku co tick DYN.
- Nie grać dźwięku przy re-renderze tego samego komunikatu.
- Nie grać dźwięku w trybie `OFF`.
- Nie blokować UI thread generowaniem dźwięku.
- Nie crashować aplikacji, jeśli audio jest niedostępne.
- Nie podłączać audio cue w `bindMessage()` — to renderer, nie decydent.

## Dynamiczne komunikaty — Light / Twilight Reminder

Dodatkowym modułem dynamicznych komunikatów może być `Light / Twilight Reminder`.

To nie jest komunikat treningowy ani pacingowy.
To komunikat logistyczno-bezpieczeństwowy, który ma pomóc włączyć lampę przednią / tylną zanim widoczność realnie się pogorszy.

### Rola modułu

Moduł ma ostrzegać o zbliżającym się:

- cywilnym zmierzchu;
- cywilnym świcie;
- jeździe w warunkach przejściowych światła.

Używać cywilnego zmierzchu i cywilnego świtu, a nie wyłącznie zachodu/wschodu słońca.

### Źródła danych

Moduł powinien korzystać z:

- aktualnej lokalizacji / pozycji GPS;
- daty;
- lokalnego czasu;
- obliczonego civil twilight dla pozycji;
- ETA / przewidywanego czasu zakończenia trasy;
- dystansu i czasu do końca trasy;
- statusu jazdy po trasie, jeśli dostępny.

Obliczenia twilight powinny działać lokalnie, jeśli to możliwe.
Nie opierać modułu na AI online.

### Warunek dla zmierzchu

Komunikat o zmierzchu pokazywać około `30 min` przed cywilnym zmierzchem, ale tylko jeśli jazda prawdopodobnie jeszcze będzie trwać.

Zasada:

```text
showTwilightWarning if:
    timeToCivilDusk <= 30 min
    AND ETA > civilDusk - 15 min
```

Czyli nie pokazywać komunikatu, jeśli z ETA wynika, że trasa zakończy się co najmniej około `15 min` przed cywilnym zmierzchem.

Przykłady:

```text
ZMIERZCH ZA 30M
WŁĄCZ LAMPĘ
```

```text
ZMIERZCH BLISKO
LAMPKA PRZÓD
```

### Warunek dla świtu

Komunikat o świcie może być użyteczny przy jeździe nocnej / bardzo wczesnej.

Pokazywać około `30 min` przed cywilnym świtem, jeśli użytkownik nadal jedzie w nocy lub w warunkach słabego światła.

Przykłady:

```text
ŚWIT ZA 30M
ŚWIATŁO WRACA
```

```text
ŚWIT BLISKO
JESZCZE LAMPA
```

Komunikat o świcie ma mieć niższy priorytet niż ostrzeżenie o zmierzchu, bo zwykle nie wymaga natychmiastowej akcji poza utrzymaniem oświetlenia.

### ETA i filtr końca trasy

Moduł musi uwzględniać ETA, żeby nie generować zbędnych komunikatów.

Nie pokazywać ostrzeżenia o zmierzchu, jeśli:

```text
ETA <= civilDusk - 15 min
```

Czyli jeśli trasa prawdopodobnie skończy się minimum `15 min` przed cywilnym zmierzchem, komunikat jest zbędny.

Jeśli ETA jest niepewne albo niedostępne, użyć konserwatywnego fallbacku:

- jeśli do końca trasy zostało dużo dystansu / czasu, pokazać komunikat;
- jeśli brak trasy i użytkownik jedzie po zmierzchu lub blisko zmierzchu, pokazać komunikat informacyjny;
- nie udawać precyzji, jeśli ETA jest niedostępne.

### Format komunikatów

Komunikaty mają być dwuliniowe i zgodne z dynamicznym polem komunikatów.

Przykłady:

```text
ZMIERZCH ZA 30M
WŁĄCZ LAMPĘ
```

```text
ZMIERZCH BLISKO
LAMPKA PRZÓD
```

```text
ŚWIT ZA 30M
ŚWIATŁO WRACA
```

```text
ŚWIT BLISKO
JESZCZE LAMPA
```

### Kolory

Komunikat o zmierzchu traktować jako informacyjny / prewencyjny:

```text
ciemne tło
żółta czcionka
```

Jeśli cywilny zmierzch już minął, a jazda nadal trwa, można użyć komunikatu alarmowego:

```text
czerwone tło
biała czcionka
```

Przykład alarmowy:

```text
JUŻ PO ZMIERZCHU
SPRAWDŹ LAMPĘ
```

### Priorytet

Robocza hierarchia:

```text
Climb Pacing Messages
> alarmowe Ride Trend Messages HR / Power / W′bal
> Light / Twilight alarm after dusk
> Route Horizon Pacing Messages
> Intake Reminders
> Light / Twilight informational reminder
> zwykłe Ride Trend Messages
```

Komunikat twilight nie powinien przerywać krytycznego pacingu na trudnym podjeździe, ale może pojawić się na spokojnym odcinku albo po zakończeniu podjazdu.

### Higiena powiadomień

- pokazać ostrzeżenie o zmierzchu maksymalnie raz przed danym civil dusk;
- ewentualnie powtórzyć tylko jeśli civil dusk już minął, a jazda nadal trwa;
- nie pokazywać komunikatu, jeśli trasa skończy się co najmniej `15 min` przed civil dusk;
- nie generować komunikatów świt/zmierzch co kilka minut;
- wszystkie komunikaty przechodzą przez `MessagePriorityEngine`.

### Czego nie robić w Light / Twilight Reminder

- Nie używać zwykłego zachodu słońca jako jedynej podstawy, jeśli dostępny jest civil twilight.
- Nie pokazywać ostrzeżenia o zmierzchu, jeśli ETA wskazuje bezpieczne zakończenie przed zmierzchem.
- Nie generować wielu przypomnień o tym samym zmierzchu / świcie.
- Nie wymagać AI online.
- Nie traktować tego jako komunikatu treningowego.

## Dynamiczne komunikaty — Intake Logging / Nutrition & Hydration

Kolejnym modułem dynamicznym może być `Intake Logging`, czyli szybkie logowanie faktycznie przyjętych węgli i płynów oraz bilans względem rekomendacji.

Ważne rozdzielenie:

```text
CARB IN / FLUID IN w STATS = rekomendowane tempo intake
Intake Logging = faktycznie zjedzone / wypite wartości
```

Nie mieszać rekomendacji z realnym logiem intake.

### Rola modułu

Moduł ma umożliwić:

- przypomnienie o jedzeniu i piciu;
- szybkie wpisanie przyjętych węgli i płynów podczas jazdy;
- prowadzenie bilansu intake względem rekomendacji;
- późniejszą korektę danych, jeśli użytkownik nie kliknął intake w odpowiednim momencie;
- opcjonalne całkowite wyłączenie funkcji w ustawieniach.

### Szybkie wejście z pola DYN

Docelowo kliknięcie / tapnięcie pola `DYN` może otwierać szybkie menu intake, jeśli Karoo SDK i używany typ pola na to pozwalają.

Jeśli tap/click na custom field nie będzie możliwy albo będzie kolidował z natywnym zachowaniem Karoo, funkcję intake trzeba udostępnić alternatywnie:

- jako osobne pole / ekran QBot;
- jako konfigurację w ekranie extension;
- jako osobne kontrolki, jeśli SDK na to pozwoli;
- bez blokowania logiki bilansu.

Nie zakładać bez weryfikacji, że każdy slot `DYN` obsługuje własne kliknięcia.

### Menu szybkiego intake

Menu ma być maksymalnie proste i używalne w trakcie jazdy.

Podstawowy układ:

```text
┌────────────┬────────────┐
│ CARB #g    │ FLUID #ml  │
└────────────┴────────────┘
```

Dwa aktywne kafle:

1. `CARB #g` — dodaje domyślną porcję węgli;
2. `FLUID #ml` — dodaje domyślną porcję płynu.

Przykładowo:

```text
CARB +40g
FLUID +150ml
```

Kliknięcie kafla zapisuje zdarzenie intake do lokalnego bilansu jazdy.

### Konfiguracja porcji

W ustawieniach użytkownik powinien móc skonfigurować domyślne porcje.

Przykłady:

- `CARB default`: `20g`, `30g`, `40g`, `50g`, custom;
- `FLUID default`: `100ml`, `150ml`, `200ml`, `250ml`, custom.

Dla użytkownika domyślna wartość `CARB +40g` jest sensowna jako szybkie kliknięcie, bo odpowiada typowej porcji z żelu / power bottle / zaplanowanej dawki.

Wartości mają być łatwe do zmiany, bo różne produkty mają różną ilość węgli.

### Bilans intake

System powinien prowadzić bilans:

```text
recommendedCarbsTotal = CARB IN g/h * movingTimeHours
actualCarbsTotal = suma klikniętych CARB
carbBalance = actualCarbsTotal - recommendedCarbsTotal

recommendedFluidTotal = FLUID IN L/h * movingTimeHours
actualFluidTotal = suma klikniętych FLUID
fluidBalance = actualFluidTotal - recommendedFluidTotal
```

Zasady:

- rekomendację liczyć z aktywnego czasu jazdy (`movingTime`), nie z postoju;
- realne intake zapisywać ze znacznikiem czasu;
- bilans może być ujemny lub dodatni;
- nie traktować braku kliknięcia jako braku jedzenia z absolutną pewnością — użytkownik mógł zapomnieć zalogować intake;
- komunikaty intake powinny uwzględniać opóźnienia i korekty ręczne.

### Korekta danych po fakcie

Musi istnieć możliwość uzupełnienia danych, jeśli użytkownik nie kliknął intake w odpowiednim momencie.

Wymagane funkcje docelowe:

- dodanie zaległego intake;
- cofnięcie ostatniego wpisu;
- korekta sumy CARB;
- korekta sumy FLUID;
- reset intake dla bieżącej jazdy;
- możliwość wpisania intake po jeździe w celu poprawy bilansu i analizy.

Minimalne MVP korekty:

```text
UNDO LAST
ADD CARB
ADD FLUID
```

Docelowo można dodać:

```text
+10g / +20g / +40g / custom
+100ml / +150ml / +250ml / custom
```

### Możliwość wyłączenia

W ustawieniach musi być możliwość wyłączenia modułu.

Osobne przełączniki:

```text
Enable intake logging: ON/OFF
Enable intake reminders: ON/OFF
Show intake menu from DYN tap: ON/OFF, jeśli technicznie możliwe
```

Jeśli moduł jest wyłączony:

- nie pokazywać komunikatów intake;
- nie wymagać logowania jedzenia/picia;
- `STATS` nadal może pokazywać rekomendowane `CARB IN` i `FLUID IN`, bo to nie jest realny log intake.

### Komunikaty intake

Komunikaty mają być proste i decyzyjne.

Przykłady:

```text
ZJEDZ TERAZ
40g WĘGLI
```

```text
PIJ TERAZ
KILKA ŁYKÓW
```

```text
CARB DEFICYT
+40g TERAZ
```

```text
PŁYNY NISKO
PIJ 150ml
```

Komunikaty intake nie mogą być zbyt częste.
Nie mogą konkurować z `Climb Pacing`, `Route Horizon Pacing` i alarmami HR / Power / W′bal.

### Algorytm przypomnień

Przypomnienia powinny brać pod uwagę:

- rekomendowane `CARB IN`;
- rekomendowane `FLUID IN`;
- movingTime;
- czas od ostatniego zalogowanego intake;
- aktualny deficyt względem rekomendacji;
- IF10;
- NP10;
- temperaturę;
- HR strain;
- RSRV;
- W′bal;
- pozostały dystans;
- pozostałe przewyższenie;
- aktywny podjazd / zbliżający się podjazd;
- możliwość jedzenia/picia na aktualnym odcinku.

Zasada:

```text
nie przypominać o jedzeniu/piciu w najgorszym momencie,
jeśli za chwilę jest stromy podjazd albo aktywny jest alarm pacingu
```

Lepiej przypomnieć:

- przed długim podjazdem;
- na spokojnym odcinku;
- po zakończeniu podjazdu, gdy HR się stabilizuje;
- gdy deficyt narasta i jest jeszcze dużo trasy.

### Podjazdy a intake

Podczas aktywnego `Climb Pacing` komunikaty intake mają niski priorytet.

Wyjątki:

- bardzo duży deficyt węgli;
- bardzo duży deficyt płynów w wysokiej temperaturze;
- długi spokojny podjazd, na którym komunikat nie przeszkadza w pacingu.

Preferowane momenty:

```text
CLIMB_AHEAD daleko przed podjazdem -> można przypomnieć o jedzeniu/piciu przed wysiłkiem
CLIMB_SUMMARY po podjeździe -> po krótkim cooldownie można przypomnieć o intake
aktywny trudny segment -> intake message milczy
```

### Intake a bilans energii / rezerwa

Realny log intake może docelowo wpływać na:

- `RSRV v2`;
- `Reserve Available`;
- `HR Trend`;
- komunikaty o deficycie;
- analizę po jeździe.

Na MVP intake może być najpierw osobnym bilansem bez wpływu na `RSRV`.
Dopiero po walidacji logiki można dodać wpływ deficytu węgli i płynów na `RSRV v2`.

### Priorytet komunikatów intake

Robocza hierarchia:

```text
Climb Pacing Messages
> alarmowe Ride Trend Messages HR / Power / W′bal
> Route Horizon Pacing Messages
> Intake Reminders
> zwykłe Ride Trend Messages
```

W praktyce intake reminders powinny być spokojne i rzadkie.
Alarm intake może pojawić się tylko przy dużym deficycie i realnym znaczeniu dla dalszej jazdy.

### Higiena powiadomień intake

Minimalne zasady:

- nie przypominać częściej niż co `20–30 min` dla węgli;
- nie przypominać częściej niż co `10–15 min` dla płynów w normalnych warunkach;
- w upale płyny mogą mieć krótszy interwał, ale nadal z cooldownem;
- nie pokazywać intake reminder, jeśli użytkownik niedawno kliknął odpowiedni kafel;
- nie pokazywać intake reminder podczas aktywnego alarmu pacingu;
- nie pokazywać kilku komunikatów intake pod rząd;
- nie robić z intake systemu nękających powiadomień;
- wszystkie komunikaty intake muszą przechodzić przez `MessagePriorityEngine`.

### Dane intake

Każdy wpis intake powinien mieć model podobny do:

```kotlin
data class IntakeEvent(
    val timestampMs: Long,
    val movingSec: Long,
    val type: IntakeType,
    val amount: Float,
    val unit: IntakeUnit,
    val source: IntakeSource
)
```

Typy:

```kotlin
enum class IntakeType { CARB, FLUID }
```

Źródło:

```kotlin
enum class IntakeSource {
    QUICK_TILE,
    MANUAL_CORRECTION,
    POST_RIDE_EDIT
}
```

### Czego nie robić w Intake Logging

- Nie mieszać rekomendowanego `CARB IN` / `FLUID IN` z realnie zalogowanym intake.
- Nie wymuszać używania logowania intake.
- Nie blokować działania `STATS`, jeśli intake logging jest wyłączony.
- Nie generować częstych przypomnień.
- Nie pokazywać intake reminders podczas ważniejszych komunikatów pacingu.
- Nie zakładać bez weryfikacji, że tap na `DYN` zawsze jest możliwy w SDK.
- Nie karać użytkownika za brak kliknięcia, bo intake mógł zostać przyjęty, ale niezaloggowany.

## Dynamiczne komunikaty — Ride Trend Messages

Trzecim modułem dynamicznych komunikatów po `Climb Pacing Messages` i `Route Horizon Pacing` mogą być `Ride Trend Messages`.

`Ride Trend Messages` nie reagują na chwilowy stan pola `LIVE`.
`LIVE` pokazuje rekomendacje tu i teraz.
`Ride Trend Messages` mają wykrywać utrwalone wzorce jazdy z ostatniego okresu i zwracać uwagę dopiero wtedy, gdy użytkownik nie skorygował zachowania albo trend zaczyna mieć znaczenie dla dalszej jazdy.

Rola modułu:

```text
analizować styl jazdy w dłuższym oknie,
wykrywać utrwalone nieekonomiczne wzorce,
alarmować tylko przy dużym odchyleniu,
nie produkować ciągłych powiadomień
```

### Model czasowy trendów

Główne okno obserwacji trendu:

```text
30 min
```

Wewnętrzna analiza / checkpoint:

```text
co 10 min
```

Krótsze okno alarmowe dla dużych odchyleń:

```text
rolling 3–8 min
```

Zasady:

- krótkie odchylenie `3–5 min` zwykle jest tylko analizą wewnętrzną;
- checkpoint `10 min` sprawdza, czy trend narasta, słabnie albo został skorygowany;
- pełny trend `30 min` może wygenerować komunikat informacyjny/korekcyjny;
- krótkie okno alarmowe `3–8 min` może wygenerować komunikat alarmowy tylko przy dużym odchyleniu od normy i ryzyku dla dalszej jazdy.

Przykład logiki:

```text
10 min: problem narasta -> zapisz, bez komunikatu
20 min: problem trwa -> zapisz / przygotuj komunikat
30 min: problem utrwalony i nieskorygowany -> pokaż komunikat
3–8 min: bardzo duże odchylenie -> pokaż alarm wcześniej
```

### Zasada wieloczynnikowości

Żaden komunikat trendowy nie może wynikać z pojedynczej wartości.

Nie wolno generować trendu tylko dlatego, że:

- kadencja jest niska;
- moc jest wysoka;
- HR jest wysokie;
- bieg wygląda chwilowo nieoptymalnie;
- `LIVE` przez chwilę pokazuje ostrzeżenie.

Każdy trend musi uwzględniać co najmniej:

- kontekst trasy;
- aktywność podjazdu z Karoo SDK / Climber;
- aktualny lub docelowy target kontekstowy mocy;
- reakcję HR;
- W′bal;
- RSRV;
- czas trwania trendu;
- etap jazdy;
- pozostały dystans;
- pozostałe przewyższenie;
- historię ostatnich komunikatów;
- informację, czy użytkownik skorygował zachowanie po poprzednim komunikacie.

Zasada ogólna:

```text
Trend message = zachowanie + kontekst trasy + reakcja organizmu + rezerwy + czas trwania + brak korekty
```

### Wspólne wejścia dla algorytmów trendów

Algorytmy trendów powinny mieć dostęp do:

- currentPower;
- power3s / power10s / rolling power windows;
- NP10;
- IF10;
- FTP / CP;
- TodayFactor;
- W′bal;
- RSRV;
- HR;
- HR trend;
- HR strain;
- HR drift / decoupling;
- cadence;
- gear;
- gearStatus z `LIVE`;
- grade;
- activeClimb z Karoo SDK / Climber;
- nextClimb z Karoo SDK / Climber;
- postClimbRecovery;
- routeRemainingKm;
- routeRemainingElevation;
- nextClimbDifficulty;
- timeInRide;
- movingTime;
- temperature;
- windImpact;
- surfaceFactor, jeśli dostępny;
- currentTargetPower;
- contextTargetPower;
- active message state;
- last message time;
- correction detected after last message.

### Kontekst podjazdów

Podjazdy muszą wpływać na interpretację trendów.

Zasady:

```text
Cadence Trend:
non-climb only

Gear Trend:
głównie non-climb,
climb osobno / pomocniczo

Power Trend:
climb i non-climb,
ale zawsze względem contextTargetPower

HR Trend:
climb / post-climb / non-climb analizowane osobno
```

### Cadence Trend

`Cadence Trend` ma wykrywać utrwaloną jazdę zbyt siłową, ale nie karać użytkownika za naturalnie niższą kadencję na podjazdach.

Zasady:

- wykluczyć aktywne podjazdy z Karoo SDK / Climber;
- wykluczyć krótki okres po podjeździe, np. `2–3 min`;
- wykluczyć postoje;
- wykluczyć zjazdy / coasting / bardzo niską moc;
- używać osobistego targetu kadencji użytkownika, nie szosowego `90 rpm`;
- dla użytkownika preferującego niższą kadencję traktować zakres około `60–70 rpm` jako sensowny punkt odniesienia;
- komunikat generować dopiero, gdy niska kadencja łączy się z wysokim kosztem, zbyt twardym biegiem albo rosnącym HR strain.

Przykłady:

```text
ZA SIŁOWO
LŻEJ / 65–70RPM
```

```text
KADENCJA TREND
ZA NISKO
```

### Gear Trend

`Gear Trend` ma używać dłuższego okna i sygnalizować nieekonomiczny styl jazdy w ostatnim okresie, nie chwilowy zły bieg.

Preferowane okno:

```text
30 min main window
10 min checkpoint
```

Zasady:

- komunikat ma oznaczać, że w ostatnim okresie użytkownik jechał nieekonomicznie;
- nie reagować na pojedynczą zmianę biegu;
- nie generować komunikatu tylko dlatego, że `LIVE Gear` chwilowo pokazuje `TOO_HARD` albo `TOO_LIGHT`;
- główny trend liczyć z odcinków non-climb;
- podjazdy z Karoo SDK / Climber liczyć osobno albo pomocniczo;
- na aktywnym podjeździe pierwszeństwo ma `Climb Pacing`, nie `Gear Trend`;
- główny problem do wykrywania to zbyt twardy bieg + niska kadencja + rosnący koszt.

Czynniki:

- czas w `TOO_HARD`;
- czas w `TOO_LIGHT`;
- średnia kadencja względem targetu;
- moc względem targetu kontekstowego;
- HR strain;
- reakcja po zmianach biegu;
- czy jazda po zmianie biegu zrobiła się ekonomiczniejsza.

Przykłady:

```text
OST. 30 MIN
ZA TWARDO
```

```text
NIEEKONOMICZNIE
ZMIEŃ LŻEJ
```

```text
ZA SIŁOWO
ZMIEŃ LŻEJ
```

### Power Trend

`Power Trend` nie może opierać się na stałym progu mocy.

Moc ma być oceniana względem `contextTargetPower`.

Zasady:

- na podjeździe porównywać do targetu `Climb Pacing`;
- poza podjazdem porównywać do targetu `Route Horizon Pacing`;
- bez trasy używać sustainable flat target z TodayFactor;
- podjazdy nie są wyłączone z Power Trend, ale zmieniają target;
- komunikat `PRZEPALASZ` generować dopiero, gdy moc jest za wysoka dla aktualnego kontekstu i zaczyna szkodzić dalszej jeździe.

Czynniki:

- powerAvg w oknach `3/5/10/30 min`;
- NP10;
- IF10;
- targetLow / targetHigh;
- W′bal;
- tempo spadku W′bal;
- RSRV trend;
- remainingKm;
- remainingElevation;
- nextClimbDifficulty;
- timeInRide;
- temperature;
- HR response.

Przykłady:

```text
ZA DŁUGO MOCNO
ZEJDŹ 170–185W
```

```text
PRZEPALASZ
ZEJDŹ 170–185W
```

```text
W′BAL SPADA
ODPUŚĆ TERAZ
```

### HR Trend

`HR Trend` musi być wolniejszy i bardziej ostrożny niż `Power Trend`.

Nie traktować wysokiego HR na podjeździe jako automatycznego problemu.

Analizować osobno:

- HR strain na podjeździe;
- HR recovery po podjeździe;
- HR drift poza podjazdami;
- HR względem expected HR dla danej mocy.

Czynniki:

- HR względem expected HR dla aktualnej mocy;
- HR trend przy stałej lub spadającej mocy;
- HR drift / decoupling;
- czas jazdy;
- temperatura;
- TodayFactor;
- fatigue;
- postClimbRecovery;
- power trend;
- W′bal;
- RSRV.

Przykłady:

```text
HR ZA WYSOKIE
ZEJDŹ 10–20W
```

```text
HR NIE SPADA
ODPUŚĆ 3–5 MIN
```

```text
DRIFT ROŚNIE
JEDŹ RÓWNO
```

### Reserve Available Trend

`Reserve Available` ma bardzo niski priorytet i długi cooldown.

Ten komunikat nie może bezmyślnie zachęcać do ciśnięcia.

Pokazywać tylko, gdy:

- RSRV jest wysokie;
- W′bal jest wysokie albo stabilne;
- HR strain jest niski;
- IF10 jest poniżej planu;
- NP10 jest spokojne;
- TodayFactor jest dobry;
- nie ma dużego HR drift;
- nie zbliża się trudny podjazd;
- remainingElevation jest rozsądne;
- remainingKm jest rozsądne;
- to nie jest sam początek bardzo długiej jazdy;
- temperatura i warunki nie podbijają ryzyka.

Przykład:

```text
MASZ REZERWĘ
MOŻESZ +10–15W
```

Cooldown dla tego typu powinien być dłuższy niż dla korekt, np. `20–30 min`.

### Wykrywanie korekty po komunikacie

Po komunikacie system powinien przez kilka minut sprawdzać, czy użytkownik skorygował zachowanie.

Przykłady korekty:

Dla `ZA TWARDO`:

- cadence wzrosła;
- gear zmienił się na lżejszy;
- power spadła do targetu;
- HR strain się stabilizuje.

Dla `PRZEPALASZ`:

- power spadła do targetu;
- W′bal przestał szybko spadać;
- HR strain się stabilizuje.

Dla `HR ZA WYSOKIE`:

- power spadła;
- HR zaczęło spadać;
- HR drift przestał narastać.

Jeśli korekta została wykryta, nie powtarzać komunikatu.
Jeśli korekty nie ma, można ponowić dopiero po cooldownie i tylko jeśli problem dalej jest istotny.

### Higiena powiadomień

Higiena powiadomień jest częścią algorytmu, nie dodatkiem UI.

Minimalne zasady:

- jeden komunikat naraz;
- maksymalnie `1` trend message na `10 min`;
- maksymalnie `3–4` trend messages na godzinę;
- cooldown tego samego typu minimum `10–15 min`;
- `Reserve Available` cooldown minimum `20–30 min`;
- nie pokazywać trend message podczas aktywnego `Climb Pacing`;
- nie pokazywać trend message bezpośrednio po `CLIMB_SUMMARY`, np. przez `2–3 min`;
- nie pokazywać komunikatu, jeśli użytkownik już skorygował zachowanie;
- nie pokazywać komunikatu, jeśli tylko powtarza cieniowanie `LIVE`;
- alarm może przerwać `Route Horizon Pacing`, ale zwykły trend nie powinien;
- komunikaty muszą przechodzić przez wspólny `MessagePriorityEngine`.

### Kolory Ride Trend Messages

Stosować te same zasady testowe jak dla innych komunikatów dynamicznych.

Komunikat informacyjny / korekcyjny:

```text
ciemne tło
żółta czcionka
```

Komunikat alarmowy:

```text
czerwone tło
biała czcionka
```

### Priorytet względem innych modułów

Robocza hierarchia:

```text
Climb Pacing Messages
> Route Horizon Pacing Messages
> Ride Trend Messages
```

Wyjątek:

```text
alarmowe trendy HR / Power / W′bal
mogą przebić Route Horizon Pacing,
ale nie powinny dublować aktywnego Climb Pacing
```

Docelowo wszystkie komunikaty dynamiczne muszą przechodzić przez globalny `MessagePriorityEngine`.

### Typy komunikatów MVP

MVP `Ride Trend Messages`:

1. `CADENCE_LOW_TREND` — utrwalona jazda zbyt siłowa poza podjazdami;
2. `GEAR_TOO_HARD_TREND` — dłuższy okres nieekonomicznie twardego biegu;
3. `POWER_OVER_TARGET_TREND` — dłuższa jazda ponad zalecany target kontekstowy;
4. `HR_STRAIN_TREND` — HR zbyt wysokie względem mocy / kontekstu;
5. `DRIFT_TREND` — narastający dryf HR-power;
6. `RESERVE_AVAILABLE` — duży zapas do wykorzystania, jeśli dalsza trasa na to pozwala.

### Czego nie robić w Ride Trend Messages

- Nie reagować na chwilową zmianę koloru w `LIVE`.
- Nie generować trendu z pojedynczego parametru.
- Nie karać niskiej kadencji na podjazdach.
- Nie oceniać mocy względem stałego progu bez kontekstu.
- Nie traktować wysokiego HR na podjeździe jako automatycznego problemu.
- Nie zachęcać do mocniejszej jazdy, jeśli przed użytkownikiem jest trudny podjazd, dużo przewyższenia albo rosnący HR drift.
- Nie generować wielu komunikatów trendowych pod rząd.
- Nie omijać cooldownów i globalnej priorytetyzacji.


## Dynamiczne komunikaty — Finish / Route Risk

Dodatkowym modułem dynamicznych komunikatów może być `Finish / Route Risk`.

`RSRV` oznacza rezerwę organizmu.
`Finish / Route Risk` ma odpowiadać na inne pytanie:

```text
czy obecna rezerwa, tempo i koszt jazdy wystarczą na pozostałą trasę?
```

Nie mieszać `RSRV` z `Route Risk`.
`RSRV` jest stanem zawodnika.
`Route Risk` jest oceną ryzyka dowiezienia konkretnej trasy w obecnych warunkach.

### Rola modułu

Moduł ma ostrzegać, gdy obecny styl jazdy może zagrozić końcówce trasy.

Przykłady sytuacji:

- dużo przewyższenia zostało, a użytkownik jedzie za mocno;
- RSRV spada szybciej niż oczekiwano;
- W′bal jest niskie, a przed użytkownikiem są jeszcze podjazdy;
- HR drift rośnie, a trasa nie jest blisko końca;
- ETA / zmierzch / postoje zwiększają ryzyko logistyczne;
- użytkownik ma rezerwę i końcówka wygląda bezpiecznie, więc można jechać trochę mocniej.

### Wejścia algorytmu

Uwzględniać:

- RSRV;
- W′bal;
- IF10;
- NP10;
- TSS / accumulated load;
- HR strain;
- HR drift;
- TodayFactor;
- pozostały dystans;
- pozostałe przewyższenie;
- liczba i trudność pozostałych podjazdów z Karoo SDK / Climber;
- Route Horizon target;
- aktualna moc względem targetu;
- temperatura;
- windImpact;
- intake balance, jeśli moduł intake jest aktywny;
- ETA;
- czas do zmierzchu / civil dusk;
- czas postojów.

### Komunikaty

Przykłady:

```text
RYZYKO KOŃCÓWKI
JEDŹ 10W LŻEJ
```

```text
DUŻO UP ZOSTAŁO
OSZCZĘDZAJ
```

```text
REZERWA SPADA
ODPUŚĆ 5 MIN
```

```text
KOŃCÓWKA OK
MOŻESZ +10W
```

```text
TRASA JESZCZE DŁUGA
TRZYMAJ RÓWNO
```

### Zasady

- nie generować komunikatu tylko dlatego, że trasa jest długa;
- nie generować komunikatu tylko dlatego, że RSRV jest niższe niż na starcie;
- komunikat ma wynikać z relacji: stan zawodnika + pozostała trasa + obecny koszt jazdy;
- nie dublować `Climb Pacing`, ale używać danych o pozostałych podjazdach;
- nie zachęcać do mocniejszej jazdy, jeśli przed użytkownikiem jest trudny podjazd, duże `UP LEFT`, wysoki HR drift albo niski W′bal;
- komunikaty pozytywne typu `KOŃCÓWKA OK` mają mieć niski priorytet i długi cooldown.

### Priorytet

`Finish / Route Risk` ma wyższy priorytet niż zwykłe `Ride Trend Messages`, ale niższy niż aktywne `Climb Pacing` i alarmowe HR / Power / W′bal.

Roboczo:

```text
Climb Pacing Messages
> alarmowe Ride Trend Messages HR / Power / W′bal
> Finish / Route Risk
> Route Horizon Pacing Messages
> Intake Reminders
> zwykłe Ride Trend Messages
```

### Czego nie robić

- Nie mieszać `RSRV` z `Route Risk`.
- Nie robić z `Route Risk` prostego procentu ukończenia trasy.
- Nie karać użytkownika za sam fakt, że zostało dużo kilometrów.
- Nie zachęcać do ciśnięcia tylko dlatego, że chwilowo RSRV jest wysokie.
- Nie generować wielu komunikatów o ryzyku końcówki pod rząd.

## Dynamiczne komunikaty — Stop / Pause Discipline

Dodatkowym modułem dynamicznych komunikatów może być `Stop / Pause Discipline`.

To nie jest komunikat treningowy.
To komunikat logistyczny dla długich tras, wypraw gravelowych i bikepackingu, gdzie postoje mogą mocno obniżyć średnią brutto i przesunąć ETA.

### Rola modułu

Moduł ma zwracać uwagę, gdy postoje zaczynają istotnie wpływać na logistykę jazdy.

Przykłady:

```text
POSTÓJ 12 MIN
ETA UCIEKA
```

```text
POSTOJE ROSNĄ
SKRÓĆ PRZERWĘ
```

```text
V BRUTTO SPADA
POSTOJE 28 MIN
```

```text
RUSZ ZA 2 MIN
ZMROK BLISKO
```

### Wejścia algorytmu

Uwzględniać:

- `TIME STOP`;
- `TIME TOTAL`;
- movingTime;
- elapsedTime;
- średnią netto;
- średnią brutto;
- różnicę netto/brutto;
- ETA;
- dystans do końca;
- czas do civil dusk;
- planowany bufor czasu, jeśli jest dostępny;
- postoje w ostatnich 30–60 min;
- długość aktualnego postoju;
- czy użytkownik jest na trasie / poza trasą.

### Warunki

Komunikaty generować tylko, gdy postoje mają realny wpływ.

Przykładowe warunki:

```text
currentStopDuration >= 10–12 min
AND routeRemainingKm jest istotne
```

albo:

```text
totalStopTime rośnie
AND avgGross spada względem avgMoving
AND ETA przesuwa się istotnie
```

albo:

```text
civilDusk blisko
AND aktualny postój zjada bufor do zmierzchu
```

### Zasady higieny

- nie pokazywać przy krótkich postojach technicznych;
- nie pokazywać, jeśli trasa praktycznie się kończy;
- nie pokazywać co minutę podczas długiego postoju;
- komunikat może się powtórzyć po dłuższym czasie, jeśli postój nadal trwa i ryzyko rośnie;
- nie przerywać ważniejszych komunikatów pacingu;
- w trakcie faktycznego postoju komunikat może mieć niższy problem z „rozpraszaniem”, ale nadal ma być rzadki.

### Priorytet

`Stop / Pause Discipline` ma niski lub średni priorytet.
Może wzrosnąć, jeśli łączy się z `Light / Twilight Reminder`, ETA albo ryzykiem końcówki.

### Czego nie robić

- Nie karać za każdy postój.
- Nie generować komunikatu bez wpływu na ETA / średnią brutto / zmierzch.
- Nie używać tonu motywacyjnego.
- Nie robić nękających przypomnień podczas odpoczynku.

## Dynamiczne komunikaty — Weather Shift / Environmental Risk

Dodatkowym modułem dynamicznych komunikatów może być `Weather Shift / Environmental Risk`.

To nie jest zwykłe pokazywanie temperatury lub wiatru, bo te dane są w `DYN`.
Moduł ma wykrywać zmianę warunków albo sytuację środowiskową, która wpływa na decyzję jazdy.

### Rola modułu

Moduł ma ostrzegać o trendach i progach środowiskowych:

- temperatura szybko spada;
- robi się zimno na długim zjeździe / po zmroku;
- upał + wysoka intensywność zwiększają ryzyko odwodnienia;
- wiatr rośnie i użytkownik zaczyna gonić tempo;
- długi headwind zmienia koszt jazdy;
- warunki sugerują wcześniejsze jedzenie/picie albo spokojniejszą moc.

### Wejścia algorytmu

Uwzględniać:

- aktualną temperaturę;
- trend temperatury;
- czas do civil dusk;
- porę dnia;
- wind speed;
- windImpact względem kierunku jazdy;
- IF10;
- NP10;
- HR strain;
- HR drift;
- FLUID IN;
- fluidBalance, jeśli intake logging jest aktywny;
- routeRemainingKm;
- activeClimb / upcomingClimb;
- długie zjazdy albo odcinki bez pracy, jeśli da się je wykryć;
- status ubioru nie jest dostępny lokalnie, więc nie zgadywać konkretnej warstwy.

### Komunikaty

Przykłady:

```text
TEMP SPADA
UWAŻAJ NA ZJAZD
```

```text
UPAŁ + WYSIŁEK
PIJ CZĘŚCIEJ
```

```text
WIATR ROŚNIE
NIE GOŃ TEMPA
```

```text
HEADWIND DŁUGO
TRZYMAJ WATY
```

```text
CHŁODNO + ZMIERZCH
SPRAWDŹ WARSTWĘ
```

### Zasady

- nie dublować stałego pola `TEMP` ani `WIND` w `DYN`;
- komunikat pokazywać tylko przy zmianie trendu, przekroczeniu progu albo realnym wpływie na jazdę;
- nie zgadywać ubrania użytkownika ani nie proponować konkretnej odzieży podczas jazdy;
- upał i płyny powinny integrować się z `Intake Reminders`;
- wiatr powinien integrować się z `Route Horizon Pacing` i targetem mocy;
- chłód / zmierzch może integrować się z `Light / Twilight Reminder`.

### Priorytet

`Weather Shift / Environmental Risk` ma średni priorytet, gdy wpływa na bezpieczeństwo lub koszt jazdy.
Ma niski priorytet, jeśli jest tylko informacją.

### Czego nie robić

- Nie powtarzać temperatury z `DYN`.
- Nie powtarzać wiatru z `DYN` bez interpretacji.
- Nie generować komunikatu przy każdej małej zmianie temperatury.
- Nie zgadywać konkretnej odzieży.
- Nie konkurować z aktywnym `Climb Pacing`.

## Dynamiczne komunikaty — Calibration / Setup Reminder

Dodatkowym modułem dynamicznych komunikatów może być `Calibration / Setup Reminder`.

To moduł startowy / przedjazdowy, a nie komunikat powtarzany w trakcie jazdy.
Ma pomóc uniknąć sytuacji, w której QBot działa na niepełnych lub złych założeniach.

### Rola modułu

Moduł ma sprawdzić na początku jazdy, czy kluczowe dane i ustawienia są dostępne.

Przykłady:

```text
KALIBRUJ MOC
PRZED STARTEM
```

```text
TODAY FACTOR?
BRAK DANYCH
```

```text
FTP / CP BRAK
TARGET UPROSZCZONY
```

```text
BRAK TRASY
CLIMB OFF
```

```text
BRAK HR
STRAIN OFF
```

### Zakres kontroli

Sprawdzać:

- dostępność power meter;
- możliwość kalibracji / zero offset, jeśli SDK pozwala;
- dostępność HR;
- dostępność Gear / AXS;
- TodayFactor;
- FTP / CP;
- masa ciała, jeśli potrzebna do CARB / FLUID;
- wczytana trasa;
- dane Climber / climb z Karoo SDK;
- temperatura;
- GPS fix;
- battery Karoo;
- RD BAT / AXS battery.

### Kiedy pokazywać

Preferowane momenty:

- przed rozpoczęciem jazdy;
- na początku jazdy, np. pierwsze `1–3 min`;
- tylko raz na dany problem;
- ponownie tylko jeśli stan krytyczny zmieni się w trakcie jazdy, np. sensor zniknie.

### Zasady

- nie spamować podczas jazdy;
- nie przerywać dynamicznych komunikatów pacingu po rozpoczęciu właściwej jazdy;
- jeśli brakuje danych, komunikat ma jasno powiedzieć, która funkcja przechodzi w tryb uproszczony;
- nie udawać pełnej precyzji, jeśli brakuje TodayFactor, HR, trasy albo mocy.

### Priorytet

Przed jazdą priorytet wysoki.
W trakcie jazdy zwykły setup reminder ma niski priorytet, ale utrata kluczowego sensora przechodzi raczej do przyszłego modułu `Sensor / Data Quality`.

### Czego nie robić

- Nie powtarzać przypomnień setup co kilka minut.
- Nie blokować jazdy, jeśli użytkownik świadomie jedzie bez danego sensora.
- Nie ukrywać faktu, że targety są uproszczone.
- Nie wymagać AI online.

## SETUP extension — konfiguracja dynamicznych komunikatów

SETUP extension musi umożliwiać włączenie i wyłączenie dynamicznych komunikatów.

Wymaganie podstawowe:

```text
użytkownik może całkowicie wyłączyć dynamiczne komunikaty
oraz osobno wyłączyć konkretne moduły komunikatów
```

### Przełączniki globalne

W SETUP extension dodać przełączniki:

```text
Enable dynamic messages: ON/OFF
Enable alarm messages: ON/OFF
Enable informational messages: ON/OFF
```

Zasady:

- jeśli `Enable dynamic messages = OFF`, nie pokazywać żadnych komunikatów dynamicznych;
- jeśli `Enable alarm messages = OFF`, nie pokazywać alarmów treningowych/pacingowych, z wyjątkiem ewentualnych krytycznych komunikatów technicznych, jeśli użytkownik zostawi je włączone osobno;
- jeśli `Enable informational messages = OFF`, nie pokazywać komunikatów informacyjnych, ale alarmy mogą działać.

### Przełączniki per moduł

Dodać osobne przełączniki:

```text
Climb Pacing Messages: ON/OFF
Route Horizon Pacing: ON/OFF
Ride Trend Messages: ON/OFF
Finish / Route Risk: ON/OFF
Intake Logging: ON/OFF
Intake Reminders: ON/OFF
Light / Twilight Reminder: ON/OFF
Stop / Pause Discipline: ON/OFF
Weather Shift / Environmental Risk: ON/OFF
Calibration / Setup Reminder: ON/OFF
```

Docelowo, jeśli powstanie osobny moduł techniczny:

```text
Sensor / Data Quality Messages: ON/OFF
Battery / Device Messages: ON/OFF
```

### Domyślne ustawienia MVP

Proponowane domyślne ustawienia dla testów:

```text
Enable dynamic messages: ON
Enable alarm messages: ON
Enable informational messages: ON

Climb Pacing Messages: ON
Route Horizon Pacing: ON
Ride Trend Messages: OFF
Finish / Route Risk: OFF
Intake Logging: OFF
Intake Reminders: OFF
Light / Twilight Reminder: ON
Stop / Pause Discipline: OFF
Weather Shift / Environmental Risk: OFF
Calibration / Setup Reminder: ON
```

Uzasadnienie:

- na początku testować przede wszystkim `Climb Pacing`, `Route Horizon` i `Light / Twilight`;
- cięższe moduły trendowe i logistyczne włączać dopiero po walidacji podstawowego `MessagePriorityEngine`;
- `Intake Logging` może być włączane osobno, bo wymaga interakcji użytkownika.

### Ustawienia częstotliwości / higieny

Docelowo SETUP extension może pozwolić ustawić poziom agresywności komunikatów:

```text
Message frequency: LOW / NORMAL / HIGH
```

MVP może używać tylko jednego profilu `NORMAL`.

Znaczenie profili:

- `LOW` — mniej komunikatów, dłuższe cooldowny;
- `NORMAL` — domyślna higiena;
- `HIGH` — częstsze komunikaty testowe, tylko do debugowania.

Nie używać `HIGH` jako domyślnego trybu jazdy.

### Intake setup

Dla intake dodać ustawienia:

```text
Enable intake logging: ON/OFF
Enable intake reminders: ON/OFF
Show intake menu from DYN tap: ON/OFF, jeśli technicznie możliwe
Default CARB amount: 20g / 30g / 40g / 50g / custom
Default FLUID amount: 100ml / 150ml / 200ml / 250ml / custom
```

Jeśli `Intake Logging = OFF`, nie prowadzić wymaganego bilansu faktycznego intake.
`STATS` nadal może pokazywać rekomendowane `CARB IN` i `FLUID IN`.

### Fallback i bezpieczeństwo

Jeśli moduł jest wyłączony:

- nie generuje kandydatów `RideMessage`;
- nie wpływa na `MessagePriorityEngine`;
- nie pokazuje komunikatów;
- nie powinien zmieniać logiki pól `LIVE`, `DYN` ani `STATS`, chyba że ustawienie wyraźnie dotyczy logowania danych, np. intake.

### Czego nie robić w SETUP

- Nie ukrywać globalnego przełącznika dynamicznych komunikatów.
- Nie wymuszać włączonych komunikatów trendowych.
- Nie mieszać przełączników intake logging z rekomendowanym `CARB IN` / `FLUID IN`.
- Nie pozwolić, żeby wyłączony moduł nadal generował kandydatów wiadomości.
- Nie używać agresywnego trybu komunikatów jako domyślnego.

## Offline Laboratory / Ride Replay

`Offline Laboratory` to narzędzie deweloperskie do walidacji logiki `LIVE 3×2` poza Karoo.

Nie jest to element UI na Karoo.
Nie jest to część pola `LIVE` ani `DYN`.
Nie wolno zmieniać layoutu, kolorów ani renderingu pól podczas prac nad Offline Laboratory.

Cel:

```text
FIT / synthetic ride data -> RideContext -> Advisors/Classifiers -> raport CSV/JSON
```

Offline Laboratory ma pozwolić sprawdzić, jak logika pola `LIVE` zachowywałaby się podczas prawdziwej jazdy, bez konieczności testowania na Karoo w terenie.

### Główne zastosowania

- replay prawdziwych jazd z plików FIT;
- replay syntetycznych scenariuszy testowych;
- walidacja `PowerAdvisor`, `GearAdvisor`, HR, Cadence, Grade i Speed;
- wykrywanie migania stanów `GOOD/OK/WARN/BAD`;
- sprawdzanie, czy histereza działa sensownie;
- wykrywanie absurdów logicznych;
- porównywanie zmian logiki między wersjami.

### Zakres danych wejściowych

Offline Laboratory powinno obsługiwać próbki jazdy z częstotliwością około 1 Hz.

Minimalny model próbki:

```kotlin
data class RideSample(
    val timestampMs: Long,
    val distanceMeters: Double?,
    val speedKph: Float?,
    val powerWatts: Int?,
    val heartRateBpm: Int?,
    val cadenceRpm: Int?,
    val altitudeMeters: Double?,
    val gradePercent: Float?,
    val gearText: String?,
)
```

Jeżeli `gradePercent` nie występuje w danych wejściowych, można go wyliczyć z `altitudeMeters` i `distanceMeters`, ale z smoothingiem i ochroną przed szumem.

### Źródła danych

Docelowo:

- `app/src/test/resources/rides/*.fit` — prywatne pliki jazd użytkownika, nie commitować do publicznego repo;
- `app/src/test/resources/rides/*.csv` — opcjonalny format prostszy do debugowania;
- syntetyczne scenariusze generowane kodem testowym.

MVP nie musi mieć pełnego parsera FIT, jeśli to opóźni prace. Można zacząć od CSV i syntetycznych scenariuszy, ale architektura ma przewidywać FIT.

### RideReplayRunner

Utworzyć moduł testowo-deweloperski:

```text
RideReplayRunner
```

Rola:

1. wczytać próbki jazdy;
2. uporządkować je chronologicznie;
3. podawać próbki do tego samego pipeline'u, którego używa `LIVE`;
4. budować `RideContext` dla każdej próbki;
5. odpalać:
   - `FieldClassifier.speed(ctx)`;
   - `PowerAdvisor.assess(ctx)`;
   - HR classifier / model;
   - Cadence classifier / model;
   - Grade classifier;
   - `GearAdvisor.assess(ctx)`;
6. zapisywać timeline wyników.

Replay musi korzystać z tej samej logiki, co produkcyjny `LIVE`, żeby nie powstał osobny, fałszywy model testowy.

### Raport CSV

Każdy replay powinien generować CSV w:

```text
build/reports/live3x2/replay_<ride_name>.csv
```

Kolumny minimalne:

```text
timestamp,
distance_km,
speed_kph,
power_w,
hr_bpm,
cadence_rpm,
grade_pct,
gear,
speed_state,
power_state,
hr_state,
cadence_state,
grade_state,
gear_state,
power_reason,
gear_reason
```

Jeżeli dane są niedostępne, używać pustej wartości albo `NA`, nie zgadywać.

### Raport summary JSON

Każdy replay powinien generować JSON w:

```text
build/reports/live3x2/replay_<ride_name>_summary.json
```

Zawartość:

- czas całkowity;
- dystans;
- procent czasu w `GOOD/OK/WARN/BAD/NEUTRAL` dla każdego pola;
- liczba zmian stanu dla każdego pola;
- liczba zmian stanu na minutę;
- najdłuższe odcinki `WARN` i `BAD`;
- lista podejrzanych wzorców.

### Podejrzane wzorce do wykrywania

Offline Laboratory ma automatycznie oznaczać sytuacje, które wymagają ręcznej oceny:

- `Gear` pozostaje `GOOD` przez >10 min, gdy cadence <55 rpm i power jest wysoki;
- `Gear` pozostaje stale `GOOD/OK` w scenariuszu stromego podjazdu z niską kadencją;
- `Power` jest `GOOD`, gdy W’bal <30%, jeśli W’bal jest dostępny;
- `HR` jest `GOOD`, gdy HR jest ewidentnie w wysokiej strefie;
- `Grade` ma inną wartość do display niż do klasyfikacji;
- `Grade` zmienia stan zbyt często na pofałdowanym/szumowym profilu;
- dowolne pole zmienia stan zbyt często, np. >6 zmian/min;
- `NEUTRAL` utrzymuje się długo mimo dostępnych danych wejściowych.

### Scenariusze syntetyczne

Dodać scenariusze generowane kodem:

1. stabilna jazda endurance po płaskim;
2. krótki podjazd typu punch;
3. długi podjazd z narastającym zmęczeniem;
4. HR drift przy stałej mocy;
5. spadek W’bal i override Power;
6. za twardy bieg na podjeździe;
7. za lekkie przełożenie / spinning;
8. szumiący grade wymagający smoothingu;
9. postój / brak mocy / brak kadencji;
10. brak HR lub brak Gear.

Scenariusze syntetyczne muszą działać w testach bez prywatnych plików FIT.

### Oczekiwany sposób oceny przez użytkownika

Użytkownik nie ma ręcznie oceniać każdej próbki.

System ma wygenerować raport, a użytkownik ocenia tylko wynik całościowo:

- czy timeline wygląda logicznie;
- czy statusy nie są za agresywne;
- czy nie wiszą zbyt długo na zielonym;
- czy ostrzeżenia pojawiają się w sensownych miejscach;
- czy pola nie migają.

### Zakres MVP Offline Laboratory

MVP:

1. `RideSample`;
2. `RideReplayRunner`;
3. synthetic scenarios;
4. CSV export;
5. summary JSON export;
6. test sprawdzający, że replay syntetyczny przechodzi przez `RideContext`, `PowerAdvisor` i `GearAdvisor`;
7. brak zmian UI.

FIT parser może być etapem drugim, jeśli jego wdrożenie komplikuje MVP.

### Czego nie robić w Offline Laboratory

- Nie zmieniać layoutu `LIVE`.
- Nie zmieniać layoutu `DYN`.
- Nie dodawać alertów.
- Nie dodawać AI online.
- Nie robić osobnego modelu logiki tylko dla replay.
- Nie zgadywać brakujących danych bez oznaczenia ich jako szacowane.
- Nie commitować prywatnych plików FIT użytkownika.

### Definicja gotowości Offline Laboratory

Offline Laboratory jest gotowe, gdy:

- można uruchomić replay syntetycznej jazdy;
- powstaje CSV timeline;
- powstaje summary JSON;
- wynik pokazuje stany wszystkich 6 pól `LIVE`;
- raport pokazuje `reasonCode` dla Power i Gear;
- testy jednostkowe przechodzą;
- nie zmieniono UI pola `LIVE` ani `DYN`.

## Zakres MVP

MVP pola `LIVE`:
- layout 3×2;
- 6 wartości;
- duże cyfry;
- minimalne ikony/etykiety;
- proste cieniowanie per field;
- stabilne źródła danych;
- fallbacki dla braku danych;
- brak dynamicznych komunikatów;
- brak AI online;
- brak pełnoekranowego dashboardu.


MVP pola `DYN`:
- osobne pole 3×2 nad `LIVE`;
- takie same ogólne proporcje jak `LIVE`;
- 6 danych kontekstowych: IF10/HRD, temperatura, wiatr, średnia netto/brutto, dystans przejechany/do celu, ETA;
- spokojniejszy wygląd niż `LIVE`;
- wartości demo na start;
- stabilne formatowanie;
- brak alertów w pierwszym etapie.

MVP pola `STATS`:
- osobne pole 3×6 spójne wizualnie z `LIVE` i `DYN`, ale gęstsze informacyjnie;
- 18 pól: `NP`, `IF`, `VI`, `CARB IN`, `FLUID IN`, `KCAL`, `TSS`, `DRIFT`, `RSRV`, `UP`, `UP LEFT`, `ETA`, `V AVG TOTAL`, `TIME TOTAL`, `TIME STOP`, `BURN BAT`, `BAT LEFT`, `RD BAT`;
- krótkie, ale czytelne etykiety tekstowe;
- wartości `22sp`, jednostki około `18sp`, etykiety około `9sp`;
- jednostki jako osobne `TextView`, nie `Spannable`;
- pasmowanie tła rzędów: czarne / ciemnogranatowe;
- jawne formattery wartości i fallback `--` bez jednostki;
- brak auto-size;
- produkcyjny `STATS` używa realnych danych, a wartości demo tylko w osobnym visual test field;
- poprawione funkcje liczące dane: pauzy, `RSRV`, `CARB IN`, `FLUID IN`, `RD BAT`;
- testy jednostkowe dla statystyk mocy, pauz, RSRV, CARB, FLUID, baterii i fallbacków;
- brak alertów;
- brak zmian w `LIVE` i `DYN`.

## Priorytety implementacyjne

1. Czytelność na realnym ekranie Karoo.
2. Stabilność wizualna.
3. Właściwe proporcje małego pola.
4. Duże wartości.
5. Brak zbędnych nagłówków.
6. Sensowna kolorystyka.
7. Lokalna logika.
8. Inteligentna interpretacja danych.
9. Spójność wizualna `LIVE`, `DYN` i `STATS`.

## Czego nie robić

- Nie robić pełnoekranowego dashboardu.
- Nie robić komunikatów dynamicznych w polu `LIVE` ani w pierwszej wersji `DYN`.
- Nie dodawać ETA do `LIVE`; ETA należy do `DYN`.
- Nie kopiować funkcjonalności Barberfish/VinHKE/RouteGraph.
- Nie zgadywać Gear z prędkości i kadencji, jeśli dane AXS/Karoo są dostępne.
- Nie robić agresywnych pełnokolorowych kafli dla wszystkich 6 pól.
- Nie robić algorytmów w izolacji dla każdego pola bez wspólnego RideContext.
- Nie projektować pod desktopowy mockup — projektować pod realny ekran mapy Karoo.
- Nie robić pola `STATS` w innym stylu niż `LIVE 3×2` i `DYN 3×2`.
- Nie wracać z `STATS` do układu 3×2 — aktualny kierunek to `STATS 3×6`.
- Nie używać auto-size w `STATS`, bo powodowało problemy z renderowaniem na Karoo/RemoteViews.
- Nie mieszać baterii Karoo z baterią tylnej przerzutki AXS.
- Nie liczyć `RSRV` jako route budget ani funkcji długości wczytanej trasy.
- Nie liczyć `CARB IN` z `elapsedSec`; używać aktywnego czasu jazdy (`movingSec`).
- Nie poprawiać wizualnie `STATS` bez równoczesnej weryfikacji funkcji liczących dane.
- Nie mieszać średniej netto i brutto w `STATS`.
- Nie pokazywać `0` jako braku danych w `STATS`.

## Definicja sukcesu

Pole `LIVE` ma pozwalać jednym krótkim spojrzeniem ocenić:

- czy jadę odpowiednio szybko;
- czy moc jest pod kontrolą;
- czy tętno nie ucieka;
- czy kadencja jest sensowna;
- czy nachylenie tłumaczy obciążenie;
- czy bieg jest dobrze dobrany.

Pole `DYN` ma pozwalać jednym krótkim spojrzeniem ocenić:

- jaka jest intensywność ostatniego odcinka jazdy;
- jakie są warunki zewnętrzne;
- czy wiatr pomaga czy przeszkadza;
- jak postoje wpływają na średnią;
- ile zostało do celu;
- o której realnie można dojechać.

Pole `STATS` ma pozwalać jednym krótkim spojrzeniem ocenić:

- jak wygląda statystyczny przebieg jazdy;
- jaki jest koszt treningowy i fizjologiczny jazdy (`NP`, `IF`, `VI`, `TSS`, `DRIFT`, `RSRV`);
- jakie są rekomendacje intake (`CARB IN`, `FLUID IN`);
- ile energii zostało zużyte (`KCAL`);
- ile przewyższenia zrobiono i ile zostało (`UP`, `UP LEFT`);
- jaki jest logistyczny przebieg jazdy (`ETA`, `V AVG TOTAL`, `TIME TOTAL`, `TIME STOP`);
- jak zachowuje się bateria Karoo (`BURN BAT`, `BAT LEFT`);
- jaki jest stan baterii tylnej przerzutki AXS (`RD BAT`);
- czy wartości są wiarygodne, a nie demonstracyjne;
- czy brak danych jest jasno oznaczony jako `--`.

---

# 2026-05-18 — Karoo Extension Runtime/Test Checkpoint

## Scope

Current work is on the local Karoo 3 extension project opened in Android Studio / OpenCode.  
Do not confuse this with the Mikr.us `/opt/qbot/app` backend/QBot project.

Mikr.us/QBot backend is not the active workspace for extension debugging.  
OpenCode/Codex/Gemini should not be run on Mikr.us for this work.

## Current validated state

The extension has been locally tested with synthetic headless tests and real replay fixture tests.

Current test status:

- `493 tests, 0 failures, 0 skipped`
- Debug APK builds successfully.
- APK path:
  `/Users/MichalSta/Downloads/karoo-final/app/build/outputs/apk/debug/app-debug.apk`
- APK size: approximately 8.2 MB.

The current task is no longer QLab visualisation. The current validated path is:

`FIT/replay fixture → local engine/context/calculators/formatters → LIVE/DYN/STATS validation → debug APK → Karoo runtime test`

## Real replay fixture

Fixture:

`app/src/test/resources/fixtures/22923840501.qbot_replay_log.json`

Source replay:

`22923840501.qbot_replay_log.json`

Replay properties:

- 3151 ticks
- approximately 53 minutes
- approximately 20.66 km
- flat route
- real LIVE sensor values from FIT

Important: replay DYN/STATS exported by older tooling may contain synthetic/fake values and must not be treated as source of truth. Tests must compute DYN/STATS locally through extension logic.

## FIT/replay sanity

The real replay fixture is valid as sensor input.

LIVE fields from replay are real enough for testing:

- speed: valid, m/s internally / km/h display
- power: valid, includes real sensor zero/gap behavior
- heart rate: valid
- cadence: valid, includes real zero/gap behavior
- grade: valid for flat route
- gear: replay does not contain actual current gear per tick

The replay may include drivetrain configuration in FIT session metadata, e.g. `front_gear` and `rear_gear`, but this is not current gear per tick.

## LIVE 3x2

LIVE layout remains:

Top row:
- Speed
- Power
- HR

Bottom row:
- Cadence
- Grade
- Gear

LIVE expectations:

- large readable values
- minimal labels/icons
- stable formatting
- status/color/tint per field
- no `reasonCode` shown in UI
- `reasonCode` may exist in logs/model only

## Gear — critical decision

Gear `no_data` is not acceptable in real runtime.

It is acceptable only in replay tests where the fixture has no current gear data.

Previous implementation used fake stream names:

- `TYPE_FRONT_GEAR_TEETH`
- `TYPE_REAR_GEAR_TEETH`

These were invalid and never emitted data.

Current implementation uses official Karoo SDK shifting stream:

- `DataType.Type.SHIFTING_GEARS`

Fields:

- `DataType.Field.SHIFTING_FRONT_GEAR_TEETH`
- `DataType.Field.SHIFTING_REAR_GEAR_TEETH`

`RideEngine.kt` was updated to use this official stream. DIAG now includes:

`gear=${frontTeeth}x${rearTeeth}`

Expected runtime:

- with AXS/shifting data: `gear=36x15`, `gear=36x13`, etc.
- without data: `gear=0x0`

If runtime remains `gear=0x0` despite paired AXS, this is a runtime integration issue and must be investigated. Potential follow-up streams:

- `DataType.Type.SHIFTING_FRONT_GEAR`
- `DataType.Type.SHIFTING_REAR_GEAR`

Do not treat Gear fallback as product-acceptable.

## STATS / StatsCalculator fixes

Two real bugs were found and fixed.

### DRIFT / decoupling

Old behavior:

- used rolling 60-second window
- compared 30s vs 30s inside the last minute
- produced unrealistic negative drift on real replay, e.g. `-16.1%`

Current behavior:

- accumulates active HR + power samples
- active sample means HR > 0 and power > 0
- compares first half of active samples vs second half
- minimum 120 active samples
- formula:
  `((secondRatio - firstRatio) / firstRatio) * 100`
- result clamped to `0f..50f`
- negative drift is treated as `0` because RSRV only cares about degradation

Real replay result:

- before fix: `DRIFT = -16.1%`
- after fix: `DRIFT = 1.7%`

### RSRV / ride reserve

Old behavior:

- used `minOf(result, lastReserve)`
- created one-way latch
- if reserve ever fell to 0, it stayed 0 for the rest of the ride

Current behavior:

- `lastReserve` is `Float`
- raw reserve still uses TodayFactor, TSS penalty, IF10 penalty, DRIFT penalty
- drop is immediate
- recovery is smoothed:
  `lastReserve = lastReserve + (raw - lastReserve) * 0.02f`
- return:
  `lastReserve.roundToInt().coerceIn(0, 100)`

Real replay result:

- before fix: `RSRV = 0%`
- after fix: `RSRV = 35%`

Note: recovery rate depends on how often the function is called. Runtime observation is still required.

## DYN 4x2

DYN layout:

Top row:
- `D`
- `IF10`
- `HRD`
- `W'`

Bottom row:
- `DTD`
- `Vśr`
- `T`
- `W`

Relevant files:

- `app/src/main/kotlin/com/bikepacking/karoo/datatypes/BpDyn3x2DataType.kt`
- `app/src/main/kotlin/com/bikepacking/karoo/field/DynValueFormatter.kt`
- `app/src/main/res/layout/field_dyn_3x2.xml`
- `app/src/test/kotlin/com/bikepacking/karoo/DynValidationTest.kt`
- `app/src/test/kotlin/com/bikepacking/karoo/DynValueFormatterWindTest.kt`

DYN has been locally verified by headless tests.

DYN source status:

| Slot | Source | Replay result |
|---|---|---|
| D | `rideState.distanceM / 1000` | PASS / real value |
| IF10 | rolling NP30 / FTP | PASS / real value |
| NP10 | rolling 10min normalized power | PASS / real value |
| W' | `StatsCalculator.wBalancePercent()` | PASS / real value |
| DTD | Karoo `DISTANCE_TO_DESTINATION` stream | OK_FALLBACK in replay |
| Vśr | distance / moving time | PASS / real value |
| T | Karoo `TEMPERATURE` stream | OK_FALLBACK in replay |
| W | external `karoo-headwind` streams | OK_FALLBACK in replay |

Important classification:

- `OK_VALUE` = real computed or streamed value
- `OK_FALLBACK` = explicit fallback because replay/runtime source is unavailable
- `FAIL` = fake/hardcoded/silent missing/bad formatting

DYN test must not pass fake values as real values.

## DTD

DTD uses official Karoo SDK type:

- `DataType.Type.DISTANCE_TO_DESTINATION`
- raw ID: `"TYPE_DISTANCE_TO_DESTINATION_ID"`

Field:

- `DataType.Field.DISTANCE_TO_DESTINATION`
- raw ID: `"FIELD_DISTANCE_TO_DESTINATION_ID"`

Confirmed to compile with karoo-ext SDK 1.1.8.

Runtime requirement:

- without route: fallback `--` is acceptable
- with route loaded: DTD must show real remaining distance
- if route is loaded and DTD remains `--`, treat as integration bug

Diagnostic examples:

- `DTD: remaining=12.3km hasRoute=true onRoute=true`
- `DTD: -- (no route)`

## Temperature

Temperature uses official Karoo SDK type:

- `DataType.Type.TEMPERATURE`
- raw ID: `"TYPE_TEMPERATURE_ID"`

Field:

- `DataType.Field.TEMPERATURE`
- raw ID: `"FIELD_TEMPERATURE_ID"`

Confirmed to compile with karoo-ext SDK 1.1.8.

Runtime requirement:

- if Karoo publishes temperature in the activity, DYN `T` must show it
- if temperature stream is unavailable, fallback `--°` is acceptable
- if Karoo native temperature exists but DYN remains `--°`, treat as integration bug

Diagnostic examples:

- `T: 15°C (sensor)`
- `T: -- (sensor)`

## Wind / Headwind

Wind data must come from external Karoo Headwind / `karoo-headwind` extension.

Do not compute fake wind locally.  
Do not derive wind from speed/power/grade.  
Do not use QLab/replay fake wind.

Headwind external DataType IDs:

- `TYPE_EXT::karoo-headwind::headwindSpeed`
- `TYPE_EXT::karoo-headwind::windSpeed`
- `TYPE_EXT::karoo-headwind::headwind`
- `TYPE_EXT::karoo-headwind::windDirection`

Units:

- `headwindSpeed`: relative headwind/tailwind, m/s
- `windSpeed`: absolute wind speed, m/s
- `headwind`: relative direction OR error code
- `windDirection`: absolute direction in degrees, 0=N, 90=E

Headwind error codes for `headwind`:

- `-1.0` = no GPS
- `-2.0` = no weather data
- `-3.0` = Headwind not configured

Error codes must not be interpreted as real wind direction. They must produce explicit fallback.

Runtime requirement:

- without Headwind extension or without configured Headwind: fallback is acceptable
- with Headwind installed, configured, GPS fixed, and weather available: Wind must show real data
- if Headwind works but QBot shows fallback, treat as integration bug

Diagnostic examples:

- `W: arrow=↗ headwind=15.2kph wind=4.2m/s dir=270°`
- `W: -- (headwind error: no GPS)`
- `W: -- (headwind error: no weather data)`
- `W: -- (headwind error: headwind not configured)`

## DYN tests

`DynValueFormatter.kt` was added to extract DYN formatting into pure functions.

`DynValidationTest.kt` validates all 8 DYN slots against real replay fixture.

`DynValueFormatterWindTest.kt` validates Wind formatting and error handling.

Current result after DYN/Headwind work:

- `493 tests, 0 failures, 0 skipped`

## Debug build

Build command:

`./gradlew app:assembleDebug`

Current APK:

`/Users/MichalSta/Downloads/karoo-final/app/build/outputs/apk/debug/app-debug.apk`

## Runtime logs

Main DIAG log should include:

- speed
- power
- HR
- cadence
- grade
- distance
- remaining distance
- moving/elapsed time
- NP
- IF
- VI
- TSS
- kcal
- drift
- RSRV
- W′bal
- carb
- fluid
- ascent / ascent left
- battery
- RD battery
- gear
- DTD/T/W diagnostics

Recommended logcat:

`adb logcat -v time -s BikepackingRideEngine:D BP_LIVE3X2:D BP_STATS:D DYN4x2:D | grep -E "DIAG|DTD|T:|W:|gear|SHIFTING"`

For only DIAG:

`adb logcat -s BikepackingRideEngine:D | grep DIAG`

## Runtime checklist for next Karoo test

Before testing:

1. Install current debug APK.
2. Pair/confirm SRAM AXS shifting on Karoo.
3. Load a route for DTD.
4. Install and configure `karoo-headwind`.
5. Wait for GPS fix.
6. Start an activity, not just pre-ride screen.
7. Run logcat.

Must confirm:

- `gear != 0x0` when AXS is available
- DTD shows remaining distance with route loaded
- T shows temperature if Karoo publishes it
- Wind shows Headwind data when Headwind is configured and data available
- DRIFT remains `>= 0`
- RSRV does not latch to 0 incorrectly
- DYN renders all 8 slots
- LIVE renders 6 slots
- STATS renders without `--` for computed fields after warm-up

## Non-goals right now

Do not work on:

- QLab visual UI
- Mikr.us backend
- OpenCode/Codex/Gemini on Mikr.us
- new features
- full dashboard UI
- ETA redesign
- dynamic message redesign

The next step is runtime validation on Karoo with logcat.
