package si.sluzbataski.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Delovno mesto (npr. "Podjetje A", "Popoldanska služba").
 *
 * Vsak task in vsak nakup pripada natanko enemu delovnemu mestu. Uporabnik v aplikaciji
 * (ali na widgetu) preklaplja med delovnimi mesti; prikazani so samo podatki aktivnega.
 *
 * Delovnih mest NIKOLI ne brišemo fizično – le arhiviramo ([archivedAt]), da ostane
 * zgodovina taskov popolna.
 */
@Entity(tableName = "workplaces")
data class Workplace(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Ime, ki se prikazuje v aplikaciji in na widgetih. */
    val name: String,
    /** Barva za hitro vizualno ločevanje delovnih mest (ARGB). */
    val colorArgb: Long = 0xFF3949AB,
    /** Kdaj je bilo delovno mesto ustvarjeno (epoch millis). */
    val createdAt: Long = System.currentTimeMillis(),
    /** Če ni null, je delovno mesto arhivirano in se ne ponuja več pri preklopu. */
    val archivedAt: Long? = null,
)
