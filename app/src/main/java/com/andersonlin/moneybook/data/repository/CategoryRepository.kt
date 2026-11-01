package com.andersonlin.moneybook.data.repository

import androidx.room.withTransaction
import com.andersonlin.moneybook.data.db.AppDatabase
import com.andersonlin.moneybook.data.db.CategoryDao
import com.andersonlin.moneybook.data.model.Category
import kotlinx.coroutines.flow.onEach

/** 分类仓库 */
class CategoryRepository(private val database: AppDatabase, private val dao: CategoryDao) {

    @Volatile
    var cachedCategories: List<Category> = emptyList()
        private set

    fun getAllCategories() = dao.getAllCategories().onEach { cachedCategories = it }

    fun getCategoriesByType(type: Int) = dao.getCategoriesByType(type)

    suspend fun getById(id: Long): Category? = dao.getById(id)

    suspend fun getCategoriesByTypeSnapshot(type: Int) = dao.getCategoriesByTypeSnapshot(type)

    suspend fun addCategory(category: Category): Long = dao.insert(category)

    suspend fun updateCategory(category: Category) = dao.update(category)

    suspend fun deleteCategory(category: Category) = database.withTransaction {
        database.categoryBudgetDao().deleteByCategory(category.id)
        dao.delete(category)
    }

    suspend fun reorder(ids: List<Long>) = database.withTransaction {
        val all = dao.getAllSnapshot()
        val selected = all.filter { it.id in ids }
        require(ids.distinct().size == ids.size && selected.size == ids.size && selected.map { it.type }.distinct().size == 1)
        require(all.filter { it.type == selected.first().type }.size == ids.size)
        ids.forEachIndexed { index, id -> dao.update(selected.first { it.id == id }.copy(sortOrder = index + 1)) }
    }

    suspend fun merge(sourceId: Long, targetId: Long) = database.withTransaction {
        val source = requireNotNull(dao.getById(sourceId))
        val target = requireNotNull(dao.getById(targetId))
        require(source.id != target.id && source.type == target.type) { "只能合并同类型的不同分类" }
        val budgets = database.categoryBudgetDao()
        budgets.getAllSnapshot().filter { it.categoryId == sourceId }.forEach { b ->
            val existing = budgets.getForMonth(targetId, b.year, b.month)
            budgets.upsert(b.copy(id = existing?.id ?: 0L, categoryId = targetId,
                amountCents = Math.addExact(b.amountCents, existing?.amountCents ?: 0L)))
        }
        budgets.deleteByCategory(sourceId)
        database.billDao().reassignCategory(sourceId, targetId)
        database.recurringBillDao().reassignCategory(sourceId, targetId)
        dao.delete(source)
    }

    suspend fun getAllSnapshot() = dao.getAllSnapshot().also { cachedCategories = it }

    suspend fun insertAll(categories: List<Category>) = dao.insertAll(categories)

    suspend fun deleteAll() = dao.deleteAll()
}
