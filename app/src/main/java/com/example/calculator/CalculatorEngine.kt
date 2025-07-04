package com.example.calculator

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.asin
import java.util.*
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan


class CalculatorEngine {

    // ***************
    // Public API
    // ***************

    // Current textual input (infix)
    private var inputText: String = ""


    // Last computed answer
    var lastAnswer: BigDecimal = BigDecimal.ZERO
    fun getLastAnswerValue(): BigDecimal = lastAnswer

    // Memory register
    private var memory: BigDecimal = BigDecimal.ZERO

    // Shift toggle
    var shiftOn: Boolean = false

    // Degree vs radian mode
    var isDegree: Boolean = true

    // Append a button’s text (e.g. “7”, “+”, “sin(”)

    fun append(text: String) {
        // Replace unicode superscripts with standard operators
        val normalized = text.replace("²", "^2").replace("³", "^3")
        inputText += normalized
    }


    // Delete last character
    fun deleteLast() {
        if (inputText.isNotEmpty()) {
            inputText = inputText.dropLast(1)
        }
    }

    // Clear input completely
    fun clear() {
        inputText = ""
    }

    // Toggle Shift
    fun toggleShift() {
        shiftOn = !shiftOn
    }

    // Press “Ans”
    fun useAns() {
        inputText += lastAnswer.toPlainString()
    }

    fun getAns(): String {
        return lastAnswer.toPlainString()
    }


    // Memory ops
//    fun memoryAdd() {
//        evaluateIfNeeded()
//        memory = memory.add(lastAnswer)
//    }
//    fun memorySubtract() {
//        evaluateIfNeeded()
//        memory = memory.subtract(lastAnswer)
//    }
//    fun memoryRecall() {
//        inputText += memory.toPlainString()
//    }
//    fun memoryClear() {
//        memory = BigDecimal.ZERO
//    }

    fun setInput(text: String) {
        inputText = text
    }

    // Compute the current expression and update lastAnswer
    fun evaluate(): BigDecimal {
        return try {
            val result = evalInfix(inputText)
            lastAnswer = result
            result
        } catch (e: Exception) {
            // on error, leave lastAnswer unchanged and return zero
            BigDecimal.ZERO
        }
    }

    // Helper to ensure lastAnswer is up-to-date before mem ops
    private fun evaluateIfNeeded() {
        if (inputText.isNotBlank()) {
            lastAnswer = evalInfix(inputText)
        }
    }

    private fun String.isNumber(): Boolean =
        this.toDoubleOrNull() != null


    // ***************
    // Shunting-yard + RPN
    // ***************

    private fun evalInfix(expr: String): BigDecimal {
        val outputQueue = LinkedList<String>()
        val opStack = Stack<String>()

//        // 1) Tokenize
//        val tokens = tokenize(expr)

        // 1) Tokenize
        val raw = tokenize(expr)
        // 1a) Insert implicit parentheses for function calls without explicit ‘(…)’
        val tokens = mutableListOf<String>()
        var i = 0
        while (i < raw.size) {
            val tk = raw[i]
            if (tk.isFunction() && i + 1 < raw.size && raw[i + 1].isNumber()) {
                // rewrite sin 21  → sin ( 21 )
                tokens += tk
                tokens += "("
                tokens += raw[i + 1]
                tokens += ")"
                i += 2
            } else {
                tokens += tk
                i++
            }
        }

        // 2) Shunting-Yard
        for (token in tokens) {
            when {
                token.isNumber() -> outputQueue.add(token)
                token.isFunction() -> opStack.push(token)
                token == "," -> {
                    while (opStack.isNotEmpty() && opStack.peek() != "(") {
                        outputQueue.add(opStack.pop())
                    }
                }

                token.isOperator() -> {
                    while (opStack.isNotEmpty() &&
                        opStack.peek().isOperator() &&
                        (token.precedence() <= opStack.peek().precedence())
                    ) {
                        outputQueue.add(opStack.pop())
                    }
                    opStack.push(token)
                }

                token == "(" -> opStack.push(token)
                token == ")" -> {
                    while (opStack.isNotEmpty() && opStack.peek() != "(") {
                        outputQueue.add(opStack.pop())
                    }
                    if (opStack.peek() == "(") opStack.pop()          // discard "("
                    if (opStack.isNotEmpty() && opStack.peek().isFunction()) {
                        outputQueue.add(opStack.pop())
                    }
                }
            }
        }
        while (opStack.isNotEmpty()) outputQueue.add(opStack.pop())

        // 3) RPN Evaluation
        val evalStack = Stack<BigDecimal>()
        while (outputQueue.isNotEmpty()) {
            val tk = outputQueue.removeFirst()
            when {
                tk.isNumber() -> evalStack.push(BigDecimal(tk))
                tk.isOperator() -> {
                    val b = evalStack.pop()
                    val a = evalStack.pop()
                    evalStack.push(applyOperator(a, b, tk))
                }

                tk.isFunction() -> {
                    val a = evalStack.pop()
                    evalStack.push(applyFunction(a, tk))
                }
            }
        }
        // Final result
        return evalStack.pop().stripTrailingZeros()
    }

    // Tokenization: split numbers, functions, operators, parentheses, commas
//    private fun tokenize(expr: String): List<String> {
//        // Simple regex-based tokenizer; expand to cover your functions/operators
////        val regex = """(\d+(\.\d+)?)|[+\-×÷^%()]|[a-zA-Z_]+""".toRegex()
////        val regex = """(\d+(\.\d+)?)|[+\-×÷^%()²³⁻¹]|[a-zA-Z_]+|√""".toRegex()
//        val regex = """(\d+(\.\d+)?)|[+\-×÷^%()]|⁻¹|[²³]|[a-zA-Z_]+|√""".toRegex()
//        return regex.findAll(expr).map { it.value }.toList()
//    }

    private fun tokenize(expr: String): List<String> {
        // Matches full function names (e.g., sin⁻¹), numbers, operators, and parentheses
        val regex =
            """(sin⁻¹|cos⁻¹|tan⁻¹|sin|cos|tan|log|√|²|³|⁻¹|\d+(\.\d+)?|[+\-×÷^%()])""".toRegex()
        return regex.findAll(expr).map { it.value }.toList()
    }

    // Operator details
    private fun String.isOperator() = this in listOf("+", "-", "×", "÷", "^", "%")
    private fun String.precedence() = when (this) {
        "+", "-" -> 1
        "×", "÷", "%" -> 2
        "^" -> 3
        else -> 0
    }

    // Function details (add “sin”, “cos”, “log”, etc. here)
    private fun String.isFunction() = this in setOf(
        "sin", "cos", "tan", "log", "ln", "√", "²", "³", "⁻¹", "sin⁻¹", "cos⁻¹", "tan⁻¹"
    )

    private fun applyOperator(a: BigDecimal, b: BigDecimal, op: String): BigDecimal = when (op) {
        "+" -> a.add(b)
        "-" -> a.subtract(b)
        "×" -> a.multiply(b)
        "÷" -> a.divide(b, MathContext.DECIMAL64)
        "%" -> a.multiply(b).divide(BigDecimal(100), MathContext.DECIMAL64)
        "^" -> a.pow(b.toInt(), MathContext.DECIMAL64)
        else -> BigDecimal.ZERO
    }

    private fun applyFunction(a: BigDecimal, fn: String): BigDecimal {
        return when (fn) {
            "√" -> BigDecimal(sqrt(a.toDouble()), MathContext.DECIMAL64)
            "²" -> a.multiply(a)
            "³" -> a.multiply(a).multiply(a)
            "⁻¹" -> BigDecimal.ONE.divide(a, MathContext.DECIMAL64)
            "sin" -> {
                val rad = if (isDegree) Math.toRadians(a.toDouble()) else a.toDouble()
                BigDecimal(sin(rad), MathContext.DECIMAL64)
            }

            "cos" -> {
                val rad = if (isDegree) Math.toRadians(a.toDouble()) else a.toDouble()
                BigDecimal(cos(rad), MathContext.DECIMAL64)
            }

            "tan" -> {
                val rad = if (isDegree) Math.toRadians(a.toDouble()) else a.toDouble()
                BigDecimal(tan(rad), MathContext.DECIMAL64)
            }


            "sin⁻¹" -> {
                val value = a.toDouble()

                // Check if input is in the valid domain
                require(value in -1.0..1.0) {
                    "Input for sin⁻¹ (arcsin) must be between -1 and 1, but was $value"
                }

                // Compute arcsin in radians
                val rad = kotlin.math.asin(value)

                // Convert to degrees if needed
                val out = if (isDegree) Math.toDegrees(rad) else rad

                // Return as BigDecimal with DECIMAL64 precision
                BigDecimal(out, MathContext.DECIMAL64)
            }

            "cos⁻¹" -> {
                val value = a.toDouble()
                require(value in -1.0..1.0) {
                    "Input for cos⁻¹ must be between -1 and 1, but was $value"
                }
                val rad = acos(value)
                val out = if (isDegree) Math.toDegrees(rad) else rad
                BigDecimal(out, MathContext.DECIMAL64)
            }

            "tan⁻¹" -> {
                val value = a.toDouble()
                val rad = kotlin.math.atan(value)
                val out = if (isDegree) Math.toDegrees(rad) else rad
                BigDecimal(out, MathContext.DECIMAL64)
            }


            "log" -> BigDecimal(log10(a.toDouble()), MathContext.DECIMAL64)
            "ln" -> BigDecimal(ln(a.toDouble()), MathContext.DECIMAL64)
            else -> a
        }
    }
}
