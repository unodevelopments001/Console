package com.unodevelopments.cblsshmngr.ssh

import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

class Utf8Decoder {
    private val pending = ArrayList<Byte>()
    private val decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPLACE)
        .onUnmappableCharacter(CodingErrorAction.REPLACE)

    fun decode(bytes: ByteArray): String {
        if (bytes.isEmpty() && pending.isEmpty()) return ""
        val data = ByteArray(pending.size + bytes.size)
        for (index in pending.indices) {
            data[index] = pending[index]
        }
        System.arraycopy(bytes, 0, data, pending.size, bytes.size)
        pending.clear()

        val input = ByteBuffer.wrap(data)
        var output = CharBuffer.allocate(data.size + 8)
        var result = decoder.decode(input, output, false)
        while (result.isOverflow) {
            val bigger = CharBuffer.allocate(output.capacity() * 2)
            output.flip()
            bigger.put(output)
            output = bigger
            result = decoder.decode(input, output, false)
        }
        if (input.hasRemaining()) {
            while (input.hasRemaining()) {
                pending.add(input.get())
            }
        }
        output.flip()
        return output.toString()
    }
}
