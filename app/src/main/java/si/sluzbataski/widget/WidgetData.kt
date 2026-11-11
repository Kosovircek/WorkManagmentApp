package si.sluzbataski.widget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.flowOf
import si.sluzbataski.AppGraph
import si.sluzbataski.data.entity.Task
import si.sluzbataski.data.entity.Workplace

/**
 * Podatki, ki jih prikazujeta widgeta: aktivno delovno mesto, ali je preklop mogoč
 * in razvrščeni odprti taski.
 *
 * ZAKAJ OPAZOVANJE (observe) IN NE ENKRATNO BRANJE:
 * Glance po izrisu nekaj časa drži widget "živ" (seja). Klic updateAll() med sejo widget
 * samo ponovno sestavi (recompose) – NE pokliče ponovno provideGlance(). Če bi podatke
 * prebrali le enkrat pred izrisom, bi widget kazal stare podatke (npr. staro delovno
 * mesto po preklopu v aplikaciji). Zato podatke opazujemo kot Flow znotraj provideContent.
 */
data class WidgetData(
    val workplace: Workplace?,
    val workplaces: List<Workplace>,
    val tasks: List<Task>,
) {
    /** Preklop ima smisel samo, če obstaja več kot eno delovno mesto. */
    val canSwitch: Boolean get() = workplaces.size > 1

    companion object {
        /** Enkratno branje – za prvi izris. */
        suspend fun load(graph: AppGraph): WidgetData {
            val wp = graph.workplaces.getActive()
            val all = graph.db.workplaceDao().getActive()
            val tasks = wp?.let { graph.tasks.getOpenSorted(it.id) }.orEmpty()
            return WidgetData(wp, all, tasks)
        }

        /** Živo opazovanje baze in nastavitev znotraj Glance kompozicije. */
        @Composable
        fun observe(graph: AppGraph, initial: WidgetData): WidgetData {
            val workplaces by remember { graph.workplaces.observeWorkplaces() }
                .collectAsState(initial = initial.workplaces)
            val activeId by remember { graph.settings.observeActiveWorkplaceId() }
                .collectAsState(initial = initial.workplace?.id ?: -1L)

            val active = workplaces.firstOrNull { it.id == activeId } ?: workplaces.firstOrNull()
            val tasks by remember(active?.id) {
                active?.let { graph.tasks.observeOpenSorted(it.id) } ?: flowOf(emptyList())
            }.collectAsState(initial = if (active?.id == initial.workplace?.id) initial.tasks else emptyList())

            return WidgetData(active, workplaces, tasks)
        }
    }
}
