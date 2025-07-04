package com.oscar0819.opencv

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.IOException
import java.io.InputStream

fun getCorrectlyOrientedBitmap(context: Context, uri: Uri): Bitmap? {
    try {
        // 비트맵 옵션만 먼저 읽기
        context.contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeStream(inputStream, null, options)
            // 여기서 이미지 리사이징 로직을 추가할 수 있음
        }

        // 실제 비트맵 디코딩
        val bitmap = context.contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
            BitmapFactory.decodeStream(inputStream)
        } ?: return null // 스트림 열기 실패 또는 디코딩 실패 시 null 반환

        // Exif 정보로 회전 각도 가져오기
        val rotationAngle = context.contentResolver.openInputStream(uri)?.use { inputStream: InputStream ->
            val exif = ExifInterface(inputStream)
            val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)

            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0 // Exif 정보 읽기 실패 시 0도(회전 없음)로 간주

        // 각도만큼 비트맵 회전시키기
        if (rotationAngle == 0) {
            return bitmap
        }

        val matrix = Matrix().apply {
            postRotate(rotationAngle.toFloat())
        }

        val rotatedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        // 원본 비트맵은 회전 후 더 이상 필요 없다면 recycle() 하는 것이 메모리 관리에 좋음
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }

        return rotatedBitmap
    } catch (e: Exception) {
        e.printStackTrace()
        return null
    }

//    var inputStream = context.contentResolver.openInputStream(uri) ?: return null
//
//    // 1. 비트맵 옵션만 먼저 읽어서 이미지 크기 확인 (OOM 방지)
//    val options = BitmapFactory.Options().apply {
//        inJustDecodeBounds = true
//    }
//    BitmapFactory.decodeStream(inputStream, null, options)
//    inputStream.close()
//
//    // 이미지 리사이징 로직
//    //
//    options.inJustDecodeBounds = false
//
//    // 실제 비트맵 디코딩
//    inputStream = context.contentResolver.openInputStream(uri) ?: return null
//    val bitmap = BitmapFactory.decodeStream(inputStream, null, options) ?: return null
//    inputStream.close()
//
//    // EXIF 정보로 회전 각도 가져오기
//    // TODO close 해야함
//    inputStream = context.contentResolver.openInputStream(uri) ?: return bitmap // EXIF 못 읽으면 원본 반환
//    val exif: ExifInterface
//    try {
//        exif = ExifInterface(inputStream)
//    } catch (e: IOException) {
//        e.printStackTrace()
//        return bitmap // EXIF 읽기 실패 시 원본 반환
//    }
//    val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
//
//    val rotationAngle = when (orientation) {
//        ExifInterface.ORIENTATION_ROTATE_90 -> 90
//        ExifInterface.ORIENTATION_ROTATE_180 -> 180
//        ExifInterface.ORIENTATION_ROTATE_270 -> 270
//        else -> 0
//    }
//
//    // 각도만큼 비트맵 회전시키기
//    if (rotationAngle == 0) {
//        return bitmap
//    }
//    val matrix = Matrix().apply {
//        postRotate(rotationAngle.toFloat())
//    }
//    return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
}