package si.sluzbataski.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * En task (naloga), ki ga je uporabnik dobil v službi.
 *
 * Življenjski cikel:
 *  1. ustvarjen ([createdAt]) – odprt, viden v seznamu in widgetu
 *  2. opravljen ([completedAt] != null) – izgine iz seznama/widgeta, ostane v arhivu
 *  3. (opcijsko) izbrisan ([deletedAt] != null) – skrit povsod, a v bazi ostane za
 *     statistiko in CSV izvoz. Fizično se ne briše nič.
 *
 * Vse spremembe se dodatno beležijo v tabelo [TaskEvent] (zgodovina taska).
 */
@Entity(
    tableName = "tasks",
    foreignKeys = [ForeignKey(
        entity = Workplace::class,
        parentColumns = ["id"],
        childColumns = ["workplaceId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("workplaceId"), Index("completedAt")],
)
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Delovno mesto, kateremu task pripada. */
    val workplaceId: Long,
    /** Opis naloge (obvezno). */
    val description: String,
    /** Nujnost – glej [Urgency]. */
    val urgency: Urgency = Urgency.NORMALNO,
    /** Rok (epoch millis) – smiseln samo, kadar je [urgency] == [Urgency.DATUM]. */
    val dueAt: Long? = null,
    /** Ali ima rok tudi uro (true) ali velja cel dan (false). */
    val dueHasTime: Boolean = false,
    /** Opcijsko: naročnik / oseba, ki je dala nalogo. */
    val client: String? = null,
    /** Opcijsko: absolutna pot do slike v notranjem pomnilniku aplikacije. */
    val photoPath: String? = null,
    /** Kdaj je bil task vnesen (= "datum nastanka"). */
    val createdAt: Long = System.currentTimeMillis(),
    /** Kdaj je bil task nazadnje spremenjen. */
    val updatedAt: Long = System.currentTimeMillis(),
    /** Kdaj je bil task označen kot opravljen (null = še odprt). */
    val completedAt: Long? = null,
    /** Kdaj je bil task izbrisan (null = ni izbrisan). Mehko brisanje. */
    val deletedAt: Long? = null,
    /** ID dogodka v Android/Google koledarju (če je bil task vpisan v koledar). */
    val calendarEventId: Long? = null,
    /**
     * Ponavljanje: "WEEKLY", "MONTHLY", "YEARLY" ali null (ne ponavlja se).
     * Glej [si.sluzbataski.data.repo.Recurrence].
     */
    val recurrenceType: String? = null,
    /**
     * Izbrani dnevi ponavljanja, ločeni z vejico:
     *  - WEEKLY:  dnevi v tednu 1–7 (1 = ponedeljek), npr. "1,3"
     *  - MONTHLY: dnevi v mesecu 1–31, npr. "1,15"
     *  - YEARLY:  datumi MMdd, npr. "0315,1224"
     */
    val recurrenceDays: String? = null,
    /** ID prvega taska v seriji ponovitev (za povezovanje ponovitev med seboj). */
    val seriesId: Long? = null,
    /** Opomnik: koliko minut pred rokom (0 = ob roku, null = brez opomnika). */
    val reminderMinutes: Int? = null,
) {
    /** Task je odprt, če ni opravljen in ni izbrisan. */
    val isOpen: Boolean get() = completedAt == null && deletedAt == null

    /** Ali ima task veljaven rok. */
    val hasDate: Boolean get() = urgency == Urgency.DATUM && dueAt != null

    /** Ali se task ponavlja. */
    val isRecurring: Boolean get() = recurrenceType != null && !recurrenceDays.isNullOrBlank()
}
