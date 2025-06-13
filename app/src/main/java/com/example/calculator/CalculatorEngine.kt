package com.example.calculator

import java.math.BigDecimal
import java.math.MathContext
import java.util.*
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
        inputText += text
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
        val result = evalInfix(inputText)
        lastAnswer = result
        return result
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

        // 1) Tokenize
        val tokens = tokenize(expr)

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
    private fun tokenize(expr: String): List<String> {
        // Simple regex-based tokenizer; expand to cover your functions/operators
        val regex = """(\d+(\.\d+)?)|[+\-×÷^%()]|[a-zA-Z_]+""".toRegex()
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
        "sin", "cos", "tan", "log", "ln", "√", "x²", "x³", "x⁻¹"
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
            "x²" -> a.multiply(a)
            "x³" -> a.multiply(a).multiply(a)
            "x⁻¹" -> BigDecimal.ONE.divide(a, MathContext.DECIMAL64)
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

            "log" -> BigDecimal(log10(a.toDouble()), MathContext.DECIMAL64)
            "ln" -> BigDecimal(ln(a.toDouble()), MathContext.DECIMAL64)
            else -> a
        }
    }
}
