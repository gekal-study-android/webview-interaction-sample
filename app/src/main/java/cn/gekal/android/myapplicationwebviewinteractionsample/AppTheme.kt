package cn.gekal.android.myapplicationwebviewinteractionsample

import android.content.Context

/** アプリの配色。既定は [SYSTEM]（端末のダークモード設定に追従）。 */
enum class AppTheme {
  SYSTEM,
  LIGHT,
  DARK,
  ;

  companion object {
    /** WebView から渡される文字列を [AppTheme] に変換する。未知の値は [SYSTEM] にフォールバックする。 */
    fun from(value: String?): AppTheme =
      entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: SYSTEM
  }
}

/**
 * 配色を次回起動まで保持する。
 *
 * 配色を選ぶのは WebView 側（MUI が localStorage に保存する）で、そちらが真実の源。
 * ここに保存するのは、起動直後に WebView が読み込まれるまでの間、ヘッダーや
 * システムバーの色が食い違ってちらつくのを防ぐためのミラー。
 */
class ThemePreference(context: Context) {
  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

  fun load(): AppTheme = AppTheme.from(prefs.getString(KEY_THEME, null))

  fun save(theme: AppTheme) {
    prefs.edit().putString(KEY_THEME, theme.name).apply()
  }

  private companion object {
    const val PREFS_NAME = "app_theme"
    const val KEY_THEME = "theme"
  }
}
