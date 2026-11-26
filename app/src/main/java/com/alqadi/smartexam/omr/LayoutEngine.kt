package com.alqadi.smartexam.omr

import com.alqadi.smartexam.model.BubbleCoord
import com.alqadi.smartexam.model.ExamConfig
import com.alqadi.smartexam.model.ExamLayout
import kotlin.math.ceil

object LayoutEngine {
    const val PAGE_W = 1200
    const val PAGE_H = 1697
    const val WARP_W = 1000
    const val WARP_H = 1400
    const val ORIGIN_X = 100
    const val ORIGIN_Y = 190
    const val MARKER_SIZE = 50

    fun buildLayout(exam: ExamConfig): ExamLayout {
        val rollGrid = mutableListOf<List<Pair<String, BubbleCoord>>>()
        val rollR = 10
        for (d in 0 until exam.rollDigits) {
            val cx = 95 + d * 44
            val col = mutableListOf<Pair<String, BubbleCoord>>()
            for (digit in 0..9) {
                val cy = 105 + digit * 27
                col.add(digit.toString() to BubbleCoord(cx, cy, rollR))
            }
            rollGrid.add(col)
        }

        val numCols = when {
            exam.numQuestions <= 30 -> 2
            exam.numQuestions <= 60 -> 3
            else -> 4
        }
        val rowsPerCol = ceil(exam.numQuestions / numCols.toDouble()).toInt().coerceAtLeast(1)
        val gridTop = 445
        val availH = 920
        val rowStep = minOf(48, availH / rowsPerCol)
        val colWidth = 940 / numCols
        val optStep = minOf(42, (colWidth - 65) / exam.options.size.coerceAtLeast(1))
        val qR = minOf(14, (minOf(rowStep, optStep) * 0.34).toInt().coerceAtLeast(8))

        val questionGrid = linkedMapOf<Int, Map<String, BubbleCoord>>()
        for (qIdx in 0 until exam.numQuestions) {
            val qNum = qIdx + 1
            val col = qIdx / rowsPerCol
            val row = qIdx % rowsPerCol
            val qx = 35 + col * colWidth
            val qy = gridTop + row * rowStep

            val optMap = linkedMapOf<String, BubbleCoord>()
            exam.options.forEachIndexed { optIdx, label ->
                val bx = qx + 55 + optIdx * optStep
                optMap[label] = BubbleCoord(bx, qy, qR)
            }
            questionGrid[qNum] = optMap
        }

        return ExamLayout(
            warpW = WARP_W,
            warpH = WARP_H,
            rollGrid = rollGrid,
            questionGrid = questionGrid,
            numCols = numCols,
            rowsPerCol = rowsPerCol,
            colWidth = colWidth,
            optStep = optStep
        )
    }
}
