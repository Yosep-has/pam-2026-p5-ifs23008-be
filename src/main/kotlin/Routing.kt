package org.delcom

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.auth.authenticate
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.delcom.data.AppException
import org.delcom.data.ErrorResponse
import org.delcom.helpers.JWTConstants
import org.delcom.helpers.parseMessageToMap
import org.delcom.services.TodoService
import org.delcom.services.AuthService
import org.delcom.services.UserService
import org.koin.ktor.ext.inject

fun Application.configureRouting() {
    val todoService: TodoService by inject()
    val authService: AuthService by inject()
    val userService: UserService by inject()

    install(StatusPages) {
        exception<AppException> { call, cause ->
            val dataMap: Map<String, List<String>> = parseMessageToMap(cause.message)
            call.respond(
                status = HttpStatusCode.fromValue(cause.code),
                message = ErrorResponse(
                    status  = "fail",
                    message = if (dataMap.isEmpty()) cause.message else "Data yang dikirimkan tidak valid!",
                    data    = if (dataMap.isEmpty()) null else dataMap.toString()
                )
            )
        }

        exception<Throwable> { call, cause ->
            call.respond(
                status = HttpStatusCode.fromValue(500),
                message = ErrorResponse(
                    status  = "error",
                    message = cause.message ?: "Unknown error",
                    data    = ""
                )
            )
        }
    }

    routing {
        get("/") {
            call.respondText("API telah berjalan. Dibuat oleh Abdullah Ubaid.")
        }

        // ── Route Auth ────────────────────────────────────────────────────────
        route("/auth") {
            post("/login")         { authService.postLogin(call) }
            post("/register")      { authService.postRegister(call) }
            post("/refresh-token") { authService.postRefreshToken(call) }
            post("/logout")        { authService.postLogout(call) }
        }

        authenticate(JWTConstants.NAME) {

            // ── Route User ────────────────────────────────────────────────────
            route("/users") {
                // Ambil profil saya
                get("/me")              { userService.getMe(call) }

                // Ubah informasi akun (name, username)
                put("/me")              { userService.putMe(call) }

                // Ubah kata sandi
                put("/me/password")     { userService.putMyPassword(call) }

                // Ubah foto profil (multipart/form-data, field: photo)
                put("/me/photo")        { userService.putMyPhoto(call) }
            }

            // ── Route Todos ───────────────────────────────────────────────────
            route("/todos") {
                // Statistik ringkasan todo (total, selesai, belum selesai) – untuk Home
                get("/stats")           { todoService.getStats(call) }

                // Daftar todo dengan pagination & filter
                // Query: ?search= &isDone=true|false &page= &perPage=
                get                     { todoService.getAll(call) }

                post                    { todoService.post(call) }
                get("/{id}")            { todoService.getById(call) }
                put("/{id}")            { todoService.put(call) }
                put("/{id}/cover")      { todoService.putCover(call) }
                delete("/{id}")         { todoService.delete(call) }
            }
        }

        // ── Route Images (publik) ─────────────────────────────────────────────
        route("/images") {
            get("users/{id}")  { userService.getPhoto(call) }
            get("todos/{id}")  { todoService.getCover(call) }
        }
    }
}