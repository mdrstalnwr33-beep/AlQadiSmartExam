package com.alqadi.smartexam.model

data class ExamConfig(
    val id: String,
    val name: String,
    val numQuestions: Int,
    val options: List<String>,
    val rollDigits: Int,
    val positiveMark: Double,
    val negativeMark: Double,
    val answerKey: Map<Int, String>,
    val createdAt: Long = System.currentTimeMillis()
)

data class StudentResult(
    val id: String,
    val examId: String,
    val rollNumber: String,
    val answers: Map<Int, String>,
    val score: Double,
    val maxScore: Double,
    val percentage: Double,
    val correctCount: Int,
    val wrongCount: Int,
    val blankCount: Int,
    val debugImagePath: String?,
    val scannedAt: Long = System.currentTimeMillis()
)

data class BubbleCoord(val x: Int, val y: Int, val r: Int)

data class ExamLayout(
    val warpW: Int,
    val warpH: Int,
    val rollGrid: List<List<Pair<String, BubbleCoord>>>,
    val questionGrid: Map<Int, Map<String, BubbleCoord>>,
    val numCols: Int,
    val rowsPerCol: Int,
    val colWidth: Int,
    val optStep: Int
)
