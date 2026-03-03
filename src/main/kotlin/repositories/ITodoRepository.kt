package org.delcom.repositories

import org.delcom.data.TodoStats
import org.delcom.entities.Todo

interface ITodoRepository {
    // Ambil semua todo dengan pagination, search, dan filter status
    suspend fun getAll(
        userId: String,
        search: String,
        page: Int,
        perPage: Int,
        isDone: Boolean?,
    ): List<Todo>

    // Hitung total todo berdasarkan filter (untuk pagination)
    suspend fun countAll(
        userId: String,
        search: String,
        isDone: Boolean?,
    ): Long

    // Statistik ringkasan todo untuk halaman home
    suspend fun getStats(userId: String): TodoStats

    suspend fun getById(todoId: String): Todo?
    suspend fun create(todo: Todo): String
    suspend fun update(userId: String, todoId: String, newTodo: Todo): Boolean
    suspend fun delete(userId: String, todoId: String): Boolean
}