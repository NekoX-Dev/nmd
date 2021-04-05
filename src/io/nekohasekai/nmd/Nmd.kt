package io.nekohasekai.nmd

import io.ktor.application.*
import io.ktor.features.*
import io.ktor.gson.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import io.nekohasekai.ktlib.td.cli.TdCli
import org.slf4j.event.Level

object Nmd : TdCli() {

    @JvmStatic
    fun main(args: Array<String>) = EngineMain.main(args)

    @JvmOverloads
    @JvmStatic
    fun Application.nmd(testing: Boolean = false) {

        install(CallLogging) {
            level = Level.TRACE
        }

        launch(emptyArray())
        loadConfig()

        install(ContentNegotiation) {
            gson {
            }
        }

        install(ForwardedHeaderSupport) // WARNING: for security, do not include this if not behind a reverse proxy
        install(XForwardedHeaderSupport) // WARNING: for security, do not include this if not behind a reverse proxy

        routing {
            post("/") {
                call.respond(mapOf("hello" to "world"))
            }
        }

    }

}