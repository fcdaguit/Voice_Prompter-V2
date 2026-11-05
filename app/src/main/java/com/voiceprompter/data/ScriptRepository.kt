package com.voiceprompter.data

import kotlinx.coroutines.flow.Flow

class ScriptRepository(private val dao: ScriptDao) {

    fun observeAll(): Flow<List<Script>> = dao.observeAll()

    fun search(query: String): Flow<List<Script>> =
        if (query.isBlank()) dao.observeAll() else dao.search(query.trim())

    fun observeById(id: Long): Flow<Script?> = dao.observeById(id)

    suspend fun get(id: Long): Script? = dao.getById(id)

    suspend fun create(title: String = "Untitled script", body: String = ""): Long =
        dao.insert(Script(title = title, body = body))

    /** Used by auto-save; bumps [Script.updatedAt] on every write. */
    suspend fun save(id: Long, title: String, body: String) {
        val existing = dao.getById(id) ?: return
        dao.update(
            existing.copy(
                title = title.ifBlank { "Untitled script" },
                body = body,
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun duplicate(id: Long): Long? {
        val original = dao.getById(id) ?: return null
        return dao.insert(
            Script(
                title = "${original.title} (copy)",
                body = original.body
            )
        )
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun savePosition(id: Long, position: Int) = dao.updatePosition(id, position)
}
