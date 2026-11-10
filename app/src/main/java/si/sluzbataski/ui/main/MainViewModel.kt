package si.sluzbataski.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import si.sluzbataski.AppGraph
import si.sluzbataski.data.entity.Task
import si.sluzbataski.data.entity.Workplace
import si.sluzbataski.widget.WidgetUpdater

/**
 * ViewModel glavnega zaslona: drži aktivno delovno mesto in razvrščen seznam
 * odprtih taskov. Seznam se samodejno posodobi ob vsaki spremembi v bazi
 * (Room Flow) ali preklopu delovnega mesta.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val g = AppGraph.get(app)

    val workplaces: StateFlow<List<Workplace>> =
        g.workplaces.observeWorkplaces().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val active: StateFlow<Workplace?> =
        g.workplaces.observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val tasks: StateFlow<List<Task>?> = active.filterNotNull()
        .flatMapLatest { g.tasks.observeOpenSorted(it.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun switchTo(w: Workplace) {
        g.workplaces.switchTo(w.id)
        viewModelScope.launch { WidgetUpdater.updateAll(getApplication()) }
    }

    fun complete(id: Long) = viewModelScope.launch { g.tasks.complete(id) }
    fun undoComplete(id: Long) = viewModelScope.launch { g.tasks.reopen(id) }
    fun shift(id: Long, up: Boolean) = viewModelScope.launch { g.tasks.shiftUrgency(id, up) }
}
