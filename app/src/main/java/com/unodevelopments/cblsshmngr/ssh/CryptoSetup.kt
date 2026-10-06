package com.unodevelopments.cblsshmngr.ssh

import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security

object CryptoSetup {
    fun install() {
        Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }
}
