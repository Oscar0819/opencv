package com.oscar0819.opencv

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.pow
import kotlin.math.sqrt

class EditableCornerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : AppCompatImageView(context, attrs) {

    private var corners: List<PointF> = emptyList()

    private val linePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 8f
        style = Paint.Style.STROKE
    }

    private val cornerPaint = Paint().apply {
        color = Color.RED
        strokeWidth = 30f
        style = Paint.Style.FILL
    }

    // 외부에서 꼮짓점 목록을 설정하는 함수
    fun setCorners(detectedCorners: List<PointF>) {
        // 꼭짓점을 시계 방향 또는 반시계 방향을 정렬하는 것이 중요
        this.corners = sortCorners(detectedCorners)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (corners.size == 4) {
            val pts = corners.flatMap { listOf(it.x, it.y) }.toFloatArray()
            imageMatrix.mapPoints(pts)

            // 꼭짓점들을 잇는 선(사각형) 그리기
            for (i in 0..3) {
                val startIdx = i * 2
                val endIdx = ((i + 1) % 4) * 2
                canvas.drawLine(pts[startIdx], pts[startIdx + 1], pts[endIdx], pts[endIdx + 1], linePaint)
            }

            // 4개 꼭짓점 그리
            for (i in 0..3) {
                canvas.drawPoint(pts[i * 2], pts[i * 2 + 1], cornerPaint)
            }
        }
    }

    // 꼭짓점들을 시계방향으로 정렬하는 헬퍼 함수
    private fun sortCorners(points: List<PointF>): List<PointF> {
        if (points.size != 4) return points
        val center = PointF(points.sumOf { it.x.toDouble() }.toFloat() / 4, points.sumOf {
            it.y.toDouble() }.toFloat() / 4)
        return points.sortedWith { a, b ->
            val angleA = Math.atan2((a.y - center.y).toDouble(), (a.x - center.x).toDouble())
            val angleB = Math.atan2((b.y - center.y).toDouble(), (b.x - center.x).toDouble())
            angleA.compareTo(angleB)
        }
    }

    private var activeCornerIndex: Int = -1 // 현재 사용자가 잡고 있는 꼭짓점의 인덱스
    private val touchTolerance = 40f // 터치 민감도 (픽셀 단위)

    // 터치 이벤트 처리
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // drawable이 없거나 꼭짓점이 설정되지 않았으면 무시
        if (drawable == null || corners.isEmpty()) return super.onTouchEvent(event)

        val touchX = event.x
        val touchY = event.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                activeCornerIndex = findClosestCornerIndex(touchX, touchY)
                return activeCornerIndex != -1 // 꼭짓점을 잡았으면 true 반환
            }
            MotionEvent.ACTION_MOVE -> {
                if (activeCornerIndex != -1) {
                    // 화면 좌표를 원본 이미지 좌표로 변환
                    val invertedMatrix = Matrix()
                    imageMatrix.invert(invertedMatrix)
                    val imageCoords = floatArrayOf(touchX, touchY)
                    invertedMatrix.mapPoints(imageCoords)

                    // 해당 꼭짓점의 위치 업데이트
                    corners[activeCornerIndex].set(imageCoords[0], imageCoords[1])

                    invalidate()
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                activeCornerIndex = -1 // 선택 해제
            }
        }
        return super.onTouchEvent(event)
    }

    // 화면 터치 좌표(x,y)와 가장 가까운 꼭짓점의 인덱스를 찾는 함수
    private fun findClosestCornerIndex(x: Float, y: Float): Int {
        var closestIndex = -1
        var minDistance = Float.MAX_VALUE

        // 꼭짓점들의 현재 화면 좌표 계산
        val mappedCorners = corners.map {
            val point = floatArrayOf(it.x, it.y)
            imageMatrix.mapPoints(point)
            PointF(point[0], point[1])
        }

        mappedCorners.forEachIndexed { index, corner ->
            val distance = sqrt((x - corner.x).pow(2) + (y - corner.y).pow(2))
            if (distance < minDistance) {
                minDistance = distance
                closestIndex = index
            }
        }

        // 터치 허용 범위(touchTolerance) 안에 있을 때만 유효
        return if (minDistance < touchTolerance) closestIndex else -1
    }

    fun getFinalCorners(): List<PointF> {
        return corners
    }
}