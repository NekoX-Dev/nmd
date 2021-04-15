package io.nekohasekai.nmd

import io.nekohasekai.nmd.utils.EncUtil
import org.bouncycastle.crypto.generators.ECKeyPairGenerator
import org.bouncycastle.crypto.params.ECKeyGenerationParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.params.ECPublicKeyParameters
import java.io.File

object KeyPairGenerator {

    @JvmStatic
    fun main(args: Array<String>) {

        val generator = ECKeyPairGenerator()
        generator.init(ECKeyGenerationParameters(EncUtil.sm2Params, EncUtil.secureRandom))
        val keyPair = generator.generateKeyPair()

        val publicKey = keyPair.public as ECPublicKeyParameters
        val privateKey = keyPair.private as ECPrivateKeyParameters

        File("resources/private.key").writeBytes(privateKey.d.toByteArray())
        File("public.key").writeBytes(publicKey.q.getEncoded(true))

    }

}