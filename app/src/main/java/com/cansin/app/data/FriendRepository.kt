package com.cansin.app.data

import com.cansin.app.domain.VersusRules
import kotlinx.coroutines.flow.Flow

class FriendRepository(private val friends: FriendDao) {
    val all: Flow<List<FriendProfile>> = friends.observeAll()

    suspend fun save(name: String, color: Int): FriendProfile? {
        val clean = VersusRules.cleanName(name) ?: return null
        val friend = FriendProfile(name = clean, color = color)
        return friend.copy(id = friends.insert(friend))
    }

    suspend fun delete(id: Long) {
        friends.unlink(id)
        friends.delete(id)
    }
}
