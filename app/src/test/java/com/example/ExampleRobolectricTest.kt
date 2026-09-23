package com.example

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.widget.WidgetReceiver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Data Tracker", appName)
    }

    @Test
    fun `data formatting helpers return expected outputs`() {
        assertEquals("0.00 MB", DataUsageManager.formatBytes(0L))
        assertEquals("1.00 KB", DataUsageManager.formatBytes(1024L))
        assertEquals("1.00 MB", DataUsageManager.formatBytes(1024L * 1024L))
        assertEquals("2.50 GB", DataUsageManager.formatBytes((2.5 * 1024.0 * 1024.0 * 1024.0).toLong()))
    }

    @Test
    fun `midnight timestamp is at 00 00 00`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DataUsageManager(context)
        val midnight = manager.getStartOfDayMidnightMillis()

        val cal = Calendar.getInstance().apply { timeInMillis = midnight }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
        assertTrue(midnight <= System.currentTimeMillis())
    }

    @Test
    fun `widget provider is properly defined and instantiable`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val provider = WidgetReceiver()
        assertNotNull(provider)

        val appWidgetManager = AppWidgetManager.getInstance(context)
        assertNotNull(appWidgetManager)

        val componentName = ComponentName(context, WidgetReceiver::class.java)
        assertNotNull(componentName)
    }
}
