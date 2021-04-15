package io.nekohasekai.nmd.utils

import cn.hutool.core.io.IoUtil
import cn.hutool.core.io.resource.ResourceUtil
import cn.hutool.crypto.BCUtil.toDomainParams
import okhttp3.internal.and
import org.bouncycastle.crypto.CipherParameters
import org.bouncycastle.crypto.digests.SM3Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.engines.SM2Engine
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.*
import org.bouncycastle.crypto.prng.DigestRandomGenerator
import org.bouncycastle.crypto.prng.RandomGenerator
import org.bouncycastle.util.Arrays
import java.math.BigInteger
import java.security.SecureRandom
import java.util.*

object EncUtil {

    var secureRandom = SecureRandom()

    fun processSM2(key: CipherParameters, forEncrypt: Boolean, content: ByteArray): ByteArray {
        val engine = SM2Engine()
        engine.init(forEncrypt, if (forEncrypt) ParametersWithRandom(key, secureRandom) else key)
        return engine.processBlock(content, 0, content.size)
    }

    val sm2Params: ECDomainParameters by lazy {
        toDomainParams(CustomNamedCurves.getByName("sm2p256v1"))
    }

    private lateinit var privKey: ECPrivateKeyParameters

    fun loadPrivKey() {
        if (::privKey.isInitialized) return

        privKey = ECPrivateKeyParameters(BigInteger(ResourceUtil.readBytes("private.key")), sm2Params)
    }

    fun publicDecode(content: ByteArray): ByteArray {
        loadPrivKey()
        return processSM2(privKey, false, content)
    }

    class MicroRandom(
        var generator: RandomGenerator
    ) : Random(0) {
        constructor() : this(DigestRandomGenerator(SM3Digest())) {
            setSeed(System.currentTimeMillis())
        }

        constructor(inSeed: ByteArray) : this(DigestRandomGenerator(SM3Digest())) {
            setSeed(inSeed)
        }

        // public final Provider getProvider();
        fun setSeed(inSeed: ByteArray?) {
            generator.addSeedMaterial(inSeed)
        }

        // public methods overriding random
        override fun nextBytes(bytes: ByteArray) {
            generator.nextBytes(bytes)
        }

        override fun setSeed(rSeed: Long) {
            if (rSeed != 0L) // to avoid problems with Random calling setSeed in construction
            {
                generator.addSeedMaterial(rSeed)
            }
        }

        override fun nextInt(): Int {
            val intBytes = ByteArray(4)
            nextBytes(intBytes)
            var result = 0
            for (i in 0..3) {
                result = (result shl 8) + (intBytes[i] and 0xff)
            }
            return result
        }

        override fun next(numBits: Int): Int {
            val size = (numBits + 7) / 8
            val bytes = ByteArray(size)
            nextBytes(bytes)
            var result = 0
            for (i in 0 until size) {
                result = (result shl 8) + (bytes[i] and 0xff)
            }
            return result and (1 shl numBits) - 1
        }
    }

    class ChaChaSession(val key: ByteArray, val time: Int, val isServer: Boolean = true) {

        private val nonceIn = MicroRandom(genSeed(false))
        private val nonceOut = MicroRandom(genSeed(true))

        /** @noinspection SameParameterValue
         */
        private fun genSeed(output: Boolean): ByteArray {
            return if (output xor isServer) {
                Arrays.concatenate(key, BigInteger.valueOf(time.toLong()).toByteArray(), byteArrayOf(0))
            } else {
                Arrays.concatenate(key, BigInteger.valueOf(time.toLong()).toByteArray(), byteArrayOf(0))
            }
        }

        fun mkMessage(content: ByteArray): ByteArray {
            val nonce = ByteArray(12)
            nonceOut.nextBytes(nonce)
            return process(true, content, nonce)
        }

        fun readMessage(message: ByteArray): ByteArray {
            val nonce = ByteArray(12)
            nonceIn.nextBytes(nonce)
            return process(false, message, nonce)
        }

        private val cipher = ChaCha20Poly1305()
        private fun process(forEncryption: Boolean, content: ByteArray, nonce: ByteArray): ByteArray {
            val parameters = AEADParameters(KeyParameter(key), 128, nonce)
            cipher.init(forEncryption, parameters)
            val result = ByteArray(content.size + IoUtil.DEFAULT_BUFFER_SIZE)
            val offset = cipher.processBytes(content, 0, content.size, result, 0)
            return Arrays.copyOfRange(result, 0, offset + cipher.doFinal(result, offset))
        }

    }

}