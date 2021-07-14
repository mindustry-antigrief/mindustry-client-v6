package mindustry.client.crypto

import mindustry.client.crypto.Base32768Coder.BITS
import mindustry.client.utils.*
import java.io.IOException
import java.util.zip.CRC32

/** You've heard of base64, now get ready for... base32768.  Encodes 15 bits of data into each unicode character,
 * which so far has not caused any problems.  If it turns out to break stuff, the [BITS] constant can be changed
 * to a more sensible value.  Note that it is not just a base conversion, it also has a length prefix.
 * FINISHME: maybe move to arbitrary base?  It sucks that it can't be 16 bit just because it has to avoid a couple chars.
 */
object Base32768Coder {
    private const val BITS = 15

    fun availableBytes(length: Int) = ((length * BITS) / 8.0).floor()

    fun encodedLengthOf(bytes: Int) = ((bytes * 8.0) / BITS).ceil()

    fun encode(input: ByteArray): String {
        // Create output array
        val out = CharArray(encodedLengthOf(input.size))
        // Create bit stream from input
        val buffer = RandomAccessInputStream(input.plus(listOf(0, 0)))

        for (index in out.indices) {
            // Get [BITS] bits out of the stream and add 128 to avoid ASCII control chars
            out[index] = buffer.readBits(BITS).toChar() + 128
        }

        // Include encoded length as 4 chars each representing 1 byte
        val lengthEncoded = String(input.size.toBytes().map { it.toInt().toChar() + 128 }.toCharArray())

        val crc = CRC32()
        crc.update(input)
        val crcEncoded = String(crc.value.toBytes().map { it.toInt().toChar() + 128 }.toCharArray())

        return lengthEncoded + out.concatToString() + crcEncoded
    }

    @Throws(IOException::class)
    fun decode(input: String): ByteArray {
        if (input.length < 12) throw IOException("String does not have length prefix and/or checksum!")
        try {
            // Extract length
            val size = input.slice(0 until 4).map { (it - 128).code.toByte() }.toByteArray().int()
            // Extract checksum
            val checksum = input.takeLast(8).map { (it - 128).code.toByte() }.toByteArray().long()
            if (size > 50_000_000) throw IOException("Array would be too big!")
            // Create output
            val array = ByteArray(size)
            // Create bit stream leading to output array
            val buffer = RandomAccessOutputStream(array)

            for ((index, c) in input.withIndex()) {
                if (index < 4 || index > input.length - 8) continue
                // Take each char, reverse the transform, and add it to the bit stream
                buffer.writeBits((c.code - 128), BITS)
            }

            val crc = CRC32()
            crc.update(array)
            if (crc.value != checksum) throw IOException("Checksum does not match!")

            return array
        } catch (e: Exception) {
            throw IOException(e)
        }
    }
}
