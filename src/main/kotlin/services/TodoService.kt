package org.delcom.services

import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.cio.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.delcom.data.AppException
import org.delcom.data.DataResponse
import org.delcom.data.PaginatedResponse
import org.delcom.data.TodoRequest
import org.delcom.helpers.ServiceHelper
import org.delcom.helpers.ValidatorHelper
import org.delcom.repositories.ITodoRepository
import org.delcom.repositories.IUserRepository
import java.io.File
import java.util.*
import kotlin.math.ceil

class TodoService(
    private val userRepo: IUserRepository,
    private val todoRepo: ITodoRepository
) {

    // ── GET /todos/stats ─────────────────────────────────────────────────────
    // Ringkasan todo untuk halaman Home: total, selesai, belum selesai
    suspend fun getStats(call: ApplicationCall) {
        val user  = ServiceHelper.getAuthUser(call, userRepo)
        val stats = todoRepo.getStats(user.id)

        val response = DataResponse(
            status  = "success",
            message = "Berhasil mengambil statistik todo",
            data    = mapOf("stats" to stats)
        )
        call.respond(response)
    }

    // ── GET /todos ────────────────────────────────────────────────────────────
    // Query params:
    //   ?search   – filter judul (opsional)
    //   ?isDone   – "true" / "false" (opsional, kosong = semua)
    //   ?page     – nomor halaman, default 1
    //   ?perPage  – jumlah item per halaman, default 10
    suspend fun getAll(call: ApplicationCall) {
        val user = ServiceHelper.getAuthUser(call, userRepo)

        val search  = call.request.queryParameters["search"]  ?: ""
        val page    = call.request.queryParameters["page"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val perPage = call.request.queryParameters["perPage"]?.toIntOrNull()?.coerceIn(1, 100) ?: 10

        // Parsing filter isDone: null = semua, true = selesai, false = belum selesai
        val isDone: Boolean? = when (call.request.queryParameters["isDone"]?.lowercase()) {
            "true"  -> true
            "false" -> false
            else    -> null
        }

        val total      = todoRepo.countAll(user.id, search, isDone)
        val totalPages = ceil(total.toDouble() / perPage).toInt().coerceAtLeast(1)
        val todos      = todoRepo.getAll(user.id, search, page, perPage, isDone)

        val paginated = PaginatedResponse(
            items       = todos,
            page        = page,
            perPage     = perPage,
            total       = total,
            totalPages  = totalPages,
            hasNextPage = page < totalPages,
        )

        val response = DataResponse(
            status  = "success",
            message = "Berhasil mengambil daftar todo saya",
            data    = mapOf("todos" to paginated)
        )
        call.respond(response)
    }

    // ── GET /todos/{id} ───────────────────────────────────────────────────────
    suspend fun getById(call: ApplicationCall) {
        val todoId = call.parameters["id"]
            ?: throw AppException(400, "Data todo tidak valid!")

        val user = ServiceHelper.getAuthUser(call, userRepo)

        val todo = todoRepo.getById(todoId)
        if (todo == null || todo.userId != user.id) {
            throw AppException(404, "Data todo tidak tersedia!")
        }

        val response = DataResponse(
            status  = "success",
            message = "Berhasil mengambil data todo",
            data    = mapOf("todo" to todo)
        )
        call.respond(response)
    }

    // ── PUT /todos/{id}/cover ─────────────────────────────────────────────────
    suspend fun putCover(call: ApplicationCall) {
        val todoId = call.parameters["id"]
            ?: throw AppException(400, "Data todo tidak valid!")

        val user    = ServiceHelper.getAuthUser(call, userRepo)
        val request = TodoRequest()
        request.userId = user.id

        val multipartData = call.receiveMultipart(formFieldLimit = 1024 * 1024 * 5)
        multipartData.forEachPart { part ->
            when (part) {
                is PartData.FileItem -> {
                    val ext = part.originalFileName
                        ?.substringAfterLast('.', "")
                        ?.let { if (it.isNotEmpty()) ".$it" else "" }
                        ?: ""

                    val fileName = UUID.randomUUID().toString() + ext
                    val filePath = "uploads/todos/$fileName"

                    withContext(Dispatchers.IO) {
                        val file = File(filePath)
                        file.parentFile.mkdirs()
                        part.provider().copyAndClose(file.writeChannel())
                        request.cover = filePath
                    }
                }
                else -> {}
            }
            part.dispose()
        }

        if (request.cover == null) throw AppException(404, "Cover todo tidak tersedia!")

        val newFile = File(request.cover!!)
        if (!newFile.exists()) throw AppException(404, "Cover todo gagal diunggah!")

        val oldTodo = todoRepo.getById(todoId)
        if (oldTodo == null || oldTodo.userId != user.id) {
            throw AppException(404, "Data todo tidak tersedia!")
        }

        request.title       = oldTodo.title
        request.description = oldTodo.description
        request.isDone      = oldTodo.isDone

        val isUpdated = todoRepo.update(user.id, todoId, request.toEntity())
        if (!isUpdated) throw AppException(400, "Gagal memperbarui cover todo!")

        if (oldTodo.cover != null) {
            val oldFile = File(oldTodo.cover!!)
            if (oldFile.exists()) oldFile.delete()
        }

        val response = DataResponse("success", "Berhasil mengubah cover todo", null)
        call.respond(response)
    }

    // ── POST /todos ───────────────────────────────────────────────────────────
    suspend fun post(call: ApplicationCall) {
        val user    = ServiceHelper.getAuthUser(call, userRepo)
        val request = call.receive<TodoRequest>()
        request.userId = user.id

        val validator = ValidatorHelper(request.toMap())
        validator.required("title",       "Judul todo tidak boleh kosong")
        validator.required("description", "Deskripsi tidak boleh kosong")
        validator.validate()

        val todoId = todoRepo.create(request.toEntity())

        val response = DataResponse(
            status  = "success",
            message = "Berhasil menambahkan data todo",
            data    = mapOf("todoId" to todoId)
        )
        call.respond(response)
    }

    // ── PUT /todos/{id} ───────────────────────────────────────────────────────
    suspend fun put(call: ApplicationCall) {
        val todoId  = call.parameters["id"]
            ?: throw AppException(400, "Data todo tidak valid!")

        val user    = ServiceHelper.getAuthUser(call, userRepo)
        val request = call.receive<TodoRequest>()
        request.userId = user.id

        val validator = ValidatorHelper(request.toMap())
        validator.required("title",       "Judul todo tidak boleh kosong")
        validator.required("description", "Deskripsi tidak boleh kosong")
        validator.required("isDone",      "Status selesai tidak boleh kosong")
        validator.validate()

        val oldTodo = todoRepo.getById(todoId)
        if (oldTodo == null || oldTodo.userId != user.id) {
            throw AppException(404, "Data todo tidak tersedia!")
        }
        request.cover = oldTodo.cover

        val isUpdated = todoRepo.update(user.id, todoId, request.toEntity())
        if (!isUpdated) throw AppException(400, "Gagal memperbarui data todo!")

        val response = DataResponse("success", "Berhasil mengubah data todo", null)
        call.respond(response)
    }

    // ── DELETE /todos/{id} ────────────────────────────────────────────────────
    suspend fun delete(call: ApplicationCall) {
        val todoId = call.parameters["id"]
            ?: throw AppException(400, "Data todo tidak valid!")

        val user = ServiceHelper.getAuthUser(call, userRepo)

        val oldTodo = todoRepo.getById(todoId)
        if (oldTodo == null || oldTodo.userId != user.id) {
            throw AppException(404, "Data todo tidak tersedia!")
        }

        val isDeleted = todoRepo.delete(user.id, todoId)
        if (!isDeleted) throw AppException(400, "Gagal menghapus data todo!")

        if (oldTodo.cover != null) {
            val oldFile = File(oldTodo.cover!!)
            if (oldFile.exists()) oldFile.delete()
        }

        val response = DataResponse("success", "Berhasil menghapus data todo", null)
        call.respond(response)
    }

    // ── GET /images/todos/{id} ────────────────────────────────────────────────
    suspend fun getCover(call: ApplicationCall) {
        val todoId = call.parameters["id"]
            ?: throw AppException(400, "Data todo tidak valid!")

        val todo = todoRepo.getById(todoId)
            ?: return call.respond(HttpStatusCode.NotFound)

        if (todo.cover == null) throw AppException(404, "Todo belum memiliki cover")

        val file = File(todo.cover!!)
        if (!file.exists()) throw AppException(404, "Cover todo tidak tersedia")

        call.respondFile(file)
    }
}