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
import io.ktor.websocket.*
import io.nekohasekai.ktlib.core.LOG_LEVEL
import io.nekohasekai.ktlib.td.cli.TdCli
import io.nekohasekai.ktlib.td.core.TdLoader
import io.nekohasekai.nmd.net.ConnectionsManager
import io.nekohasekai.nmd.utils.EncUtil
import io.nekohasekai.tmicro.tmnet.SerializedData
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import org.bouncycastle.util.encoders.Base64
import java.io.File
import java.time.Duration
import kotlin.math.abs
import kotlin.properties.Delegates

object Nmd : TdCli() {

    override var configFile = File("nmd.yml")
    var API_ID by Delegates.notNull<Int>()
    lateinit var API_HASH: String

    @JvmStatic
    fun main(args: Array<String>) {
        TdLoader.tryLoad(File("cache"), true)
        EngineMain.main(args)
    }

    override fun onLoad() {
        API_ID = intConfig("API_ID") ?: error("Missing API_ID")
        API_HASH = stringConfig("API_HASH") ?: error("Missing API_HASH")
    }

    @JvmStatic
    @Suppress("unused")
    fun Application.nmd() {

        System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
        launch(emptyArray())
        loadConfig()
        initDatabase("nmd.db")
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
                    call.respond(HttpStatusCode.BadRequest, "Bad request")
                    return@webSocket
                }

                try {
                    val data = SerializedData(EncUtil.publicDecode(Base64.decode(authorization.substringAfter(" "))))
                    val key = data.readByteArray(true)
                    val time = data.readInt32(true)
                    val connection = ConnectionsManager(key, time, this)
                    if (abs((System.currentTimeMillis() / 1000) - time) > 30) {
                        error("Invalid timeMs")
                    }
                    connection.loopEvents()
                } catch (e: Exception) {
                    e.printStackTrace()
                    call.respond(HttpStatusCode.BadRequest, "Bad request")
                }
            }
        }

    }

}