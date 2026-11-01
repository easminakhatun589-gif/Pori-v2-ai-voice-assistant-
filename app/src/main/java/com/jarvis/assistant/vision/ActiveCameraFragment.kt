package com.jarvis.assistant.vision

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.jarvis.assistant.audio.PariRealVoiceSpeaker
import com.jarvis.assistant.ui.home.MainActivity
import com.jarvis.assistant.ui.home.MainViewModel
import com.jarvis.assistant.util.HapticFeedbackHelper
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ActiveCameraFragment : Fragment() {

    private val viewModel: MainViewModel
        get() = (requireActivity() as MainActivity).viewModel

    private var previewView: PreviewView? = null
    private var permissionCard: LinearLayout? = null
    private lateinit var tvCameraStatus: TextView
    private lateinit var tvDetectedResult: TextView
    private lateinit var btnScreenShare: Button
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var cameraExecutor: ExecutorService? = null
    private var latestOcrText: String = ""
    private var lastOcrTimestamp: Long = 0L

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionCard?.visibility = View.GONE
            startCameraPreview()
        } else {
            permissionCard?.visibility = View.VISIBLE
            tvCameraStatus.text = "● PERMISSION NEEDED"
            tvCameraStatus.setTextColor(Color.parseColor("#F59E0B"))
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val ctx = requireContext()
        val density = resources.displayMetrics.density
        fun dp(v: Int): Int = (v * density).toInt()

        cameraExecutor = Executors.newSingleThreadExecutor()

        val root = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#0B1120"))
        }

        // 1. Top Header Bar
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(12))
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        val titleCol = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        val tvTitle = TextView(ctx).apply {
            text = "📷 Active Camera (একটিভ ক্যামেরা)"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }

        tvCameraStatus = TextView(ctx).apply {
            text = "● AI VISION & OCR READY"
            textSize = 11.5f
            setTextColor(Color.parseColor("#22C55E"))
            setPadding(0, dp(2), 0, 0)
        }

        titleCol.addView(tvTitle)
        titleCol.addView(tvCameraStatus)

        val btnFlipLens = Button(ctx).apply {
            text = "🔄 Flip Camera"
            isAllCaps = false
            textSize = 11.5f
            setTextColor(Color.WHITE)
            minHeight = dp(42)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(12).toFloat()
                setStroke(dp(1), Color.parseColor("#38BDF8"))
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                    CameraSelector.LENS_FACING_FRONT
                } else {
                    CameraSelector.LENS_FACING_BACK
                }
                startCameraPreview()
            }
        }

        header.addView(titleCol)
        header.addView(btnFlipLens)
        root.addView(header)

        // 2. Center CameraX Stage Frame
        val cameraStage = FrameLayout(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                setMargins(dp(14), dp(10), dp(14), dp(10))
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(20).toFloat()
                setStroke(dp(2), Color.parseColor("#38BDF8"))
            }
            clipToOutline = true
        }

        val pv = PreviewView(ctx).apply {
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        previewView = pv
        cameraStage.addView(pv)

        // Permission Overlay Card inside Camera Stage
        val permCard = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(24), dp(24), dp(24), dp(24))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#E60F172A"))
            visibility = View.GONE
        }

        val tvPermTitle = TextView(ctx).apply {
            text = "📷 ক্যামেরা পারমিশন প্রয়োজন\n(Camera Access Required for Live AI Vision)"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
        }

        val btnGrantPerm = Button(ctx).apply {
            text = "✓ Allow Camera Access"
            isAllCaps = false
            textSize = 13.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(14)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        permCard.addView(tvPermTitle)
        permCard.addView(btnGrantPerm)
        permissionCard = permCard
        cameraStage.addView(permCard)

        root.addView(cameraStage)

        // 3. Bottom AI Vision Results & Controls Deck
        val bottomDeck = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(16))
            setBackgroundColor(Color.parseColor("#0F172A"))
        }

        tvDetectedResult = TextView(ctx).apply {
            text = "👁️ Live Vision: ক্যামেরার সামনে কোনো লেখা বা বস্তু ধরুন এবং 'Analyze Scene' চাপুন।"
            textSize = 13.5f
            setTextColor(Color.parseColor("#BAE6FD"))
            maxLines = 4
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(14).toFloat()
                setStroke(dp(1), Color.parseColor("#334155"))
            }
        }
        bottomDeck.addView(tvDetectedResult)

        val actionsRow = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(12)
            }
        }

        val btnAnalyze = Button(ctx).apply {
            text = "👁️ Analyze & Speak"
            isAllCaps = false
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f).apply {
                marginEnd = dp(6)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                analyzeCurrentCameraScene()
            }
        }

        btnScreenShare = Button(ctx).apply {
            text = "🖥️ Screen Share"
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
                marginEnd = dp(6)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#0D9488"))
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                ScreenCaptureManager.getInstance().toggleSharing()
            }
        }

        val btnSendToChat = Button(ctx).apply {
            text = "💬 Ask in Chat"
            isAllCaps = false
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
            minHeight = dp(48)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = dp(6)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#7C3AED"))
                cornerRadius = dp(16).toFloat()
            }
            setOnClickListener {
                HapticFeedbackHelper.performControlClick(it)
                val query = if (latestOcrText.isNotBlank()) {
                    "Analyze this camera text: $latestOcrText"
                } else {
                    "ক্যামেরা ভিশন দিয়ে আশেপাশের দৃশ্য বিশ্লেষণ করো"
                }
                viewModel.sendChatMessage(ctx, query, enableWebSearch = false)
                (activity as? MainActivity)?.selectTab(MainActivity.TAB_CHAT)
            }
        }

        actionsRow.addView(btnAnalyze)
        actionsRow.addView(btnScreenShare)
        actionsRow.addView(btnSendToChat)
        bottomDeck.addView(actionsRow)

        root.addView(bottomDeck)
        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkCameraPermissionAndStart()

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                ScreenCaptureManager.getInstance().isSharing.collect { sharing ->
                    btnScreenShare.text = if (sharing) "⏹ Stop Screen" else "🖥️ Screen Share"
                }
            }
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden && isAdded) {
            checkCameraPermissionAndStart()
        }
    }

    private fun checkCameraPermissionAndStart() {
        val ctx = context ?: return
        val granted = ContextCompat.checkSelfPermission(
            ctx,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            permissionCard?.visibility = View.GONE
            startCameraPreview()
        } else {
            permissionCard?.visibility = View.VISIBLE
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCameraPreview() {
        val ctx = context ?: return
        val pv = previewView ?: return
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            return
        }

        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        cameraProviderFuture.addListener({
            if (!isAdded) return@addListener
            try {
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(pv.surfaceProvider)
                }

                val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                val exec = cameraExecutor
                if (exec != null) {
                    imageAnalysis.setAnalyzer(exec) { imageProxy ->
                        processCameraFrame(imageProxy, textRecognizer)
                    }
                }

                val selector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()

                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )

                val lensName = if (lensFacing == CameraSelector.LENS_FACING_BACK) "BACK LENS" else "FRONT LENS"
                tvCameraStatus.text = "● LIVE CAMERA ($lensName)"
                tvCameraStatus.setTextColor(Color.parseColor("#22C55E"))
            } catch (e: Exception) {
                tvCameraStatus.text = "● CAMERA READY (${e.javaClass.simpleName})"
                tvCameraStatus.setTextColor(Color.parseColor("#38BDF8"))
            }
        }, ContextCompat.getMainExecutor(ctx))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun processCameraFrame(
        imageProxy: ImageProxy,
        recognizer: com.google.mlkit.vision.text.TextRecognizer
    ) {
        val now = System.currentTimeMillis()
        if (now - lastOcrTimestamp < 1200L) {
            imageProxy.close()
            return
        }
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            lastOcrTimestamp = now
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            recognizer.process(image)
                .addOnSuccessListener { visionText ->
                    val detected = visionText.text.trim()
                    if (detected.isNotBlank() && isAdded) {
                        latestOcrText = detected
                        val snippet = detected.replace("\n", " ").take(120)
                        tvDetectedResult.text = "📝 OCR Detected: $snippet"
                        VisionAnalysisModule.getInstance(requireContext()).updateScene(
                            VisionAnalysisModule.SceneCategory.GENERAL,
                            snippet
                        )
                    }
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }

    private fun analyzeCurrentCameraScene() {
        val ctx = context ?: return
        val summary = if (latestOcrText.isNotBlank()) {
            "ক্যামেরায় লেখা পাওয়া গেছে: ${latestOcrText.take(160)}"
        } else {
            "একটিভ ক্যামেরা ভিশন চালু আছে। আশেপাশের দৃশ্য পরিষ্কার দেখা যাচ্ছে। আপনি যেকোনো বস্তু বা লেখার ছবি দেখিয়ে প্রশ্ন করতে পারেন।"
        }
        tvDetectedResult.text = "👁️ AI Vision: $summary"
        VisionAnalysisModule.getInstance(ctx).updateScene(
            VisionAnalysisModule.SceneCategory.GENERAL,
            summary
        )
        PariRealVoiceSpeaker.speakInActiveModeVoice(
            context = ctx,
            text = summary
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        try {
            cameraExecutor?.shutdown()
        } catch (_: Exception) {
        }
        cameraExecutor = null
        previewView = null
        permissionCard = null
    }
}
