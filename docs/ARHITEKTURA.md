# Arhitektura

## 1. Funkcionalni diagram

Kako se deli aplikacije povezujejo (od uporabnika do podatkov):

```mermaid
flowchart TB
    U([Uporabnik])

    subgraph HOME["Domači zaslon"]
        W1["Widget: Hitri vnos<br/>QuickAddWidget"]
        W2["Widget: Seznam taskov<br/>TaskListWidget"]
    end

    subgraph UI["Aktivnosti (Jetpack Compose)"]
        QA["Hitri vnos<br/>QuickAddActivity"]
        MA["Glavni seznam<br/>MainActivity"]
        AD["Nov task<br/>AddTaskActivity"]
        TD["Podrobnosti<br/>TaskDetailActivity"]
        AR["Arhiv<br/>ArchiveActivity"]
        SH["Nakupi<br/>ShoppingActivity"]
        ST["Statistika<br/>StatsActivity"]
        SE["Nastavitve + izvoz<br/>SettingsActivity"]
    end

    subgraph LOGIC["Poslovna logika (repozitoriji)"]
        TR["TaskRepository<br/>taski + zgodovina"]
        WR["WorkplaceRepository<br/>delovna mesta, preklop"]
        SR["ShoppingRepository"]
        SC["StatsCalculator"]
        CE["CsvExporter"]
    end

    subgraph DATA["Podatki"]
        DB[("Room baza<br/>sluzbataski.db")]
        PR[("SharedPreferences<br/>aktivno delovno mesto")]
        PH[("files/photos<br/>slike")]
    end

    GC[["Google Koledar<br/>(CalendarContract)"]]
    CSV[["CSV datoteke<br/>(izbrana mapa)"]]

    U --> W1 & W2 & MA
    W1 -- "klik gumba (nujnost/kamera)" --> QA
    W1 & W2 -- "klik ⇄" --> WR
    W2 -- "klik ✓" --> TR
    W2 -- "klik taska" --> TD
    MA --> AD & TD & AR & SH & ST & SE
    QA & AD & TD & MA & AR --> TR
    SH --> SR
    ST --> SC
    SE --> WR & CE
    TR --> DB
    TR -- "taski z datumom" --> CalendarSync --> GC
    TR -- "po vsaki spremembi" --> WU["WidgetUpdater"] --> W1 & W2
    WR --> DB & PR
    SR --> DB
    SC --> DB
    CE --> DB --> CE --> CSV
    QA & AD & TD -- "kamera / galerija" --> PH
```

## 2. Sloji

| Sloj | Paket | Naloga |
|---|---|---|
| **Predstavitev** | `ui.*`, `widget` | Compose zasloni in Glance widgeti. Ne vsebujejo poslovne logike. |
| **ViewModel** | `*ViewModel` ob vsaki aktivnosti | Pretvori podatke iz repozitorijev v `StateFlow`, preživi zasuk zaslona. |
| **Repozitoriji** | `data.repo` | *Edina* pot do spreminjanja podatkov. Skrbijo za zgodovino, koledar in osvežitev widgetov. |
| **Dostop do podatkov** | `data.dao`, `data.db`, `data.prefs` | Room DAO-ji (SQL), baza, nastavitve. |
| **Storitve** | `calendar`, `photo`, `export`, `stats` | Koledar, slike, CSV izvoz, izračun statistike. |
| **Povezovanje** | `AppGraph` | Ustvari in hrani vse zgornje objekte (preprost service locator). |

Pravilo: **zasloni in widgeti nikoli ne pišejo neposredno v bazo**, vedno gredo prek repozitorija.
Zato se vsaka sprememba zagotovo zapiše v zgodovino, prenese v koledar in prikaže na widgetih.

## 3. Podatkovni model

```mermaid
erDiagram
    WORKPLACES ||--o{ TASKS : "ima"
    WORKPLACES ||--o{ SHOPPING_ITEMS : "ima"
    TASKS ||--o{ TASK_EVENTS : "zgodovina"

    WORKPLACES {
        long id PK
        string name
        long colorArgb
        long createdAt
        long archivedAt "null = aktivno"
    }
    TASKS {
        long id PK
        long workplaceId FK
        string description
        string urgency "DATUM, URGENTNO, NUJNO, NORMALNO, KO_BO_CAS"
        long dueAt "samo pri DATUM"
        bool dueHasTime
        string client "opcijsko"
        string photoPath "opcijsko"
        long createdAt
        long updatedAt
        long completedAt "null = odprt"
        long deletedAt "null = ni izbrisan"
        long calendarEventId
        string recurrenceType "WEEKLY, MONTHLY, YEARLY ali null"
        string recurrenceDays "npr. 1,3 / 1,15 / 0315,1224"
        long seriesId "prvi task serije ponovitev"
        int reminderMinutes "minute pred rokom ali null"
    }
    TASK_EVENTS {
        long id PK
        long taskId
        long workplaceId
        string type "CREATED, EDITED, URGENCY_CHANGED, COMPLETED, ..."
        long at
        string detail
    }
    SHOPPING_ITEMS {
        long id PK
        long workplaceId FK
        string name
        string quantity
        string store
        long createdAt
        long boughtAt "null = še ni kupljeno"
        long deletedAt
    }
```

Vsi časi so v milisekundah od 1. 1. 1970 (epoch). **Nič se ne briše fizično** – "brisanje" le nastavi `deletedAt`.

## 4. Pravilo razvrščanja (TaskSorting)

1. Taski z nujnostjo **Datum** – najbližji (ali zamujeni) rok najprej.
2. **Urgentno → Nujno → Normalno → Ko bo čas.**
3. Znotraj iste skupine: **najstarejši vnos najprej** (kar najdlje čaka, je najvišje).

Isto pravilo uporabljata glavni seznam in widget (`data/repo/TaskSorting.kt`).

## 5. Življenjski cikel taska

```mermaid
stateDiagram-v2
    [*] --> Odprt: vnos (widget / aplikacija)
    Odprt --> Odprt: urejanje, ▲▼ nujnost
    Odprt --> Opravljen: ✓
    Opravljen --> Odprt: Ponovno odpri
    Odprt --> Izbrisan: Izbriši
    Opravljen --> Izbrisan: Izbriši
    Izbrisan --> Odprt: Obnovi
```

Vsak prehod zapiše vrstico v `task_events`.

## 6. Tok: hitri vnos z widgeta

```mermaid
sequenceDiagram
    actor U as Uporabnik
    participant W as QuickAddWidget
    participant Q as QuickAddActivity
    participant R as TaskRepository
    participant DB as Room
    participant C as CalendarSync
    participant WU as WidgetUpdater

    U->>W: klik "Nujno"
    W->>Q: Intent (urgency=NUJNO)
    Q-->>U: okno z obrazcem, tipkovnica
    U->>Q: opis (+ slika) → Shrani
    Q->>R: add(...)
    R->>DB: INSERT tasks + task_events
    R->>C: sync(task) (samo pri Datum)
    R->>WU: updateAll()
    WU->>W: ponovni izris obeh widgetov
    Q-->>U: "Task shranjen ✓", okno se zapre
```

## 7. Google Koledar

Aplikacija uporablja Androidov *Calendar Provider*, ki vsebuje koledarje Google računov na telefonu.
Dogodek, vpisan v koledar Google računa, Android sam sinhronizira z Google Koledarjem, zato ni
potrebna nobena Google prijava ali API ključ. Ciljni koledar je ročno izbran v Nastavitvah ali
(privzeto) koledar z imenom `SluzbaTaski`. Če dovoljenje ob vnosu še ni bilo dano, se task vpiše
ob naslednjem zagonu (`TaskRepository.syncPendingCalendar`). Pred vpisom novega dogodka aplikacija
preveri, ali v koledarju že obstaja dogodek z enakim naslovom in začetkom (npr. po uvozu na nov
telefon z istim Google računom) in ga prevzame, namesto da bi ga podvojila.

## 8. Ponavljajoči taski in opomniki

**Ponavljanje** (`data/repo/Recurrence.kt`): ponavljajoč task ima vedno datum. Ko ga označiš kot
opravljenega, `TaskRepository.complete` izračuna naslednji ustrezen dan (po roku trenutnega, a ne
v preteklosti) in ustvari nov task z enakimi podatki (brez slike), povezan prek `seriesId`.

**Opomniki** (`reminder/`): `ReminderScheduler` nastavi `AlarmManager.setAlarmClock` (točno, kot
budilka) `reminderMinutes` pred rokom (pri tasku brez ure ob 9:00). `ReminderReceiver` pokaže
obvestilo z gumboma *Opravljeno* in *Odloži 10 min* ter vibrira. Alarm se nastavi/prekliče ob vsaki
spremembi taska, `BootReceiver` pa jih po ponovnem zagonu telefona nastavi znova.

```mermaid
sequenceDiagram
    participant R as TaskRepository
    participant S as ReminderScheduler
    participant A as AlarmManager
    participant RR as ReminderReceiver
    actor U as Uporabnik
    R->>S: schedule(task) ob vsaki spremembi
    S->>A: setAlarmClock(rok − opomnik)
    A-->>RR: ob času opomnika
    RR->>U: obvestilo + vibriranje
    U->>RR: "Opravljeno" / "Odloži 10 min"
    RR->>R: complete(id)  (ponavljajoč → nova ponovitev)
```

## 9. Spreminjanje baze v prihodnosti

Zgodovina verzij: **v1** začetna, **v2** ponavljanje in opomniki (`MIGRATION_1_2`).


Ob spremembi entitete povečaj `version` v `AppDatabase` in dodaj `Migration`. **Nikoli** ne uporabi
`fallbackToDestructiveMigration()` – to bi izbrisalo celotno zgodovino.
