package si.sluzbataski.ui.shopping

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import si.sluzbataski.AppGraph
import si.sluzbataski.data.entity.ShoppingItem
import si.sluzbataski.data.entity.Workplace
import si.sluzbataski.ui.components.WorkplaceSwitcher
import si.sluzbataski.ui.theme.SluzbaTaskiTheme
import si.sluzbataski.util.Format

/**
 * NAKUPOVALNI SEZNAM (ločen za vsako delovno mesto).
 *
 *  - zgoraj vnos: izdelek, količina (opc.), trgovina (opc., s predlogi)
 *  - seznam nekupljenih, razdeljen po trgovinah; filter po trgovini
 *  - kljukica = kupljeno (shrani se čas nakupa); spodaj "Nedavno kupljeno",
 *    kjer lahko kljukico odstraniš
 *  - dolg pritisk na postavko = uredi / izbriši
 */
class ShoppingActivity : ComponentActivity() {
    private val vm: ShoppingViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { SluzbaTaskiTheme { Surface { ShoppingScreen(vm) { finish() } } } }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ShoppingViewModel(app: Application) : AndroidViewModel(app) {
    private val g = AppGraph.get(app)
    val active = g.workplaces.observeActive().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val workplaces = g.workplaces.observeWorkplaces().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val open = active.filterNotNull().flatMapLatest { g.shopping.observeOpen(it.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bought = active.filterNotNull().flatMapLatest { g.shopping.observeBought(it.id) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val stores = g.shopping.observeStores().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val knownNames = g.shopping.observeKnownNames().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun switchTo(w: Workplace) = g.workplaces.switchTo(w.id)
    fun add(name: String, qty: String, store: String) = viewModelScope.launch {
        active.value?.let { g.shopping.add(it.id, name, qty, store) }
    }
    fun setBought(i: ShoppingItem, b: Boolean) = viewModelScope.launch { g.shopping.setBought(i, b) }
    fun update(i: ShoppingItem) = viewModelScope.launch { g.shopping.update(i) }
    fun delete(i: ShoppingItem) = viewModelScope.launch { g.shopping.delete(i) }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)
@Composable
private fun ShoppingScreen(vm: ShoppingViewModel, onBack: () -> Unit) {
    val active by vm.active.collectAsStateWithLifecycle()
    val workplaces by vm.workplaces.collectAsStateWithLifecycle()
    val open by vm.open.collectAsStateWithLifecycle()
    val bought by vm.bought.collectAsStateWithLifecycle()
    val stores by vm.stores.collectAsStateWithLifecycle()
    val known by vm.knownNames.collectAsStateWithLifecycle()

    var name by rememberSaveable { mutableStateOf("") }
    var qty by rememberSaveable { mutableStateOf("") }
    var store by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<ShoppingItem?>(null) }
    var showBought by rememberSaveable { mutableStateOf(false) }

    fun submit() {
        if (name.isNotBlank()) { vm.add(name, qty, store); name = ""; qty = "" } // trgovino pustimo za naslednji vnos
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Nakupi") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Nazaj") } },
            actions = { WorkplaceSwitcher(active, workplaces, onSelect = vm::switchTo) },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            // ------------- vnos
            Column(Modifier.padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(name, { name = it }, Modifier.weight(1f), label = { Text("Kaj kupiti") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { submit() }))
                    IconButton(onClick = { submit() }, enabled = name.isNotBlank()) { Icon(Icons.Default.Add, "Dodaj") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(qty, { qty = it }, Modifier.weight(0.4f), label = { Text("Količina") }, singleLine = true)
                    OutlinedTextField(store, { store = it }, Modifier.weight(0.6f), label = { Text("Trgovina (opc.)") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                }
                // predlogi: izdelki med tipkanjem, sicer trgovine
                val nameSuggestions = remember(name, known) {
                    if (name.length < 2) emptyList() else known.filter { it.startsWith(name, true) && !it.equals(name, true) }.take(5)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    nameSuggestions.forEach { s -> SuggestionChip(onClick = { name = s }, label = { Text(s) }) }
                    if (nameSuggestions.isEmpty()) stores.filter { !it.equals(store, true) }.take(6).forEach { s ->
                        SuggestionChip(onClick = { store = s }, label = { Text("🏪 $s") })
                    }
                }
                // filter po trgovini
                val openStores = open.mapNotNull { it.store }.distinct()
                if (openStores.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(filter == null, { filter = null }, { Text("Vse (${open.size})") })
                        openStores.forEach { s ->
                            FilterChip(filter == s, { filter = if (filter == s) null else s }, { Text("$s (${open.count { it.store == s }})") })
                        }
                    }
                }
            }

            // ------------- seznam
            val visible = open.filter { filter == null || it.store == filter }
            LazyColumn(Modifier.fillMaxSize()) {
                if (visible.isEmpty()) item { Text("Seznam je prazen.", Modifier.padding(16.dp)) }
                visible.groupBy { it.store ?: "" }.forEach { (s, group) ->
                    item(key = "store_$s") {
                        Text(if (s.isEmpty()) "Brez trgovine" else s, style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 10.dp))
                    }
                    items(group, key = { it.id }) { i -> ItemRow(i, onCheck = { vm.setBought(i, true) }, onLong = { editing = i }) }
                }
                item(key = "bought_header") {
                    TextButton(onClick = { showBought = !showBought }, modifier = Modifier.padding(8.dp)) {
                        Text((if (showBought) "▾ " else "▸ ") + "Nedavno kupljeno (${bought.size})")
                    }
                }
                if (showBought) items(bought, key = { "b${it.id}" }) { i ->
                    ItemRow(i, onCheck = { vm.setBought(i, false) }, onLong = { editing = i })
                }
            }
        }
    }

    editing?.let { item -> EditDialog(item, onDismiss = { editing = null },
        onSave = { vm.update(it); editing = null }, onDelete = { vm.delete(item); editing = null }) }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(i: ShoppingItem, onCheck: () -> Unit, onLong: () -> Unit) {
    val isBought = i.boughtAt != null
    Row(
        Modifier.fillMaxWidth().combinedClickable(onClick = onCheck, onLongClick = onLong).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = isBought, onCheckedChange = { onCheck() })
        Column(Modifier.weight(1f)) {
            Text(i.name + (i.quantity?.let { "  ·  $it" } ?: ""),
                textDecoration = if (isBought) TextDecoration.LineThrough else null)
            if (isBought) Text("kupljeno ${Format.dateTime(i.boughtAt!!)}" + (i.store?.let { " · $it" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EditDialog(item: ShoppingItem, onDismiss: () -> Unit, onSave: (ShoppingItem) -> Unit, onDelete: () -> Unit) {
    var name by remember { mutableStateOf(item.name) }
    var qty by remember { mutableStateOf(item.quantity ?: "") }
    var store by remember { mutableStateOf(item.store ?: "") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Uredi postavko") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Izdelek") }, singleLine = true)
                OutlinedTextField(qty, { qty = it }, label = { Text("Količina") }, singleLine = true)
                OutlinedTextField(store, { store = it }, label = { Text("Trgovina") }, singleLine = true)
                Text("Dodano: ${Format.dateTime(item.createdAt)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(item.copy(name = name.trim().ifBlank { item.name }, quantity = qty.trim().ifBlank { null }, store = store.trim().ifBlank { null }))
            }) { Text("Shrani") }
        },
        dismissButton = { Row { TextButton(onClick = onDelete) { Text("Izbriši") }; TextButton(onClick = onDismiss) { Text("Prekliči") } } },
    )
}
