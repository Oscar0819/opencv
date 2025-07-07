package com.oscar0819.opencv

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