package com.google.codelabs.productimagesearch

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView
import com.google.mlkit.vision.objects.DetectedObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.pow

/**
 * Tùy chỉnh ImageView có thể nhấp được trên một số Giới hạn kết quả phát hiện.
 */
class ImageClickableView : AppCompatImageView {

    companion object {
        private const val TAG = "ImageClickableView"
        private const val CLICKABLE_RADIUS = 40f
        private const val SHADOW_RADIUS = 10f
    }

    private val dotPaint = createDotPaint()
    private var onObjectClickListener: ((cropBitmap: Bitmap) -> Unit)? = null

    // Biến này được sử dụng để giữ kích thước thực tế của kết quả phát hiện hộp giới hạn do
    // tỷ lệ có thể thay đổi sau khi Bitmap điền vào ImageView
    private var transformedResults = listOf<TransformedDetectionResult>()

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    /**
     * Gọi lại khi người dùng nhấp vào hình chữ nhật kết quả phát hiện.
     */
    fun setOnObjectClickListener(listener: ((objectImage: Bitmap) -> Unit)) {
        this.onObjectClickListener = listener
    }

    /**
     * Vẽ vòng tròn màu trắng ở tâm của từng đối tượng được phát hiện trên ảnh
     */
    fun drawDetectionResults(results: List<DetectedObject>) {
        (drawable as? BitmapDrawable)?.bitmap?.let { srcImage ->
            // Nhận kích thước tỷ lệ dựa trên chiều rộng/chiều cao
            val scaleFactor =
                max(srcImage.width / width.toFloat(), srcImage.height / height.toFloat())
            // Tính toán tổng số phần đệm (dựa trên trung tâm bên trong loại tỷ lệ)
            val diffWidth = abs(width - srcImage.width / scaleFactor) / 2
            val diffHeight = abs(height - srcImage.height / scaleFactor) / 2

            // Chuyển đổi Hộp giới hạn ban đầu thành hộp giới hạn thực tế dựa trên kích thước hiển thị của ImageView.
            transformedResults = results.map { result ->
                // Tính toán để tạo tọa độ mới của Rectangle Box match trên ImageView.
                val actualRectBoundingBox = RectF(
                    (result.boundingBox.left / scaleFactor) + diffWidth,
                    (result.boundingBox.top / scaleFactor) + diffHeight,
                    (result.boundingBox.right / scaleFactor) + diffWidth,
                    (result.boundingBox.bottom / scaleFactor) + diffHeight
                )
                val dotCenter = PointF(
                    (actualRectBoundingBox.right + actualRectBoundingBox.left) / 2,
                    (actualRectBoundingBox.bottom + actualRectBoundingBox.top) / 2,
                )
                // Chuyển sang đối tượng mới để chứa dữ liệu bên trong.
                // Đối tượng này là cần thiết để tránh hiệu suất
                TransformedDetectionResult(actualRectBoundingBox, result.boundingBox, dotCenter)
            }
            Log.d(
                TAG,
                "srcImage: ${srcImage.width}/${srcImage.height} - imageView: ${width}/${height} => scaleFactor: $scaleFactor"
            )
            // Không hợp lệ để vẽ lại canvas
            // Phương thức onDraw sẽ được gọi với dữ liệu mới.
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // Nhận kết quả phát hiện và vẽ dạng xem chấm lên đối tượng được phát hiện.
        transformedResults.forEach { result ->
            // Vẽ Dot View
            canvas.drawCircle(result.dotCenter.x, result.dotCenter.y, CLICKABLE_RADIUS, dotPaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val touchX = event.x
                val touchY = event.y
                val index =
                    transformedResults.indexOfFirst {
                        val dx = (touchX - it.dotCenter.x).toDouble().pow(2.0)
                        val dy = (touchY - it.dotCenter.y).toDouble().pow(2.0)
                        (dx + dy) < CLICKABLE_RADIUS.toDouble().pow(2.0)
                    }
                // Nếu tìm thấy một đối tượng phù hợp, hãy gọi đối tượngClickListener
                if (index != -1) {
                    cropBitMapBasedResult(transformedResults[index])?.let {
                        onObjectClickListener?.invoke(it)
                    }
                }
            }
        }
        return super.onTouchEvent(event)
    }

    /**
     * Chức năng này sẽ được sử dụng để cắt đoạn Bitmap dựa trên thao tác chạm của người dùng.
     */
    private fun cropBitMapBasedResult(result: TransformedDetectionResult): Bitmap? {
        // Cắt hình ảnh từ Bitmap gốc với Hộp giới hạn Rect gốc
        (drawable as? BitmapDrawable)?.bitmap?.let {
            return Bitmap.createBitmap(
                it,
                result.originalBoxRectF.left,
                result.originalBoxRectF.top,
                result.originalBoxRectF.width(),
                result.originalBoxRectF.height()
            )
        }
        return null
    }

    /**
     *Quay lại Dot Paint để vẽ hình tròn
     */
    private fun createDotPaint() = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        setShadowLayer(SHADOW_RADIUS, 0F, 0F, Color.BLACK)
        // Buộc sử dụng phần mềm để kết xuất bằng cách tắt tăng tốc phần cứng.
        // Quan trọng: bóng sẽ không hoạt động nếu không có dòng này.
        setLayerType(LAYER_TYPE_SOFTWARE, this)
    }
}

/**
 * Lớp này chứa dữ liệu được chuyển đổi
 * @property: factBoxRectF: Hộp giới hạn sau khi tính toán
 * @property: originalBoxRectF: Hộp giới hạn ban đầu (Trước khi chuyển đổi), sử dụng để cắt ảnh bitmap.
 */
data class TransformedDetectionResult(
    val actualBoxRectF: RectF,
    val originalBoxRectF: Rect,
    val dotCenter: PointF
)
