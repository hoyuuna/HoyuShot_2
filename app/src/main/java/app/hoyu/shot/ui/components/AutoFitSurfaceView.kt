package app.hoyu.shot.ui.components

import android.content.Context
import android.util.AttributeSet
import android.view.SurfaceView
import kotlin.math.roundToInt

class AutoFitSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : SurfaceView(context, attrs, defStyle) {

    private var aspectRatio = 0f
    private var lastFixedWidth = 0
    private var lastFixedHeight = 0

    fun setAspectRatio(width: Int, height: Int) {
        require(width > 0 && height > 0) { "Dimensions must be positive" }
        if (lastFixedWidth == width && lastFixedHeight == height) return
        lastFixedWidth = width
        lastFixedHeight = height
        aspectRatio = width.toFloat() / height.toFloat()
        holder.setFixedSize(width, height)
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        if (aspectRatio == 0f) {
            setMeasuredDimension(width, height)
        } else {
            val newWidth: Int
            val newHeight: Int

            // Scale to fill the box while strictly preserving aspect ratio
            if (width < height * aspectRatio) {
                newWidth = width
                newHeight = (width / aspectRatio).roundToInt()
            } else {
                newWidth = (height * aspectRatio).roundToInt()
                newHeight = height
            }
            setMeasuredDimension(newWidth, newHeight)
        }
    }
}
