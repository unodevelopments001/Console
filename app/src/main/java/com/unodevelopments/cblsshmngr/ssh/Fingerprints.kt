package com.unodevelopments.cblsshmngr.ssh

import android.util.Base64
import net.schmizz.sshj.common.Buffer
import java.security.MessageDigest
import java.security.PublicKey

object Fingerprints {
    fun sha256(key: PublicKey): String {
        val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
        val digest = MessageDigest.getInstance("SHA-256").digest(blob)
        val encoded = Base64.encodeToString(digest, Base64.NO_WRAP).trimEnd('=')
        return "SHA256:$encoded"
    }
}
