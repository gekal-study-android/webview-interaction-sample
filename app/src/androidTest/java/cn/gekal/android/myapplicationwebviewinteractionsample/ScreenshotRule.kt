package cn.gekal.android.myapplicationwebviewinteractionsample

import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.io.FileOutputStream

/**
 * 実機 E2E の画面キャプチャを残す JUnit ルール。
 *
 * 節目で [capture] を呼ぶほか、テストが失敗したときは自動で 1 枚撮る。
 * 実機でしか出せない画面（ネイティブのエラー画面・アプリ内オーバーレイ）が
 * 本当にその見た目で出ているかは、ログだけでは分からないため。
 *
 * Activity より内側のルールにすること。外側だと、失敗時のキャプチャより先に
 * Activity が閉じてしまう。
 *
 * 保存先は AGP がテスト後に回収するディレクトリで、`connectedDebugAndroidTest` のあと
 * `app/build/outputs/connected_android_test_additional_output/` に降りてくる。
 */
class ScreenshotRule : TestWatcher() {
  private val instrumentation = InstrumentationRegistry.getInstrumentation()
  private var prefix = ""
  private var index = 0

  override fun starting(description: Description) {
    prefix = "${description.className.substringAfterLast('.')}-${safe(description.methodName)}"
    index = 0
  }

  override fun failed(e: Throwable, description: Description) {
    capture("failed")
  }

  /** いまの画面を PNG で保存する。連番が付くので、撮った順に並ぶ。 */
  fun capture(step: String) {
    // WebView の描画は別プロセスで進むため、DOM が変わった直後に撮ると 1 フレーム前が写る
    instrumentation.waitForIdleSync()
    SystemClock.sleep(PAINT_SETTLE_MILLIS)

    val bitmap = instrumentation.uiAutomation.takeScreenshot()
    if (bitmap == null) {
      Log.w(TAG, "画面キャプチャを取得できませんでした: $step")
      return
    }
    index += 1
    val file = File(outputDir, "$prefix-${"%02d".format(index)}-${safe(step)}.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    bitmap.recycle()
    Log.i(TAG, "画面キャプチャ: ${file.absolutePath}")
  }

  private val outputDir: File by lazy {
    // Gradle 経由なら AGP が回収先を渡してくる。am instrument で直接叩いたときは
    // アプリの外部ファイル領域に置く（adb pull で取り出せる）。
    val fromRunner = InstrumentationRegistry.getArguments().getString(ADDITIONAL_OUTPUT_ARG)
    val dir = fromRunner?.let(::File)
      ?: File(instrumentation.targetContext.getExternalFilesDir(null), "screenshots")
    dir.mkdirs()
    dir
  }

  /** ファイル名に使えない文字を落とす。日本語のテスト名はそのまま残す。 */
  private fun safe(value: String) = value.replace(FILE_NAME_UNSAFE, "_")

  private companion object {
    const val TAG = "ScreenshotRule"
    const val ADDITIONAL_OUTPUT_ARG = "additionalTestOutputDir"
    const val PAINT_SETTLE_MILLIS = 300L
    val FILE_NAME_UNSAFE = """[\\/:*?"<>|\s]""".toRegex()
  }
}
