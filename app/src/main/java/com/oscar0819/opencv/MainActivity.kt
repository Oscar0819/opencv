package com.oscar0819.opencv

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.oscar0819.opencv.databinding.ActivityMainBinding
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

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
    private fun findDocumentCorners(bitmap: Bitmap): List<Point>? {
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)

        val grayMat = Mat()
        Imgproc.cvtColor(mat, grayMat, Imgproc.COLOR_BGR2GRAY) // 흑백으로 변환

        val blurredMat = Mat()
        Imgproc.GaussianBlur(grayMat, blurredMat, Size(5.0, 5.0), 0.0) // 노이즈 제거

        val edgesMat = Mat()
        Imgproc.Canny(blurredMat, edgesMat, 50.0, 150.0) // 가장자리 감지

//        val lines = Mat()
//        Imgproc.HoughLinesP(
//            edgesMat,           // 엣지 이미지
//            lines,              // 결과 선 저장 Mat
//            1.0,           // rho (거리) 해상도
//            Math.PI / 180,// theta (각도) 해상도
//            80,        // threshold (선을 감지하기 위한 최소 교차점 수)
//            50.0,  // minLineLength (최소 선 길이)
//            10.0     // maxLineGap (선 위의 점들 사이의 최대 간격)
//        )
//
//        val lineList = mutableListOf<Pair<Point, Point>>()
//
//        for (i in 0 until lines.rows()) {
//            val line = lines.get(i, 0)
//            val x1 = line[0]
//            val y1 = line[1]
//            val x2 = line[2]
//            val y2 = line[3]
//
//            // 문자 구분선은 대부분 수평/수직이므로, 이를 필터합니다.
//            val angle = Math.atan2(y2 - y1, x2 - x1) * 180 / Math.PI
//            val tolerance = 5.0 // 5도 이내의 오차 허용
//
//            // 수평선 (angle이 0도 또는 180도에 가까움)
//            if (Math.abs(angle) < tolerance || Math.abs(angle - 180) < tolerance) {
//                lineList.add(Pair(Point(x1,y1), Point(x2, y2)))
//            }
//            // 수직선 (angle이 90도 또는 -90도에 가까움)
//            else if (Math.abs(Math.abs(angle) - 90) < tolerance) {
//                lineList.add(Pair(Point(x1,y1), Point(x2, y2)))
//            }
//        }
//
//        mat.release()
//        grayMat.release()
//        blurredMat.release()
//        edgesMat.release()
//        lines.release()
//
//        return lineList

        val contours = mutableListOf<MatOfPoint>()
        val hierarchy = Mat()
        Imgproc.findContours(
            edgesMat, contours, hierarchy, Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
        ) // 윤곽선 찾기

        if (contours.isEmpty()) {
            return null
        }

        // 가장 큰 윤곽선을 찾기 위한 변수
        var maxArea = -1.0
        var biggestContour: MatOfPoint? = null

        for (contour in contours) {
            val area = Imgproc.contourArea(contour)
            if (area > 1000) { // 너무 작은 윤곽선은 무시
                val curve = MatOfPoint2f(*contour.toArray())
                val approxCurve = MatOfPoint2f()
                val peri = Imgproc.arcLength(curve, true)
                Imgproc.approxPolyDP(curve, approxCurve, 0.02 * peri, true) // 윤곽선 근사화

                // 근사화된 윤곽선의 꼭짓점이 4개이면 사각형이므로 후보로 선정
                if (approxCurve.total() == 4L && area > maxArea) {
                    maxArea = area
                    biggestContour = MatOfPoint(*approxCurve.toArray())
                }
            }
        }

        return biggestContour?.toList()
    }

    private val galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            // URI를 비트맵으로 변환
            val inputStream = contentResolver.openInputStream(it)
            val bitmap = BitmapFactory.decodeStream(inputStream)

            binding.imageView.setImageBitmap(bitmap)

            // 문서 가장자리 좌표 찾기 함수 호출
            val cornerPoints = findDocumentCorners(bitmap)

            if (cornerPoints != null) {
                Log.d("DocumentCorners", "Found corners: $cornerPoints")
                // TODO: 찾은 좌표를 사용하여 이미지 위에 그리거나 다른 작업 수행
            } else {
                Log.d("DocumentCorners", "Could not find document corners.")
            }
        }
    }

}