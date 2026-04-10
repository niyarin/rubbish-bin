package com.niyarin.simplemozckeyboard

class RomajiConverter {
    private val romajiTable = mapOf(
        // あ行
        "a" to "あ", "i" to "い", "u" to "う", "e" to "え", "o" to "お",
        // か行
        "ka" to "か", "ki" to "き", "ku" to "く", "ke" to "け", "ko" to "こ",
        "kya" to "きゃ", "kyu" to "きゅ", "kyo" to "きょ",
        // が行
        "ga" to "が", "gi" to "ぎ", "gu" to "ぐ", "ge" to "げ", "go" to "ご",
        "gya" to "ぎゃ", "gyu" to "ぎゅ", "gyo" to "ぎょ",
        // さ行
        "sa" to "さ", "si" to "し", "shi" to "し", "su" to "す", "se" to "せ", "so" to "そ",
        "sya" to "しゃ", "syu" to "しゅ", "syo" to "しょ",
        "sha" to "しゃ", "shu" to "しゅ", "sho" to "しょ",
        // ざ行
        "za" to "ざ", "zi" to "じ", "ji" to "じ", "zu" to "ず", "ze" to "ぜ", "zo" to "ぞ",
        "zya" to "じゃ", "zyu" to "じゅ", "zyo" to "じょ",
        "ja" to "じゃ", "ju" to "じゅ", "jo" to "じょ",
        // た行
        "ta" to "た", "ti" to "ち", "chi" to "ち", "tu" to "つ", "tsu" to "つ",
        "te" to "て", "to" to "と",
        "tya" to "ちゃ", "tyu" to "ちゅ", "tyo" to "ちょ",
        "cha" to "ちゃ", "chu" to "ちゅ", "cho" to "ちょ",
        // だ行
        "da" to "だ", "di" to "ぢ", "du" to "づ", "de" to "で", "do" to "ど",
        // な行
        "na" to "な", "ni" to "に", "nu" to "ぬ", "ne" to "ね", "no" to "の",
        "nya" to "にゃ", "nyu" to "にゅ", "nyo" to "にょ",
        // は行
        "ha" to "は", "hi" to "ひ", "hu" to "ふ", "fu" to "ふ", "he" to "へ", "ho" to "ほ",
        "hya" to "ひゃ", "hyu" to "ひゅ", "hyo" to "ひょ",
        // ば行
        "ba" to "ば", "bi" to "び", "bu" to "ぶ", "be" to "べ", "bo" to "ぼ",
        "bya" to "びゃ", "byu" to "びゅ", "byo" to "びょ",
        // ぱ行
        "pa" to "ぱ", "pi" to "ぴ", "pu" to "ぷ", "pe" to "ぺ", "po" to "ぽ",
        "pya" to "ぴゃ", "pyu" to "ぴゅ", "pyo" to "ぴょ",
        // ま行
        "ma" to "ま", "mi" to "み", "mu" to "む", "me" to "め", "mo" to "も",
        "mya" to "みゃ", "myu" to "みゅ", "myo" to "みょ",
        // や行
        "ya" to "や", "yu" to "ゆ", "yo" to "よ",
        // ら行
        "ra" to "ら", "ri" to "り", "ru" to "る", "re" to "れ", "ro" to "ろ",
        "rya" to "りゃ", "ryu" to "りゅ", "ryo" to "りょ",
        // わ行
        "wa" to "わ", "wo" to "を", "nn" to "ん",
        // 小文字
        "la" to "ぁ", "li" to "ぃ", "lu" to "ぅ", "le" to "ぇ", "lo" to "ぉ",
        "lya" to "ゃ", "lyu" to "ゅ", "lyo" to "ょ",
        "ltu" to "っ", "ltsu" to "っ", "lwa" to "ゎ",
        "xa" to "ぁ", "xi" to "ぃ", "xu" to "ぅ", "xe" to "ぇ", "xo" to "ぉ",
        "xya" to "ゃ", "xyu" to "ゅ", "xyo" to "ょ",
        "xtu" to "っ", "xtsu" to "っ", "xwa" to "ゎ",
        // 記号
        "-" to "ー", "," to "、", "." to "。"
    )

    fun convert(romaji: String): ConversionResult {
        // 最長一致で変換（前から）
        for (length in minOf(romaji.length, 4) downTo 1) {
            val substring = romaji.take(length)  // 前から取る

            // 'n'は特別処理：次の文字が母音でなければ「ん」
            if (substring == "n" && length == 1 && romaji.length == 1) {
                // 'n'だけの場合は変換しない（次の文字を待つ）
                return ConversionResult(converted = null, remaining = romaji)
            }

            val hiragana = romajiTable[substring]
            if (hiragana != null) {
                val remaining = romaji.drop(length)  // 前からlength文字を削除
                return ConversionResult(
                    converted = hiragana,
                    remaining = remaining
                )
            }
        }

        // 促音の処理（子音の連続）
        if (romaji.length >= 2 && romaji[0] == romaji[1]) {
            val char = romaji[0]
            if (char in "kgsztdhbpmyrw") {
                return ConversionResult(
                    converted = "っ",
                    remaining = romaji.drop(1)
                )
            }
        }

        // 'n'の後に子音が来たら'n'を「ん」に変換
        if (romaji.length >= 2 && romaji[0] == 'n' && romaji[1] !in "aiueoyn") {
            return ConversionResult(
                converted = "ん",
                remaining = romaji.drop(1)
            )
        }

        return ConversionResult(converted = null, remaining = romaji)
    }

    data class ConversionResult(
        val converted: String?,
        val remaining: String
    )
}
