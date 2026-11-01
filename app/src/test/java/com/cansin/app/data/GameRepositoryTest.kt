package com.cansin.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameRepositoryTest {
    private class FakeDao : CustomQuestionDao {
        val rows = MutableStateFlow(listOf<CustomQuestion>())
        private var next = 1L
        override fun observeAll(): Flow<List<CustomQuestion>> = rows
        override suspend fun all() = rows.value
        override suspend fun count() = rows.value.size
        override suspend fun insert(question: CustomQuestion): Long = next++.also { rows.value += question.copy(id = it) }
        override suspend fun delete(id: Long) {
            rows.value = rows.value.filterNot { it.id == id }
        }
    }

    private val dao = FakeDao()
    private val repo = GameRepository(dao, clock = { 5 })

    @Test
    fun addsCleanTextAndRespectsLimits() = runBlocking {
        assertTrue(repo.add(QuestionKind.DARE, "  Sing a song\u0007 "))
        assertEquals(CustomQuestion(1, QuestionKind.DARE, "Sing a song", 5), dao.rows.value.single())
        assertFalse(repo.add(QuestionKind.TRUTH, "   "))
        assertFalse(repo.add(QuestionKind.TRUTH, "x".repeat(201)))
        repeat(CustomQuestion.MAX_COUNT - 1) { repo.add(QuestionKind.TRUTH, "q$it") }
        assertFalse(repo.add(QuestionKind.TRUTH, "one too many"))
        repo.delete(1)
        assertEquals(CustomQuestion.MAX_COUNT - 1, dao.rows.value.size)
    }
}
