package cn.gekal.android.myapplicationwebviewinteractionsample

import org.junit.Assert.assertEquals
import org.junit.Test

/** WebView / SharedPreferences から来る文字列を [AppTheme] に変換するロジックの検証。 */
class AppThemeTest {
  @Test
  fun `名前を変換する（大文字小文字は問わない）`() {
    assertEquals(AppTheme.LIGHT, AppTheme.from("light"))
    assertEquals(AppTheme.DARK, AppTheme.from("DARK"))
    assertEquals(AppTheme.SYSTEM, AppTheme.from("System"))
  }

  @Test
  fun `未知の値や null はシステム設定にフォールバックする`() {
    // WebView から任意の文字列が来ても、保存値が壊れていても壊れないこと
    assertEquals(AppTheme.SYSTEM, AppTheme.from("nope"))
    assertEquals(AppTheme.SYSTEM, AppTheme.from(""))
    assertEquals(AppTheme.SYSTEM, AppTheme.from(null))
  }
}
