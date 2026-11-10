package si.sluzbataski.data.repo

import si.sluzbataski.data.entity.Task

/**
 * EDINO mesto, kjer je definirano, v kakšnem vrstnem redu se prikazujejo odprti taski.
 * Uporabljajo ga glavni seznam in widget, da sta vedno enaka.
 *
 * Pravilo:
 *  1. Taski z DATUMOM so na vrhu, razvrščeni po roku (najbližji / zamujeni najprej).
 *  2. Nato po nujnosti: Urgentno → Nujno → Normalno → Ko bo čas.
 *  3. Znotraj iste nujnosti: najstarejši vnos najprej (kar najdlje čaka, je višje).
 */
object TaskSorting {
    val comparator: Comparator<Task> = compareBy<Task>(
        { it.urgency.rank },
        { if (it.hasDate) it.dueAt else Long.MAX_VALUE },
        { it.createdAt },
        { it.id },
    )

    fun sort(tasks: List<Task>): List<Task> = tasks.sortedWith(comparator)
}
