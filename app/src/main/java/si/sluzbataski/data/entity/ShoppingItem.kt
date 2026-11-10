package si.sluzbataski.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Postavka na nakupovalnem seznamu (za določeno delovno mesto).
 *
 * Ko uporabnik postavko odkljuka, se nastavi [boughtAt] – postavka ostane v bazi
 * za vedno (zgodovina nakupov, statistika, CSV izvoz).
 */
@Entity(
    tableName = "shopping_items",
    foreignKeys = [ForeignKey(
        entity = Workplace::class,
        parentColumns = ["id"],
        childColumns = ["workplaceId"],
        onDelete = ForeignKey.RESTRICT,
    )],
    indices = [Index("workplaceId")],
)
data class ShoppingItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workplaceId: Long,
    /** Kaj je treba kupiti. */
    val name: String,
    /** Opcijsko: količina kot prosto besedilo ("2 kg", "3x"). */
    val quantity: String? = null,
    /** Opcijsko: oznaka trgovine (npr. "Merkur", "Hofer"). */
    val store: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    /** Kdaj je bilo kupljeno (null = še ni kupljeno). */
    val boughtAt: Long? = null,
    /** Mehko brisanje (napačen vnos). */
    val deletedAt: Long? = null,
)
