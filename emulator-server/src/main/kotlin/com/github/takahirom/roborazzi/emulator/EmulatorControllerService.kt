package com.github.takahirom.roborazzi.emulator

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Bitmap.Config
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.android.emulator.control.BatteryState
import com.android.emulator.control.EmulatorControllerWireGrpc
import com.android.emulator.control.GpsState
import com.android.emulator.control.Image
import com.android.emulator.control.ImageFormat
import com.android.emulator.control.LogMessage
import com.github.takahirom.roborazzi.emulator.RoboInstances.executeOnMain
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.yield
import okio.ByteString.Companion.toByteString
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowWindowManagerImpl
import java.io.ByteArrayOutputStream


class EmulatorControllerService(val dispatcher: CoroutineDispatcher) :
  EmulatorControllerWireGrpc.EmulatorControllerImplBase() {

  val androidContext: Context
    get() = InstrumentationRegistry.getInstrumentation().targetContext

  inline fun <reified T> systemService(): T {
    return ContextCompat.getSystemService(androidContext, T::class.java)!!
  }

  override suspend fun getBattery(request: Unit): BatteryState = executeOnMain {
    val batteryManager = systemService<BatteryManager>()

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return@executeOnMain BatteryState()
    }

    val statusProperty = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_STATUS)
    val capacityProperty = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)

    val status = when (statusProperty) {
      BatteryManager.BATTERY_STATUS_FULL -> BatteryState.BatteryStatus.FULL
      BatteryManager.BATTERY_STATUS_CHARGING -> BatteryState.BatteryStatus.CHARGING
      BatteryManager.BATTERY_STATUS_DISCHARGING -> BatteryState.BatteryStatus.DISCHARGING
      BatteryManager.BATTERY_STATUS_NOT_CHARGING -> BatteryState.BatteryStatus.NOT_CHARGING
      else -> BatteryState.BatteryStatus.UNKNOWN
    }
    BatteryState(
      hasBattery = true,
      isPresent = true,
      charger = BatteryState.BatteryCharger.USB,
      chargeLevel = capacityProperty,
      status = status
    )
  }

  @SuppressLint("MissingPermission")
  override suspend fun getGps(request: Unit): GpsState = executeOnMain {
    val locationManager = systemService<LocationManager>()

    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
      return@executeOnMain GpsState()
    }

    println(GrpcMetadata.instanceContextKey.get())

    val providers = locationManager.getProviders(true)
    println(providers)

    val location = locationManager.getLastKnownLocation("gps")

    GpsState(
      latitude = location?.latitude ?: 0.0,
      longitude = location?.longitude ?: 0.0
    )
  }

  override suspend fun setGps(request: GpsState) = executeOnMain {
    val locationManager = systemService<LocationManager>()

    val shadow = Shadows.shadowOf(locationManager)

    shadow.simulateLocation(Location("gps").apply {
      latitude = request.latitude
      longitude = request.longitude
    })
  }

  @SuppressLint("NewApi")
  override suspend fun getScreenshot(request: ImageFormat): Image = executeOnMain {
    val monitor = ActivityLifecycleMonitorRegistry.getInstance()
    val activities = monitor.getActivitiesInStage(Stage.RESUMED)
    println(activities)
    val activity = activities.first()

    val window = activity.window
    val view = window.findViewById<View>(android.R.id.content)
    val bitmap = Bitmap.createBitmap(view.width, view.height, Config.ARGB_8888)
    val result = CompletableDeferred<Image>()

    println("copying")
    PixelCopy.request(
      window!!,
      bitmap,
      {
        println("copied $it")
        val output = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        println("compressed")
        val image = Image(
          format = ImageFormat(format = ImageFormat.ImgFormat.PNG),
          width = view.width,
          height = view.height,
          image = output.toByteArray().toByteString()
        )
        result.complete(image)
      },
      Handler(Looper.getMainLooper())
    )

    println("awaiting")
    result.await().also {
      println("returning Image")
    }
  }

  override fun streamLogcat(request: LogMessage): Flow<LogMessage> = channelFlow {
    repeat(100) {
      trySend(LogMessage("Hello $it"))
      yield()
    }
  }
}
