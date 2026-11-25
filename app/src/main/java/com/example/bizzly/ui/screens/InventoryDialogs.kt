package com.example.bizzly.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bizzly.data.local.InventoryItemEntity
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.ui.theme.LocalBizzlyTokens
import com.example.bizzly.ui.theme.SoraFontFamily

private val inventoryCategories = listOf("Boissons", "Cuisine", "Fournitures", "Emballages", "Général")
private val inventoryUnits = listOf("unités", "bouteilles", "kg", "litres", "cartons")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryItemFormDialog(
    editingItem: InventoryItemEntity?,
    defaultThreshold: Int,
    onDismiss: () -> Unit,
    onSave: (
        editingId: Long?,
        name: String,
        sku: String,
        category: String,
        unit: String,
        quantity: String,
        minThreshold: String,
        unitPrice: String,
        notes: String
    ) -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    var name by remember(editingItem) { mutableStateOf(editingItem?.name.orEmpty()) }
    var sku by remember(editingItem) { mutableStateOf(editingItem?.sku.orEmpty()) }
    var category by remember(editingItem) { mutableStateOf(editingItem?.category ?: "Général") }
    var unit by remember(editingItem) { mutableStateOf(editingItem?.unit ?: "unités") }
    var quantity by remember(editingItem) {
        mutableStateOf(editingItem?.let { BizzlyCalculator.formatQty(it.quantity) } ?: "0")
    }
    var minThreshold by remember(editingItem, defaultThreshold) {
        mutableStateOf(
            editingItem?.let { BizzlyCalculator.formatQty(it.minThreshold) }
                ?: defaultThreshold.toString()
        )
    }
    var unitPrice by remember(editingItem) {
        mutableStateOf(
            if (editingItem == null || editingItem.unitPrice == 0.0) ""
            else BizzlyCalculator.formatQty(editingItem.unitPrice)
        )
    }
    var notes by remember(editingItem) { mutableStateOf(editingItem?.notes.orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = {
            Text(
                text = if (editingItem == null) "Nouvel article d’inventaire" else "Modifier l’article",
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                color = tokens.text
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nom de l’article *") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inv_form_name_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = quantity,
                        onValueChange = { quantity = it },
                        label = { Text("Quantité actuelle") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inv_form_qty_input")
                    )
                    OutlinedTextField(
                        value = minThreshold,
                        onValueChange = { minThreshold = it },
                        label = { Text("Seuil d’alerte") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inv_form_threshold_input")
                    )
                }

                Text("Catégorie", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = tokens.muted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    inventoryCategories.forEach { cat ->
                        FilterChip(
                            selected = category.equals(cat, ignoreCase = true),
                            onClick = { category = cat },
                            label = { Text(cat, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tokens.tint,
                                selectedLabelColor = tokens.accentReadable
                            )
                        )
                    }
                }

                Text("Unité de mesure", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = tokens.muted)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    inventoryUnits.forEach { u ->
                        FilterChip(
                            selected = unit.equals(u, ignoreCase = true),
                            onClick = { unit = u },
                            label = { Text(u, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = tokens.tint,
                                selectedLabelColor = tokens.accentReadable
                            )
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = unitPrice,
                        onValueChange = { unitPrice = it },
                        label = { Text("Prix unitaire (FCFA)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inv_form_price_input")
                    )
                    OutlinedTextField(
                        value = sku,
                        onValueChange = { sku = it },
                        label = { Text("Réf. / SKU") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("inv_form_sku_input")
                    )
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes / fournisseur (facultatif)") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inv_form_notes_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(editingItem?.id, name, sku, category, unit, quantity, minThreshold, unitPrice, notes)
                },
                enabled = name.trim().isNotEmpty(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("inv_form_save_btn")
            ) {
                Text(if (editingItem == null) "Ajouter" else "Enregistrer")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Annuler", color = tokens.muted)
            }
        }
    )
}

@Composable
fun InventoryQuickAdjustDialog(
    item: InventoryItemEntity,
    onDismiss: () -> Unit,
    onConfirm: (qtyRaw: String, thresholdRaw: String) -> Unit
) {
    val tokens = LocalBizzlyTokens.current
    var qtyText by remember(item.id) { mutableStateOf(BizzlyCalculator.formatQty(item.quantity)) }
    var thresholdText by remember(item.id) { mutableStateOf(BizzlyCalculator.formatQty(item.minThreshold)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.card,
        title = {
            Text(
                text = item.name,
                fontFamily = SoraFontFamily,
                fontWeight = FontWeight.Bold,
                color = tokens.text
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Ajustez la quantité exacte en stock (${item.unit}) et le seuil déclenchant l’alerte.",
                    fontSize = 13.sp,
                    color = tokens.muted
                )
                Spacer(modifier = Modifier.height(2.dp))
                OutlinedTextField(
                    value = qtyText,
                    onValueChange = { qtyText = it },
                    label = { Text("Quantité (${item.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inv_quick_qty_input")
                )
                OutlinedTextField(
                    value = thresholdText,
                    onValueChange = { thresholdText = it },
                    label = { Text("Seuil d’alerte (${item.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("inv_quick_threshold_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(qtyText, thresholdText) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.primary,
                    contentColor = tokens.onPrimary
                ),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("inv_quick_save_btn")
            ) {
                Text("Valider")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, modifier = Modifier.height(48.dp)) {
                Text("Annuler", color = tokens.muted)
            }
        }
    )
}
