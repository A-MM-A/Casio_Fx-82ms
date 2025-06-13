//package com.example.calculator
//
//import org.junit.Assert.assertEquals
//import org.junit.Before
//import org.junit.Test
//import java.math.BigDecimal
//
//class CalculatorEngineTest {
//    private lateinit var engine: CalculatorEngine
//
//    @Before
//    fun setup() {
//        engine = CalculatorEngine()
//    }
//
//    @Test
//    fun testSimpleAddition() {
//        engine.clear()
//        engine.append("2+3")
//        val result = engine.evaluate()
//        assertEquals(BigDecimal("5"), result)
//    }
//
//    @Test
//    fun testOperatorPrecedence() {
//        engine.clear()
//        engine.append("2+3×4")
//        // Expect 2+(3*4)=14
//        assertEquals(BigDecimal("14"), engine.evaluate())
//    }
//
//    @Test
//    fun testParentheses() {
//        engine.clear()
//        engine.append("(2+3)×4")
//        assertEquals(BigDecimal("20"), engine.evaluate())
//    }
//
//    @Test
//    fun testFunctions() {
//        engine.clear()
//        engine.append("√(9)")
//        assertEquals(BigDecimal("3"), engine.evaluate())
//    }
//
//    @Test
//    fun testExponent() {
//        engine.clear()
//        engine.append("2^10")
//        assertEquals(BigDecimal("1024"), engine.evaluate())
//    }
//
//    @Test
//    fun testMemory() {
//        engine.clear()
//        engine.append("7")
//        engine.evaluate()
//        engine.memoryAdd()
//        engine.clear()
//        engine.memoryRecall()
//        assertEquals("7", engine.inputText)
//    }
//
//    @Test
//    fun testAns() {
//        engine.clear()
//        engine.append("5×5")
//        engine.evaluate()
//        engine.useAns()
//        // Should append "25"
//        assertEquals("25", engine.inputText)
//    }
//}
