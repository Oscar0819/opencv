package com.oscar0819.opencv

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.oscar0819.opencv.databinding.ActivityMainBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.pow
import kotlin.math.sqrt

class MainActivity : AppCompatActivity() {

    val binding: ActivityMainBinding by lazy {
        ActivityMainBinding.inflate(layoutInflater)
    }

    init {
        if (!OpenCVLoader.initDebug()) {
            Log.e("OpenCV", "Unable to load OpenCV!")
        } else {
            Log.d("OpenCV", "OpenCV loaded Successfully!")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.button.setOnClickListener {
            openGallery()
        }

        binding.button2.setOnClickListener {
            onConfirmButtonClick()
        }

        binding.button3.setOnClickListener {
            startActivity(Intent(this@MainActivity, DebugActivity::class.java))
        }
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    // 연산량이 많으니 백그라운드 스레드로 호출하기
    // Bitmap을 입력받아 선 좌표 리스트(Point 쌍)를 반환하는 함수.
    private fun detectLines(bitmap: Bitmap): List<Pair<Point, Point>> {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY) // 흑백으로 변환

        val blurredMat = Mat()
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0) // 노이즈 제거

        val edgesMat = Mat()
        Imgproc.Canny(blurredMat, edgesMat, 50.0, 150.0) // 가장자리 감지

        val lines = Mat()
        Imgproc.HoughLinesP(
            edgesMat,           // 엣지 이미지
            lines,              // 결과 선 저장 Mat
            1.0,           // rho (거리) 해상도
            Math.PI / 180,// theta (각도) 해상도
            80,        // threshold (선을 감지하기 위한 최소 교차점 수)
            50.0,  // minLineLength (최소 선 길이)
            10.0     // maxLineGap (선 위의 점들 사이의 최대 간격)
        )

        val lineList = mutableListOf<Pair<Point, Point>>()

        for (i in 0 until lines.rows()) {
            val line = lines.get(i, 0)
            val x1 = line[0]
            val y1 = line[1]
            val x2 = line[2]
            val y2 = line[3]

            // 문자 구분선은 대부분 수평/수직이므로, 이를 필터합니다.
            val angle = Math.atan2(y2 - y1, x2 - x1) * 180 / Math.PI
            val tolerance = 5.0 // 5도 이내의 오차 허용

            // 수평선 (angle이 0도 또는 180도에 가까움)
            if (Math.abs(angle) < tolerance || Math.abs(angle - 180) < tolerance) {
                lineList.add(Pair(Point(x1,y1), Point(x2, y2)))
            }
            // 수직선 (angle이 90도 또는 -90도에 가까움)
            else if (Math.abs(Math.abs(angle) - 90) < tolerance) {
                lineList.add(Pair(Point(x1,y1), Point(x2, y2)))
            }
        }

        mat.release()
        grayMat.release()
        blurredMat.release()
        edgesMat.release()
        lines.release()

        return lineList
    }

    // 연산량이 많으니 백그라운드 스레드로 호출하기
    private fun findDocumentCorners(bitmap: Bitmap): List<PointF>? {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY) // 흑백으로 변환

        val kernelSize = getDynamicBlurKernelSize(mat.width())
        Log.d("BlurParams", "동적 설정된 블러 커널 크기 : $kernelSize")

        val blurredMat = Mat()
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(kernelSize.toDouble(), kernelSize.toDouble()), 0.0) // 노이즈 제거

        /**
         * Otsu의 이진화를 사용하여 최적의 임계값 계산
         * threshold 함수의 반환값이 Otsu가 계산한 임계값
         * 세번째 인자(thresh)는 0으로 둬도 Otsu 플래그 때문에 자동으로 계산됨.
         * 네번째 인자(maxval)는 255로 설정.
         * 이진화된 이미지를 저장할 dummyMat를 추가. 이 Mat 자체는 사용하지 않음.
         */
        val dummyMat = Mat()
        val otsuThreshold = Imgproc.threshold(
            blurredMat,
            dummyMat,
            0.0,
            255.0,
            Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU
        )
        dummyMat.release() // 더 이상 필요 없으므로 메모리 해제

        Log.d("Otsu", "자동으로 계산된 최적 임계값 : $otsuThreshold")

        val cannyThreshold1 = otsuThreshold * 0.5 // 낮은 임계값은 Otsu 값의 절반
        val cannyThreshold2 = otsuThreshold       // 높은 임계값은 Otsu 값 자체

        Log.d("CannyParams", "동적 설정된 Canny 임계값 : $cannyThreshold1, $cannyThreshold2")

        val edgesMat = Mat()
        // 파라미터는 이미지에 따라 조절
//        Imgproc.Canny(blurredMat, edgesMat, 50.0, 200.0) // 가장자리 감지
//        Imgproc.Canny(blurredMat, edgesMat, 10.0, 100.0, 3, true) // 가장자리 감지
        Imgproc.Canny(blurredMat, edgesMat, cannyThreshold1, cannyThreshold2)

        // 닫힘 연산 추가
        // 닫힘 연산에 사용할 커널 생성. 커널 크기가 틈을 메우는 강도를 결정합니다.
//        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(5.0, 5.0))
        // 닫힘 연산을 적용하여 끊어진 엣지를 연결합니다.
//        Imgproc.morphologyEx(edgesMat, edgesMat, Imgproc.MORPH_CLOSE, kernel)

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edgesMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        ) // 윤곽선 찾기

        // 최소 면적을 전체 이미지의 일정 비율(예: 5%) 이상으로 설정
        val minAreaThreshold = mat.total() * 0.05 // mat.total() = 가로 * 세로 픽셀 수

        if (contours.isEmpty()) {
            mat.release()
            grayMat.release()
            blurredMat.release()
            edgesMat.release()
            hierarchy.release()
            return null
        }

        // 가장 큰 윤곽선을 찾기 위한 변수
        var maxArea = -1.0
        var biggestContour: MatOfPoint? = null

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > minAreaThreshold) { // 너무 작은 윤곽선은 무시
                val curve = MatOfPoint2f(*contour.toArray())
                val approxCurve = MatOfPoint2f()
                val peri = Imgproc.arcLength(curve, true)
                Imgproc.approxPolyDP(curve, approxCurve, 0.02 * peri, true) // 윤곽선 근사화

                // 근사화된 윤곽선의 꼭짓점이 4개이면 사각형이므로 후보로 선정
                if (approxCurve.total() == 4L && area > maxArea) {
                    maxArea = area
                    biggestContour = MatOfPoint(*approxCurve.toArray())
                }
                curve.release()
                approxCurve.release()
            }
            contour.release()
        }

        mat.release()
        grayMat.release()
        blurredMat.release()
        edgesMat.release()
        hierarchy.release()

        return biggestContour?.toList()?.map { PointF(it.x.toFloat(), it.y.toFloat()) }
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // URI를 비트맵으로 변환
//            val inputStream = contentResolver.openInputStream(it)
//            val bitmap = BitmapFactory.decodeStream(inputStream)
            val bitmap = getCorrectlyOrientedBitmap(context = this@MainActivity, it) ?: return@registerForActivityResult

            binding.ecv.setImageBitmap(bitmap)

            // 문서 가장자리 좌표 찾기 함수 호출
            val cornerPoints = findDocumentCorners(bitmap)

            if (cornerPoints != null) {
                Log.d("DocumentCorners", "Found corners: $cornerPoints")
                // TODO: 찾은 좌표를 사용하여 이미지 위에 그리거나 다른 작업 수행

                binding.ecv.setCorners(cornerPoints)
            } else {
                Log.d("DocumentCorners", "Could not find document corners.")
            }
        }
    }

    fun cropAndStraightenImage(originalBitmap: Bitmap, finalCorners: List<PointF>): Bitmap {
        // 1. 원본 이미지 Mat 생성
        val inputMat = Mat()
        Utils.bitmapToMat(originalBitmap, inputMat)

        // 2. 소스 꼭짓점(수정된 4개)과 목적지 꼭짓점(결과 이미지의 4개) 정의
        val sourcePoints = MatOfPoint2f()
        sourcePoints.fromList(finalCorners.map { Point(it.x.toDouble(), it.y.toDouble()) })

        // 3. 결과 이미지의 크기 계산
        // 좌상, 우상, 좌하, 우하 꼭짓점을 찾습니다.
        val tl = finalCorners[0] // 이미 정렬되었으므로 인덱스로 접근 가능 (정렬 방식에 따라 조정 필요)
        val tr = finalCorners[1]
        val br = finalCorners[2]
        val bl = finalCorners[3]

        val widthA = sqrt((br.x - bl.x).pow(2) + (br.y - bl.y).pow(2).toDouble())
        val widthB = sqrt((tr.x - tl.x).pow(2) + (tr.y - tl.y).pow(2).toDouble())
        val maxWidth = maxOf(widthA, widthB)

        val heightA = sqrt((tr.x - br.x).pow(2) + (tr.y - br.y).pow(2).toDouble())
        val heightB = sqrt((tl.x - bl.x).pow(2) + (tl.y - bl.y).pow(2).toDouble())
        val maxHeight = maxOf(heightA, heightB)

        val destSize = Size(maxWidth, maxHeight)

        val destPoints = MatOfPoint2f(
            Point(0.0, 0.0),                                // 좌상
            Point(destSize.width - 1, 0.0),                 // 우상
            Point(destSize.width - 1, destSize.height - 1), // 우하
            Point(0.0, destSize.height - 1)                 // 좌하
        )

        // 4. 원근 변환 행렬 계산
        val perspectiveTransform = Imgproc.getPerspectiveTransform(sourcePoints, destPoints)

        // 5. 원근 변환 적용
        val outputMat = Mat()
        Imgproc.warpPerspective(inputMat, outputMat, perspectiveTransform, destSize)

        // 6. 결과 Mat를 Bitmap으로 변환
        val outputBitmap = Bitmap.createBitmap(outputMat.cols(), outputMat.rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(outputMat, outputBitmap)

        inputMat.release()
        outputMat.release()
        sourcePoints.release()
        destPoints.release()
        perspectiveTransform.release()

        return outputBitmap
    }

    private fun onConfirmButtonClick() {
        val ecv = binding.ecv
        val finalCorners = ecv.getFinalCorners()
        if (finalCorners.size == 4) {
            val originalBitmap = (ecv.drawable as BitmapDrawable).bitmap

            lifecycleScope.launch(Dispatchers.Default) {
                val croppedBitmap = cropAndStraightenImage(originalBitmap, finalCorners)

                withContext(Dispatchers.Main) {
                    ecv.setImageBitmap(croppedBitmap)
                }
            }
        }
    }

}