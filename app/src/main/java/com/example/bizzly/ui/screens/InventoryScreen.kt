package com.example.bizzly.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bizzly.data.local.InventoryItemEntity
import com.example.bizzly.data.local.InventoryStockStatus
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.BizzlyRole
import com.example.bizzly.model.BizzlyTab
import com.example.bizzly.ui.components.BizzlyCard
import com.example.bizzly.ui.components.BizzlyHeroHeader
import com.example.bizzly.ui.components.BizzlySignatureFooter
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily
import com.example.bizzly.ui.viewmodel.BizzlyUiState
import com.example.bizzly.ui.viewmodel.BizzlyViewModel

private enum class InventoryFilter(val label: String) {
    ALL("Tous"),
    ALERTS("En alerte"),
    OUT_OF_STOCK("Rupture"),
    HEALTHY("OK")
}

@Composable
fun InventoryScreen(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current

    BackHandler { viewModel.selectTab(BizzlyTab.MORE) }

    Column(modifier = Modifier.fillMaxWidth()) {
        BizzlyHeroHeader(
            subtitle = "Inventaire & alertes de seuil",
            account = state.activeAccount,
            roleLabel = state.activeProfile?.let { BizzlyRole.fromKey(it.role).label },
            syncStatus = state.syncStatus,
            isDark = state.isDarkTheme,
            onToggleTheme = viewModel::toggleTheme,
            onLockClick = viewModel::lockNow
        ) {
            OutlinedButton(
                onClick = { viewModel.selectTab(BizzlyTab.MORE) },
                border = BorderStroke(1.dp, tokens.headerControlBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("inventory_back_btn")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = tokens.onHeader)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Retour aux options", color = tokens.onHeader)
            }
        }

        InventoryTrackerContent(state = state, viewModel = viewModel)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryTrackerContent(
    state: BizzlyUiState,
    viewModel: BizzlyViewModel
) {
    val tokens = LocalBizzlyTokens.current
    val items by viewModel.inventoryItems.collectAsStateWithLifecycle()
    val lowStockItems by viewModel.lowStockInventoryItems.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(InventoryFilter.ALL) }
    var selectedCategory by remember { mutableStateOf("Toutes") }
    var defaultThresholdText by remember(state.businessData.seuil) {
        mutableStateOf(state.businessData.seuil.toString())
    }
    var showFormDialog by remember { mutableStateOf(false) }
    var editingItem by remember { mutableStateOf<InventoryItemEntity?>(null) }
    var quickAdjustItem by remember { mutableStateOf<InventoryItemEntity?>(null) }

    val outOfStockCount = remember(items) { items.count { it.stockStatus() == InventoryStockStatus.OUT_OF_STOCK } }
    val lowOnlyCount = remember(items) { items.count { it.stockStatus() == InventoryStockStatus.LOW_STOCK } }
    val alertCount = outOfStockCount + lowOnlyCount
    val healthyCount = items.size - alertCount
    val totalValue = remember(items) { items.sumOf { it.quantity * it.unitPrice } }

    val categories = remember(items) {
        val distinct = items.map { it.category.ifBlank { "Général" } }.distinct().sorted()
        if (distinct.size > 1) listOf("Toutes") + distinct else emptyList()
    }

    val filteredItems = remember(items, searchQuery, activeFilter, selectedCategory) {
        val q = searchQuery.trim().lowercase()
        items.filter { item ->
            val matchesFilter = when (activeFilter) {
                InventoryFilter.ALL -> true
                InventoryFilter.ALERTS -> item.isBelowThreshold
                InventoryFilter.OUT_OF_STOCK -> item.stockStatus() == InventoryStockStatus.OUT_OF_STOCK
                InventoryFilter.HEALTHY -> !item.isBelowThreshold
            }
            val matchesCat = selectedCategory == "Toutes" ||
                item.category.equals(selectedCategory, ignoreCase = true)
            val matchesSearch = q.isEmpty() ||
                item.name.lowercase().contains(q) ||
                item.sku.lowercase().contains(q) ||
                item.category.lowercase().contains(q)
            matchesFilter && matchesCat && matchesSearch
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live Low-Stock Alert Banner
        if (lowStockItems.isNotEmpty()) {
            val isCritical = outOfStockCount > 0
            val alertColor = if (isCritical) tokens.ko else tokens.warn
            BizzlyCard(
                borderColor = alertColor,
                modifier = Modifier.testTag("inventory_alert_banner")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(alertColor.copy(alpha = 0.14f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCritical) Icons.Default.WarningAmber else Icons.Default.NotificationsActive,
                            contentDescription = "Alerte stock",
                            tint = alertColor
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCritical) {
                                "$outOfStockCount rupture${if (outOfStockCount > 1) "s" else ""} · ${lowStockItems.size} article${if (lowStockItems.size > 1) "s" else ""} en alerte"
                            } else {
                                "${lowStockItems.size} article${if (lowStockItems.size > 1) "s" else ""} sous le seuil défini"
                            },
                            fontFamily = SoraFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.5.sp,
                            color = tokens.text
                        )
                        Text(
                            text = lowStockItems.take(4).joinToString(" · ") {
                                "${it.name} (${BizzlyCalculator.formatQty(it.quantity)}/${BizzlyCalculator.formatQty(it.minThreshold)})"
                            } + if (lowStockItems.size > 4) " …" else "",
                            fontSize = 12.5.sp,
                            color = tokens.muted
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { activeFilter = InventoryFilter.ALERTS },
                    border = BorderStroke(1.dp, alertColor.copy(alpha = 0.6f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("inv_filter_alerts_quick_btn")
                ) {
                    Text("Afficher uniquement les alertes (${lowStockItems.size})", color = alertColor, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // 3 KPI Summary Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InventoryKpiBox(
                label = "Articles",
                value = items.size.toString(),
                accent = tokens.accentReadable,
                modifier = Modifier.weight(1f)
            )
            InventoryKpiBox(
                label = "En alerte",
                value = alertCount.toString(),
                accent = if (outOfStockCount > 0) tokens.ko else if (alertCount > 0) tokens.warn else tokens.ok,
                modifier = Modifier.weight(1f)
            )
            InventoryKpiBox(
                label = "Valeur estimée",
                value = BizzlyCalculator.formatFcfa(totalValue),
                accent = tokens.text,
                modifier = Modifier.weight(1.3f)
            )
        }

        // Actions & Global Threshold Bar
        BizzlyCard {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        editingItem = null
                        showFormDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = tokens.primary,
                        contentColor = tokens.onPrimary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("inv_add_item_btn")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Nouvel article", fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                if (state.businessData.prod.isNotEmpty()) {
                    OutlinedButton(
                        onClick = viewModel::importCatalogToInventory,
                        border = BorderStroke(1.dp, tokens.border),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("inv_import_catalog_btn")
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, tint = tokens.accentReadable)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Catalogue", color = tokens.text, maxLines = 1)
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Seuil global",
                    fontSize = 13.sp,
                    color = tokens.muted,
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = defaultThresholdText,
                    onValueChange = { defaultThresholdText = it.filter { ch -> ch.isDigit() } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .width(88.dp)
                        .testTag("inv_global_threshold_input")
                )
                OutlinedButton(
                    onClick = {
                        defaultThresholdText.toIntOrNull()?.let { viewModel.applyDefaultInventoryThreshold(it) }
                    },
                    border = BorderStroke(1.dp, tokens.border),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("inv_apply_global_threshold_btn")
                ) {
                    Text("Appliquer", color = tokens.accentReadable, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // Search & Filter Chips
        BizzlyCard {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Rechercher (nom, SKU, catégorie)") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("inv_search_input")
            )
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val counts = mapOf(
                    InventoryFilter.ALL to items.size,
                    InventoryFilter.ALERTS to alertCount,
                    InventoryFilter.OUT_OF_STOCK to outOfStockCount,
                    InventoryFilter.HEALTHY to healthyCount
                )
                InventoryFilter.entries.forEach { filter ->
                    val count = counts[filter] ?: 0
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        label = { Text("${filter.label} ($count)", fontSize = 12.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = tokens.tint,
                            selectedLabelColor = tokens.accentReadable
                        ),
                        modifier = Modifier.testTag("inv_filter_${filter.name.lowercase()}")
                    )
                }
            }
            if (categories.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat, fontSize = 11.5.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tokens.primary.copy(alpha = 0.14f),
                                selectedLabelColor = tokens.accentReadable
                            )
                        )
                    }
                }
            }
        }

        // Item List
        if (filteredItems.isEmpty()) {
            BizzlyCard {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Inventory2, contentDescription = null, tint = tokens.muted)
                    Text(
                        text = if (items.isEmpty()) {
                            "Aucun article dans l’inventaire local. Touchez « Nouvel article » ou importez le catalogue."
                        } else {
                            "Aucun article ne correspond aux filtres actuels."
                        },
                        color = tokens.muted,
                        fontSize = 13.5.sp
                    )
                }
            }
        } else {
            filteredItems.forEach { item ->
                InventoryItemCard(
                    item = item,
                    onAdjust = { delta -> viewModel.adjustInventoryQuantity(item.id, delta) },
                    onQuickAdjustClick = { quickAdjustItem = item },
                    onEditClick = {
                        editingItem = item
                        showFormDialog = true
                    },
                    onDeleteClick = { viewModel.deleteInventoryItem(item.id) }
                )
            }
        }

        BizzlySignatureFooter()
    }

    if (showFormDialog) {
        InventoryItemFormDialog(
            editingItem = editingItem,
            defaultThreshold = state.businessData.seuil,
            onDismiss = { showFormDialog = false },
            onSave = { id, name, sku, category, unit, qty, minTh, price, notes ->
                viewModel.saveInventoryItem(id, name, sku, category, unit, qty, minTh, price, notes)
                showFormDialog = false
            }
        )
    }

    quickAdjustItem?.let { target ->
        InventoryQuickAdjustDialog(
            item = target,
            onDismiss = { quickAdjustItem = null },
            onConfirm = { qtyRaw, thRaw ->
                viewModel.setInventoryQuantityAndThreshold(target.id, qtyRaw, thRaw)
                quickAdjustItem = null
            }
        )
    }
}

@Composable
private fun InventoryKpiBox(
    label: String,
    value: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    val tokens = LocalBizzlyTokens.current
    BizzlyCard(modifier = modifier) {
        Text(label, fontSize = 11.5.sp, color = tokens.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontFamily = SoraFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.5.sp,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun InventoryItemCard(
    item: InventoryItemEntity,
    onAdjust: (Double) -> Unit,
    onQuickAdjustClick: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    val status = item.stockStatus()
    val statusColor = when (status) {
        InventoryStockStatus.OUT_OF_STOCK -> tokens.ko
        InventoryStockStatus.LOW_STOCK -> tokens.warn
        InventoryStockStatus.HEALTHY -> tokens.ok
    }
    val statusLabel = when (status) {
        InventoryStockStatus.OUT_OF_STOCK -> "Rupture"
        InventoryStockStatus.LOW_STOCK -> "Sous le seuil"
        InventoryStockStatus.HEALTHY -> "Stock OK"
    }
    val targetReference = (item.minThreshold * 2.0).coerceAtLeast(1.0)
    val progress = (item.quantity / targetReference).toFloat().coerceIn(0f, 1f)

    BizzlyCard(
        borderColor = if (item.isBelowThreshold) statusColor else null,
        modifier = Modifier.testTag("inv_item_card_${item.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                    )
                    Text(
                        text = item.name,
                        fontWeight = FontWeight.Bold,
                        color = tokens.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val metaParts = buildList {
                    add(item.category.ifBlank { "Général" })
                    if (item.sku.isNotBlank()) add("SKU ${item.sku}")
                    if (item.unitPrice > 0.0) add(BizzlyCalculator.formatFcfa(item.unitPrice))
                }
                Text(
                    text = metaParts.joinToString(" · "),
                    fontSize = 12.sp,
                    color = tokens.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(statusColor.copy(alpha = 0.14f))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        LinearProgressIndicator(
            progress = { progress },
            color = statusColor,
            trackColor = tokens.border.copy(alpha = 0.5f),
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(99.dp))
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Seuil : ${BizzlyCalculator.formatQty(item.minThreshold)} ${item.unit}",
                    fontSize = 12.5.sp,
                    color = tokens.muted
                )
                if (item.notes.isNotBlank()) {
                    Text(
                        text = item.notes,
                        fontSize = 11.5.sp,
                        color = tokens.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = { onAdjust(-1.0) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.background)
                        .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                        .testTag("inv_minus_${item.id}")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Diminuer le stock", tint = tokens.text)
                }

                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.tint)
                        .border(1.dp, statusColor.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                        .clickable(onClick = onQuickAdjustClick)
                        .padding(horizontal = 12.dp)
                        .testTag("inv_qty_btn_${item.id}"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${BizzlyCalculator.formatQty(item.quantity)} ${item.unit}",
                        fontFamily = SoraFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.5.sp,
                        color = if (item.isBelowThreshold) statusColor else tokens.text
                    )
                }

                IconButton(
                    onClick = { onAdjust(1.0) },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(tokens.background)
                        .border(1.dp, tokens.border, RoundedCornerShape(10.dp))
                        .testTag("inv_plus_${item.id}")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Augmenter le stock", tint = tokens.accentReadable)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (item.isBelowThreshold) {
                OutlinedButton(
                    onClick = { onAdjust(10.0) },
                    border = BorderStroke(1.dp, tokens.accentReadable.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("inv_restock_${item.id}")
                ) {
                    Text("+10 Réappro", fontSize = 12.5.sp, color = tokens.accentReadable, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Spacer(modifier = Modifier.width(1.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("inv_edit_${item.id}")
                ) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifier l’article", tint = tokens.muted)
                }
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("inv_del_${item.id}")
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Supprimer l’article", tint = tokens.ko)
                }
            }
        }
    }
}
