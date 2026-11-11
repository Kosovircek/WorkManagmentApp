package si.sluzbataski.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import si.sluzbataski.data.dao.ShoppingDao
import si.sluzbataski.data.dao.TaskDao
import si.sluzbataski.data.dao.TaskEventDao
import si.sluzbataski.data.dao.WorkplaceDao
import si.sluzbataski.data.entity.ShoppingItem
import si.sluzbataski.data.entity.Task
import si.sluzbataski.data.entity.TaskEvent
import si.sluzbataski.data.entity.Workplace

/**
 * Glavna Room baza aplikacije (datoteka "sluzbataski.db" v notranjem pomnilniku).
 *
 * POMEMBNO za prihodnje spremembe: ko spremeniš katerokoli entiteto, povečaj [version]
 * in dodaj Migration – NIKOLI ne uporabljaj fallbackToDestructiveMigration(), ker bi
 * to izbrisalo celotno zgodovino.
 */
@Database(
    entities = [Workplace::class, Task::class, TaskEvent::class, ShoppingItem::class],
    version = 2,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workplaceDao(): WorkplaceDao
    abstract fun taskDao(): TaskDao
    abstract fun taskEventDao(): TaskEventDao
    abstract fun shoppingDao(): ShoppingDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        /** v1 → v2: ponavljajoči taski in opomniki (samo nova, prazna polja; podatki ostanejo). */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceType TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN recurrenceDays TEXT")
                db.execSQL("ALTER TABLE tasks ADD COLUMN seriesId INTEGER")
                db.execSQL("ALTER TABLE tasks ADD COLUMN reminderMinutes INTEGER")
            }
        }

        /** Vrne edino instanco baze (singleton) – varno tudi iz widgetov. */
        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "sluzbataski.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
