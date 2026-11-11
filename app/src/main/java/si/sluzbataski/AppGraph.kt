package si.sluzbataski

import android.content.Context
import si.sluzbataski.calendar.CalendarSync
import si.sluzbataski.data.db.AppDatabase
import si.sluzbataski.data.prefs.SettingsStore
import si.sluzbataski.data.repo.ShoppingRepository
import si.sluzbataski.data.repo.TaskRepository
import si.sluzbataski.data.repo.WorkplaceRepository
import si.sluzbataski.export.CsvExporter
import si.sluzbataski.export.CsvImporter
import si.sluzbataski.widget.WidgetUpdater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Preprost "service locator": na enem mestu ustvari in hrani vse glavne objekte
 * (bazo, repozitorije, koledar ...). Do njega dostopajo aktivnosti, ViewModeli in widgeti
 * prek [AppGraph.get].
 *
 * Namesto knjižnice za vbrizgavanje odvisnosti (Hilt/Koin) uporabljamo ta preprost pristop,
 * ker je aplikacija majhna in je tako koda lažje berljiva.
 */
class AppGraph private constructor(context: Context) {
    private val app = context.applicationContext

    val db: AppDatabase = AppDatabase.get(app)
    val settings = SettingsStore(app)
    val calendar = CalendarSync(app, settings)
    /** Obseg za kratka ozadna opravila (npr. osvežitev widgetov po preklopu). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val workplaces = WorkplaceRepository(db, settings) { scope.launch { WidgetUpdater.updateAll(app) } }
    val tasks = TaskRepository(app, db, calendar)
    val shopping = ShoppingRepository(db)
    val exporter = CsvExporter(app, db)
    val importer = CsvImporter(app, db)

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context).also { instance = it }
            }
    }
}
