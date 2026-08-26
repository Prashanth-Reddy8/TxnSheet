package app.txnsheet.personal.parsing

import java.text.Normalizer

/** Linear-time normalization shared by parsing and duplicate fingerprinting. */
object TextNormalizer {
    const val MAX_PARSER_INPUT_CHARS: Int = 4_096

    fun normalize(rawText: String): String {
        val unicodeNormalized = Normalizer.normalize(rawText, Normalizer.Form.NFKC)
        val output = StringBuilder(unicodeNormalized.length)
        var pendingSpace = false

        for (character in unicodeNormalized) {
            if (character.isWhitespace() || character == '\u00A0') {
                pendingSpace = output.isNotEmpty()
                continue
            }

            // Strip zero-width and bidi-formatting characters before matching or hashing.
            if (Character.getType(character) == Character.FORMAT.toInt()) continue

            if (pendingSpace) {
                output.append(' ')
                pendingSpace = false
            }
            output.append(character)
        }

        return output.toString().trim()
    }
}
