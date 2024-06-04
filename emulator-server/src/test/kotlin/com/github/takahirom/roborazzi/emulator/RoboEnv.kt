package com.github.takahirom.roborazzi.emulator

import org.robolectric.android.AndroidSdkShadowMatcher
import org.robolectric.annotation.GraphicsMode
import org.robolectric.annotation.LooperMode
import org.robolectric.annotation.SQLiteMode
import org.robolectric.interceptors.AndroidInterceptors
import org.robolectric.internal.ResourcesMode
import org.robolectric.internal.SandboxManager
import org.robolectric.internal.ShadowProvider
import org.robolectric.internal.bytecode.InstrumentationConfiguration
import org.robolectric.internal.bytecode.Interceptors
import org.robolectric.internal.bytecode.ShadowMap
import org.robolectric.internal.bytecode.ShadowWrangler
import org.robolectric.internal.dependency.MavenDependencyResolver
import org.robolectric.manifest.AndroidManifest
import org.robolectric.pluginapi.Sdk
import org.robolectric.pluginapi.SdkProvider
import org.robolectric.plugins.DefaultSdkProvider
import org.robolectric.plugins.HierarchicalConfigurationStrategy.ConfigurationImpl
import org.robolectric.plugins.SdkCollection
import org.robolectric.util.inject.Injector
import java.util.Properties
import java.util.ServiceLoader
import kotlin.io.path.Path


class RoboEnv {
  val sdkProvider = DefaultSdkProvider(MavenDependencyResolver())
  val sdkCollection = SdkCollection(sdkProvider)
  val sdk34 = sdkCollection.getSdk(34)
  val injector = Injector.Builder()
    .bind(Properties::class.java, System.getProperties())
    .bind(SdkProvider::class.java, sdkProvider)
    .bind(SdkCollection::class.java, sdkCollection)
    .bind(Injector.Key(Sdk::class.java, "runtimeSdk"), sdk34)
    .bind(Injector.Key(Sdk::class.java, "compileSdk"), sdk34)
    .bind(Injector.Key(Sdk::class.java, null), sdk34)
    .build()

  val sandboxManager = injector.getInstance(SandboxManager::class.java)

  inline fun <T> withSandbox(noinline function: () -> T): T {
    val classLoaderConfig =
      InstrumentationConfiguration.newBuilder()
        .doNotAcquirePackage("java.")
        .doNotAcquirePackage("javax.")
        .doNotAcquirePackage("jdk.internal.")
        .doNotAcquirePackage("sun.")
        .doNotAcquirePackage("org.robolectric.annotation.")
        .doNotAcquirePackage("org.robolectric.internal.")
        .doNotAcquirePackage("org.robolectric.pluginapi.")
        .doNotAcquirePackage("org.robolectric.util.")
        .build()

    val sdk = sdk34

    val resourcesMode = ResourcesMode.BINARY
    val looperMode: LooperMode.Mode = LooperMode.Mode.INSTRUMENTATION_TEST
    val sqliteMode = SQLiteMode.Mode.NATIVE
    val graphicsMode = GraphicsMode.Mode.NATIVE

    val sandbox = sandboxManager.getAndroidSandbox(
      classLoaderConfig, sdk, resourcesMode, looperMode, sqliteMode, graphicsMode
    )

    val shadowProviders = ServiceLoader.load(
      ShadowProvider::class.java
    ).toList()
    val baseShadowMap = ShadowMap.createFromShadowProviders(shadowProviders)

    val interceptors = Interceptors(AndroidInterceptors.all())
    val classHandler =
      ShadowWrangler(baseShadowMap, AndroidSdkShadowMatcher(sdk34.apiLevel), interceptors)
    sandbox.configure(classHandler, interceptors)

    val configuration = ConfigurationImpl()
    val appManifest = AndroidManifest(
      Path("src/main/AndroidManifest.xml"),
      Path("src/main/res"),
      Path("src/main/assets")
    )

    sandbox.testEnvironment.setUpApplicationState(
      RoboEnv::class.java.getMethod("aMethod"),
      configuration,
      appManifest
    )

    return sandbox.runOnMainThread(function)
  }

  fun aMethod() {
  }
}