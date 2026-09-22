package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("Card Clone AI", appName)
  }

  @Test
  fun `default corners are reasonable and convex`() {
    val defaultCorners = com.example.model.CardCorners()
    assertEquals(true, defaultCorners.areCornersReasonable())
    assertEquals(true, defaultCorners.isConvex())
  }
}
