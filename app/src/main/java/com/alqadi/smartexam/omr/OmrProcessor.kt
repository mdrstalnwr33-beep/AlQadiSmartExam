package com.alqadi.smartexam.omr

import android.content.Context
import android.graphics.Bitmap
import com.alqadi.smartexam.model.ExamConfig
import com.alqadi.smartexam.model.StudentResult
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Rect
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

object OmrProcessor {
    private var initialized = false

    fun ensureInitialized(): Boolean {
        if (!initialized) {
            initialized = OpenCVLoader.initLocal()
        }
        return initialized
    }

    fun scanAndGrade(
        context: Context,
        inputBitmap: Bitmap,
        exam: ExamConfig,
        fillThreshold: Double = 0.42
    ): StudentResult {
        check(ensureInitialized()) { "تعذر تهيئة محرك OpenCV على الجهاز." }

        val prepared = resizeIfTooLarge(
            if (inputBitmap.config == Bitmap.Config.ARGB_8888) inputBitmap
            else inputBitmap.copy(Bitmap.Config.ARGB_8888, false),
            maxDim = 1600
        )

        val layout = LayoutEngine.buildLayout(exam)
        val rgba = Mat()
        val gray = Mat()
        val blurredFull = Mat()
        val threshFull = Mat()
        val warpedColor = Mat()
        val warpedGray = Mat()
        val warpedBlur = Mat()
        val warpedThresh = Mat()

        try {
            Utils.bitmapToMat(prepared, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurredFull, Size(5.0, 5.0), 0.0)
            Imgproc.adaptiveThreshold(
                blurredFull,
                threshFull,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                101,
                12.0
            )

            val markers = findFourCornerMarkers(threshFull, gray.rows() * gray.cols())
            val dstPts = MatOfPoint2f(
                Point(0.0, 0.0),
                Point(layout.warpW.toDouble(), 0.0),
                Point(layout.warpW.toDouble(), layout.warpH.toDouble()),
                Point(0.0, layout.warpH.toDouble())
            )
            val perspective = Imgproc.getPerspectiveTransform(markers, dstPts)
            markers.release()
            dstPts.release()

            val warpSize = Size(layout.warpW.toDouble(), layout.warpH.toDouble())
            Imgproc.warpPerspective(rgba, warpedColor, perspective, warpSize)
            Imgproc.warpPerspective(gray, warpedGray, perspective, warpSize)
            perspective.release()

            Imgproc.GaussianBlur(warpedGray, warpedBlur, Size(5.0, 5.0), 0.0)
            Imgproc.adaptiveThreshold(
                warpedBlur,
                warpedThresh,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                101,
                12.0
            )

            val rollChars = StringBuilder()
            for (col in layout.rollGrid) {
                val marked = col.filter { (_, b) ->
                    bubbleFillRatio(warpedThresh, b.x, b.y, b.r) >= fillThreshold
                }
                if (marked.size == 1) {
                    val (digit, b) = marked.first()
                    rollChars.append(digit)
                    Imgproc.circle(
                        warpedColor,
                        Point(b.x.toDouble(), b.y.toDouble()),
                        b.r + 3,
                        Scalar(255.0, 140.0, 0.0, 255.0),
                        2
                    )
                } else {
                    rollChars.append("?")
                }
            }

            val studentAnswers = linkedMapOf<Int, String>()
            var score = 0.0
            var correctCount = 0
            var wrongCount = 0
            var blankCount = 0

            layout.questionGrid.forEach { (qNum, optMap) ->
                val marked = optMap.entries.filter { (_, b) ->
                    bubbleFillRatio(warpedThresh, b.x, b.y, b.r) >= fillThreshold
                }
                val chosen = when (marked.size) {
                    0 -> "BLANK"
                    1 -> marked.first().key
                    else -> "MULTIPLE"
                }
                studentAnswers[qNum] = chosen

                val correctOpt = exam.answerKey[qNum]
                if (chosen == "BLANK") {
                    blankCount++
                } else if (correctOpt != null && chosen == correctOpt) {
                    score += exam.positiveMark
                    correctCount++
                    val b = marked.first().value
                    Imgproc.circle(
                        warpedColor,
                        Point(b.x.toDouble(), b.y.toDouble()),
                        b.r + 3,
                        Scalar(0.0, 200.0, 0.0, 255.0),
                        3
                    )
                } else {
                    score -= exam.negativeMark
                    wrongCount++
                    marked.forEach { (_, b) ->
                        Imgproc.circle(
                            warpedColor,
                            Point(b.x.toDouble(), b.y.toDouble()),
                            b.r + 3,
                            Scalar(230.0, 30.0, 30.0, 255.0),
                            3
                        )
                    }
                    if (correctOpt != null) {
                        optMap[correctOpt]?.let { cb ->
                            Imgproc.circle(
                                warpedColor,
                                Point(cb.x.toDouble(), cb.y.toDouble()),
                                cb.r + 3,
                                Scalar(30.0, 120.0, 255.0, 255.0),
                                2
                            )
                        }
                    }
                }
            }

            val maxScore = exam.numQuestions * exam.positiveMark
            val pct = if (maxScore > 0) (score / maxScore) * 100.0 else 0.0

            val debugBmp = Bitmap.createBitmap(warpedColor.cols(), warpedColor.rows(), Bitmap.Config.ARGB_8888)
            Utils.matToBitmap(warpedColor, debugBmp)
            val resultId = UUID.randomUUID().toString()
            val debugFile = File(context.filesDir, "scan_$resultId.jpg")
            FileOutputStream(debugFile).use { fos ->
                debugBmp.compress(Bitmap.CompressFormat.JPEG, 88, fos)
            }
            debugBmp.recycle()

            return StudentResult(
                id = resultId,
                examId = exam.id,
                rollNumber = rollChars.toString(),
                answers = studentAnswers,
                score = kotlin.math.round(score * 100.0) / 100.0,
                maxScore = maxScore,
                percentage = kotlin.math.round(pct * 100.0) / 100.0,
                correctCount = correctCount,
                wrongCount = wrongCount,
                blankCount = blankCount,
                debugImagePath = debugFile.absolutePath
            )
        } finally {
            rgba.release()
            gray.release()
            blurredFull.release()
            threshFull.release()
            warpedColor.release()
            warpedGray.release()
            warpedBlur.release()
            warpedThresh.release()
        }
    }

    fun hasFourValidCornerMarkers(bitmap: Bitmap): Boolean {
        if (!ensureInitialized()) return false
        val rgba = Mat()
        val gray = Mat()
        val blurred = Mat()
        val threshold = Mat()
        return try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.GaussianBlur(gray, blurred, Size(5.0, 5.0), 0.0)
            Imgproc.adaptiveThreshold(blurred, threshold, 255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C, Imgproc.THRESH_BINARY_INV, 101, 12.0)
            val points = findFourCornerMarkers(threshold, gray.rows() * gray.cols())
            points.release()
            true
        } catch (_: Exception) {
            false
        } finally {
            rgba.release(); gray.release(); blurred.release(); threshold.release()
        }
    }

    private fun findFourCornerMarkers(thresh: Mat, imgArea: Int): MatOfPoint2f {
        val contours = ArrayList<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(thresh, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        hierarchy.release()
        val candidates = mutableListOf<Pair<Double, Point>>()
        for (cnt in contours) {
            val area = Imgproc.contourArea(cnt)
            if (area < 0.00025 * imgArea || area > 0.04 * imgArea) { cnt.release(); continue }
            val cnt2f = MatOfPoint2f(*cnt.toArray())
            val peri = Imgproc.arcLength(cnt2f, true)
            val approx = MatOfPoint2f()
            Imgproc.approxPolyDP(cnt2f, approx, 0.04 * peri, true)
            val approxPts = MatOfPoint(*approx.toArray())
            val isQuad = approx.rows() == 4 && Imgproc.isContourConvex(approxPts)
            val rect = Imgproc.minAreaRect(cnt2f)
            cnt2f.release(); approx.release(); approxPts.release(); cnt.release()
            if (!isQuad || rect.size.width <= 0 || rect.size.height <= 0) continue
            val aspect = rect.size.width / rect.size.height
            val fill = area / (rect.size.width * rect.size.height)
            if (aspect in 0.65..1.45 && fill >= 0.68) candidates.add(area to rect.center)
        }
        if (candidates.size < 4) throw IllegalStateException("لم يتم العثور على المربعات المرجعية الأربعة.")
        val points = candidates.sortedByDescending { it.first }.take(4).map { it.second }
        val tl = points.minBy { it.x + it.y }
        val br = points.maxBy { it.x + it.y }
        val tr = points.minBy { it.y - it.x }
        val bl = points.maxBy { it.y - it.x }
        val ordered = listOf(tl, tr, br, bl)
        val edges = ordered.indices.map { i -> distance(ordered[i], ordered[(i + 1) % 4]) }
        val diagonals = listOf(distance(tl, br), distance(tr, bl))
        val oppositeHorizontal = edges[0] / edges[2].coerceAtLeast(1.0)
        val oppositeVertical = edges[1] / edges[3].coerceAtLeast(1.0)
        val diagonalRatio = diagonals.max() / diagonals.min().coerceAtLeast(1.0)
        val cross = (tr.x - tl.x) * (bl.y - tl.y) - (tr.y - tl.y) * (bl.x - tl.x)
        if (edges.any { it < 30.0 } || oppositeHorizontal !in 0.55..1.8 ||
            oppositeVertical !in 0.55..1.8 || diagonalRatio > 1.9 || cross <= 0.0) {
            throw IllegalStateException("المربعات المرجعية لا تشكل إطار ورقة صحيحاً. ضع الورقة كاملة داخل الكاميرا.")
        }
        return MatOfPoint2f(tl, tr, br, bl)
    }

    private fun distance(a: Point, b: Point): Double {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }

    private fun bubbleFillRatio(thresh: Mat, x: Int, y: Int, r: Int): Double {
        val innerR = max(4, (r * 0.68).toInt())
        val size = innerR * 2
        if (x - innerR < 0 || y - innerR < 0 || x + innerR > thresh.cols() || y + innerR > thresh.rows()) {
            return 0.0
        }

        val mask = Mat.zeros(size, size, CvType.CV_8UC1)
        Imgproc.circle(mask, Point(innerR.toDouble(), innerR.toDouble()), innerR, Scalar(255.0), -1)

        val roi = Mat(thresh, Rect(x - innerR, y - innerR, size, size))
        val masked = Mat.zeros(size, size, CvType.CV_8UC1)
        roi.copyTo(masked, mask)

        val filled = Core.countNonZero(masked)
        val total = Core.countNonZero(mask)

        mask.release()
        roi.release()
        masked.release()

        return if (total > 0) filled / total.toDouble() else 0.0
    }

    private fun resizeIfTooLarge(src: Bitmap, maxDim: Int): Bitmap {
        val longest = max(src.width, src.height)
        if (longest <= maxDim) return src
        val ratio = maxDim.toFloat() / longest.toFloat()
        val nw = (src.width * ratio).toInt()
        val nh = (src.height * ratio).toInt()
        return Bitmap.createScaledBitmap(src, nw, nh, true)
    }
}
