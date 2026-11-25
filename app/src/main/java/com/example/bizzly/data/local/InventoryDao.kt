package com.example.bizzly.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

enum class InventoryStockStatus {
    OUT_OF_STOCK,
    LOW_STOCK,
    HEALTHY
}

@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["businessId"]),
        Index(value = ["businessId", "quantity"])
    ]
)
data class InventoryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val businessId: String,
    val productId: Long? = null,
    val name: String,
    val sku: String = "",
    val category: String = "Général",
    val unit: String = "unités",
    val quantity: Double = 0.0,
    val minThreshold: Double = 10.0,
    val unitPrice: Double = 0.0,
    val notes: String = "",
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun stockStatus(): InventoryStockStatus = when {
        quantity <= 0.0 -> InventoryStockStatus.OUT_OF_STOCK
        quantity < minThreshold -> InventoryStockStatus.LOW_STOCK
        else -> InventoryStockStatus.HEALTHY
    }

    val isBelowThreshold: Boolean
        get() = quantity < minThreshold || quantity <= 0.0
}

@Dao
interface InventoryDao {
    @Query(
        """
        SELECT * FROM inventory_items
        WHERE businessId = :businessId
        ORDER BY CASE WHEN quantity <= 0 THEN 0 WHEN quantity < minThreshold THEN 1 ELSE 2 END, quantity ASC, name ASC
        """
    )
    fun observeItemsForBusiness(businessId: String): Flow<List<InventoryItemEntity>>

    @Query(
        """
        SELECT * FROM inventory_items
        WHERE businessId = :businessId AND (quantity < minThreshold OR quantity <= 0)
        ORDER BY quantity ASC, name ASC
        """
    )
    fun observeLowStockItems(businessId: String): Flow<List<InventoryItemEntity>>

    @Query("SELECT COUNT(*) FROM inventory_items WHERE businessId = :businessId AND (quantity < minThreshold OR quantity <= 0)")
    fun observeLowStockCount(businessId: String): Flow<Int>

    @Query("SELECT * FROM inventory_items WHERE businessId = :businessId ORDER BY name ASC")
    suspend fun getItemsForBusiness(businessId: String): List<InventoryItemEntity>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): InventoryItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InventoryItemEntity>)

    @Update
    suspend fun updateItem(item: InventoryItemEntity)

    @Query("UPDATE inventory_items SET quantity = MAX(0.0, quantity + :delta), updatedAt = :updatedAt WHERE id = :id")
    suspend fun adjustQuantity(id: Long, delta: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE inventory_items SET quantity = MAX(0.0, :quantity), updatedAt = :updatedAt WHERE id = :id")
    suspend fun setQuantity(id: Long, quantity: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE inventory_items SET minThreshold = MAX(0.0, :threshold), updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateThreshold(id: Long, threshold: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE inventory_items SET minThreshold = MAX(0.0, :threshold), updatedAt = :updatedAt WHERE businessId = :businessId")
    suspend fun updateAllThresholds(businessId: String, threshold: Double, updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM inventory_items WHERE businessId = :businessId")
    suspend fun deleteAllForBusiness(businessId: String)
}
