package com.example.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.model.ExpenseCategory
import com.example.model.ExpenseItem
import com.example.model.TransactionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val description: String,
    val amount: Double,
    val categoryName: String,
    val dateFormatted: String,
    val dateSortKey: Long,
    val yearMonthKey: String,
    val transactionType: String = TransactionType.GASTO.name,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface ExpenseDao {
    @Query(
        """
        SELECT * FROM expenses
        WHERE yearMonthKey = :yearMonthKey
        ORDER BY dateSortKey DESC, createdAt DESC, id DESC
        """
    )
    fun observeByMonth(yearMonthKey: String): Flow<List<ExpenseEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ExpenseEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ExpenseEntity): Long

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Database(entities = [ExpenseEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun expenseDao(): ExpenseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE expenses ADD COLUMN transactionType TEXT NOT NULL DEFAULT 'GASTO'"
                )
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "gasto_rapido_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

class ExpenseRepository(private val dao: ExpenseDao) {
    fun observeExpensesForMonth(yearMonthKey: String): Flow<List<ExpenseItem>> {
        return dao.observeByMonth(yearMonthKey).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun insertExpenses(items: List<ExpenseItem>) {
        dao.insertAll(items.map { it.toEntity() })
    }

    suspend fun restoreExpense(item: ExpenseItem) {
        dao.insert(item.toEntity())
    }

    suspend fun deleteExpense(id: Long) {
        dao.deleteById(id)
    }

    private fun ExpenseEntity.toDomain(): ExpenseItem = ExpenseItem(
        id = id,
        description = description,
        amount = amount,
        category = ExpenseCategory.fromRaw(categoryName),
        dateFormatted = dateFormatted,
        dateSortKey = dateSortKey,
        yearMonthKey = yearMonthKey,
        type = TransactionType.fromRaw(transactionType),
        createdAt = createdAt
    )

    private fun ExpenseItem.toEntity(): ExpenseEntity = ExpenseEntity(
        id = id,
        description = description,
        amount = amount,
        categoryName = category.apiValue,
        dateFormatted = dateFormatted,
        dateSortKey = dateSortKey,
        yearMonthKey = yearMonthKey,
        transactionType = type.name,
        createdAt = createdAt
    )
}
