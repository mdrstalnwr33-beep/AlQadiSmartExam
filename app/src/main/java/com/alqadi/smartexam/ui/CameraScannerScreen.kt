package com.alqadi.smartexam.ui

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.alqadi.smartexam.model.ExamConfig
import com.alqadi.smartexam.model.StudentResult
import com.alqadi.smartexam.omr.OmrProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScannerScreen(
    exam: ExamConfig,
    onBack: () -> Unit,
    onScanned: (StudentResult) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()

    var hasCameraPermission by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var isProcessing by remember { mutableStateOf(false) }
    var torchEnabled by remember { mutableStateOf(false) }
    var cornersStable by remember { mutableStateOf(false) }
    var cameraRef by remember { mutableStateOf<Camera?>(null) }

    val imageCapture = remember {
        ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()
    }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val autoCaptureStarted = remember { AtomicBoolean(false) }
    val stableFrames = remember { AtomicInteger(0) }
    val lastAnalysisAt = remember { AtomicLong(0L) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            analysisExecutor.shutdown()
        }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        hasCameraPermission = it
    }
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    fun processBitmap(bmp: Bitmap) {
        scope.launch {
            isProcessing = true
            try {
                val res = withContext(Dispatchers.Default) { OmrProcessor.scanAndGrade(context, bmp, exam) }
                onScanned(res)
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "فشل المسح", Toast.LENGTH_LONG).show()
                autoCaptureStarted.set(false)
                stableFrames.set(0)
                cornersStable = false
            } finally {
                if (!bmp.isRecycled) bmp.recycle()
                isProcessing = false
            }
        }
    }

    fun captureNow(alreadyLocked: Boolean = false) {
        if (!alreadyLocked && !autoCaptureStarted.compareAndSet(false, true)) return
        isProcessing = true
        imageCapture.takePicture(cameraExecutor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val bmp = imageProxyToRotatedBitmap(image)
                image.close()
                processBitmap(bmp)
            }
            override fun onError(exception: ImageCaptureException) {
                autoCaptureStarted.set(false)
                stableFrames.set(0)
                ContextCompat.getMainExecutor(context).execute {
                    isProcessing = false
                    cornersStable = false
                    Toast.makeText(context, "تعذر التقاط الورقة: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.let(::processBitmap)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("المسح التلقائي: ${exam.name}") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") } },
                actions = {
                    IconButton(onClick = { torchEnabled = !torchEnabled; cameraRef?.cameraControl?.enableTorch(torchEnabled) }) {
                        Icon(if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff, contentDescription = "الفلاش")
                    }
                    IconButton(onClick = { galleryLauncher.launch("image/*") }) {
                        Icon(Icons.Default.PhotoLibrary, contentDescription = "من المعرض")
                    }
                }
            )
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (hasCameraPermission) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER }
                        val providerFuture = ProcessCameraProvider.getInstance(ctx)
                        providerFuture.addListener({
                            val provider = providerFuture.get()
                            val preview = Preview.Builder().build().also { it.surfaceProvider = previewView.surfaceProvider }
                            val analysis = ImageAnalysis.Builder()
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .setTargetResolution(android.util.Size(960, 720))
                                .build()
                            analysis.setAnalyzer(analysisExecutor) { frame ->
                                val now = System.currentTimeMillis()
                                if (!isProcessing && !autoCaptureStarted.get() && now - lastAnalysisAt.get() >= 300L) {
                                    lastAnalysisAt.set(now)
                                    var detected = false
                                    try {
                                        val frameBitmap = imageProxyToRotatedBitmap(frame)
                                        detected = OmrProcessor.hasFourValidCornerMarkers(frameBitmap)
                                        frameBitmap.recycle()
                                    } catch (_: Exception) {
                                        detected = false
                                    }
                                    val count = if (detected) stableFrames.incrementAndGet() else stableFrames.set(0).let { 0 }
                                    val ready = count >= 3 && autoCaptureStarted.compareAndSet(false, true)
                                    ContextCompat.getMainExecutor(ctx).execute {
                                        cornersStable = count >= 3
                                        if (ready && !isProcessing) {
                                            // Latch is already set by the analyzer to prevent duplicate captures.
                                            isProcessing = true
                                            try {
                                                ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90).startTone(ToneGenerator.TONE_PROP_ACK, 180)
                                                val vibrator = if (Build.VERSION.SDK_INT >= 31) ctx.getSystemService(Vibrator::class.java)
                                                    else @Suppress("DEPRECATION") (ctx.getSystemService(android.content.Context.VIBRATOR_SERVICE) as Vibrator)
                                                if (Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createOneShot(120, VibrationEffect.DEFAULT_AMPLITUDE))
                                                else @Suppress("DEPRECATION") vibrator?.vibrate(120)
                                            } catch (_: Exception) { }
                                            captureNow(alreadyLocked = true)
                                        }
                                    }
                                }
                                frame.close()
                            }
                            provider.unbindAll()
                            cameraRef = provider.bindToLifecycle(
                                lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis
                            )
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    }
                )

                Box(
                    Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 48.dp)
                        .border(BorderStroke(3.dp, if (cornersStable) Color(0xFF00E676) else Color(0xFFFFC107)))
                )
                Text(
                    text = if (isProcessing) "جارٍ تصحيح الورقة..." else if (cornersStable) "تم التعرف على المربعات الأربع — جارٍ الالتقاط" else "حرّك الورقة حتى تظهر المربعات الأربع داخل الإطار",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 16.dp)
                )
                FloatingActionButton(
                    onClick = { if (!isProcessing) captureNow() },
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp)
                ) {
                    if (isProcessing) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.CameraAlt, contentDescription = "التقاط وتصحيح يدوي")
                }
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("التطبيق بحاجة إلى إذن الكاميرا لمسح أوراق الإجابة")
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { permLauncher.launch(Manifest.permission.CAMERA) }) { Text("منح الإذن") }
                }
            }
        }
    }
}

private fun imageProxyToRotatedBitmap(image: ImageProxy): Bitmap {
    val raw = image.toBitmap()
    val deg = image.imageInfo.rotationDegrees
    if (deg == 0) return raw
    val matrix = Matrix().apply { postRotate(deg.toFloat()) }
    return Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, matrix, true).also { raw.recycle() }
}
