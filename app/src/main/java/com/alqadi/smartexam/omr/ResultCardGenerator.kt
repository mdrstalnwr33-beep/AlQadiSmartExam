package com.alqadi.smartexam.omr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.alqadi.smartexam.model.ExamConfig
import com.alqadi.smartexam.model.StudentResult
import java.io.File
import java.io.FileOutputStream

object ResultCardGenerator {
    private const val WIDTH = 1200
    private const val HEIGHT = 1600

    fun createImage(context: Context, exam: ExamConfig, result: StudentResult): File {
        val bitmap = render(context, exam, result)
        val file = File(context.cacheDir, "result_card_${result.id}.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        return file
    }

    fun createPdf(context: Context, exam: ExamConfig, result: StudentResult): File {
        val bitmap = render(context, exam, result)
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = document.startPage(pageInfo)
        page.canvas.drawBitmap(bitmap, null, RectF(0f, 0f, 595f, 842f), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        document.finishPage(page)
        val file = File(context.cacheDir, "result_card_${result.id}.pdf")
        FileOutputStream(file).use { document.writeTo(it) }
        document.close()
        bitmap.recycle()
        return file
    }

    private fun render(context: Context, exam: ExamConfig, result: StudentResult): Bitmap {
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(246, 248, 252))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(20, 43, 76)
        canvas.drawRoundRect(RectF(36f, 36f, 1164f, 300f), 34f, 34f, paint)

        paint.color = Color.WHITE
        paint.textSize = 58f
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("بطاقة نتيجة الطالب", 600f, 125f, paint)
        paint.textSize = 34f
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        canvas.drawText(exam.name.take(48), 600f, 205f, paint)

        paint.color = Color.WHITE
        canvas.drawRoundRect(RectF(60f, 340f, 1140f, 690f), 28f, 28f, paint)
        paint.color = Color.rgb(25, 45, 75)
        paint.textAlign = Paint.Align.RIGHT
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = 42f
        canvas.drawText("الطالب: ${result.studentName.ifBlank { "غير محدد" }}", 1080f, 420f, paint)
        paint.textSize = 34f
        paint.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        canvas.drawText("رقم الجلوس: ${result.rollNumber}", 1080f, 490f, paint)

        paint.color = Color.rgb(232, 241, 255)
        canvas.drawRoundRect(RectF(90f, 530f, 1110f, 650f), 22f, 22f, paint)
        paint.color = Color.rgb(12, 91, 160)
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.create("sans-serif", Typeface.BOLD)
        paint.textSize = 54f
        canvas.drawText("الدرجة: ${result.score} / ${result.maxScore}", 600f, 605f, paint)
        paint.textSize = 30f
        canvas.drawText("${result.percentage}%  •  صحيح ${result.correctCount}  •  خطأ ${result.wrongCount}  •  متروك ${result.blankCount}", 600f, 740f, paint)

        paint.color = Color.rgb(20, 43, 76)
        paint.textSize = 36f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("صورة ورقة الإجابة بعد التصحيح", 600f, 810f, paint)
        val sheet = result.debugImagePath?.let { path -> if (File(path).exists()) BitmapFactory.decodeFile(path) else null }
        if (sheet != null) {
            val area = RectF(80f, 850f, 1120f, 1510f)
            val scale = minOf(area.width() / sheet.width, area.height() / sheet.height)
            val w = sheet.width * scale
            val h = sheet.height * scale
            val dst = RectF(area.centerX() - w / 2f, area.centerY() - h / 2f, area.centerX() + w / 2f, area.centerY() + h / 2f)
            paint.color = Color.WHITE
            canvas.drawRoundRect(area, 18f, 18f, paint)
            canvas.drawBitmap(sheet, null, dst, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            sheet.recycle()
        } else {
            paint.color = Color.DKGRAY
            paint.textSize = 30f
            canvas.drawText("لا تتوفر صورة الورقة", 600f, 1150f, paint)
        }
        paint.color = Color.rgb(120, 130, 145)
        paint.textSize = 22f
        canvas.drawText("القاضي الذكي لتصحيح الاختبارات", 600f, 1560f, paint)
        return bitmap
    }
}
