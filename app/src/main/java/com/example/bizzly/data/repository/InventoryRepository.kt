package com.example.bizzly.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.bizzly.data.local.InventoryDao
import com.example.bizzly.data.local.InventoryItemEntity
import com.example.bizzly.domain.BizzlyCalculator
import com.example.bizzly.model.BusinessData
import kotlinx.coroutines.flow.Flow

class InventoryRepository(
    context: Context,
    private val inventoryDao: InventoryDao
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bizzly_prefs", Context.MODE_PRIVATE)

    fun observeItems(businessId: String): Flow<List<InventoryItemEntity>> =
        inventoryDao.observeItemsForBusiness(businessId)

    fun observeLowStockItems(businessId: String): Flow<List<InventoryItemEntity>> =
        inventoryDao.observeLowStockItems(businessId)

    fun observeLowStockCount(businessId: String): Flow<Int> =
        inventoryDao.observeLowStockCount(businessId)

    suspend fun getItemById(id: Long): InventoryItemEntity? = inventoryDao.getItemById(id)

    suspend fun saveItem(item: InventoryItemEntity): Long {
        val clean = item.copy(
            name = item.name.trim().take(80),
            sku = item.sku.trim().take(40),
            category = item.category.trim().ifEmpty { "Général" }.take(40),
            unit = item.unit.trim().ifEmpty { "unités" }.take(24),
            quantity = BizzlyCalculator.clampNumber(item.quantity),
            minThreshold = BizzlyCalculator.clampNumber(item.minThreshold),
            unitPrice = BizzlyCalculator.clampNumber(item.unitPrice),
            notes = item.notes.trim().take(200),
            updatedAt = System.currentTimeMillis()
        )
        return inventoryDao.insertItem(clean)
    }

    suspend fun adjustQuantity(id: Long, delta: Double): InventoryItemEntity? {
        inventoryDao.adjustQuantity(id, delta, System.currentTimeMillis())
        return inventoryDao.getItemById(id)
    }

    suspend fun setQuantity(id: Long, quantity: Double): InventoryItemEntity? {
        val safeQty = BizzlyCalculator.clampNumber(quantity)
        inventoryDao.setQuantity(id, safeQty, System.currentTimeMillis())
        return inventoryDao.getItemById(id)
    }

    suspend fun updateThreshold(id: Long, threshold: Double) {
        val safe = BizzlyCalculator.clampNumber(threshold)
        inventoryDao.updateThreshold(id, safe, System.currentTimeMillis())
    }

    suspend fun applyDefaultThresholdToAll(businessId: String, threshold: Double) {
        val safe = BizzlyCalculator.clampNumber(threshold)
        inventoryDao.updateAllThresholds(businessId, safe, System.currentTimeMillis())
    }

    suspend fun deleteItem(id: Long) {
        inventoryDao.deleteItemById(id)
    }

    suspend fun deleteAllForBusiness(businessId: String) {
        inventoryDao.deleteAllForBusiness(businessId)
        prefs.edit().remove(seedKey(businessId)).apply()
    }

    suspend fun seedFromCatalogIfNeeded(businessId: String, data: BusinessData) {
        if (prefs.getBoolean(seedKey(businessId), false)) return
        val existing = inventoryDao.getItemsForBusiness(businessId)
        if (existing.isEmpty() && data.prod.isNotEmpty()) {
            val mapped = data.prod.map { product ->
                val stock = BizzlyCalculator.stockInfo(data, product)
                InventoryItemEntity(
                    businessId = businessId,
                    productId = product.id,
                    name = product.n,
                    category = "Catalogue",
                    unit = "unités",
                    quantity = stock.current.coerceAtLeast(0.0),
                    minThreshold = data.seuil.toDouble(),
                    unitPrice = product.pv
                )
            }
            inventoryDao.insertItems(mapped)
        }
        prefs.edit().putBoolean(seedKey(businessId), true).apply()
    }

    suspend fun importMissingFromCatalog(businessId: String, data: BusinessData): Int {
        val existing = inventoryDao.getItemsForBusiness(businessId)
        val existingProductIds = existing.mapNotNull { it.productId }.toSet()
        val existingNames = existing.map { it.name.trim().lowercase() }.toSet()
        val toInsert = data.prod.filter { p ->
            p.id !in existingProductIds && p.n.trim().lowercase() !in existingNames
        }.map { product ->
            val stock = BizzlyCalculator.stockInfo(data, product)
            InventoryItemEntity(
                businessId = businessId,
                productId = product.id,
                name = product.n,
                category = "Catalogue",
                unit = "unités",
                quantity = stock.current.coerceAtLeast(0.0),
                minThreshold = data.seuil.toDouble(),
                unitPrice = product.pv
            )
        }
        if (toInsert.isNotEmpty()) {
            inventoryDao.insertItems(toInsert)
        }
        prefs.edit().putBoolean(seedKey(businessId), true).apply()
        return toInsert.size
    }

    private fun seedKey(businessId: String) = "inventory_seeded_$businessId"
}
