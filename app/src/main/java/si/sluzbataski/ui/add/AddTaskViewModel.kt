package si.sluzbataski.ui.add

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import si.sluzbataski.AppGraph
import si.sluzbataski.data.entity.Workplace
import si.sluzbataski.ui.components.TaskFormState
import si.sluzbataski.widget.WidgetUpdater

/**
 * Skupni ViewModel za oba načina dodajanja: polni zaslon [AddTaskActivity]
 * in okno hitrega vnosa [QuickAddActivity].
 */
class AddTaskViewModel(app: Application) : AndroidViewModel(app) {
    private val g = AppGraph.get(app)

    val active: StateFlow<Workplace?> =
        g.workplaces.observeActive().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val workplaces: StateFlow<List<Workplace>> =
        g.workplaces.observeWorkplaces().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val clients: StateFlow<List<String>> =
        g.tasks.observeClients().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun switchTo(w: Workplace) {
        g.workplaces.switchTo(w.id)
        viewModelScope.launch { WidgetUpdater.updateAll(getApplication()) }
    }

    /**
     * Shrani task v aktivno delovno mesto. [onSaved] se pokliče na glavni niti,
     * ko je task zapisan (in widgeti osveženi).
     */
    fun save(state: TaskFormState, onSaved: () -> Unit) {
        viewModelScope.launch {
            val wp = active.value ?: g.workplaces.getActive() ?: return@launch
            g.tasks.add(
                workplaceId = wp.id,
                description = state.description,
                urgency = state.urgency,
                dueAt = state.dueAt,
                dueHasTime = state.dueHasTime,
                client = state.client,
                photoPath = state.photoPath,
            )
            onSaved()
        }
    }
}
