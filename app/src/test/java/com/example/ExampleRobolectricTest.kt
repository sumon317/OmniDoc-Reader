package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.DocFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `verify app name and format detection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("OmniDoc Reader", appName)

        assertEquals(DocFormat.PDF, DocFormat.fromFileName("manual.pdf"))
        assertEquals(DocFormat.DOCX, DocFormat.fromFileName("spec.docx"))
        assertEquals(DocFormat.XLSX, DocFormat.fromFileName("metrics.xlsx"))
        assertEquals(DocFormat.PPTX, DocFormat.fromFileName("keynote.pptx"))
        assertEquals(DocFormat.TXT, DocFormat.fromFileName("notes.md"))
        assertEquals(DocFormat.CBZ, DocFormat.fromFileName("issue_01.cbz"))
        assertEquals(DocFormat.CBR, DocFormat.fromFileName("vol_01.cbr"))
        assertTrue(DocFormat.CBZ.isComic)
        assertTrue(DocFormat.CBR.isComic)
    }
}
