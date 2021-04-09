package io.nekohasekai.nmd

import cn.hutool.log.level.Level
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.http.cio.websocket.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.netty.*
import io.ktor.utils.io.core.*
import io.ktor.websocket.*
import io.nekohasekai.ktlib.core.LOG_LEVEL
import io.nekohasekai.ktlib.td.cli.TdCli
import io.nekohasekai.nmd.net.ConnectionsManager
import io.nekohasekai.nmd.utils.EncUtil
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import java.time.Duration

object Nmd : TdCli() {

    @JvmStatic
    fun main(args: Array<String>) {
        EngineMain.main(args)
    }

    @JvmStatic
    fun Application.nmd() {

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)

        launch(emptyArray())
        loadConfig()
        LOG_LEVEL = Level.TRACE

        install(ForwardedHeaderSupport)
        install(XForwardedHeaderSupport)

        install(WebSockets) {
            pingPeriod = Duration.ofSeconds(60)
            timeout = Duration.ofSeconds(120)
            maxFrameSize = Long.MAX_VALUE
        }

        routing {
            get("/") {
                call.response.header("Location", "http://127.0.0.1")
                call.respond(HttpStatusCode.TemporaryRedirect)
            }
            webSocket("/") {
                val authorization = call.request.authorization()
                if (authorization == null) {
                    call.respond(HttpStatusCode.BadRequest, byteArrayOf())
                    return@webSocket
                }

                println(authorization)

                val key = EncUtil.publicDecode(authorization.substringAfter(" "))
                ConnectionsManager(key, this).loopEvents()
            }
        }

    }

}