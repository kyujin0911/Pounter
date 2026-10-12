package io.github.kyujin0911.pounter.model

import kotlin.math.min

data class PhotoGeometry(val imageWidth: Int,val imageHeight: Int,val viewportWidth: Float,val viewportHeight: Float,val zoom: Float=1f,val panX: Float=0f,val panY: Float=0f) {
    val fit: Float = min(viewportWidth/imageWidth,viewportHeight/imageHeight)
    val drawnWidth: Float = imageWidth*fit*zoom
    val drawnHeight: Float = imageHeight*fit*zoom
    val left: Float = (viewportWidth-drawnWidth)/2f+panX
    val top: Float = (viewportHeight-drawnHeight)/2f+panY
    fun imagePoint(x: Float,y: Float): Pair<Float,Float>? {
        val nx=(x-left)/drawnWidth; val ny=(y-top)/drawnHeight
        return if(nx in 0f..1f && ny in 0f..1f) nx to ny else null
    }
    fun screenPoint(x: Float,y: Float) = (left+x*drawnWidth) to (top+y*drawnHeight)
    fun clampPan(x: Float,y: Float): Pair<Float,Float> {
        val limitX=((drawnWidth-viewportWidth)/2).coerceAtLeast(0f)
        val limitY=((drawnHeight-viewportHeight)/2).coerceAtLeast(0f)
        return x.coerceIn(-limitX,limitX) to y.coerceIn(-limitY,limitY)
    }
}
