package com.oscar0819.opencv

import android.graphics.Bitmap
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

// TODO 확인하기 이상항
fun getDynamicBlurKernelSize(imageWidth: Int): Int {
    // 기존 해상도
    val baseResolution = 1000.0
    // 기준 커널 크기
    val baseKernelSize = 5.0

    // 현재 이미지 해상도에 비례하여 커널 크기 계산
    val calculatedSize = (imageWidth / baseResolution * baseKernelSize).toInt()

    // 홀수로
    val oddSize = if (calculatedSize % 2 == 0) calculatedSize + 1 else calculatedSize

    return oddSize.coerceIn(3, 15)
}

fun resizeMatWithScale(mat: Mat, maxLength: Int = 1000): Pair<Mat, Double> {
    val width = mat.width()
    val height = mat.height()

    val scale = if (width > height) {
        maxLength.toDouble() / width
    } else {
        maxLength.toDouble() / height
    }

    val newWidth = (width * scale).toInt()
    val newHeight = (height * scale).toInt()

    val resizedMat = Mat()
    Imgproc.resize(mat, resizedMat, Size(newWidth.toDouble(), newHeight.toDouble()))
    return Pair(resizedMat, scale)
}

