package com.lmg.vk.ui.effects

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal data class DustRetainedItem<T>(val item: T, val index: Int)

internal fun <T> retainDustRows(
    items: List<T>,
    retained: Collection<DustRetainedItem<T>>,
    keyOf: (T) -> String,
): List<T> {
    if (retained.isEmpty()) return items
    val keys = items.mapTo(hashSetOf(), keyOf)
    val result = items.toMutableList()
    retained.sortedBy { it.index }.forEach {
        if (keys.add(keyOf(it.item))) result.add(it.index.coerceIn(0, result.size), it.item)
    }
    return result
}

internal class DustRemovalList<T>(
    val items: List<T>,
    val dissolvingKeys: Set<String>,
    val retain: (T) -> Unit,
    val finish: (String) -> Unit,
)

@Composable
internal fun <T> rememberDustRemovalList(
    items: List<T>,
    resetKey: Any?,
    keyOf: (T) -> String,
): DustRemovalList<T> {
    val retained = remember(resetKey) { mutableStateMapOf<String, DustRetainedItem<T>>() }
    val scope = rememberCoroutineScope()
    val present = items.mapTo(hashSetOf(), keyOf)
    val dissolving = retained.keys.filterTo(hashSetOf()) { it !in present }
    LaunchedEffect(dissolving) {
        if (dissolving.isNotEmpty()) {
            delay(1500)
            dissolving.forEach { retained.remove(it) }
        }
    }
    return DustRemovalList(
        items = retainDustRows(items, retained.values, keyOf),
        dissolvingKeys = dissolving,
        retain = { item ->
            val key = keyOf(item)
            val entry = DustRetainedItem(item, items.indexOfFirst { keyOf(it) == key })
            retained[key] = entry
            scope.launch {
                delay(10000)
                if (retained[key] === entry) retained.remove(key)
            }
        },
        finish = { retained.remove(it) },
    )
}
