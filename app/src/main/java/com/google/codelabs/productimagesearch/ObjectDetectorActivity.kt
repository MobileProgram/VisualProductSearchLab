package com.google.codelabs.productimagesearch

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import com.google.codelabs.productimagesearch.databinding.ActivityObjectDetectorBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.objects.DetectedObject
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.objects.defaults.PredefinedCategory
import java.io.File
import java.io.FileOutputStream
import java.io.IOException


class ObjectDetectorActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_IMAGE_CAPTURE = 1000
        private const val REQUEST_IMAGE_GALLERY = 1001
        private const val TAKEN_BY_CAMERA_FILE_NAME = "MLKitDemo_"
        private const val IMAGE_PRESET_1 = "Preset1.jpg"
        private const val IMAGE_PRESET_2 = "Preset2.jpg"
        private const val IMAGE_PRESET_3 = "Preset3.jpg"
        private const val TAG = "MLKit-ODT"
    }

    private lateinit var viewBinding: ActivityObjectDetectorBinding
    private var cameraPhotoUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityObjectDetectorBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        initViews()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // Sau khi chụp ảnh, hiển thị để xem trước
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_IMAGE_CAPTURE -> cameraPhotoUri?.let {
                    this.setViewAndDetect(
                        getBitmapFromUri(it)
                    )
                }
                REQUEST_IMAGE_GALLERY -> data?.data?.let { this.setViewAndDetect(getBitmapFromUri(it)) }
            }
        }
    }

    private fun initViews() {
        with(viewBinding) {
            ivPreset1.setImageBitmap(getBitmapFromAsset(IMAGE_PRESET_1))
            ivPreset2.setImageBitmap(getBitmapFromAsset(IMAGE_PRESET_2))
            ivPreset3.setImageBitmap(getBitmapFromAsset(IMAGE_PRESET_3))
            ivCapture.setOnClickListener { dispatchTakePictureIntent() }
            ivGalleryApp.setOnClickListener { choosePhotoFromGalleryApp() }
            ivPreset1.setOnClickListener { setViewAndDetect(getBitmapFromAsset(IMAGE_PRESET_1)) }
            ivPreset2.setOnClickListener { setViewAndDetect(getBitmapFromAsset(IMAGE_PRESET_2)) }
            ivPreset3.setOnClickListener { setViewAndDetect(getBitmapFromAsset(IMAGE_PRESET_3)) }
            // Callback khi người dùng chạm vào bất kỳ đối tượng nào được phát hiện.
            ivPreview.setOnObjectClickListener { objectImage ->
                startProductImageSearch(objectImage)
            }
            // hiển thị mặc định
            setViewAndDetect(getBitmapFromAsset(IMAGE_PRESET_2))
        }
    }

    /**
     * Bắt đầu search
     */
    private fun startProductImageSearch(objectImage: Bitmap) {

    }

    /**
     * Cập nhật giao diện người dùng với hình ảnh đầu vào và bắt đầu phát hiện đối tượng
     */
    private fun setViewAndDetect(bitmap: Bitmap?) {
        bitmap?.let {
            // Xóa các dấu chấm cho biết kết quả phát hiện trước đó
            viewBinding.ivPreview.drawDetectionResults(emptyList())

            // Hiển thị hình ảnh đầu vào trên màn hình.
            viewBinding.ivPreview.setImageBitmap(bitmap)

            // Chạy phát hiện đối tượng và hiển thị kết quả phát hiện.
            runObjectDetection(bitmap)
        }
    }

    /**
     * Detect Objects in a given Bitmap
     */
    private fun runObjectDetection(bitmap: Bitmap) {
        // Step 1: tạo đối tượng InputImage của ML Kit
        val image = InputImage.fromBitmap(bitmap, 0)

        // Step 2: có được đối tượng dò và cấu hình
        val options = ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build()
        val objectDetector = ObjectDetection.getClient(options)

        // Step 3: cung cấp hình ảnh đã cho cho trình phát hiện và thiết lập gọi lại
        objectDetector.process(image)
            .addOnSuccessListener { results ->
                debugPrint(results)

                // Chỉ giữ lại các đối tượng FASHION_GOOD
                val filteredResults = results.filter { result ->
                    result.labels.indexOfFirst { it.text == PredefinedCategory.FASHION_GOOD } != -1
                }

                // Trực quan hóa kết quả phát hiện
                runOnUiThread {
                    viewBinding.ivPreview.drawDetectionResults(filteredResults)
                }

            }
            .addOnFailureListener {
                // Nhiệm vụ không thành công với một ngoại lệ
                Log.e(TAG, it.message.toString())
            }

    }

    /**
     * Hiển thị Ứng dụng Máy ảnh để chụp ảnh dựa trên Ý định
     */
    private fun dispatchTakePictureIntent() {
        Intent(MediaStore.ACTION_IMAGE_CAPTURE).also { takePictureIntent ->
            // Đảm bảo rằng có một hoạt động camera để xử lý mục đích
            takePictureIntent.resolveActivity(packageManager)?.also {
                // Tạo Tệp nơi ảnh sẽ đi
                val photoFile: File? = try {
                    createImageFile(TAKEN_BY_CAMERA_FILE_NAME)
                } catch (ex: IOException) {
                    // Đã xảy ra lỗi khi tạo Tệp
                    null
                }
                // Chỉ tiếp tục nếu Tệp đã được tạo thành công
                photoFile?.also {
                    cameraPhotoUri = FileProvider.getUriForFile(
                        this,
                        "com.google.codelabs.productimagesearch.fileprovider",
                        it
                    )
                    // Đặt tệp đầu ra để chụp ảnh
                    takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, cameraPhotoUri)
                    // Mở mục đích dựa trên máy ảnh.
                    startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE)
                }
            } ?: run {
                Toast.makeText(this, getString(R.string.camera_app_not_found), Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    /**
     * Hiển thị ứng dụng thư viện để chọn ảnh tuỳ ý.
     */
    private fun choosePhotoFromGalleryApp() {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }, REQUEST_IMAGE_GALLERY)
    }

    /**
     * Tệp đầu ra sẽ được lưu trữ trên bộ nhớ riêng của ứng dụng này
     * Bằng cách gọi hàm getExternalFilesDir
     * Ảnh này sẽ bị xóa khi gỡ cài đặt ứng dụng.
     */
    @Throws(IOException::class)
    private fun createImageFile(fileName: String): File {
        // Tạo tên tệp hình ảnh
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            fileName, /* prefix */
            ".jpg", /* suffix */
            storageDir /* directory */
        )
    }

    /**
     * Phương pháp sao chép mẫu tệp nội dung vào thư mục ứng dụng riêng.
     * Trả về Uri của tệp đầu ra.
     */
    private fun getBitmapFromAsset(fileName: String): Bitmap? {
        return try {
            BitmapFactory.decodeStream(assets.open(fileName))
        } catch (ex: IOException) {
            null
        }
    }

    /**
     * Chức năng lấy Bitmap từ Uri.
     * Uri được nhận bằng cách sử dụng Ý định được gọi tới ứng dụng Máy ảnh hoặc Thư viện
     * SuppressWarnings => đã đề cập đến cảnh báo này.
     */
    private fun getBitmapFromUri(imageUri: Uri): Bitmap? {
        val bitmap = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                ImageDecoder.decodeBitmap(ImageDecoder.createSource(contentResolver, imageUri))
            } else {
                // Thêm chú thích Suppress để bỏ qua cảnh báo của Android Studio.
                // Cảnh báo này được giải quyết bằng chức năng ImageDecoder.
                @Suppress("DEPRECATION")
                MediaStore.Images.Media.getBitmap(contentResolver, imageUri)
            }
        } catch (ex: IOException) {
            null
        }

        // Tạo một bản sao của bitmap theo định dạng mong muốn
        return bitmap?.copy(Bitmap.Config.ARGB_8888, false)
    }

    /**
     * Chức năng ghi thông tin về đối tượng được phát hiện bởi ML Kit.
     */
    private fun debugPrint(detectedObjects: List<DetectedObject>) {
        detectedObjects.forEachIndexed { index, detectedObject ->
            val box = detectedObject.boundingBox

            Log.d(TAG, "Detected object: $index")
            Log.d(TAG, " trackingId: ${detectedObject.trackingId}")
            Log.d(TAG, " boundingBox: (${box.left}, ${box.top}) - (${box.right},${box.bottom})")
            detectedObject.labels.forEach {
                Log.d(TAG, " categories: ${it.text}")
                Log.d(TAG, " confidence: ${it.confidence}")
            }
        }
    }

}
