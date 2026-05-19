# QBot Offline Lab — instrukcja dla Code

## 0. Cel

Budujemy **QBot Offline Lab**, czyli lokalne środowisko testowo-emulacyjne dla aplikacji Karoo 3 extension.

Główny cel:

> Dokończyć i debugować QBot bez konieczności jazd testowych w terenie.

Lab ma pozwolić:
- odtwarzać realne jazdy z plików FIT / GPX / JSON log,
- emulować wyświetlanie pól LIVE / DYN / STATS,
- automatycznie wykrywać błędy logiki,
- testować aplikację na dużej paczce danych, np. 100 plików FIT,
- porównywać wersje logiki,
- generować raporty HTML/JSON z problematycznych momentów.

Jazdy w terenie mają być potrzebne dopiero do końcowej walidacji czytelności i stabilności na realnym Karoo.

---

## 1. Najważniejsza zasada architektoniczna

Offline Lab **nie może mieć własnej niezależnej logiki jazdy**.

Lab ma używać możliwie tego samego kodu co extension:

- `RideEngine`
- klasyfikatory LIVE
- logika STATS
- logika DYN
- smoothing
- histereza
- sanity guards
- reason codes
- modele stanu

Różnica ma być tylko w źródle danych:

```text
Karoo extension:
live sensor streams / route streams / SDK streams

Offline Lab:
replay stream z FIT / GPX / JSON log
```

Jeśli jakaś logika działa tylko w Labie, ale nie działa na Karoo, to znaczy, że architektura jest zła.

---

## 2. Zakres MVP

MVP Offline Lab ma mieć trzy tryby:

### 2.1 Visual Replay

Interfejs HTML uruchamiany lokalnie w przeglądarce.

Funkcje:
- wczytanie jednej jazdy,
- play / pause,
- przewijanie timeline,
- prędkość replay: 1x / 2x / 5x / 20x,
- podgląd emulowanego HUD,
- podgląd danych debug.

HUD ma pokazywać:
- LIVE 3×2,
- DYN,
- STATS.

### 2.2 Auto QA

Tryb automatyczny.

Lab odpala całą jazdę od początku do końca i sam sprawdza:
- crash / exception,
- NaN / Infinity / null,
- reset STATS,
- martwe pola,
- statyczne DYN,
- dziwne skoki ETA / TTS,
- błędny stopped time,
- nielogiczne kcal,
- RSRV rosnące bez jawnego modelu recovery,
- brak trusted source dla danych,
- migotanie kolorów LIVE,
- częste zmiany klasyfikatorów bez histerezy,
- wartości spoza sensownego zakresu.

### 2.3 Batch Replay

Tryb testowania wielu jazd.

Przykład:

```text
/input/rides/
  ride_001.fit
  ride_002.fit
  ...
  ride_100.fit
```

Lab ma automatycznie:
- odpalić każdą jazdę,
- przeprowadzić replay,
- zapisać raport per jazda,
- zapisać raport zbiorczy,
- wskazać najgorsze jazdy i najczęstsze typy błędów.

---

## 3. Proponowana struktura katalogów

```text
qbot/
  app/
    src/
      main/
        ...
  core/
    src/
      main/
        kotlin/
          RideEngine.kt
          classifiers/
          stats/
          dyn/
          models/
  offline-lab/
    README.md
    lab.md
    backend/
      src/
        main/
          kotlin/
            LabServer.kt
            ReplayEngine.kt
            FitParser.kt
            GpxParser.kt
            JsonLogParser.kt
            BatchRunner.kt
            QaRunner.kt
            ReportWriter.kt
    web/
      index.html
      hud.css
      lab.js
    input/
      rides/
    reports/
      summary.html
      summary.json
```

Jeśli przeniesienie `RideEngine` do wspólnego modułu `core/` jest za duże na start, można tymczasowo zrobić adapter, ale kierunek docelowy to wspólny kod.

---

## 4. Backend

Preferowany backend:

```text
Kotlin/JVM
```

Powód: łatwiejsze współdzielenie logiki z Android/Karoo.

Backend ma wystawić lokalne API, np.:

```text
GET  /api/rides
POST /api/load
GET  /api/state?t=1234
POST /api/play
POST /api/pause
POST /api/run-qa
POST /api/run-batch
GET  /api/report/latest
```

Na MVP API może być proste i lokalne. Nie budujemy chmury.

---

## 5. Frontend HTML

Frontend ma być prosty, techniczny, lokalny.

Nie robimy pełnego dashboardu treningowego.

Robimy emulator pola QBot.

### 5.1 Widok główny

Układ:

```text
+------------------------------------------------+
| QBot Offline Lab                               |
+------------------------------------------------+
| [Karoo HUD Emulator]     [Debug Panel]         |
|                                                |
| LIVE 3×2                 raw/smoothed values   |
| DYN                      classifier states     |
| STATS                    reason codes          |
|                                                |
+------------------------------------------------+
| timeline | play | speed | timestamp | issues   |
+------------------------------------------------+
```

### 5.2 Rider Mode

Pokazuje tylko to, co miałby widzieć użytkownik na Karoo.

Cel:
- ocena czytelności,
- ocena proporcji,
- ocena migotania,
- ocena stabilności wartości.

### 5.3 Engineer Mode

Pokazuje dodatkowo:
- raw values,
- smoothed values,
- classifier status,
- reason code,
- source trust,
- hysteresis state,
- last update time,
- warnings.

Przykład debug dla jednego pola:

```json
{
  "field": "power",
  "raw": 243,
  "smoothed3s": 238,
  "targetLow": 180,
  "targetHigh": 215,
  "status": "HIGH",
  "color": "AMBER",
  "reasonCode": "POWER_ABOVE_SUSTAINABLE_TARGET",
  "trustedSource": true
}
```

---

## 6. Dane wejściowe

Lab ma obsługiwać docelowo:

1. FIT
2. GPX
3. JSON log z QBot/Karoo

### 6.1 Priorytet MVP

Najpierw obsłużyć JSON log, bo jest najłatwiejszy i najbardziej kontrolowalny.

Potem FIT.

GPX może być pomocniczy do trasy/elewacji, ale nie zastąpi FIT, bo nie ma pełnych danych sensora.

---

## 7. Replay model

ReplayEngine ma podawać dane do `RideEngine` tak, jakby przychodziły z Karoo.

Każdy tick powinien zawierać np.:

```json
{
  "timestampMs": 123456,
  "distanceM": 15432.4,
  "speedMps": 7.2,
  "powerW": 218,
  "heartRateBpm": 139,
  "cadenceRpm": 66,
  "gradePct": 3.4,
  "altitudeM": 141.2,
  "temperatureC": 12.0,
  "position": {
    "lat": 52.123,
    "lon": 21.123
  },
  "gear": {
    "frontTeeth": 40,
    "rearTeeth": 15,
    "ratio": 2.67,
    "source": "trusted"
  },
  "route": {
    "distanceToDestinationM": 38400,
    "ascentRemainingM": 520,
    "activeClimbIndex": 3,
    "climbsTotal": 7
  }
}
```

Brakujące dane muszą być jawnie oznaczone jako brakujące, a nie zastępowane zgadywaniem.

---

## 8. Trusted source rule

Zasada globalna:

```text
Jeśli wartość nie ma wiarygodnego źródła, renderujemy "--".
Nie zgadujemy.
Nie pokazujemy defaultów jako prawdziwych danych.
```

Dotyczy szczególnie:
- gear,
- grade,
- derailleur battery,
- wind,
- route remaining,
- ascent remaining,
- climb count.

Zakazane:
- domyślne `3%`,
- domyślne `8%`,
- domyślne `100%`,
- domyślne `0`, jeśli zero nie jest realnym pomiarem,
- ukryte fallbacki bez reason code.

---

## 9. Auto QA — reguły testowe

### 9.1 Stabilność

Testy:
- brak exception,
- brak crash,
- brak NaN,
- brak Infinity,
- brak null w polach wymaganych,
- brak dzielenia przez zero,
- brak ujemnych wartości tam, gdzie są nielogiczne.

### 9.2 Reset state

Wykrywać:
- spadek dystansu całkowitego bez nowej jazdy,
- spadek moving time,
- spadek elapsed time,
- reset kcal,
- reset TSS/TTS/RSRV,
- reset climb count,
- utratę STATS po symulowanym reloadzie.

Lab powinien mieć test:

```text
simulate extension restart at t=25%, t=50%, t=75%
verify stats continue from persisted state
```

### 9.3 LIVE

Sprawdzać:
- Speed aktualizuje wartość i kolor.
- Power aktualizuje wartość i kolor.
- HR aktualizuje wartość i kolor.
- Cadence aktualizuje wartość i kolor.
- Grade działa albo pokazuje `--`.
- Gear działa albo pokazuje `--`.
- Kolory nie migoczą zbyt często.
- Brak statusu bez trusted source.

Przykład reguły:

```text
Jeśli colorState zmienia się > X razy na minutę przy stabilnym raw input, oznacz jako flicker.
```

### 9.4 DYN

Sprawdzać:
- DYN nie jest statyczny,
- wartości DYN aktualizują się w czasie,
- komunikaty climb pacing pojawiają się w odpowiednich momentach,
- komunikaty nie spamują,
- komunikaty mają czas życia,
- DYN wraca do domyślnego stanu po wygaśnięciu komunikatu,
- brak nadpisywania LIVE w sposób trwały.

### 9.5 STATS

Sprawdzać:

#### kcal
- kcal nie może być ujemne,
- kcal powinno rosnąć monotonicznie,
- kcal nie powinno być absurdalnie niskie względem średniej mocy i czasu ruchu.

Minimalny sanity check:

```text
mechanical_kJ = avgPowerW * movingSec / 1000
estimated_kcal ≈ mechanical_kJ / efficiency
dla efficiency około 0.22-0.25
```

#### TTS / ETA
- brak gwałtownych skoków bez zmiany źródła danych,
- brak radykalnego zaniżenia,
- brak wartości ujemnych,
- brak infinity,
- przy braku route distance render `--`.

#### Decoupling
- nie może być stałą wartością z fallbacku,
- musi wynikać z realnego porównania,
- jeśli za mało danych, render `--`.

#### RSRV
- jeśli RSRV oznacza rezerwę/staminę, nie może rosnąć bez jawnego modelu recovery,
- każdy wzrost musi mieć reason code,
- brak danych wejściowych => `--`.

#### Time stop
- stopped time = elapsed time - moving time,
- nie może pokazywać ride time,
- nie może być ujemny.

#### Battery
- nie pokazuj baterii przerzutki bez trusted source,
- jeśli brak danych, render `--`.

---

## 10. Raporty

Lab ma generować:

```text
/reports/
  summary.html
  summary.json
  ride_001_report.html
  ride_001_events.json
  ride_001_snapshots/
```

### 10.1 Summary

Przykład:

```text
100 rides tested
92 passed
8 failed

Failures:
- 3x ETA jump > 30%
- 2x missing grade source
- 1x kcal too low
- 1x DYN frozen
- 1x crash in GearClassifier

Worst rides:
- 2026-05-18.fit — 14 issues
- 2026-04-27.fit — 9 issues
```

### 10.2 Per ride report

Każdy raport jazdy ma zawierać:
- podstawowe info o pliku,
- czas jazdy,
- dystans,
- dostępne źródła danych,
- lista issues,
- timestamp każdego problemu,
- severity,
- snapshot HudState,
- reason code,
- raw input.

Przykład issue:

```json
{
  "timestampMs": 1845000,
  "severity": "ERROR",
  "module": "STATS",
  "field": "eta",
  "code": "ETA_JUMP_TOO_LARGE",
  "message": "ETA changed from 03:40:00 to 01:12:00 within 10 seconds",
  "raw": {
    "distanceToDestinationM": 42100,
    "avgSpeedMps": 6.8,
    "currentSpeedMps": 12.1
  }
}
```

---

## 11. Snapshot system

Lab ma umieć zapisać problematyczny moment jako snapshot.

Snapshot powinien zawierać:
- timestamp,
- raw input,
- RideState,
- HudState,
- classifier outputs,
- reason codes,
- screenshot / HTML render state.

Przykład:

```text
/reports/ride_001_snapshots/problem_eta_001.json
/reports/ride_001_snapshots/problem_eta_001.png
```

Na MVP PNG może być pominięty, ale JSON snapshot jest obowiązkowy.

---

## 12. Porównywanie wersji

Docelowo Lab powinien wspierać Compare Mode:

```text
version A: current
version B: changed logic
same ride input
compare outputs
```

Porównujemy:
- wartości,
- kolory,
- statusy,
- reason codes,
- liczbę issues,
- stabilność ETA/TTS,
- migotanie kolorów,
- liczbę komunikatów DYN.

MVP może tylko generować dwa raporty i diff JSON.

---

## 13. Symulowane crashe / restarty

Lab musi mieć tryb testu odporności na crash:

```text
simulateRestartAt = [25%, 50%, 75%]
```

Test:
1. Odtwarzaj jazdę.
2. W wybranym momencie zapisz persist state.
3. Zniszcz instancję `RideEngine`.
4. Utwórz nową instancję.
5. Wczytaj persist state.
6. Kontynuuj replay.
7. Sprawdź, czy STATS nie zresetowały się.

To jest krytyczne po problemach z jazdy 18.05.

---

## 14. Kontekst regresji 18.05

Lab musi być zbudowany tak, żeby wykrywać problemy zauważone w iteracji z 18.05:

### LIVE
- nie działa grade,
- nie działa gears,
- dziwne kolorowanie HR,
- dziwne kolorowanie Power,
- dziwne kolorowanie Cadence.

### DYN
- DYN nie działa dynamicznie,
- pokazuje statyczne wartości.

### STATS
- kcal za nisko,
- TTS radykalnie zaniżony,
- decoupling stale 8–9%,
- RSRV potrafi rosnąć,
- ETA zawyża, potem gwałtownie spada,
- time stop pokazuje ride time,
- bateria przerzutki może być halucynowana.

### STABILITY
- extension wywalił się 2 razy w trakcie jazdy,
- znikały dane STATS,
- możliwe liczenie od nowa po crashu.

Lab ma te przypadki traktować jako testy regresyjne.

---

## 15. Definicja sukcesu MVP

MVP Offline Lab jest gotowy, gdy:

1. Można wczytać jedną jazdę testową.
2. Można odtworzyć jazdę na timeline.
3. W HTML widać emulowany HUD.
4. Widać LIVE / DYN / STATS.
5. Auto QA generuje raport problemów.
6. Batch mode potrafi przejść folder z wieloma plikami.
7. Brak danych jest pokazywany jako `--`, nie jako zgadywana wartość.
8. Symulowany restart nie resetuje STATS.
9. Raport wskazuje timestamp, pole, moduł i reason code problemu.

---

## 16. Kolejność implementacji

### Etap 1 — fundament

- utworzyć katalog `offline-lab`,
- przygotować prosty backend lokalny,
- przygotować prosty frontend HTML,
- przygotować model `ReplaySample`,
- przygotować parser JSON log.

### Etap 2 — integracja z silnikiem

- podłączyć replay stream do `RideEngine`,
- wyprowadzić `HudState`,
- renderować LIVE / DYN / STATS w HTML.

### Etap 3 — Auto QA

- dodać sanity checks,
- dodać issue model,
- dodać raport JSON,
- dodać raport HTML.

### Etap 4 — restart/persist test

- dodać symulowany restart,
- sprawdzić brak resetu STATS.

### Etap 5 — Batch mode

- folder input,
- pętla po plikach,
- raport zbiorczy.

### Etap 6 — FIT

- dodać parser FIT,
- mapować FIT records do `ReplaySample`,
- jawnie oznaczać brakujące dane.

---

## 17. Czego nie robić teraz

Nie robić teraz:
- pełnej aplikacji webowej z ciężkim frameworkiem, jeśli nie jest potrzebny,
- chmury,
- logowania użytkowników,
- AI online,
- osobnej logiki rowerowej w JavaScript,
- pełnoekranowego dashboardu treningowego,
- ładnych wykresów kosztem replay i QA,
- zgadywania brakujących danych,
- mocków oderwanych od realnego `RideEngine`.

---

## 18. Minimalny pierwszy deliverable

Pierwszy sensowny commit powinien dać:

```text
offline-lab/
  lab.md
  web/
    index.html
    hud.css
    lab.js
  input/
    sample_ride.json
  reports/
```

Oraz możliwość:
- otworzenia `index.html`,
- załadowania `sample_ride.json`,
- przewijania timeline,
- zobaczenia HUD,
- uruchomienia prostego Auto QA,
- wygenerowania `summary.json`.

Na tym etapie dane mogą być jeszcze syntetyczne, ale struktura musi być zgodna z docelowym replay mode.
