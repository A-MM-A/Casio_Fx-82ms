package com.example.calculator


import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.calculator.databinding.ActivityMainBinding
import android.os.Handler
import android.os.Looper
import kotlin.math.max
import kotlin.math.min
import androidx.core.graphics.toColorInt
import java.math.BigDecimal
import kotlin.math.roundToInt
import java.math.MathContext
import java.math.RoundingMode
import androidx.core.content.edit
import android.animation.ArgbEvaluator
import android.animation.ValueAnimator


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val engine = CalculatorEngine()

    // ─── Cursor & Windowing State ───────────────────────────
    private var inputText = ""
    private var cursorPos = 0
    private var windowStart = 0
    private var windowEnd = 0
    private var justEvaluated = false
    private var lastRawResult = BigDecimal.ZERO
    private var resultFormat = 0
    private var justInserted = false
    private var isViewingHistory = false
    private var isDarkMode = false

    // History: list of (expression, result)
    private val history = mutableListOf<Pair<String, String>>()

    // Current position in history navigation; -1 = not navigating
    private var historyPos = -1


    private var showCursor = true
    private val cursorHandler = Handler(Looper.getMainLooper())
    private val cursorRunnable = object : Runnable {
        override fun run() {
            showCursor = !showCursor
            refreshInput()
            cursorHandler.postDelayed(this, 500)
        }
    }
    // ──────────────────────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.InputTv.text = ""
        binding.ResultTv.text = "0."


        // 1) On / Mode button
        binding.btnOn.setOnClickListener {
            inputText = ""
            binding.ResultTv.text = "0."

            cursorPos = 0
            windowStart = 0


            binding.IndicatorTvShift.visibility = View.GONE
            binding.IndicatorTvM.visibility = View.GONE

            engine.shiftOn = false
            justEvaluated = false
            isViewingHistory = false
            updateShiftStateUI()

            refreshInput()

            // restart the blinking cursor
            cursorHandler.removeCallbacks(cursorRunnable)
            showCursor = true
            cursorHandler.post(cursorRunnable)

            history.clear()
            historyPos = -1
            getSharedPreferences("calc_prefs", MODE_PRIVATE)
                .edit {
                    remove("history_json")
                }
        }


        binding.btnMode.setOnClickListener {
            historyPos = -1

            if (engine.shiftOn) {
                engine.shiftOn = false
                updateShiftStateUI()
                binding.IndicatorTvShift.visibility = View.GONE
            }

            engine.isDegree = !engine.isDegree
            binding.IndicatorTvDegree.visibility =
                if (engine.isDegree) View.INVISIBLE else View.VISIBLE

            // Define colors
            val colorFrom = if (isDarkMode) "#0F0E0E".toColorInt() else "#D3D1D1".toColorInt()
            val colorTo = if (isDarkMode) "#D3D1D1".toColorInt() else "#0F0E0E".toColorInt()

            // Animate background color
            val colorAnimation = ValueAnimator.ofObject(ArgbEvaluator(), colorFrom, colorTo)
            colorAnimation.duration = 500 // duration in milliseconds
            colorAnimation.addUpdateListener { animator ->
                binding.root.setBackgroundColor(animator.animatedValue as Int)
            }
            colorAnimation.start()

            isDarkMode = !isDarkMode
        }


        // 2) Number buttons
        listOf(
            binding.btn0 to "0",
            binding.btn1 to "1",
            binding.btn2 to "2",
            binding.btn3 to "3",
            binding.btn4 to "4",
            binding.btn5 to "5",
            binding.btn6 to "6",
            binding.btn7 to "7",
            binding.btn8 to "8",
            binding.btn9 to "9",
            binding.btnDot to ".",
            binding.btnOpenBracket to "( ",
            binding.btnCloseBracket to " )"
        ).forEach { (btn, str) ->
            btn.setOnClickListener {
                historyPos = -1
                if (engine.shiftOn) {
                    engine.shiftOn = false
                    updateShiftStateUI()
                    binding.IndicatorTvShift.visibility = View.GONE
                }
                handlePostEval(str) { insertChar(str) }
            }
        }

        // 3) Delete button
        binding.btnDel.setOnClickListener {
            if (!justEvaluated) {

                if (cursorPos == 0) return@setOnClickListener

                // Look for multi-char tokens:
                val text = inputText
                val before = text.substring(0, cursorPos)
                val deleteLen = when {
                    before.endsWith("Ans ") -> 4
                    before.endsWith("sin ") -> 4
                    before.endsWith("sin⁻¹ ") -> 6
                    before.endsWith("cos ") -> 4
                    before.endsWith("cos⁻¹ ") -> 6
                    before.endsWith("tan ") -> 4
                    before.endsWith("tan⁻¹ ") -> 6
                    before.endsWith("log ") -> 4
                    before.endsWith("₁₀ ") -> 3
                    before.endsWith("ln ") -> 3
                    before.endsWith("⁻¹") -> 2
                    before.endsWith("( ") -> 2
                    before.endsWith(" )") -> 2
                    else -> 1
                }
                inputText = text.removeRange(cursorPos - deleteLen, cursorPos)
                cursorPos -= deleteLen
                if (cursorPos < 0) cursorPos = 0
                refreshInput()
            }
            if (engine.shiftOn) {
                engine.shiftOn = false
                updateShiftStateUI()
                binding.IndicatorTvShift.visibility = View.GONE
            }
        }

        //  AC button
        binding.btnAc.setOnClickListener {
            inputText = ""
            cursorPos = 0
            windowStart = 0
            binding.ResultTv.text = "0."
            binding.IndicatorTvShift.visibility = View.GONE
            engine.shiftOn = false
            justEvaluated = false
            isViewingHistory = false
            updateShiftStateUI()

            // refresh the display
            refreshInput()

            // restart the blinking cursor
            cursorHandler.removeCallbacks(cursorRunnable)
            showCursor = true
            cursorHandler.post(cursorRunnable)


        }

        // 4) Operators
        mapOf(
            binding.btnAdd to "+",
            binding.btnMinus to "-",
            binding.btnMultiply to "×",
            binding.btnDivide to "÷",
            binding.btnNegative to "-",
            binding.btnPower to "^",
            binding.btnSquare to "²",
            binding.btnCube to "³",
            binding.btnSqrt to "√",
            binding.btnInverse to "⁻¹"
        ).forEach { (btn, op) ->
            btn.setOnClickListener {
                historyPos = -1
                if (engine.shiftOn) {
                    engine.shiftOn = false
                    updateShiftStateUI()
                    binding.IndicatorTvShift.visibility = View.GONE
                }
                handlePostEval(op) { insertChar(op) }
            }
        }


        // 5) Functions
        binding.btnSin.setOnClickListener {
            historyPos = -1
            handlePostEval("sin ") { handleShiftableFunction("sin ", "sin⁻¹ ") }
        }
        binding.btnCos.setOnClickListener {
            historyPos = -1
            handlePostEval("cos ") { handleShiftableFunction("cos ", "cos⁻¹ ") }
        }
        binding.btnTan.setOnClickListener {
            historyPos = -1
            handlePostEval("tan ") { handleShiftableFunction("tan ", "tan⁻¹ ") }
        }
        binding.btnLog.setOnClickListener {
            historyPos = -1
            handlePostEval("log ") { insertChar("log ") }
        }


        // 6) Shift toggle
        binding.btnShift.setOnClickListener {
            historyPos = -1
            // if we just pressed “=”, clear the old input so shift‑sin starts fresh
            if (justEvaluated || isViewingHistory) {
                inputText = ""
                cursorPos = 0
                windowStart = 0
                justEvaluated = false
            }
            engine.toggleShift()
            binding.IndicatorTvShift.visibility = if (engine.shiftOn) View.VISIBLE else View.GONE

            refreshInput()
            updateShiftStateUI()
        }


        //  Ans button
        binding.btnAns.setOnClickListener {
            historyPos = -1
            if (engine.shiftOn) {
                engine.shiftOn = false
                updateShiftStateUI()
                binding.IndicatorTvShift.visibility = View.GONE
            }
            handlePostEval("Ans ") { insertChar("Ans ") }
        }


        //  Equal button
        binding.btnEqual.setOnClickListener {
            // if input ends with a function token (no argument), feed it Ans
            Regex("(sin⁻¹|cos⁻¹|tan⁻¹|sin|cos|tan|log|ln)\\s*$").find(inputText)?.let {
                inputText = inputText.trimEnd() + " Ans "
                refreshInput()
            }

            try {

                // Just before evaluate():
                val evalString = inputText.replace(
                    "Ans", engine.getLastAnswerValue().toPlainString()
                )


                // 1) push our visual input into the engine
                engine.setInput(evalString)

                // 2) evaluate safely
                val result = engine.evaluate()

                //  Round to 10 significant digits:
                val rounded = result.round(MathContext(10, RoundingMode.HALF_UP))
                //  Remove any trailing “.0” or zeros:
                val clean = rounded.stripTrailingZeros().toPlainString()
                //  If it's still longer than 10 chars (e.g. huge integer),
                //    truncate *right* (keeping the most significant digits):
                val disp = if (clean.length <= 10) clean else clean.substring(0, 10)

                binding.ResultTv.text = disp

                // record into history (expression before Ans→value replacement)
                history.add(inputText to disp)
                // reset nav pointer
                historyPos = -1

                // 3) update lastAnswer inside engine (if you track it there)
                engine.lastAnswer = result

                lastRawResult = result
                resultFormat = 0
                updateResultDisplay()


                // stop blinking
                cursorHandler.removeCallbacks(cursorRunnable)
                showCursor = false
                refreshInput()

                justEvaluated = true
                isViewingHistory = false

            } catch (e: Exception) {
                // prevents crash, shows error
                e.printStackTrace()
                binding.ResultTv.text = " Error"
            }

        }


        // 9) Degree and fraction toggle

        binding.btnFraction.setOnClickListener {
            historyPos = -1
            if (engine.shiftOn) {
                engine.shiftOn = false
                updateShiftStateUI()
                binding.IndicatorTvShift.visibility = View.GONE
            }
            handlePostEval("/") { insertChar("") }
        }

        binding.btnDegree.setOnClickListener {
            historyPos = -1
            if (engine.shiftOn) {
                engine.shiftOn = false
                updateShiftStateUI()
                binding.IndicatorTvShift.visibility = View.GONE
            }
            handlePostEval("º") { insertChar("") }
        }


        // 10) History up/down (you’ll need to store past inputs in a list)
        binding.btnUp.setOnClickListener {

            if (history.isEmpty()) return@setOnClickListener
            // start navigation
            historyPos = if (historyPos == -1) {
                // first Up press → last entry
                history.size - 1
            } else {
                // loop backward
                (historyPos - 1 + history.size) % history.size
            }
            val (expr, res) = history[historyPos]
            inputText = expr
            cursorPos = expr.length
            binding.ResultTv.text = res
            justEvaluated = false
            isViewingHistory = true
            // stop and show cursor once
            cursorHandler.removeCallbacks(cursorRunnable)
            showCursor = false
            refreshInput()

        }
        binding.btnDown.setOnClickListener {

            if (history.isEmpty()) return@setOnClickListener
            historyPos = if (historyPos == -1) {
                // first Down press → first entry
                0
            } else {
                // loop forward
                (historyPos + 1) % history.size
            }
            val (expr, res) = history[historyPos]
            inputText = expr
            cursorPos = expr.length
            binding.ResultTv.text = res
            justEvaluated = false
            isViewingHistory = true
            cursorHandler.removeCallbacks(cursorRunnable)
            showCursor = false
            refreshInput()

        }


        // 11) Left/Right arrows in input
        binding.btnLeft.setOnClickListener {
            historyPos = -1
            if (engine.shiftOn) {
                engine.shiftOn = false
                updateShiftStateUI()
                binding.IndicatorTvShift.visibility = View.GONE
            }


            if (cursorPos > 0) {
                // try to move one char left
                var newPos = cursorPos - 1
                // define tokens (with trailing space where appropriate)
                val tokens = listOf(
                    "sin⁻¹ ",
                    "cos⁻¹ ",
                    "tan⁻¹ ",
                    "sin ",
                    "cos ",
                    "tan ",
                    "log ",
                    "Ans ",
                    "ln ",
                    "₁₀ ",
                    "⁻¹",
                    "( ",
                    " )"
                )
                // check each token, longest first
                for (tok in tokens.sortedByDescending { it.length }) {
                    val start = newPos + 1 - tok.length
                    if (start >= 0 && inputText.substring(start, newPos + 1) == tok) {
                        // jump to before the token
                        newPos = start
                        break
                    }
                }
                cursorPos = newPos
            }

            refreshInput()

            showCursor = true
            cursorHandler.removeCallbacks(cursorRunnable)
            cursorHandler.post(cursorRunnable)
        }
        binding.btnRight.setOnClickListener {
            historyPos = -1
            if (engine.shiftOn) {
                engine.shiftOn = false
                updateShiftStateUI()
                binding.IndicatorTvShift.visibility = View.GONE
            }

            if (cursorPos < inputText.length) {
                // try to move one char right
                var newPos = cursorPos + 1
                // same tokens as left, match at cursor
                val tokens = listOf(
                    "sin⁻¹ ",
                    "cos⁻¹ ",
                    "tan⁻¹ ",
                    "sin ",
                    "cos ",
                    "tan ",
                    "log ",
                    "Ans ",
                    "ln ",
                    "₁₀ ",
                    "⁻¹",
                    "( ",
                    " )"
                )
                // check each token, longest first
                for (tok in tokens.sortedByDescending { it.length }) {
                    if (inputText.regionMatches(cursorPos, tok, 0, tok.length)) {
                        newPos = cursorPos + tok.length
                        break
                    }
                }
                // clamp at end
                cursorPos = min(newPos, inputText.length)
            }

            refreshInput()

            showCursor = true
            cursorHandler.removeCallbacks(cursorRunnable)
            cursorHandler.post(cursorRunnable)
        }

        // Start blinking cursor and initial draw
        cursorHandler.post(cursorRunnable)
        refreshInput()
    }


    private fun handlePostEval(keyText: String, action: () -> Unit) {
        if (justEvaluated  || isViewingHistory) {
            when {
                // a) Fraction or Degree toggles: only transform the result view
                keyText == "/" || keyText == "º" -> {
                    toggleResultFormat(keyText)
                    // stay in post-eval state
                    return
                }
                // b) Operator buttons: start new expression with "Ans{op}"
                keyText in listOf("+", "-", "×", "÷", "^", "²", "³", "⁻¹") -> {
                    inputText = ""              // clear old input
                    cursorPos = 0
                    insertChar("Ans ")
                    insertChar(keyText)
                    resultFormat = 0
                    updateResultDisplay()

                }
                // c) Any other key: clear and start fresh
                else -> {
                    inputText = ""
                    cursorPos = 0
                    insertChar(keyText)
                    resultFormat = 0
                    updateResultDisplay()

                }
            }
            // Leaving post-eval mode now that a non-toggle button was pressed
            justEvaluated = false
            // restart blinking cursor
            cursorHandler.post(cursorRunnable)
        } else {
            // Normal editing mode
            action()
        }
    }

    private fun approximateFraction(value: Double, maxDenominator: Int = 1000): Pair<Int, Int> {
        // Continued fraction expansion
        var x = value
        var a0 = x.toInt()
        var p0 = 1;
        var q0 = 0
        var p1 = a0;
        var q1 = 1
        var a: Int
        var p2: Int;
        var q2: Int
        while (true) {
            x = 1.0 / (x - a0)
            a = x.toInt()
            p2 = a * p1 + p0
            q2 = a * q1 + q0
            if (q2 > maxDenominator) break
            p0 = p1; q0 = q1
            p1 = p2; q1 = q2
            a0 = a
        }
        return Pair(p1, q1)
    }

    private fun toggleResultFormat(mode: String) {
        when (mode) {
            "/" -> {
                // toggle Fraction mode
                resultFormat = if (resultFormat == 1) 0 else 1
            }

            "º" -> {
                // toggle DMS mode
                resultFormat = if (resultFormat == 2) 0 else 2
            }
        }
        updateResultDisplay()
    }

    private fun updateResultDisplay() {
        val txt = when (resultFormat) {
            1 -> { // Fraction
                val d = lastRawResult.toDouble()
                val (num, den) = approximateFraction(d)
                if (den == 1) "$num" else "$num/$den"
            }

            2 -> { // DMS
                val d = lastRawResult.toDouble()
                val degrees = d.toInt()
                val remMinutes = (d - degrees) * 60
                val minutes = remMinutes.toInt()
                val seconds = ((remMinutes - minutes) * 60).roundToInt()
                "${degrees}° ${minutes}′ ${seconds}″"
            }

            else -> { // Decimal
                // 10-sig-digit rounding + strip zeros
                val mc = MathContext(10, RoundingMode.HALF_UP)
                val rounded = lastRawResult.round(mc).stripTrailingZeros()
                val s = rounded.toPlainString()
                if (s.length <= 10) s else s.substring(0, 10)
            }
        }
        binding.ResultTv.text = txt
    }

    private fun handleShiftableFunction(normal: String, shifted: String) {
        if (engine.shiftOn) {
            insertChar(shifted)
            engine.shiftOn = false
            updateShiftStateUI()
            binding.IndicatorTvShift.visibility = View.GONE
        } else {
            insertChar(normal)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updateShiftStateUI() {
        if (engine.shiftOn) {
            binding.btnSin.text = "sin⁻¹"
            binding.btnSin.setTextColor("#FFEB3B".toColorInt())

            binding.btnCos.text = "cos⁻¹"
            binding.btnCos.setTextColor("#FFEB3B".toColorInt())

            binding.btnTan.text = "tan⁻¹"
            binding.btnTan.setTextColor("#FFEB3B".toColorInt())


        } else {
            binding.btnSin.text = "sin"
            binding.btnSin.setTextColor("#FFFFFF".toColorInt())

            binding.btnCos.text = "cos"
            binding.btnCos.setTextColor("#FFFFFF".toColorInt())

            binding.btnTan.text = "tan"
            binding.btnTan.setTextColor("#FFFFFF".toColorInt())


        }
    }

    private fun insertChar(str: String) {
        justInserted = true

        // If at cursor there’s a multi‐char token, remove it entirely first:
        val tokens = listOf(
            "sin⁻¹ ", "cos⁻¹ ", "tan⁻¹ ",
            "sin ", "cos ", "tan ",
            "log ", "Ans ", "ln ",
            "₁₀ ", "⁻¹", "( ", " )"
        ).sortedByDescending { it.length }
        for (tok in tokens) {
            if (cursorPos + tok.length <= inputText.length
                && inputText.regionMatches(cursorPos, tok, 0, tok.length)
            ) {
                // remove the entire token before inserting
                inputText = inputText.removeRange(cursorPos, cursorPos + tok.length)
                break
            }
        }

        // If cursor is before the end, replace the single character there
        inputText = if (cursorPos < inputText.length) {
            inputText.substring(0, cursorPos) +
                    str +
                    inputText.substring(cursorPos + 1)
        } else {
            // else append at end
            inputText + str
        }

        // Move cursor past inserted/replaced text

        cursorPos = min(inputText.length, cursorPos + str.length)
        refreshInput()
    }

    private fun refreshInput() {
        val tv = binding.InputTv
        val paint = tv.paint

        // 1) Determine how many chars fit
        val avail = tv.width - tv.paddingLeft - tv.paddingRight
        if (avail <= 0) {
            tv.text = if (showCursor) "_" else " "
            return
        }
        val charW = paint.measureText("W")
        val maxChars = max(1, (avail / charW).toInt())

        // 2) Decide windowStart so cursor is visible
        // Aim to center cursor: offset = maxChars/2
        val half = maxChars / 2
        var windowStart = (cursorPos - half).coerceAtLeast(0)
        // clamp so we don’t overflow the text
        windowStart = windowStart.coerceAtMost(max(0, inputText.length - maxChars))

        // 3) Extract that slice
        val windowEnd = min(inputText.length, windowStart + maxChars)
        val vis = inputText.substring(windowStart, windowEnd)

        // 4) Compute local cursor position in this slice
        val localPos = (cursorPos - windowStart).coerceIn(0, vis.length)

        // 5) Build display string by inserting blinking cursor
        val displayRaw = buildString {
            append(vis.substring(0, localPos))
            append(if (showCursor) "_" else " ")
            append(vis.substring(localPos))
        }

        // 6) If raw is too long (it will be maxChars+1), trim to maxChars,
        //    keeping the cursor in view in the middle-ish
        val disp = if (displayRaw.length <= maxChars) {
            displayRaw
        } else {
            // if cursor at or left of half, show leading maxChars
            if (localPos <= half) {
                displayRaw.take(maxChars)
            } else {
                // otherwise, show the last maxChars so cursor stays toward right
                displayRaw.takeLast(maxChars)
            }
        }

        // 7) Finally, set the text and arrows
        tv.text = disp
        binding.InputTvArrowLeft.visibility = if (windowStart > 0) View.VISIBLE else View.INVISIBLE
        binding.InputTvArrowRight.visibility =
            if (windowEnd < inputText.length) View.VISIBLE else View.INVISIBLE
    }

    override fun onDestroy() {
        super.onDestroy()
        cursorHandler.removeCallbacks(cursorRunnable)
    }

    override fun onPause() {
        super.onPause()
        // save history as a proper JSONArray
        val prefs = getSharedPreferences("calc_prefs", MODE_PRIVATE)
        val arr = org.json.JSONArray()
        history.forEach { (expr, res) ->
            val obj = org.json.JSONObject()
            obj.put("expr", expr)
            obj.put("res", res)
            arr.put(obj)
        }
        prefs.edit()
            .putString("history_json", arr.toString())
            .apply()
    }

    override fun onResume() {
        super.onResume()
        // load history from JSONArray
        val prefs = getSharedPreferences("calc_prefs", MODE_PRIVATE)
        val json = prefs.getString("history_json", null) ?: return
        try {
            val arr = org.json.JSONArray(json)
            history.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val expr = obj.getString("expr")
                val res = obj.getString("res")
                history += expr to res
            }
        } catch (e: org.json.JSONException) {
            // corrupted or invalid: clear history
            history.clear()
        }
    }

}

