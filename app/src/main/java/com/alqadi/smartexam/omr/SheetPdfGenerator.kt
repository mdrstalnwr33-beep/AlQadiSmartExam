package com.alqadi.smartexam.omr

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.alqadi.smartexam.model.ExamConfig
import java.io.File
import java.io.FileOutputStream

object SheetPdfGenerator {

    fun generatePdf(context: Context, exam: ExamConfig): File {
        val layout = LayoutEngine.buildLayout(exam)
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(LayoutEngine.PAGE_W, LayoutEngine.PAGE_H, 1).create()
        val page = doc.startPage(pageInfo)
        val canvas = page.canvas

        canvas.drawColor(Color.WHITE)

        val blackFill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 2.2f
        }
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 21f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.DKGRAY
            textSize = 18f
        }

        canvas.drawText(exam.name.uppercase(), 100f, 80f, titlePaint)
        canvas.drawText("Name: _____________________________   Class: ____________", 100f, 130f, smallPaint)

        val half = LayoutEngine.MARKER_SIZE / 2f
        val corners = listOf(
            LayoutEngine.ORIGIN_X to LayoutEngine.ORIGIN_Y,
            (LayoutEngine.ORIGIN_X + LayoutEngine.WARP_W) to LayoutEngine.ORIGIN_Y,
            (LayoutEngine.ORIGIN_X + LayoutEngine.WARP_W) to (LayoutEngine.ORIGIN_Y + LayoutEngine.WARP_H),
            LayoutEngine.ORIGIN_X to (LayoutEngine.ORIGIN_Y + LayoutEngine.WARP_H)
        )
        for ((cx, cy) in corners) {
            canvas.drawRect(cx - half, cy - half, cx + half, cy + half, blackFill)
        }

        canvas.drawText(
            "STUDENT ID / ROLL NO.",
            (LayoutEngine.ORIGIN_X + 45).toFloat(),
            (LayoutEngine.ORIGIN_Y + 40).toFloat(),
            labelPaint
        )
        layout.rollGrid.forEachIndexed { colIdx, colBubbles ->
            val firstX = (LayoutEngine.ORIGIN_X + colBubbles.first().second.x).toFloat()
            canvas.drawRect(
                firstX - 15f,
                (LayoutEngine.ORIGIN_Y + 55).toFloat(),
                firstX + 15f,
                (LayoutEngine.ORIGIN_Y + 83).toFloat(),
                strokePaint
            )
            colBubbles.forEach { (digit, b) ->
                val bx = (LayoutEngine.ORIGIN_X + b.x).toFloat()
                val by = (LayoutEngine.ORIGIN_Y + b.y).toFloat()
                if (colIdx == 0) {
                    canvas.drawText(digit, (LayoutEngine.ORIGIN_X + 45).toFloat(), by + 6f, smallPaint)
                }
                canvas.drawCircle(bx, by, b.r.toFloat(), strokePaint)
            }
        }

        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 2f
        }
        canvas.drawLine(
            (LayoutEngine.ORIGIN_X + 20).toFloat(),
            (LayoutEngine.ORIGIN_Y + 382).toFloat(),
            (LayoutEngine.ORIGIN_X + LayoutEngine.WARP_W - 20).toFloat(),
            (LayoutEngine.ORIGIN_Y + 382).toFloat(),
            linePaint
        )

        for (col in 0 until layout.numCols) {
            val qx = 35 + col * layout.colWidth
            exam.options.forEachIndexed { optIdx, label ->
                val bx = (LayoutEngine.ORIGIN_X + qx + 55 + optIdx * layout.optStep).toFloat()
                canvas.drawText(label, bx - 7f, (LayoutEngine.ORIGIN_Y + 415).toFloat(), labelPaint)
            }
        }

        layout.questionGrid.forEach { (qNum, optMap) ->
            val firstBubble = optMap.values.first()
            val col = (qNum - 1) / layout.rowsPerCol
            val qx = (LayoutEngine.ORIGIN_X + 35 + col * layout.colWidth).toFloat()
            val qy = (LayoutEngine.ORIGIN_Y + firstBubble.y).toFloat()

            canvas.drawText(String.format("%02d.", qNum), qx, qy + 6f, labelPaint)
            optMap.values.forEach { b ->
                canvas.drawCircle(
                    (LayoutEngine.ORIGIN_X + b.x).toFloat(),
                    (LayoutEngine.ORIGIN_Y + b.y).toFloat(),
                    b.r.toFloat(),
                    strokePaint
                )
            }
        }

        doc.finishPage(page)
        val outFile = File(context.cacheDir, "omr_${exam.id}.pdf")
        FileOutputStream(outFile).use { doc.writeTo(it) }
        doc.close()
        return outFile
    }
}
