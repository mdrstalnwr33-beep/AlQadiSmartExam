package com.alqadi.smartexam

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.alqadi.smartexam.model.ExamConfig
import com.alqadi.smartexam.model.StudentResult
import com.alqadi.smartexam.omr.ResultCardGenerator
import com.alqadi.smartexam.omr.OmrProcessor
import com.alqadi.smartexam.omr.SheetPdfGenerator
import com.alqadi.smartexam.storage.ExamRepository
import com.alqadi.smartexam.ui.CameraScannerScreen
import java.io.File
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OmrProcessor.ensureInitialized()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme { AlQadiApp() }
            }
        }
    }
}

private sealed class AppScreen {
    data object Home : AppScreen()
    data class Key(val exam: ExamConfig) : AppScreen()
    data class Scan(val exam: ExamConfig) : AppScreen()
    data class Result(val exam: ExamConfig, val result: StudentResult) : AppScreen()
    data class Results(val exam: ExamConfig) : AppScreen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlQadiApp() {
    val context = LocalContext.current
    var exams by remember { mutableStateOf(ExamRepository.loadExams(context)) }
    var screen by remember { mutableStateOf<AppScreen>(AppScreen.Home) }
    var createDialog by remember { mutableStateOf(false) }

    when (val current = screen) {
        AppScreen.Home -> Scaffold(
            topBar = { TopAppBar(title = { Text("القاضي الذكي لتصميم أوراق الإجابة", fontWeight = FontWeight.Bold) }) },
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { createDialog = true },
                    icon = { Icon(Icons.Default.Add, null) },
                    text = { Text("اختبار جديد") }
                )
            }
        ) { padding ->
            if (exams.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("أنشئ اختباراً جديداً للبدء.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(exams, key = { it.id }) { exam ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column(Modifier.weight(1f)) {
                                        Text(exam.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text("${exam.numQuestions} فقرة • ${ExamRepository.loadResultsForExam(context, exam.id).size} ورقة مصححة")
                                    }
                                    IconButton(onClick = {
                                        ExamRepository.deleteExam(context, exam.id)
                                        exams = ExamRepository.loadExams(context)
                                    }) { Icon(Icons.Default.Delete, "حذف الاختبار") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    OutlinedButton(
                                        onClick = {
                                            val pdf = SheetPdfGenerator.generatePdf(context, exam)
                                            shareFile(context, pdf, "application/pdf", "ورقة الإجابة")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Icon(Icons.Default.PictureAsPdf, null); Text("ورقة PDF") }
                                    OutlinedButton(
                                        onClick = { screen = AppScreen.Key(exam) },
                                        modifier = Modifier.weight(1f)
                                    ) { Icon(Icons.Default.Edit, null); Text("مفتاح الإجابة") }
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Button(onClick = { screen = AppScreen.Scan(exam) }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.CameraAlt, null); Text("تصحيح ورقة")
                                    }
                                    FilledTonalButton(onClick = { screen = AppScreen.Results(exam) }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Default.Assessment, null); Text("النتائج")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        is AppScreen.Key -> AnswerKeyScreen(
            exam = current.exam,
            onBack = { screen = AppScreen.Home },
            onSave = { updated ->
                ExamRepository.saveExam(context, updated)
                exams = ExamRepository.loadExams(context)
                screen = AppScreen.Home
                Toast.makeText(context, "تم حفظ مفتاح الإجابة", Toast.LENGTH_SHORT).show()
            }
        )

        is AppScreen.Scan -> CameraScannerScreen(
            exam = current.exam,
            onBack = { screen = AppScreen.Home },
            onScanned = { result ->
                ExamRepository.saveResult(context, result)
                screen = AppScreen.Result(current.exam, result)
            }
        )

        is AppScreen.Result -> ResultScreen(
            exam = current.exam,
            result = current.result,
            onBack = { screen = AppScreen.Results(current.exam) },
            onNext = { screen = AppScreen.Scan(current.exam) },
            onSave = { updated -> ExamRepository.saveResult(context, updated) }
        )

        is AppScreen.Results -> ResultsScreen(
            exam = current.exam,
            onBack = { screen = AppScreen.Home },
            onOpen = { result -> screen = AppScreen.Result(current.exam, result) }
        )
    }

    if (createDialog) {
        CreateExamDialog(
            onDismiss = { createDialog = false },
            onCreate = { exam ->
                ExamRepository.saveExam(context, exam)
                exams = ExamRepository.loadExams(context)
                createDialog = false
                screen = AppScreen.Key(exam)
            }
        )
    }
}

@Composable
private fun CreateExamDialog(onDismiss: () -> Unit, onCreate: (ExamConfig) -> Unit) {
    var name by remember { mutableStateOf("") }
    var count by remember { mutableStateOf("20") }
    var rollDigits by remember { mutableStateOf("4") }
    var optionType by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بيانات الاختبار") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("اسم الاختبار (مثال: كيمياء ثاني ثانوي نهاية الفصل)") })
                OutlinedTextField(count, { count = it.filter(Char::isDigit) }, label = { Text("عدد الفقرات") })
                OutlinedTextField(rollDigits, { rollDigits = it.filter(Char::isDigit) }, label = { Text("خانات رقم الطالب") })
                Text("نوع الإجابة")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(optionType == 0, { optionType = 0 }, label = { Text("اختيار متعدد") })
                    FilterChip(optionType == 1, { optionType = 1 }, label = { Text("صح / خطأ") })
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val n = (count.toIntOrNull() ?: 20).coerceIn(1, 100)
                val opts = if (optionType == 1) listOf("صح", "خطأ") else listOf("A", "B", "C", "D")
                onCreate(
                    ExamConfig(
                        id = UUID.randomUUID().toString(),
                        name = name.trim().ifBlank { "اختبار جديد" },
                        numQuestions = n,
                        options = opts,
                        rollDigits = (rollDigits.toIntOrNull() ?: 4).coerceIn(1, 6),
                        positiveMark = 1.0,
                        negativeMark = 0.0,
                        answerKey = emptyMap()
                    )
                )
            }) { Text("متابعة") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnswerKeyScreen(exam: ExamConfig, onBack: () -> Unit, onSave: (ExamConfig) -> Unit) {
    val keys = remember(exam.id) { mutableStateMapOf<Int, String>().apply { putAll(exam.answerKey) } }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("مفتاح الإجابة") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = { TextButton(onClick = { onSave(exam.copy(answerKey = keys.toMap())) }) { Text("حفظ") } }
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp)) {
            items((1..exam.numQuestions).toList()) { number ->
                Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
                    Text("الفقرة $number", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        exam.options.forEach { option ->
                            FilterChip(
                                selected = keys[number] == option,
                                onClick = { keys[number] = option },
                                label = { Text(option) }
                            )
                        }
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultScreen(exam: ExamConfig, result: StudentResult, onBack: () -> Unit, onNext: () -> Unit, onSave: (StudentResult) -> Unit) {
    val context = LocalContext.current
    var studentName by remember(result.id) { mutableStateOf(result.studentName) }
    val bitmap = remember(result.debugImagePath) {
        result.debugImagePath?.let { if (File(it).exists()) BitmapFactory.decodeFile(it) else null }
    }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("نتيجة التصحيح") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } }
        )
    }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(14.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = studentName,
                onValueChange = { studentName = it },
                label = { Text("اسم الطالب لبطاقة النتيجة") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Button(onClick = { onSave(result.copy(studentName = studentName.trim())) }, modifier = Modifier.fillMaxWidth()) {
                Text("حفظ اسم الطالب")
            }
            Text("رقم الجلوس: ${result.rollNumber}", style = MaterialTheme.typography.titleMedium)
            Text("الدرجة: ${result.score} / ${result.maxScore}  (${result.percentage}%)", style = MaterialTheme.typography.headlineSmall)
            Text("صحيح: ${result.correctCount} • خطأ: ${result.wrongCount} • متروك: ${result.blankCount}")
            Button(
                onClick = {
                    val namedResult = result.copy(studentName = studentName.trim())
                    onSave(namedResult)
                    val file = ResultCardGenerator.createImage(context, exam, namedResult)
                    shareFile(context, file, "image/png", "مشاركة بطاقة النتيجة عبر واتساب أو تطبيق آخر")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("مشاركة بطاقة النتيجة صورة") }
            OutlinedButton(
                onClick = {
                    val namedResult = result.copy(studentName = studentName.trim())
                    onSave(namedResult)
                    val file = ResultCardGenerator.createPdf(context, exam, namedResult)
                    shareFile(context, file, "application/pdf", "مشاركة بطاقة النتيجة PDF")
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("مشاركة بطاقة النتيجة PDF") }
            Button(onClick = onNext, modifier = Modifier.fillMaxWidth()) { Text("مسح الورقة التالية") }
            bitmap?.let {
                Image(it.asImageBitmap(), null, Modifier.fillMaxWidth())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResultsScreen(exam: ExamConfig, onBack: () -> Unit, onOpen: (StudentResult) -> Unit) {
    val context = LocalContext.current
    var results by remember(exam.id) { mutableStateOf(ExamRepository.loadResultsForExam(context, exam.id)) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("نتائج: ${exam.name}") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            actions = {
                IconButton(onClick = {
                    val file = ExamRepository.exportCsv(context, exam, results)
                    shareFile(context, file, "text/csv", "تصدير النتائج")
                }) { Icon(Icons.Default.Share, null) }
            }
        )
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(12.dp)) {
            items(results, key = { it.id }) { result ->
                Card(onClick = { onOpen(result) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text(result.studentName.ifBlank { "رقم الجلوس: ${result.rollNumber}" }, fontWeight = FontWeight.Bold)
                            if (result.studentName.isNotBlank()) Text("رقم الجلوس: ${result.rollNumber}")
                            Text("${result.score} / ${result.maxScore}  •  ${result.percentage}%")
                        }
                        IconButton(onClick = {
                            ExamRepository.deleteResult(context, result.id)
                            results = ExamRepository.loadResultsForExam(context, exam.id)
                        }) { Icon(Icons.Default.Delete, "حذف النتيجة") }
                    }
                }
            }
        }
    }
}

private fun shareFile(context: Context, file: File, mime: String, title: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, title))
}
