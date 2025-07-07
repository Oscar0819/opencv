package com.oscar0819.opencv

import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.SeekBar
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.oscar0819.opencv.databinding.ActivityDebugBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

class DebugActivity : AppCompatActivity() {

    val binding: ActivityDebugBinding by lazy {
        ActivityDebugBinding.inflate(layoutInflater)
    }

    private var originalBitmap: Bitmap? = null // 원본 비트맵 저장

    // 현재 SeekBar 값 저장 변수
    private var blurKernelSize = 5
    private var cannyThreshold1 = 50.0
    private var cannyThreshold2 = 150.0
    private var closeKernelSize = 5

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        openGallery()

        setupListeners()
    }

    private fun setupListeners() {
        // 라디오 그룹 리스너
        binding.radioGroupStep.setOnCheckedChangeListener { _, _ -> processImageAndUpdate() }

        // SeekBar 리스너들
        val seekBarListener = object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    updateParameters()
                    processImageAndUpdate()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }

        binding.seekBarBlurKernel.setOnSeekBarChangeListener(seekBarListener)
        binding.seekBarCannyThreshold1.setOnSeekBarChangeListener(seekBarListener)
        binding.seekBarCannyThreshold2.setOnSeekBarChangeListener(seekBarListener)
        binding.seekBarCloseKernel.setOnSeekBarChangeListener(seekBarListener)
    }


    private fun updateParameters() {
        // SeekBar 값(0~10)을 홀수 커널 사이즈(1, 3, 5...)로 변환
        blurKernelSize = binding.seekBarBlurKernel.progress * 2 + 1
        cannyThreshold1 = binding.seekBarCannyThreshold1.progress.toDouble()
        cannyThreshold2 = binding.seekBarCannyThreshold2.progress.toDouble()
        closeKernelSize = binding.seekBarCloseKernel.progress * 2 + 1

        // TextView 업데이트
        binding.tvBlurKernel.text = "블러 커널 크기: $blurKernelSize"
        binding.tvCannyThreshold1.text = "Canny 낮은 임계값: ${cannyThreshold1.toInt()}"
        binding.tvCannyThreshold2.text = "Canny 높은 임계값: ${cannyThreshold2.toInt()}"
        binding.tvCloseKernel.text = "닫힘 커널 크기: $closeKernelSize"
    }

    private fun processImageAndUpdate() {
        val bitmapToProcess = originalBitmap ?: return

        // 코루틴으로 백그라운드에서 실행
        lifecycleScope.launch(Dispatchers.Default) {
            val resultMat = Mat()
            val src = Mat()
            Utils.bitmapToMat(bitmapToProcess, src)

            val (resizedMat, scale) = resizeMatWithScale(src)

            val gray = Mat()
            Imgproc.cvtColor(resizedMat, gray, Imgproc.COLOR_BGR2GRAY)

            val blurred = Mat()
            if (blurKernelSize > 0) {
                Imgproc.GaussianBlur(gray, blurred, Size(blurKernelSize.toDouble(), blurKernelSize.toDouble()), 0.0)
            } else {
                gray.copyTo(blurred)
            }
            if (binding.radioGroupStep.checkedRadioButtonId == R.id.radioBlurred) {
                blurred.copyTo(resultMat)
            }

            val canny = Mat()
            Imgproc.Canny(blurred, canny, cannyThreshold1, cannyThreshold2)
            if (binding.radioGroupStep.checkedRadioButtonId == R.id.radioCanny) {
                canny.copyTo(resultMat)
            }

            val closed = Mat()
            if (closeKernelSize > 0) {
                val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(closeKernelSize.toDouble(), closeKernelSize.toDouble()))
                Imgproc.morphologyEx(canny, closed, Imgproc.MORPH_CLOSE, kernel)
                kernel.release()
            } else {
                canny.copyTo(closed)
            }
            if (binding.radioGroupStep.checkedRadioButtonId == R.id.radioClosed) {
                closed.copyTo(resultMat)
            }

            if (binding.radioGroupStep.checkedRadioButtonId == R.id.radioFinal) {
                // 윤곽선을 찾아 원본 이미지 위에 그리기
                val contours = mutableListOf<MatOfPoint>()
                val hierarchy = Mat()
                Imgproc.findContours(closed, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)

                // 원본 컬러 이미지에 그리기 위해 src를 복사
                resizedMat.copyTo(resultMat)
                for (contour in contours) {
                    val area = Imgproc.contourArea(contour)
                    // 최소 면적 기준 (여기서도 조절 가능하게 만들 수 있음)
                    if (area > src.total() * 0.01) {
                        Imgproc.drawContours(resultMat, listOf(contour), -1, Scalar(0.0, 255.0, 0.0), 5)
                    }
                    contour.release()
                }
                hierarchy.release()
            }

            // 최종 결과 Mat을 Bitmap으로 변환
            val resultBitmap = Bitmap.createBitmap(resultMat.cols(), resultMat.rows(), Bitmap.Config.ARGB_8888)
            // 채널 수가 1인 그레이스케일 Mat을 BGRA로 변환
            if (resultMat.channels() == 1) {
                Imgproc.cvtColor(resultMat, resultMat, Imgproc.COLOR_GRAY2BGRA)
            }
            Utils.matToBitmap(resultMat, resultBitmap)

            // 메모리 해제
            src.release()
            resizedMat.release()
            gray.release()
            blurred.release()
            canny.release()
            closed.release()
            resultMat.release()

            // UI 스레드에서 ImageView 업데이트
            withContext(Dispatchers.Main) {
                binding.debugImageView.setImageBitmap(resultBitmap)
            }
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // URI를 비트맵으로 변환
            val bitmap = getCorrectlyOrientedBitmap(context = this@DebugActivity, it) ?: return@registerForActivityResult

            originalBitmap = bitmap
            processImageAndUpdate()
        }
    }
}