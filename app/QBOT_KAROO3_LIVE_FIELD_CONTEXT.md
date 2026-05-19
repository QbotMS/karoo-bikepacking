# QBot / Karoo 3 LIVE Field — Project Context

## 1. Cel projektu

Projekt polega na stworzeniu własnego pola danych LIVE dla Hammerhead Karoo 3.

To pole ma działać jako mały, bardzo czytelny HUD widoczny na ekranie mapy Karoo 3.

Nie projektujemy pełnoekranowego dashboardu.
Nie projektujemy ekranu ETA.
Nie dodajemy dynamicznych komunikatów do tego pola.
Dynamiczne komunikaty będą osobnym polem lub osobnym modułem w przyszłości.

Główne pole LIVE ma pozwolić jednym krótkim spojrzeniem ocenić:

- czy jadę odpowiednio szybko,
- czy moc jest pod kontrolą,
- czy tętno nie ucieka,
- czy kadencja jest sensowna,
- czy nachylenie tłumaczy obciążenie,
- czy aktualny bieg jest dobrze dobrany.

## 2. Kontekst urządzenia

Docelowe urządzenie: Hammerhead Karoo 3.

Pole ma być projektowane pod realny mały obszar na ekranie mapy Karoo 3, a nie pod ekran pełnoekranowy.

Priorytetem jest czytelność podczas jazdy gravelowej, przy krótkim spojrzeniu na ekran.

## 3. Główne pole LIVE

Layout pola LIVE:

```text
3 kolumny × 2 rzędy
```

Pole pokazuje 6 danych:

1. Speed
2. Power
3. HR
4. Cadence
5. Grade
6. Gear

Wstępny układ logiczny:

```text
┌──────────┬──────────┬──────────┐
│  Speed   │  Power   │    HR    │
├──────────┼──────────┼──────────┤
│ Cadence  │  Grade   │   Gear   │
└──────────┴──────────┴──────────┘
```

Układ może zostać dopracowany, ale domyślne założenie MVP to powyższy podział 3×2.

## 4. Najważniejsze zasady UI

Pole musi być projektowane jako mały HUD na ekranie mapy.

Najważniejsze zasady:

- nie tworzyć pełnoekranowej wizualizacji;
- nie tworzyć klasycznego dashboardu;
- nie dodawać dużych nagłówków;
- cyfry mają dominować wizualnie;
- wartości powinny zajmować około 70–80% wysokości subpola;
- etykiety mają być minimalne: małe ikony albo bardzo krótkie skróty;
- wartości muszą być stabilne wizualnie i nie mogą skakać przy zmianie cyfr;
- każdy slot ma mieć stabilną szerokość i wysokość;
- 6 subpól ma mieć równy, uporządkowany podział;
- UI ma być zwarte, techniczne, natywne i czytelne;
- każde subpole ma mieć własne tło, cieniowanie albo kolor wynikające z logiki parametru;
- kolor ma pomagać w interpretacji, ale nie może utrudniać odczytu wartości;
- liczby są ważniejsze niż opisy.

## 5. Wzorzec graficzno-techniczny: Barberfish

Barberfish traktujemy jako wzorzec techniczno-graficzny, nie jako bazę funkcjonalną.

Nie kopiujemy funkcjonalności Barberfish.
Nie forkować Barberfish jako bazy projektu.

Barberfish ma służyć jako referencja dla:

- czytelnego HUD-a;
- równego podziału slotów;
- dużych cyfr;
- małych ikon;
- stabilnego renderowania wartości;
- kontrolowanego użycia koloru;
- natywnego wyglądu pola danych;
- sposobu organizacji layoutów i drawable.

Lokalna referencja może znajdować się np. tutaj:

```text
/reference/Barberfish
```

Istotne katalogi Barberfish:

```text
app/src/main/res/drawable
app/src/main/res/layout
```

Istotne ikony / drawable jako referencje:

```text
ic_avg_hr.xml
ic_avg_power.xml
ic_cadence.xml
ic_col_hr.xml
ic_col_power.xml
ic_col_speed.xml
ic_grade.xml
ic_speed_average.xml
ic_time_to_dest.xml
```

Istotne layouty jako referencje:

```text
barberfish_hud.xml
barberfish_hud_four.xml
barberfish_field.xml
barberfish_sparkline.xml
```

Dla pola Gear trzeba przygotować własną ikonę.

## 6. Semantyka pól

### 6.1 Speed

Speed pokazuje aktualną prędkość.

Kolor / stan pola Speed powinien odnosić aktualną prędkość do jednego z punktów odniesienia:

- średnia jazdy,
- planowana średnia,
- wartość oczekiwana wyliczana przez aplikację.

Na start MVP można użyć prostej logiki względem średniej jazdy lub planowanej średniej.

Kolor ma pokazywać, czy aktualna prędkość:

- pomaga utrzymać lub podnieść średnią,
- jest neutralna,
- zaniża średnią.

Przykładowa interpretacja:

```text
currentSpeed > targetSpeed + próg  -> pozytywnie
currentSpeed w zakresie neutralnym -> neutralnie
currentSpeed < targetSpeed - próg  -> negatywnie
```

Nie należy robić agresywnej interpretacji prędkości bez kontekstu nachylenia. Docelowo Speed może być interpretowany razem z Grade, ale MVP może mieć prostszą regułę.

### 6.2 Power

Power pokazuje aktualną moc.

Kolor pola Power powinien odnosić moc do:

- stref mocy użytkownika,
- albo zaleceń aplikacji.

Kolor ma pokazywać:

- za nisko,
- OK,
- za wysoko.

MVP może używać prostych progów opartych o FTP lub zakres docelowy.

Docelowo Power powinien wspierać jazdę gravelową/endurance, a nie wymuszać wyścigowego tempa.

### 6.3 HR

HR pokazuje aktualne tętno.

Kolor pola HR powinien odnosić tętno do:

- stref tętna użytkownika,
- albo zaleceń aplikacji.

Kolor ma pokazywać obciążenie organizmu względem planu.

Interpretacja HR powinna być spokojniejsza niż Power, bo HR reaguje wolniej.

MVP może używać prostych stref:

```text
niska intensywność / OK / za wysoko
```

Docelowo HR może być używane jako sygnał dryfu, zmęczenia lub przegrzania, ale nie w pierwszej wersji pola LIVE.

### 6.4 Cadence

Cadence pokazuje aktualną kadencję.

Użytkownik preferuje raczej niską kadencję około 60–70 rpm.
Nie należy wymuszać szosowej, wysokiej kadencji.

Kolor pola Cadence powinien odnosić aktualną kadencję do:

- średniej kadencji,
- wartości oczekiwanej,
- albo preferowanego zakresu użytkownika.

Na start można użyć progu około ±10% względem średniej lub wartości docelowej.

Przykład MVP:

```text
expectedCadence = 65 rpm
zakres neutralny = ±10%
```

Interpretacja:

```text
za nisko  -> możliwe mielenie zbyt ciężkiego biegu / przeciążenie mięśniowe
OK        -> zgodne z preferowanym stylem
za wysoko -> możliwe zbyt lekkie przełożenie
```

Cadence powinno być docelowo powiązane z Gear.

### 6.5 Grade

Grade pokazuje aktualne nachylenie.

Kolor pola Grade powinien zależeć od nachylenia.

Przykładowe klasy:

```text
zjazd          < -2%
płasko         od -2% do +2%
lekki podjazd  od +2% do +5%
mocny podjazd  od +5% do +9%
bardzo stromo  > +9%
```

Progi mogą być później dostrojone do jazdy gravelowej.

Grade jest ważne jako kontekst dla Speed, Power, HR i Gear.

### 6.6 Gear

Gear pokazuje aktualne przełożenie / bieg.

To pole ma być inteligentne.
Nie ma tylko wyświetlać biegu, ale oceniać, czy aktualne przełożenie jest dobrze dobrane.

Gear powinno oceniać, czy bieg jest:

- za niski,
- za wysoki,
- w sweet spocie.

Logika Gear powinna brać pod uwagę:

- moc,
- kadencję,
- nachylenie,
- prędkość,
- preferowany styl jazdy użytkownika,
- aktualne przełożenie.

Użytkownik preferuje niższą kadencję około 60–70 rpm, więc Gear nie powinien automatycznie sugerować redukcji tylko dlatego, że kadencja jest niższa niż typowe szosowe 85–95 rpm.

Przykładowa logika MVP:

```text
jeśli kadencja niska + moc wysoka + grade dodatni -> bieg może być za ciężki
jeśli kadencja wysoka + moc niska/średnia -> bieg może być za lekki
jeśli kadencja w zakresie preferowanym + moc pod kontrolą -> sweet spot
```

Docelowo Gear może być najbardziej „magiczne” pole, ale pierwsza wersja powinna być prosta i lokalna.

## 7. Kolory i stany

Każde subpole powinno mieć własny stan wizualny.

Kolor ma być funkcjonalny, nie dekoracyjny.

Przykładowa skala stanów:

```text
GOOD / OK / WARN / BAD / NEUTRAL
```

Nie należy przesadzać z jaskrawymi kolorami.
Pole musi pozostać czytelne na mapie Karoo.

Preferowane podejście:

- ciemne tła,
- subtelne cieniowanie,
- mocniejszy kolor tylko jako akcent / tint / pasek / gradient,
- wysoki kontrast cyfr,
- brak wizualnego chaosu.

## 8. Stabilność renderowania wartości

Wartości nie mogą skakać przy zmianie cyfr.

Należy stosować rozwiązania stabilizujące layout:

- stała szerokość slotów;
- stała wysokość slotów;
- stały rozmiar tekstu lub kontrolowany auto-fit;
- font / ustawienia cyfr zapewniające stabilną szerokość;
- wyrównanie wartości w obrębie slotu;
- ograniczenie liczby znaków;
- brak dużych etykiet zmieniających układ.

Przykładowe formatowanie:

```text
Speed:   24.8 albo 25
Power:   185
HR:      142
Cadence: 67
Grade:   4.5 albo +5
Gear:    40×15 / 15 / S / H / OK — do decyzji projektowej
```

W MVP preferować prostsze, krótsze formaty.

## 9. AI i logika lokalna

Podstawowa logika pola LIVE musi działać lokalnie na Karoo.

AI online nie może być głównym systemem podczas jazdy, ze względu na:

- baterię,
- opóźnienia,
- niezawodność,
- brak gwarancji łączności.

Raspberry Pi / bot kolarski może być używany:

- przed jazdą,
- po jeździe,
- na żądanie,
- do analizy formy,
- do konfiguracji zaleceń,
- do przygotowania parametrów dla pola LIVE.

Podczas jazdy pole LIVE ma działać samodzielnie.

Nie budować MVP wokół zapytań online do AI.

## 10. Decyzje projektowe

Projekt ma być nowym projektem Android Studio.

Nie bazujemy funkcjonalnie na Barberfish.
Nie forkować Barberfish jako podstawy aplikacji.

Barberfish jest tylko wzorcem techniczno-graficznym.

Najpierw należy zbudować MVP:

- layout 3×2;
- 6 wartości;
- duże cyfry;
- minimalne ikony;
- proste tła/kolory;
- lokalne reguły kolorów;
- brak dynamicznych komunikatów;
- brak ETA;
- brak pełnoekranowego dashboardu;
- brak AI online.

## 11. Priorytety projektu

Priorytety w kolejności:

1. Czytelność na realnym ekranie Karoo 3.
2. Stabilność wizualna.
3. Właściwe proporcje małego pola.
4. Duże wartości liczbowe.
5. Brak zbędnych nagłówków.
6. Sensowna kolorystyka.
7. Lokalna logika.
8. Inteligentna interpretacja danych.
9. Dopiero później rozbudowa o bardziej zaawansowane reguły.

## 12. Czego nie robić

Nie robić:

- pełnoekranowego dashboardu;
- dużej wizualizacji na cały ekran;
- pola ETA w tym module;
- dynamicznych komunikatów w tym polu;
- dużych nagłówków typu „SPEED”, „POWER”, „HEART RATE”;
- ozdobnego UI kosztem czytelności;
- zbyt wielu kolorów naraz;
- niestabilnych wartości, które zmieniają pozycję przy każdej zmianie cyfr;
- zależności od AI online podczas jazdy;
- forka Barberfish jako bazy funkcjonalnej;
- kopiowania funkcjonalności Barberfish.

## 13. Proponowana struktura implementacji

Docelowo dobrze rozdzielić projekt na warstwy:

```text
UI / layout
- widok pola 3×2
- subpola
- ikony
- typografia
- tła / drawable

Data layer
- odczyt Speed
- odczyt Power
- odczyt HR
- odczyt Cadence
- odczyt Grade
- odczyt Gear

Logic layer
- klasyfikacja Speed
- klasyfikacja Power
- klasyfikacja HR
- klasyfikacja Cadence
- klasyfikacja Grade
- klasyfikacja Gear

Formatting layer
- formatowanie wartości
- formatowanie jednostek
- ograniczanie długości tekstu
```

Nie mieszać całej logiki bezpośrednio w layoutach.

## 14. MVP — minimalna pierwsza wersja

Pierwsza wersja powinna zawierać:

```text
- jedno pole danych LIVE
- układ 3×2
- Speed / Power / HR / Cadence / Grade / Gear
- duże wartości
- minimalne ikony albo skróty
- stabilne sloty
- podstawowe kolory stanów
- lokalne reguły interpretacji
```

MVP nie musi od razu mieć perfekcyjnej logiki Gear.
Wystarczy pierwsza sensowna lokalna reguła.

## 15. Pierwsze zadanie dla OpenCode

Po przeczytaniu tego pliku OpenCode powinien najpierw przeanalizować projekt i zaproponować plan implementacji.

Nie powinien od razu pisać dużej ilości kodu bez planu.

Pierwsze polecenie może brzmieć:

```text
Przeczytaj QBOT_KAROO3_LIVE_FIELD_CONTEXT.md i traktuj go jako główną specyfikację projektu.
Następnie przeanalizuj strukturę obecnego projektu Android/Karoo i zaproponuj minimalny plan implementacji pola LIVE 3×2.
Nie twórz dashboardu fullscreen.
Nie dodawaj ETA.
Nie dodawaj dynamicznych komunikatów.
Skup się wyłącznie na małym polu danych na ekranie mapy.
```
