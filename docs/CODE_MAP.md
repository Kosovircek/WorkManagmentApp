# Zemljevid kode (code map)

Vsa koda je v `app/src/main/java/si/sluzbataski/`. Vsaka datoteka ima na vrhu komentar, ki
opisuje, kaj počne.

```
si/sluzbataski/
├── SluzbaTaskiApp.kt          Application: ob zagonu ustvari privzeto delovno mesto, dopolni koledar
├── AppGraph.kt                "Service locator": ena instanca baze, repozitorijev, koledarja, izvoza
│
├── data/
│   ├── entity/                Room tabele (podatkovni model)
│   │   ├── Workplace.kt         delovno mesto
│   │   ├── Task.kt              task (opis, nujnost, rok, naročnik, slika, časi)
│   │   ├── TaskEvent.kt         zgodovina sprememb taska + vrste dogodkov
│   │   ├── ShoppingItem.kt      postavka nakupovalnega seznama
│   │   └── Urgency.kt           nujnosti: ime, barva, vrstni red
│   ├── dao/                   SQL poizvedbe (Room DAO)
│   │   ├── WorkplaceDao.kt, TaskDao.kt, TaskEventDao.kt, ShoppingDao.kt
│   ├── db/
│   │   ├── AppDatabase.kt       definicija baze in verzija (MIGRACIJE!)
│   │   └── Converters.kt        enum <-> besedilo
│   ├── prefs/SettingsStore.kt aktivno delovno mesto, izbran koledar
│   └── repo/                  POSLOVNA LOGIKA – edina pot za spremembe
│       ├── TaskRepository.kt    dodaj/uredi/opravi/izbriši + zgodovina + koledar + widgeti
│       ├── TaskSorting.kt       pravilo razvrščanja (eno mesto za app in widget)
│       ├── WorkplaceRepository.kt  delovna mesta, preklop, kroženje za widget
│       └── ShoppingRepository.kt
│
├── calendar/CalendarSync.kt   vpis/posodobitev/brisanje dogodkov v Google koledarju
├── photo/PhotoStorage.kt      datoteke slik, FileProvider za kamero, nalaganje sličic
├── export/CsvExporter.kt      izvoz vseh tabel v CSV (; ločilo, UTF-8 BOM za Excel) + slike
├── export/CsvImporter.kt      uvoz izvožene mape (združevanje brez podvajanja)
├── stats/StatsCalculator.kt   izračun vseh številk in serij za statistiko (čisti Kotlin)
├── util/Format.kt             slovensko oblikovanje datumov, starosti, trajanj
│
├── widget/
│   ├── QuickAddWidget.kt      widget za hitri vnos (+ Receiver)
│   ├── TaskListWidget.kt      widget s seznamom taskov (+ Receiver)
│   ├── WidgetActions.kt       dejanja: preklop mesta, opravljeno; Intenti za odpiranje zaslonov
│   ├── WidgetUi.kt            skupni gradniki (glava, gumbi, svetle/temne barve)
│   ├── WidgetData.kt          živo opazovanje podatkov za widgeta
│   └── WidgetUpdater.kt       osveži oba widgeta
│
└── ui/
    ├── theme/Theme.kt         Material 3 tema (dinamične barve, temni način)
    ├── components/
    │   ├── TaskForm.kt          obrazec taska (opis+narek, nujnost, datum/ura, naročnik, slika)
    │   ├── TaskCard.kt          kartica taska v seznamih
    │   ├── Charts.kt            grafi (stolpčni, vodoravni) in KPI ploščice
    │   ├── PhotoThumb.kt        predogled slike + celozaslonski ogled
    │   └── Common.kt            izbirnik delovnega mesta, barvna pika, pomožne funkcije
    ├── main/                  MainActivity + MainViewModel – glavni seznam
    ├── add/                   AddTaskActivity (poln zaslon), QuickAddActivity (okno z widgeta), AddTaskViewModel
    ├── detail/                TaskDetailActivity – urejanje, opravljeno, zgodovina
    ├── archive/               ArchiveActivity – opravljeni in izbrisani
    ├── shopping/              ShoppingActivity – nakupi
    ├── stats/                 StatsActivity – statistika
    └── settings/              SettingsActivity – delovna mesta, koledar, CSV izvoz
```

Viri (`app/src/main/res/`): `xml/*_widget_info.xml` (velikost widgetov), `xml/file_paths.xml`
(FileProvider), `values/themes.xml` (prosojna tema hitrega vnosa), ikona v `drawable` in `mipmap-anydpi-v26`.

## Kje kaj spremeniti

| Želim … | Datoteka |
|---|---|
| spremeniti vrstni red taskov | `data/repo/TaskSorting.kt` |
| dodati/preimenovati nujnost ali barvo | `data/entity/Urgency.kt` |
| dodati polje tasku | `Task.kt` → `AppDatabase` (verzija + migracija) → `TaskForm.kt` → `CsvExporter.kt` |
| spremeniti videz widgeta | `widget/QuickAddWidget.kt`, `widget/TaskListWidget.kt`, `widget/WidgetUi.kt` |
| spremeniti ime koledarja | `CalendarSync.CALENDAR_NAME` |
| dodati statistiko | `stats/StatsCalculator.kt` (izračun) + `ui/stats/StatsActivity.kt` (prikaz) |
| dodati stolpec v CSV | `export/CsvExporter.kt` in `export/CsvImporter.kt` |
| spremeniti barve teme | `ui/theme/Theme.kt` (aplikacija), `WidgetColors` v `widget/WidgetUi.kt` (widgeti) |
