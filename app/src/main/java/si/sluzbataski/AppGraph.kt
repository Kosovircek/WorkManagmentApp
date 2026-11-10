package si.sluzbataski

import android.content.Context
import si.sluzbataski.calendar.CalendarSync
import si.sluzbataski.data.db.AppDatabase
import si.sluzbataski.data.prefs.SettingsStore
import si.sluzbataski.data.repo.ShoppingRepository
import si.sluzbataski.data.repo.TaskRepository
import si.sluzbataski.data.repo.WorkplaceRepository
import si.sluzbataski.export.CsvExporter

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
    val workplaces = WorkplaceRepository(db, settings)
    val tasks = TaskRepository(app, db, calendar)
    val shopping = ShoppingRepository(db)
    val exporter = CsvExporter(app, db)

    companion object {
        @Volatile private var instance: AppGraph? = null

        fun get(context: Context): AppGraph =
            instance ?: synchronized(this) {
                instance ?: AppGraph(context).also { instance = it }
            }
    }
}
