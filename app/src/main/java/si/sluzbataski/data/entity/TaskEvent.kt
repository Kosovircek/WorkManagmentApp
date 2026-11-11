package si.sluzbataski.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Vrsta dogodka v zgodovini taska.
 */
enum class TaskEventType(val label: String) {
    CREATED("Ustvarjen"),
    EDITED("Urejen"),
    URGENCY_CHANGED("Sprememba nujnosti"),
    COMPLETED("Opravljen"),
    REOPENED("Ponovno odprt"),
    DELETED("Izbrisan"),
    RESTORED("Obnovljen"),
    PHOTO_CHANGED("Sprememba slike"),
    MOVED_WORKPLACE("Premaknjen na drugo delovno mesto"),
    NEXT_OCCURRENCE("Ustvarjena naslednja ponovitev"),
    REMINDER("Opomnik");

    companion object {
        fun fromName(name: String?): TaskEventType = entries.firstOrNull { it.name == name } ?: EDITED
    }
}

/**
 * En zapis v zgodovini taska (dnevnik sprememb).
 *
 * Ta tabela je samo za dodajanje (append-only): vrstic nikoli ne spreminjamo ali brišemo.
 * Iz nje lahko kadarkoli rekonstruiramo, kaj se je s taskom dogajalo in kdaj.
 */
@Entity(tableName = "task_events", indices = [Index("taskId"), Index("at")])
data class TaskEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val workplaceId: Long,
    val type: TaskEventType,
    /** Čas dogodka (epoch millis). */
    val at: Long = System.currentTimeMillis(),
    /** Človeku berljiv opis spremembe, npr. "Normalno → Nujno". */
    val detail: String? = null,
)
