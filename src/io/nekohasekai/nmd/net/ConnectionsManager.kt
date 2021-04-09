package io.nekohasekai.nmd.net

import cn.hutool.core.util.ZipUtil
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.websocket.*
import io.nekohasekai.ktlib.core.mkLog
import io.nekohasekai.nmd.utils.EncUtil
import io.nekohasekai.tmicro.tmnet.SerializedData
import io.nekohasekai.tmicro.tmnet.TMApi
import io.nekohasekai.tmicro.tmnet.TMClassStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.params.ECPublicKeyParameters

class ConnectionsManager(val key: ByteArray, val session: DefaultWebSocketServerSession) {

    val chaChaSession = EncUtil.ChaChaSession(key)
    val log = mkLog("Sessions ${session.call.request.origin.remoteHost}")

    companion object {
        val connections = HashMap<ByteArray, ConnectionsManager>()
    }

    private suspend fun sendRaw(content: ByteArray) = withContext(Dispatchers.IO) {
        var message = chaChaSession.mkMessage(content)
        if (message.size > 1024) {
            message = ZipUtil.gzip(message)
        }
        session.send(message)
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    suspend fun loopEvents() {

        log.debug("Connection open")

        try {
            for (frame in session.incoming) {
                try {
                    var data = frame.data
                    if (data[0] == 0x1f.toByte() && data[1] == 0x8b.toByte()) {
                        data = ZipUtil.unGzip(data)
                    }
                    data = chaChaSession.readMessage(data)
                    GlobalScope.launch(Dispatchers.Default) {
                        processRequest(data)
                    }
                } catch (e: Exception) {
                    log.warn(e, "Decrypt failed: ")
                    //  session.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, "Bad Request"))
                }
            }
        } catch (e: ClosedReceiveChannelException) {
            log.debug("Client closed: ${session.closeReason.await()}")
            onClosed()
            return
        } catch (e: Throwable) {
            log.debug("Connection error: ${session.closeReason.await()}")
            onClosed()
            return
        }

        log.debug("Connection closed")
        onClosed()

    }

    private suspend fun processRequest(data: ByteArray) {
        val request = try {
            TMClassStore.deserializeFromSteam(SerializedData(data), true)
        } catch (e: Throwable) {
            log.warn(e, "Deserialize request failed: ")
            return
        }
        processRequest(request)
    }


    enum class Status {
        START, WAIT_VERIFY, VERIFIED, DENY
    }

    var status = Status.START
    private lateinit var tempData: ByteArray

    private suspend fun processRequest(request: TMApi.Object) {

        var requestId = -1
        var function = request
        if (request is TMApi.RpcRequest) {
            requestId = request.requestId
            function = request.request
        }

        if (status == Status.DENY) {
            sendError(requestId, 400, "Bad session.")
            return
        }

        when (function) {
            is TMApi.InitConnection -> {
                if (status != Status.START) {
                    sendError(requestId, 400, "Connection started.")
                    return
                }
                if (function.layer > TMApi.LAYER) {
                    sendError(requestId, 501, "Max layer of this server is ${TMApi.LAYER}.")
                    return
                }
                if (function.session.size != 33) {
                    sendError(requestId, 400, "Bad session.")
                    return
                }
                val pubKey = try {
                    ECPublicKeyParameters(EncUtil.sm2Params.curve.decodePoint(function.session), EncUtil.sm2Params)
                } catch (e: Exception) {
                    log.warn(e, "Encrypt sm2 failed: ")
                    sendError(requestId, 400, "Bad session.")
                    return
                }
                tempData = ByteArray(32)
                EncUtil.secureRandom.nextBytes(tempData)
                val data = EncUtil.processSM2(pubKey, true, tempData)
                val response = TMApi.ConnInitTemp()
                response.data = data
                status = Status.WAIT_VERIFY
                sendResponse(requestId, response)
            }
            is TMApi.VerifyConnection -> {
                if (status == Status.START) {
                    sendError(requestId, 400, "Connection not started.")
                    return
                } else if (status == Status.VERIFIED) {
                    sendError(requestId, 400, "Connection verified.")
                    return
                }
                if (!tempData.contentEquals(function.data)) {
                    status = Status.DENY
                    sendError(requestId, 400, "Bad data.")
                    return
                }
                status = Status.VERIFIED
                val response = TMApi.Ok()
                sendOk(requestId)
            }
        }

    }

    private suspend fun sendResponse(requestId: Int, result: TMApi.Object) {
        if (requestId == -1) {
            // no response
            return
        }

        val response = TMApi.RpcResponse()
        response.requestId = requestId
        response.response = result

        val data = SerializedData()
        TMClassStore.serializeToStream(data, response)
        sendRaw(data.toByteArray())
    }

    private suspend fun sendOk(requestId: Int) {
        sendResponse(requestId, TMApi.Ok())
    }

    private suspend fun sendError(requestId: Int, code: Int, message: String) {
        val error = TMApi.Error()
        error.code = code
        error.message = message

        sendResponse(requestId, error)
    }

    suspend fun onClosed() {
        connections.remove(key)
    }

}