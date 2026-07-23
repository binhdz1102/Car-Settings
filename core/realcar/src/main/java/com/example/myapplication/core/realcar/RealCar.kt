package com.example.myapplication.core.realcar

import android.car.Car
import android.content.Context

/**
 * App-facing entry point cho CarService thật.
 *
 * Class này giữ bề mặt API tương tự `FakeCar` để code gọi có thể chuyển từ fake sang real
 * ít thay đổi nhất: tạo qua [createCar], lấy property service bằng [getCarManager], và
 * đóng kết nối bằng [disconnect]. Bên trong không bind đến service mô phỏng mà ủy quyền
 * trực tiếp cho `android.car.Car`.
 */
class RealCar private constructor(
    context: Context,
) {
    private val propertyManager = RealCarPropertyManager(context.applicationContext)

    /**
     * Trả về manager thật theo service name của `android.car.Car`.
     *
     * Hiện module chỉ expose `PROPERTY_SERVICE`; nếu cần thêm audio, info hoặc UX
     * restriction service thì nên tạo wrapper riêng để mỗi service có lifecycle/error
     * handling rõ ràng.
     */
    fun getCarManager(serviceName: String): Any =
        when (serviceName) {
            PROPERTY_SERVICE -> propertyManager
            else -> error("Unsupported real car service: $serviceName")
        }

    fun getCarPropertyManager(): RealCarPropertyManager = propertyManager

    suspend fun connect(): RealCarPropertyResult<Unit> = propertyManager.connect()

    fun connectAsync() = propertyManager.connectAsync()

    fun disconnect() {
        propertyManager.close()
    }

    companion object {
        val PROPERTY_SERVICE: String = Car.PROPERTY_SERVICE

        fun createCar(context: Context): RealCar = RealCar(context)
    }
}
