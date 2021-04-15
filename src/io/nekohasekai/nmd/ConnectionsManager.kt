package io.nekohasekai.nmd

import cn.hutool.core.codec.Base64
import cn.hutool.core.util.ZipUtil
import io.ktor.features.*
import io.ktor.http.cio.websocket.*
import io.ktor.websocket.*
import io.nekohasekai.ktlib.core.mkLog
import io.nekohasekai.ktlib.td.core.TdClient
import io.nekohasekai.nmd.database.Sessions
import io.nekohasekai.nmd.utils.EncUtil
import io.nekohasekai.tmicro.tmnet.SerializedData
import io.nekohasekai.tmicro.tmnet.TMApi
import io.nekohasekai.tmicro.tmnet.TMApi.*
import io.nekohasekai.tmicro.tmnet.TMStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import org.jetbrains.exposed.sql.select
import td.TdApi

class ConnectionsManager(val sessionKey: ByteArray, time: Int, val session: DefaultWebSocketServerSession) {

    val chaChaSession = EncUtil.ChaChaSession(sessionKey, time)
    val log = mkLog("Sessions ${session.call.request.origin.remoteHost}#${Base64.encodeUrlSafe(sessionKey).hashCode()}")

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
            TMStore.deserializeFromSteam(SerializedData(data), true)
        } catch (e: Throwable) {
            log.warn(e, "Deserialize request failed: ")
            return
        }
        if (request !is TMApi.Function) {
            close()
            return
        }
        processRequest(request)
    }

    suspend fun close() {
        session.close(CloseReason(CloseReason.Codes.INTERNAL_ERROR, ":("))
        if (::client.isInitialized) {
            client.stop()
        }
    }


    enum class Status {
        START, WAIT_VERIFY, VERIFIED, DENY
    }

    var status = Status.START
    private lateinit var account: ByteArray
    private lateinit var tempData: ByteArray

    private suspend fun processRequest(request: TMApi.Function) {

        val requestId = request.requestId
        log.debug("Server received #0x${Integer.toHexString(requestId)} ${request.javaClass.simpleName}")

        if (status == Status.DENY) {
            sendError(requestId, 403, "Bad session.")
            return
        }

        when (request) {
            is InitConnection -> {
                if (status != Status.START) {
                    sendError(requestId, 400, "Connection started.")
                    return
                }
                if (request.layer > LAYER) {
                    sendError(requestId, 501, "Max layer of this server is $LAYER.")
                    return
                }
                if (request.session.size != 33) {
                    sendError(requestId, 400, "Bad session.")
                    close()
                    return
                }
                account = request.session
                val pubKey = try {
                    ECPublicKeyParameters(EncUtil.sm2Params.curve.decodePoint(request.session), EncUtil.sm2Params)
                } catch (e: Exception) {
                    log.warn(e, "Encrypt sm2 failed: ")
                    sendError(requestId, 400, "Bad session.")
                    close()
                    return
                }
                tempData = ByteArray(32)
                EncUtil.secureRandom.nextBytes(tempData)
                val data = EncUtil.processSM2(pubKey, true, tempData)
                val response = ConnInitTemp()
                response.data = data
                status = Status.WAIT_VERIFY
                sendResponse(requestId, response)
            }
            is VerifyConnection -> {
                if (status == Status.START) {
                    sendError(requestId, 400, "Connection not started.")
                    return
                } else if (status == Status.VERIFIED) {
                    sendError(requestId, 400, "Connection verified.")
                    return
                }
                if (!tempData.contentEquals(request.data)) {
                    status = Status.DENY
                    sendError(requestId, 403, "Bad data.")
                    close()
                    return
                }
                status = Status.VERIFIED
                sendOk(requestId)

                onConnected()
            }
            else -> if (status != Status.VERIFIED) {
                sendError(requestId, 401, "Unauthorized.")
                return
            }
        }

    }

    private suspend fun sendResponse(requestId: Int, result: Object) {
        if (requestId == -1) {
            // no response
            return
        }

        log.debug("Server send #0x${Integer.toHexString(requestId)} ${result.javaClass.simpleName}")

        val response = Response()
        response.requestId = requestId
        response.response = result

        sendUpdate(response)
    }

    private suspend fun sendUpdate(update: Object) {
        val data = SerializedData()
        TMStore.serializeToStream(data, update)
        sendRaw(data.toByteArray())
    }

    private suspend fun sendOk(requestId: Int) {
        sendResponse(requestId, Ok())
    }

    private suspend fun sendError(requestId: Int, code: Int, message: String) {
        val error = Error()
        error.code = code
        error.message = message

        sendResponse(requestId, error)
    }

    private suspend fun onConnected() {
        val account = Nmd.database {
            Sessions.select { Sessions.key eq sessionKey }.firstOrNull()
        }
        if (account == null || account[Sessions.status] == 0) {
            sendUpdate(UpdateAuthorizationState(AuthorizationStateWaitPhoneNumber()))
        } else {
            requireClient().start()
        }
    }

    private lateinit var client: SessionClient
    fun requireClient(): SessionClient {
        if (!::client.isInitialized) {
            client = SessionClient()
            client.start()
        }
        return client
    }

    inner class SessionClient : TdClient() {

        init {
            options databaseDirectory "data/sessions/${Base64.encodeUrlSafe(sessionKey)}"
            options apiId Nmd.API_ID
            options apiHash Nmd.API_HASH
        }

        override suspend fun onAuthorizationState(authorizationState: TdApi.AuthorizationState) {
            super.onAuthorizationState(authorizationState)

            updateStatus(authorizationState)
        }

        suspend fun updateStatus(authorizationState: TdApi.AuthorizationState) {
            fun TdApi.AuthenticationCodeType.trans(): AuthenticationCodeType {
                return when (this) {
                    is TdApi.AuthenticationCodeTypeTelegramMessage -> AuthenticationCodeTypeTelegramMessage()
                    is TdApi.AuthenticationCodeTypeCall -> AuthenticationCodeTypeCall()
                    is TdApi.AuthenticationCodeTypeSms -> AuthenticationCodeTypeSms()
                    else -> error("Illegal code type $this")
                }
            }

            when (authorizationState) {
                is TdApi.AuthorizationStateWaitPhoneNumber -> {
                    sendUpdate(UpdateAuthorizationState(AuthorizationStateWaitPhoneNumber()))
                }
                is TdApi.AuthorizationStateWaitCode -> {
                    val codeInfo = AuthenticationCodeInfo(
                        authorizationState.codeInfo.phoneNumber,
                        authorizationState.codeInfo.type.trans(),
                        authorizationState.codeInfo.nextType?.trans(),
                        authorizationState.codeInfo.timeout
                    )
                    sendUpdate(UpdateAuthorizationState(AuthorizationStateWaitCode(codeInfo)))
                }
                is TdApi.AuthorizationStateWaitPassword -> {
                    val state = AuthorizationStateWaitPassword(
                        authorizationState.passwordHint,
                        authorizationState.hasRecoveryEmailAddress,
                        authorizationState.recoveryEmailAddressPattern
                    )

                    sendUpdate(UpdateAuthorizationState(state))

                }
                is TdApi.AuthorizationStateReady -> {
                    sendUpdate(UpdateAuthorizationState(AuthorizationStateReady()))
                }
            }
        }

    }

    suspend fun onClosed() {
        connections.remove(sessionKey)
        if (::client.isInitialized) {
            client.stop()
        }
    }


}