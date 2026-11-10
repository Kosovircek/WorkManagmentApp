# Službeni taski (SluzbaTaski)

Osebni Android task manager za službo: hiter vnos nalog z domačega zaslona, seznam razvrščen po
nujnosti in datumu, več delovnih mest, Google Koledar, nakupovalni seznam, statistika in CSV izvoz.
Napisano v **Kotlinu** (Jetpack Compose, Room, Glance), vsa koda je podrobno komentirana v slovenščini.

## Funkcije na kratko

| Kje | Kaj |
|---|---|
| **Widget "Hitri vnos"** | gumbi Ko bo čas / Normalno / Nujno / Urgentno / Z datumom / S sliko → odpre majhno okno z že izbrano nujnostjo, tipkovnica takoj pripravljena, po shranjevanju se zapre in je naslednjič spet prazno |
| **Widget "Seznam taskov"** | odprti taski aktivnega delovnega mesta (najprej z datumom, nato po nujnosti, nato najstarejši), ✓ za opravljeno, klik za podrobnosti, "+" za nov task |
| **Oba widgeta** | klik na ime delovnega mesta (⇄) preklopi na naslednje delovno mesto |
| **Glavni zaslon** | isti seznam, razdeljen po skupinah; ▲▼ premik nujnosti, ✓ opravljeno (z razveljavitvijo) |
| **Nov task / podrobnosti** | opis (tudi z glasom 🎤), nujnost ali datum (+ opcijska ura), naročnik s predlogi, slika iz kamere ali galerije, zgodovina vseh sprememb |
| **Google Koledar** | taski z datumom se vpišejo v koledar **SluzbaTaski**; ob opravljenem dobijo "✔", ob izbrisu se odstranijo |
| **Nakupi** | seznam po delovnih mestih, opcijska količina in trgovina, filter po trgovini, zgodovina kupljenega |
| **Statistika** | prejeti/opravljeni, povprečni in mediani čas izvedbe, zamujeni, grafi skozi čas, po nujnosti, naročnikih, dnevih v tednu, trgovinah |
| **Arhiv** | vsi opravljeni in izbrisani taski z iskanjem; vse je mogoče ponovno odpreti ali obnoviti |
| **Nastavitve** | gumba za postavitev widgetov na domači zaslon, delovna mesta (dodaj, preimenuj, barva, arhiviraj), izbira koledarja, **izvoz vseh podatkov v CSV** |

Nič se nikoli ne izbriše iz baze: opravljeni taski, izbrisani taski, kupljene stvari in vsaka
sprememba ostanejo shranjeni s časi (ustvarjeno, spremenjeno, opravljeno, izbrisano).

## Namestitev

**Najlažje (brez Android Studia):** odpri zavihek **Actions** v tem repozitoriju → zadnji uspešen
zagon "Android build" → spodaj **Artifacts → SluzbaTaski-debug-apk**. Razpakiraj ZIP, APK prenesi na
telefon in ga namesti (dovoli "namestitev iz neznanih virov").

**Z Android Studiem:** `File → Open` na to mapo, počakaj na Gradle sync, priklopi telefon in pritisni ▶.
Iz ukazne vrstice: `./gradlew assembleDebug` (potrebuješ JDK 17 in Android SDK 34).

## Prva nastavitev

1. **Google Koledar:** na [calendar.google.com](https://calendar.google.com) → *Drugi koledarji → + → Ustvari nov koledar* →
   ime `SluzbaTaski`. Na telefonu v aplikaciji Google Koledar preveri, da je ta koledar vklopljen za sinhronizacijo.
   V aplikaciji *Nastavitve → Google koledar* dovoli dostop; prikaže se "✓ Ciljni koledar: SluzbaTaski".
   (Namesto tega lahko izbereš katerikoli drug koledar.)
2. **Delovna mesta:** ob prvem zagonu obstaja "Služba". V *Nastavitvah* ga preimenuj in dodaj ostala.
3. **Widgeta:** v aplikaciji *Meni → Dodaj widget na domači zaslon* in pritisni *+ Hitri vnos* / *+ Seznam taskov*. Ali ročno: dolg pritisk na domači zaslon → *Pripomočki* → "Službeni taski" (na Xiaomiju se pomakni navzdol pod sistemske pripomočke).

## Dokumentacija

- [docs/ARHITEKTURA.md](docs/ARHITEKTURA.md) – funkcionalni diagram, sloji, podatkovni model, tokovi podatkov
- [docs/UPORABNIKI.md](docs/UPORABNIKI.md) – uporabniki, primeri uporabe, diagram primerov uporabe
- [docs/CODE_MAP.md](docs/CODE_MAP.md) – zemljevid kode: katera datoteka kaj dela in kje kaj spremeniti

## Tehnologije

Kotlin 2.0 · Jetpack Compose (Material 3) · Room 2.6 · Glance 1.1 (widgeti) · Android Calendar Provider ·
minSdk 26 (Android 8.0) · targetSdk 34
