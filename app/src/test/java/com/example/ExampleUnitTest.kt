package com.example

import org.junit.Assert.*
import org.junit.Test
import com.example.data.SupportedApp
import com.example.ui.feedAppColor

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun feedColorsAreStableAndDistinctForSupportedApps() {
    val colors = SupportedApp.filterableApps.map { feedAppColor(it.packageName) }
    assertEquals(colors.size, colors.distinct().size)
    assertEquals(feedAppColor("com.example.custom"), feedAppColor("com.example.custom"))
    assertNotEquals(feedAppColor("com.example.custom"), feedAppColor("com.example.custom2"))
  }
}
