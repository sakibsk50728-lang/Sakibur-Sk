package com.example

import android.graphics.Bitmap
import com.example.model.PaperSize
import com.example.model.PrintLayoutCalculator
import com.example.model.PrintQueueItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PrintLayoutCalculatorTest {

  @Test
  fun `300 dpi millimeter conversion is accurate`() {
    // 25.4 mm = 1 inch = 300 px
    assertEquals(300, PrintLayoutCalculator.mmToPx300(25.4f))
    // 35 x 45 mm passport photo at 300 DPI = 413 x 531 px
    assertEquals(413, PrintLayoutCalculator.mmToPx300(35f))
    assertEquals(531, PrintLayoutCalculator.mmToPx300(45f))
  }

  @Test
  fun `auto fit calculates max passport photos on 4x6 and A4`() {
    val fit4x6 = PrintLayoutCalculator.calculateBestFit(PaperSize.PAPER_4X6, 35f, 45f)
    // Landscape 152.4 x 101.6 fits 4x2 = 8 passport photos
    assertEquals(8, fit4x6.maxCopiesPerSheet)

    val fitA4 = PrintLayoutCalculator.calculateBestFit(PaperSize.PAPER_A4, 35f, 45f)
    assertTrue(fitA4.maxCopiesPerSheet >= 30)
  }

  @Test
  fun `multi customer queue packs across multiple pages cleanly`() {
    val tile = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
    val queue =
      listOf(
        PrintQueueItem("1", "Customer A", tile, 35f, 45f, copies = 6),
        PrintQueueItem("2", "Customer B", tile, 35f, 45f, copies = 6),
      )
    val pages = PrintLayoutCalculator.packQueueOntoSheets(queue, PaperSize.PAPER_4X6)
    assertEquals(2, pages.size)
    assertEquals(8, pages[0].placements.size)
    assertEquals(4, pages[1].placements.size)
  }
}
