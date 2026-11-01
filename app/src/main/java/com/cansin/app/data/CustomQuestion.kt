package com.cansin.app.data

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class QuestionKind { TRUTH, DARE }

/** P26: the user's own Truth or Dare items. Only these are stored; game answers never are. */
@Entity(tableName = "custom_question")
data class CustomQuestion(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: QuestionKind,
    val text: String,
    val createdAt: Long,
) {
    companion object {
        const val MAX_TEXT = 200
        const val MAX_COUNT = 300
    }
}

@Dao
interface CustomQuestionDao {
    @Query("SELECT * FROM custom_question ORDER BY createdAt DESC, id DESC")
    fun observeAll(): Flow<List<CustomQuestion>>

    @Query("SELECT * FROM custom_question ORDER BY createdAt, id")
    suspend fun all(): List<CustomQuestion>

    @Query("SELECT COUNT(*) FROM custom_question")
    suspend fun count(): Int

    @Insert
    suspend fun insert(question: CustomQuestion): Long

    @Query("DELETE FROM custom_question WHERE id = :id")
    suspend fun delete(id: Long)
}

class GameRepository(private val dao: CustomQuestionDao, private val clock: () -> Long = System::currentTimeMillis) {
    val own: Flow<List<CustomQuestion>> = dao.observeAll()

    /** False when the text is empty/too long after cleaning or the list is full. */
    suspend fun add(kind: QuestionKind, raw: String): Boolean {
        val text = com.cansin.app.domain.GameRules.cleanQuestion(raw) ?: return false
        if (dao.count() >= CustomQuestion.MAX_COUNT) return false
        dao.insert(CustomQuestion(kind = kind, text = text, createdAt = clock()))
        return true
    }

    suspend fun delete(id: Long) = dao.delete(id)
}
