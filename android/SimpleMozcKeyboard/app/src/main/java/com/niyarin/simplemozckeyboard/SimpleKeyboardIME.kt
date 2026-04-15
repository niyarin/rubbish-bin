package com.niyarin.simplemozckeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import android.widget.HorizontalScrollView
import com.niyarin.mozc.converter.MozcConverter
import com.niyarin.mozc.factory.MozcFactory
import com.niyarin.mozc.models.Candidate
import com.niyarin.mozc.models.MozcResult

// Type alias for compatibility with existing code
typealias CandidateInfo = Candidate

class SimpleKeyboardIME : InputMethodService(), KeyboardView.OnKeyboardActionListener,
    LongPressKeyboardView.LongPressPopupListener {

    companion object {
        private val EMOJI_CODE_MAP = mapOf(
            2001 to "\uD83D\uDE00", // 😀
            2002 to "\uD83D\uDE03", // 😃
            2003 to "\uD83D\uDE04", // 😄
            2004 to "\uD83D\uDE01", //😁
            2005 to "\uD83D\uDE06", // 😆
            2006 to "\uD83D\uDE05", // 😅
            2007 to "\uD83E\uDD23", // 🤣
            2008 to "\uD83D\uDE02", // 😂
            2009 to "\uD83D\uDE42", // 🙂
            2010 to "\uD83D\uDE43", // 🙃
            2011 to "\uD83D\uDE09", // 😉
            2012 to "\uD83D\uDE0A", // 😊
            2013 to "\uD83D\uDE07", // 😇
            2014 to "\uD83E\uDD70", // 🥰
            2015 to "\uD83D\uDE0D", // 😍
        )
    }

    // 入力モードの定義
    private enum class InputMode {
        JAPANESE,  // ひらがな（ローマ字変換）
        ENGLISH,   // 英字
        NUMBER,    // 数字・記号
        EMOJI      // 絵文字
    }

    private var keyboardView: LongPressKeyboardView? = null
    private lateinit var japaneseKeyboard: Keyboard
    private lateinit var englishKeyboard: Keyboard
    private lateinit var numberKeyboard: Keyboard
    private lateinit var emojiKeyboard: Keyboard
    private val romajiConverter = RomajiConverter()
    private var mozcConverter: MozcConverter? = null
    private var currentEditorInfo: android.view.inputmethod.EditorInfo? = null

    // 候補表示用のビュー
    private var candidateStrip: HorizontalScrollView? = null
    private var candidateContainer: LinearLayout? = null
    private var currentCandidates: List<Candidate> = emptyList()
    private var currentCandidateIndex = 0

    // 現在の入力モード
    private var currentMode = InputMode.JAPANESE

    // 未確定文字列（ローマ字のまま）
    private var romajiBuffer = StringBuilder()
    // 変換済みひらがな（未確定）
    private var hiraganaBuffer = StringBuilder()

    // 変換モード中かどうか（Mozcが変換候補を持っている状態）
    private var isInConversionMode = false

    // 未確定文字列全体（ひらがな+ローマ字）
    private val composingText: StringBuilder
        get() {
            val result = StringBuilder()
            result.append(hiraganaBuffer)
            result.append(romajiBuffer)
            return result
        }

    // 未確定文字列をクリアする
    private fun clearComposingText() {
        hiraganaBuffer.clear()
        romajiBuffer.clear()
        isInConversionMode = false
    }

    override fun onCreate() {
        super.onCreate()
        // Pre-load classes that are referenced by native code via JNI
        // This ensures they are available when the native library initializes
        try {
            Class.forName("org.mozc.android.inputmethod.japanese.nativecallback.HttpClient")
            Class.forName("com.google.android.apps.inputmethod.libs.mozc.session.MozcJNI")
            Class.forName("org.mozc.android.inputmethod.japanese.MozcLog")
            android.util.Log.d("SimpleKeyboardIME", "Pre-loaded JNI classes successfully")
        } catch (e: ClassNotFoundException) {
            android.util.Log.e("SimpleKeyboardIME", "Failed to pre-load JNI classes", e)
        }

        // Initialize Mozc using new 3-layer architecture
        try {
            val config = com.niyarin.mozc.models.MozcConfig(
                candidatePageSize = 20,
                textDeletionCapability = true,
                enableDebugLog = true  // デバッグログを有効化
            )
            
            // Load dictionary from assets
            val result = MozcFactory.createFromContextWithAsset(this, "mozc.data", config)
            when (result) {
                is MozcResult.Success -> {
                    mozcConverter = result.data
                    android.util.Log.d("SimpleKeyboardIME", "Mozc initialized successfully with new architecture and dictionary from assets")
                }
                is MozcResult.Error -> {
                    android.util.Log.w("SimpleKeyboardIME", "Mozc initialization failed: ${result.message}", result.cause)
                    mozcConverter = null
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("SimpleKeyboardIME", "Mozc initialization error", e)
            mozcConverter = null
        }
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        currentEditorInfo = attribute
        updateEnterKeyLabel()
    }

    override fun onCreateInputView(): View {
        val inputView = layoutInflater.inflate(R.layout.keyboard, null)

        // キーボードビューの初期化
        keyboardView = inputView.findViewById(R.id.keyboard)
        keyboardView?.longPressPopupListener = this

        // 候補ストリップの初期化
        candidateStrip = inputView.findViewById(R.id.candidate_strip_container)
        candidateContainer = candidateStrip?.findViewById(R.id.candidate_container)
        android.util.Log.d("SimpleKeyboardIME", "candidateStrip initialized: ${candidateStrip != null}")
        android.util.Log.d("SimpleKeyboardIME", "candidateContainer initialized: ${candidateContainer != null}")

        // 各モードのキーボードを準備
        japaneseKeyboard = Keyboard(this, R.xml.qwerty)
        englishKeyboard = Keyboard(this, R.xml.english)
        numberKeyboard = Keyboard(this, R.xml.numbers)
        emojiKeyboard = Keyboard(this, R.xml.emoji)

        // 初期はQWERTYキーボード
        keyboardView?.keyboard = japaneseKeyboard
        keyboardView?.longPressNumbersEnabled = false
        keyboardView?.setOnKeyboardActionListener(this)

        return inputView
    }

    private fun updateEnterKeyLabel() {
        val keyboard = keyboardView?.keyboard ?: return
        val editorInfo = currentEditorInfo ?: return

        // エンターキー（keyCode=10）を探す
        for (key in keyboard.keys) {
            if (key.codes.isNotEmpty() && key.codes[0] == 10) {
                // IMEアクションに応じてラベルを変更
                val actionId = editorInfo.imeOptions and android.view.inputmethod.EditorInfo.IME_MASK_ACTION
                key.label = when (actionId) {
                    android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH -> "検索"
                    android.view.inputmethod.EditorInfo.IME_ACTION_GO -> "移動"
                    android.view.inputmethod.EditorInfo.IME_ACTION_SEND -> "送信"
                    android.view.inputmethod.EditorInfo.IME_ACTION_NEXT -> "次へ"
                    android.view.inputmethod.EditorInfo.IME_ACTION_DONE -> "完了"
                    else -> "改行"
                }
                android.util.Log.d("SimpleKeyboardIME", "Enter key label set to: ${key.label} (action=$actionId)")
                keyboardView?.invalidateAllKeys()
                break
            }
        }
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        val inputConnection: InputConnection = currentInputConnection ?: return

        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                handleBackspace(inputConnection)
            }
            -100 -> {
                // モード切り替え（あ/A/123 キー）
                toggleInputMode()
            }
            -101 -> {
                // 絵文字モード切り替え
                switchToEmojiMode()
            }
            10 -> {
                // 改行（エンター）
                if (isInConversionMode) {
                    // 変換モード中の場合は、変換結果を確定
                    mozcConverter?.let { converter ->
                        when (val result = converter.commit()) {
                            is MozcResult.Success -> {
                                inputConnection.commitText(result.data, 1)
                                clearComposingText()
                                hideCandidates()
                                converter.reset()
                                android.util.Log.d("SimpleKeyboardIME", "Committed: ${result.data}")
                            }
                            is MozcResult.Error -> {
                                android.util.Log.e("SimpleKeyboardIME", "Commit failed: ${result.message}")
                                // Fallback: finish composing
                                inputConnection.finishComposingText()
                                clearComposingText()
                                hideCandidates()
                                converter.reset()
                            }
                        }
                    }
                } else {
                    // 未確定文字列があれば確定
                    commitComposingText(inputConnection)

                    // IMEアクションに応じた処理
                    handleEnterKey(inputConnection)
                }
            }
            32 -> {
                // スペース
                if (currentMode == InputMode.JAPANESE) {
                    if (isInConversionMode) {
                        advanceConversionCandidate(inputConnection)
                    } else if (composingText.isNotEmpty()) {
                        // 未確定文字列がある場合は変換を開始
                        handleConversion(inputConnection)
                    } else {
                        // 通常のスペース
                        inputConnection.commitText(" ", 1)
                    }
                } else {
                    commitComposingText(inputConnection)
                    inputConnection.commitText(" ", 1)
                }
            }
            in 'a'.code..'z'.code -> {
                handleAlphabetKey(inputConnection, primaryCode.toChar())
            }
            '-'.code, 12540 -> {
                if (currentMode == InputMode.JAPANESE) {
                    handleAlphabetKey(inputConnection, '-')
                } else {
                    commitComposingText(inputConnection)
                    inputConnection.commitText(primaryCode.toChar().toString(), 1)
                }
            }
            else -> {
                // その他の文字（数字、記号など）、または絵文字モードでの入力
                val emoji = EMOJI_CODE_MAP[primaryCode]
                if (currentMode == InputMode.EMOJI && emoji != null) {
                    commitComposingText(inputConnection)
                    inputConnection.commitText(emoji, 1)
                } else {
                    commitComposingText(inputConnection)
                    inputConnection.commitText(primaryCode.toChar().toString(), 1)
                }
            }
        }
    }

    private fun handleAlphabetKey(ic: InputConnection, char: Char) {
        when (currentMode) {
            InputMode.ENGLISH -> {
                // 英語モード：そのまま入力
                ic.commitText(char.toString(), 1)
            }
            InputMode.JAPANESE -> {
                // 日本語モード：ローマ字変換
                romajiBuffer.append(char)
                val result = romajiConverter.convert(romajiBuffer.toString())

                if (result.converted != null) {
                    // 変換成功：ひらがなをhiraganaBufferに追加、romajiBufferは残りのみ
                    hiraganaBuffer.append(result.converted)
                    romajiBuffer.clear()
                    romajiBuffer.append(result.remaining)
                }

                // 未確定文字列を表示（ひらがな+ローマ字）
                val displayText = hiraganaBuffer.toString() + romajiBuffer.toString()
                ic.setComposingText(displayText, 1)

                // ひらがなが入力されたら、サジェストを表示
                if (hiraganaBuffer.isNotEmpty()) {
                    updateSuggestions()
                }
            }
            InputMode.NUMBER -> {
                // 数字モードでは英字は入力されない（通常は発生しない）
            }
            InputMode.EMOJI -> {
                // 絵文字モードでは英字は入力されない
            }
        }
    }

    private fun updateSuggestions() {
        mozcConverter?.let { converter ->
            try {
                val hiraganaText = hiraganaBuffer.toString()
                android.util.Log.d("SimpleKeyboardIME", "Getting suggestions for: $hiraganaText")

                when (val result = converter.getSuggestions(hiraganaText)) {
                    is MozcResult.Success -> {
                        val directCandidates = result.data
                        val fallbackCandidates =
                            if (directCandidates.size <= 1) {
                                getSuggestionsFromConversionFallback(converter, hiraganaText)
                            } else {
                                emptyList()
                            }

                        val candidates = if (shouldPreferFallback(directCandidates, fallbackCandidates)) {
                            fallbackCandidates
                        } else {
                            directCandidates
                        }
                        android.util.Log.d("SimpleKeyboardIME", "Got ${candidates.size} suggestions")

                        if (candidates.isNotEmpty()) {
                            showCandidates(candidates)
                        } else {
                            hideCandidates()
                        }
                    }
                    is MozcResult.Error -> {
                        android.util.Log.e("SimpleKeyboardIME", "Failed to get suggestions: ${result.message}")
                        val fallbackCandidates = getSuggestionsFromConversionFallback(converter, hiraganaText)
                        if (fallbackCandidates.isNotEmpty()) {
                            showCandidates(fallbackCandidates)
                        } else {
                            hideCandidates()
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SimpleKeyboardIME", "Failed to get suggestions", e)
                hideCandidates()
            }
        }
    }

    private fun getSuggestionsFromConversionFallback(
        converter: MozcConverter,
        hiraganaText: String
    ): List<Candidate> {
        if (hiraganaText.isEmpty()) {
            return emptyList()
        }

        return try {
            converter.reset()
            when (val result = converter.convert(hiraganaText)) {
                is MozcResult.Success -> {
                    result.data.candidates
                        .distinctBy { it.value }
                        .take(20)
                }
                is MozcResult.Error -> {
                    android.util.Log.e(
                        "SimpleKeyboardIME",
                        "Fallback conversion for suggestions failed: ${result.message}"
                    )
                    emptyList()
                }
            }
        } finally {
            converter.reset()
        }
    }

    private fun shouldPreferFallback(
        directCandidates: List<Candidate>,
        fallbackCandidates: List<Candidate>
    ): Boolean {
        if (fallbackCandidates.isEmpty()) {
            return false
        }
        if (directCandidates.isEmpty()) {
            return true
        }
        if (directCandidates.size > 1) {
            return false
        }

        val directValue = directCandidates.first().value
        return fallbackCandidates.size > 1 || fallbackCandidates.firstOrNull()?.value != directValue
    }

    private fun handleBackspace(ic: InputConnection) {
        if (isInConversionMode) {
            // 変換モード中の場合は、MozcにBACKSPACEキーを送る
            mozcConverter?.let { converter ->
                android.util.Log.d("SimpleKeyboardIME", "Backspace in conversion mode")

                when (val result = converter.backspace()) {
                    is MozcResult.Success -> {
                        val conversionResult = result.data
                        if (conversionResult.preedit.isNotEmpty()) {
                            // まだpreeditがある場合は、それを表示
                            ic.setComposingText(conversionResult.preedit, 1)
                            showCandidates(conversionResult.candidates)
                        } else {
                            // preeditがなくなったら、変換モードを終了
                            ic.setComposingText("", 1)
                            ic.finishComposingText()
                            clearComposingText()
                            hideCandidates()
                        }
                    }
                    is MozcResult.Error -> {
                        // エラーの場合は変換モードを終了
                        android.util.Log.e("SimpleKeyboardIME", "Backspace failed: ${result.message}")
                        ic.setComposingText("", 1)
                        ic.finishComposingText()
                        clearComposingText()
                        hideCandidates()
                    }
                }
            }
        } else if (composingText.isNotEmpty()) {
            // 未確定文字列がある場合は、それを削除
            if (romajiBuffer.isNotEmpty()) {
                // ローマ字バッファから削除
                romajiBuffer.deleteCharAt(romajiBuffer.length - 1)
            } else if (hiraganaBuffer.isNotEmpty()) {
                // ひらがなバッファから削除
                hiraganaBuffer.deleteCharAt(hiraganaBuffer.length - 1)
            }

            if (composingText.isEmpty()) {
                ic.setComposingText("", 1)
                ic.finishComposingText()
                hideCandidates()
            } else {
                ic.setComposingText(composingText.toString(), 1)
                // サジェストを更新
                // Note: サジェストは一旦無効化（変換機能と競合するため）
                // if (hiraganaBuffer.isNotEmpty()) {
                //     updateSuggestions()
                // } else {
                    hideCandidates()
                // }
            }
        } else {
            // 通常の削除
            ic.deleteSurroundingText(1, 0)
        }
    }

    private fun commitComposingText(ic: InputConnection) {
        if (composingText.isNotEmpty()) {
            // 未確定文字列をそのまま確定
            ic.commitText(composingText.toString(), 1)
            clearComposingText()
        }
    }

    private fun handleEnterKey(ic: InputConnection) {
        val editorInfo = currentEditorInfo

        if (editorInfo != null) {
            val actionId = editorInfo.imeOptions and android.view.inputmethod.EditorInfo.IME_MASK_ACTION

            when (actionId) {
                android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH,
                android.view.inputmethod.EditorInfo.IME_ACTION_GO,
                android.view.inputmethod.EditorInfo.IME_ACTION_SEND,
                android.view.inputmethod.EditorInfo.IME_ACTION_NEXT,
                android.view.inputmethod.EditorInfo.IME_ACTION_DONE -> {
                    // アクションを実行（検索、移動、送信など）
                    android.util.Log.d("SimpleKeyboardIME", "Performing editor action: $actionId")
                    ic.performEditorAction(actionId)
                    return
                }
            }
        }

        // デフォルトは改行
        ic.commitText("\n", 1)
    }

    private fun showCandidates(candidates: List<CandidateInfo>) {
        android.util.Log.d("SimpleKeyboardIME", "showCandidates called with ${candidates.size} candidates")
        android.util.Log.d("SimpleKeyboardIME", "candidateStrip: $candidateStrip, candidateContainer: $candidateContainer")

        candidateContainer?.removeAllViews()
        currentCandidates = candidates
        if (currentCandidateIndex !in candidates.indices) {
            currentCandidateIndex = 0
        }

        if (candidates.isEmpty()) {
            android.util.Log.d("SimpleKeyboardIME", "No candidates, hiding candidate strip")
            candidateStrip?.visibility = View.GONE
            return
        }

        android.util.Log.d("SimpleKeyboardIME", "Adding ${candidates.size} candidate buttons")
        candidates.forEach { candidate ->
            val button = Button(this).apply {
                text = candidate.value
                textSize = 18f
                setBackgroundResource(R.drawable.candidate_button_background)
                setPadding(24, 12, 24, 12)

                // マージンを設定
                val layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
                layoutParams.setMargins(4, 8, 4, 8)
                this.layoutParams = layoutParams

                setOnClickListener {
                    onCandidateSelected(candidate)
                }
            }
            candidateContainer?.addView(button)
        }

        android.util.Log.d("SimpleKeyboardIME", "Setting candidateStrip visibility to VISIBLE")
        candidateStrip?.visibility = View.VISIBLE
        android.util.Log.d("SimpleKeyboardIME", "candidateStrip visibility is now: ${candidateStrip?.visibility}")
    }

    private fun hideCandidates() {
        candidateContainer?.removeAllViews()
        currentCandidates = emptyList()
        currentCandidateIndex = 0
        candidateStrip?.visibility = View.GONE
    }

    private fun advanceConversionCandidate(ic: InputConnection) {
        if (!isInConversionMode || currentCandidates.isEmpty()) {
            return
        }

        mozcConverter?.let { converter ->
            val nextIndex = (currentCandidateIndex + 1) % currentCandidates.size
            val nextCandidate = currentCandidates[nextIndex]

            when (val result = converter.selectCandidate(nextCandidate.id)) {
                is MozcResult.Success -> {
                    currentCandidateIndex = nextIndex
                    val conversionResult = result.data
                    if (conversionResult.preedit.isNotEmpty()) {
                        ic.setComposingText(conversionResult.preedit, 1)
                    }
                    if (conversionResult.candidates.isNotEmpty()) {
                        showCandidates(conversionResult.candidates)
                        val updatedIndex =
                            conversionResult.candidates.indexOfFirst { it.id == nextCandidate.id }
                        if (updatedIndex >= 0) {
                            currentCandidateIndex = updatedIndex
                        } else {
                            currentCandidateIndex =
                                nextIndex.coerceAtMost(conversionResult.candidates.lastIndex)
                        }
                    }
                    Unit
                }
                is MozcResult.Error -> {
                    android.util.Log.e("SimpleKeyboardIME", "Failed to advance candidate: ${result.message}")
                }
            }
        }
    }

    private fun onCandidateSelected(candidate: Candidate) {
        val ic = currentInputConnection ?: return

        if (isInConversionMode) {
            // 変換モード中の候補選択
            mozcConverter?.let { converter ->
                try {
                    android.util.Log.d("SimpleKeyboardIME", "Candidate selected: ${candidate.value}")

                    when (val result = converter.selectCandidate(candidate.id)) {
                        is MozcResult.Success -> {
                            // 候補が選択された後の状態を取得
                            // その後確定
                            when (val commitResult = converter.commit()) {
                                is MozcResult.Success -> {
                                    ic.commitText(commitResult.data, 1)
                                    clearComposingText()
                                    hideCandidates()
                                    converter.reset()
                                }
                                is MozcResult.Error -> {
                                    android.util.Log.e("SimpleKeyboardIME", "Commit after selection failed: ${commitResult.message}")
                                    // Fallback: commit the candidate value directly
                                    ic.commitText(candidate.value, 1)
                                    clearComposingText()
                                    hideCandidates()
                                    converter.reset()
                                }
                            }
                        }
                        is MozcResult.Error -> {
                            android.util.Log.e("SimpleKeyboardIME", "Failed to select candidate: ${result.message}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("SimpleKeyboardIME", "Failed to select candidate", e)
                }
            }
        } else {
            // サジェストモード中の候補選択：そのまま確定
            android.util.Log.d("SimpleKeyboardIME", "Suggestion selected: ${candidate.value}")
            ic.commitText(candidate.value, 1)
            clearComposingText()
            hideCandidates()
            mozcConverter?.reset()
        }
    }

    private fun handleConversion(ic: InputConnection) {
        if (composingText.isEmpty()) {
            android.util.Log.d("SimpleKeyboardIME", "handleConversion: composingText is empty")
            return
        }

        val hiraganaText = composingText.toString()
        android.util.Log.d("SimpleKeyboardIME", "handleConversion: hiragana='$hiraganaText'")

        // Try to use Mozc for conversion
        val mozcSuccess = mozcConverter?.let { converter ->
            try {
                // ひらがな文字列を送って変換
                when (val result = converter.convert(hiraganaText)) {
                    is MozcResult.Success -> {
                        val conversionResult = result.data
                        android.util.Log.d("SimpleKeyboardIME", "Conversion successful: preedit=${conversionResult.preedit}, ${conversionResult.candidates.size} candidates")

                        if (conversionResult.preedit.isNotEmpty()) {
                            // 変換候補を未確定状態で表示（確定はしない）
                            ic.setComposingText(conversionResult.preedit, 1)
                            isInConversionMode = true
                            currentCandidateIndex = 0

                            // 候補リストを表示
                            showCandidates(conversionResult.candidates)

                            // Note: hiraganaBufferとromajiBufferはクリアしない
                            // エンターキーで確定、候補リストから選択可能
                            true
                        } else {
                            android.util.Log.d("SimpleKeyboardIME", "No preedit in conversion result")
                            false
                        }
                    }
                    is MozcResult.Error -> {
                        android.util.Log.e("SimpleKeyboardIME", "Mozc conversion failed: ${result.message}")
                        false
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("SimpleKeyboardIME", "Mozc conversion failed", e)
                false
            }
        } ?: false

        // Fallback: just show the hiragana text with a space
        if (!mozcSuccess) {
            android.util.Log.d("SimpleKeyboardIME", "Mozc conversion failed, using fallback")
            ic.commitText("$hiraganaText ", 1)
            clearComposingText()
            hideCandidates()

            // セッションをリセット
            mozcConverter?.reset()
        }
    }

    private fun toggleInputMode() {
        // モード切り替え時は未確定文字列をクリア
        val ic = currentInputConnection
        if (ic != null && composingText.isNotEmpty()) {
            ic.commitText(composingText.toString(), 1)
            clearComposingText()
        }
        hideCandidates()

        // モードをサイクル：ひらがな → 英字 → 数字 → ひらがな（絵文字スキップ）
        currentMode = when (currentMode) {
            InputMode.JAPANESE -> InputMode.ENGLISH
            InputMode.ENGLISH -> InputMode.NUMBER
            InputMode.NUMBER -> InputMode.JAPANESE
            InputMode.EMOJI -> InputMode.JAPANESE  // 絵文字モードから戻る場合
        }

        // モードに応じてキーボードを切り替え
        keyboardView?.keyboard = when (currentMode) {
            InputMode.JAPANESE -> japaneseKeyboard
            InputMode.ENGLISH -> englishKeyboard
            InputMode.NUMBER -> numberKeyboard
            InputMode.EMOJI -> emojiKeyboard
        }
        keyboardView?.longPressNumbersEnabled = currentMode == InputMode.ENGLISH

        // エンターキーのラベルを更新
        updateEnterKeyLabel()
    }

    private fun switchToEmojiMode() {
        val ic = currentInputConnection
        if (ic != null && composingText.isNotEmpty()) {
            ic.commitText(composingText.toString(), 1)
            clearComposingText()
        }
        hideCandidates()

        currentMode = InputMode.EMOJI
        keyboardView?.keyboard = emojiKeyboard
        keyboardView?.longPressNumbersEnabled = false
    }

    override fun onPress(primaryCode: Int) {}

    override fun onRelease(primaryCode: Int) {}

    override fun onText(text: CharSequence?) {}

    override fun swipeLeft() {}

    override fun swipeRight() {}

    override fun swipeDown() {}

    override fun swipeUp() {}

    override fun onPopupTextSelected(text: String) {
        val ic = currentInputConnection ?: return
        commitComposingText(ic)
        ic.commitText(text, 1)
    }
}
