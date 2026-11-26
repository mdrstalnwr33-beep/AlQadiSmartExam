package com.alqadi.smartexam.storage

import android.content.Context
import com.alqadi.smartexam.model.ExamConfig
import com.alqadi.smartexam.model.StudentResult
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object ExamRepository {
    private const val EXAMS_FILE = "exams.json"
    private const val RESULTS_FILE = "results.json"

    fun loadExams(context: Context): List<ExamConfig> {
        val file = File(context.filesDir, EXAMS_FILE)
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        val list = mutableListOf<ExamConfig>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val optsArr = o.getJSONArray("options")
            val opts = List(optsArr.length()) { idx -> optsArr.getString(idx) }

            val keyObj = o.getJSONObject("answerKey")
            val keyMap = linkedMapOf<Int, String>()
            keyObj.keys().forEach { k -> keyMap[k.toInt()] = keyObj.getString(k) }

            list.add(
                ExamConfig(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    numQuestions = o.getInt("numQuestions"),
                    options = opts,
                    rollDigits = o.getInt("rollDigits"),
                    positiveMark = o.getDouble("positiveMark"),
                    negativeMark = o.getDouble("negativeMark"),
                    answerKey = keyMap,
                    createdAt = o.optLong("createdAt", System.currentTimeMillis())
                )
            )
        }
        return list.sortedByDescending { it.createdAt }
    }

    fun saveExam(context: Context, exam: ExamConfig) {
        val current = loadExams(context).filterNot { it.id == exam.id }.toMutableList()
        current.add(0, exam)
        val arr = JSONArray()
        current.forEach { e ->
            val o = JSONObject().apply {
                put("id", e.id)
                put("name", e.name)
                put("numQuestions", e.numQuestions)
                put("options", JSONArray(e.options))
                put("rollDigits", e.rollDigits)
                put("positiveMark", e.positiveMark)
                put("negativeMark", e.negativeMark)
                put("answerKey", JSONObject().apply {
                    e.answerKey.forEach { (k, v) -> put(k.toString(), v) }
                })
                put("createdAt", e.createdAt)
            }
            arr.put(o)
        }
        File(context.filesDir, EXAMS_FILE).writeText(arr.toString())
    }

    fun deleteExam(context: Context, examId: String) {
        val exams = loadExams(context).filterNot { it.id == examId }
        val arr = JSONArray()
        exams.forEach { e ->
            arr.put(JSONObject().apply {
                put("id", e.id)
                put("name", e.name)
                put("numQuestions", e.numQuestions)
                put("options", JSONArray(e.options))
                put("rollDigits", e.rollDigits)
                put("positiveMark", e.positiveMark)
                put("negativeMark", e.negativeMark)
                put("answerKey", JSONObject().apply {
                    e.answerKey.forEach { (k, v) -> put(k.toString(), v) }
                })
                put("createdAt", e.createdAt)
            })
        }
        File(context.filesDir, EXAMS_FILE).writeText(arr.toString())

        val remainingResults = loadAllResults(context).filterNot { it.examId == examId }
        writeResults(context, remainingResults)
    }

    fun loadResultsForExam(context: Context, examId: String): List<StudentResult> =
        loadAllResults(context).filter { it.examId == examId }.sortedByDescending { it.scannedAt }

    fun saveResult(context: Context, result: StudentResult) {
        val all = loadAllResults(context).filterNot { it.id == result.id }.toMutableList()
        all.add(0, result)
        writeResults(context, all)
    }

    fun deleteResult(context: Context, resultId: String) {
        val all = loadAllResults(context).filterNot { it.id == resultId }
        writeResults(context, all)
    }

    private fun loadAllResults(context: Context): List<StudentResult> {
        val file = File(context.filesDir, RESULTS_FILE)
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        val list = mutableListOf<StudentResult>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val ansObj = o.getJSONObject("answers")
            val ansMap = linkedMapOf<Int, String>()
            ansObj.keys().forEach { k -> ansMap[k.toInt()] = ansObj.getString(k) }

            list.add(
                StudentResult(
                    id = o.getString("id"),
                    examId = o.getString("examId"),
                    rollNumber = o.getString("rollNumber"),
                    answers = ansMap,
                    score = o.getDouble("score"),
                    maxScore = o.getDouble("maxScore"),
                    percentage = o.getDouble("percentage"),
                    correctCount = o.getInt("correctCount"),
                    wrongCount = o.getInt("wrongCount"),
                    blankCount = o.getInt("blankCount"),
                    debugImagePath = o.optString("debugImagePath").takeIf { it.isNotBlank() },
                    studentName = o.optString("studentName"),
                    scannedAt = o.optLong("scannedAt", System.currentTimeMillis())
                )
            )
        }
        return list
    }

    private fun writeResults(context: Context, results: List<StudentResult>) {
        val arr = JSONArray()
        results.forEach { r ->
            arr.put(JSONObject().apply {
                put("id", r.id)
                put("examId", r.examId)
                put("rollNumber", r.rollNumber)
                put("answers", JSONObject().apply {
                    r.answers.forEach { (k, v) -> put(k.toString(), v) }
                })
                put("score", r.score)
                put("maxScore", r.maxScore)
                put("percentage", r.percentage)
                put("correctCount", r.correctCount)
                put("wrongCount", r.wrongCount)
                put("blankCount", r.blankCount)
                put("debugImagePath", r.debugImagePath ?: "")
                put("studentName", r.studentName)
                put("scannedAt", r.scannedAt)
            })
        }
        File(context.filesDir, RESULTS_FILE).writeText(arr.toString())
    }

    fun exportCsv(context: Context, exam: ExamConfig, results: List<StudentResult>): File {
        val sb = StringBuilder("\uFEFF")
        sb.append("رقم الجلوس,الدرجة,الدرجة العظمى,النسبة %,صحيح,خطأ,متروك\n")
        results.forEach { r ->
            sb.append("${r.rollNumber},${r.score},${r.maxScore},${r.percentage},${r.correctCount},${r.wrongCount},${r.blankCount}\n")
        }
        val csvFile = File(context.cacheDir, "grades_${exam.id}.csv")
        csvFile.writeText(sb.toString(), Charsets.UTF_8)
        return csvFile
    }
}
