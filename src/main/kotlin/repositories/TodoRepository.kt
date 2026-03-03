package org.delcom.repositories

import org.delcom.dao.TodoDAO
import org.delcom.data.TodoStats
import org.delcom.entities.Todo
import org.delcom.helpers.suspendTransaction
import org.delcom.helpers.todoDAOToModel
import org.delcom.tables.TodoTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.like
import org.jetbrains.exposed.sql.deleteWhere
import java.util.*

class TodoRepository : ITodoRepository {

    // Bangun kondisi WHERE yang dipakai bersama oleh getAll dan countAll
    private fun buildCondition(
        userId: String,
        search: String,
        isDone: Boolean?,
    ): Op<Boolean> {
        var condition: Op<Boolean> = TodoTable.userId eq UUID.fromString(userId)

        if (search.isNotBlank()) {
            val keyword = "%${search.lowercase()}%"
            condition = condition and (TodoTable.title.lowerCase() like keyword)
        }

        if (isDone != null) {
            condition = condition and (TodoTable.isDone eq isDone)
        }

        return condition
    }

    override suspend fun getAll(
        userId: String,
        search: String,
        page: Int,
        perPage: Int,
        isDone: Boolean?,
    ): List<Todo> = suspendTransaction {
        val condition = buildCondition(userId, search, isDone)
        val offset = ((page - 1) * perPage).toLong()

        TodoDAO
            .find { condition }
            .orderBy(TodoTable.createdAt to SortOrder.DESC)
            .limit(perPage)
            .offset(offset)
            .map(::todoDAOToModel)
    }

    override suspend fun countAll(
        userId: String,
        search: String,
        isDone: Boolean?,
    ): Long = suspendTransaction {
        val condition = buildCondition(userId, search, isDone)
        TodoDAO.find { condition }.count()
    }

    override suspend fun getStats(userId: String): TodoStats = suspendTransaction {
        val uid = UUID.fromString(userId)
        val total  = TodoDAO.find { TodoTable.userId eq uid }.count()
        val done   = TodoDAO.find { (TodoTable.userId eq uid) and (TodoTable.isDone eq true) }.count()
        val notDone = total - done

        org.delcom.data.TodoStats(
            total   = total,
            done    = done,
            notDone = notDone,
        )
    }

    override suspend fun getById(todoId: String): Todo? = suspendTransaction {
        TodoDAO
            .find { TodoTable.id eq UUID.fromString(todoId) }
            .limit(1)
            .map(::todoDAOToModel)
            .firstOrNull()
    }

    override suspend fun create(todo: Todo): String = suspendTransaction {
        val todoDAO = TodoDAO.new {
            userId      = UUID.fromString(todo.userId)
            title       = todo.title
            description = todo.description
            cover       = todo.cover
            isDone      = todo.isDone
            createdAt   = todo.createdAt
            updatedAt   = todo.updatedAt
        }
        todoDAO.id.value.toString()
    }

    override suspend fun update(userId: String, todoId: String, newTodo: Todo): Boolean = suspendTransaction {
        val todoDAO = TodoDAO
            .find {
                (TodoTable.id eq UUID.fromString(todoId)) and
                        (TodoTable.userId eq UUID.fromString(userId))
            }
            .limit(1)
            .firstOrNull()

        if (todoDAO != null) {
            todoDAO.title       = newTodo.title
            todoDAO.description = newTodo.description
            todoDAO.cover       = newTodo.cover
            todoDAO.isDone      = newTodo.isDone
            todoDAO.updatedAt   = newTodo.updatedAt
            true
        } else {
            false
        }
    }

    override suspend fun delete(userId: String, todoId: String): Boolean = suspendTransaction {
        val rowsDeleted = TodoTable.deleteWhere {
            (TodoTable.id eq UUID.fromString(todoId)) and
                    (TodoTable.userId eq UUID.fromString(userId))
        }
        rowsDeleted >= 1
    }
}