package cn.gekal.android.myapplicationwebviewinteractionsample.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = WebPrimaryDark,
    secondary = PurpleGrey80,
    tertiary = Pink80,
    background = WebBackgroundDark,
    surface = WebSurfaceDark,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = WebPrimaryLight,
    secondary = PurpleGrey40,
    tertiary = Pink40,
    background = WebBackgroundLight,
    surface = WebSurfaceLight,
  )

@Composable
fun myApplicationWebviewInteractionSampleTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  // WebView のコンテンツと配色を揃えるため、既定ではダイナミックカラーを使わない
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme

      else -> LightColorScheme
    }

  MaterialTheme(
    colorScheme = colorScheme,
    typography = Typography,
    content = content,
  )
}
