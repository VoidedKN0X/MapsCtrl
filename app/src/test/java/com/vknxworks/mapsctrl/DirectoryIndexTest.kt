package com.vknxworks.mapsctrl

import com.vknxworks.mapsctrl.data.DirectoryIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DirectoryIndexTest {

    private val apache = """
        <html><body><h1>Index of /maps/v5/europe</h1>
          <table>
           <tr><th valign="top"><img src="/icons/blank.gif" alt="[ICO]"></th><th><a href="?C=N;O=D">Name</a></th><th><a href="?C=M;O=A">Last modified</a></th><th><a href="?C=S;O=A">Size</a></th><th><a href="?C=D;O=A">Description</a></th></tr>
           <tr><th colspan="5"><hr></th></tr>
        <tr><td valign="top"><img src="/icons/back.gif" alt="[PARENTDIR]"></td><td><a href="/maps/v5/">Parent Directory</a>       </td><td>&nbsp;</td><td align="right">  - </td><td>&nbsp;</td></tr>
        <tr><td valign="top"><img src="/icons/unknown.gif" alt="[   ]"></td><td><a href="albania.map">albania.map</a>            </td><td align="right">2026-08-21 15:48  </td><td align="right"> 43M</td><td>&nbsp;</td></tr>
        <tr><td valign="top"><img src="/icons/unknown.gif" alt="[   ]"></td><td><a href="andorra.map">andorra.map</a>            </td><td align="right">2026-09-07 15:30  </td><td align="right">2.4M</td><td>&nbsp;</td></tr>
        <tr><td valign="top"><img src="/icons/folder.gif" alt="[DIR]"></td><td><a href="germany/">germany/</a>                </td><td align="right">2026-10-25 11:22  </td><td align="right">  - </td><td>&nbsp;</td></tr>
        <tr><td valign="top"><img src="/icons/unknown.gif" alt="[   ]"></td><td><a href="monaco.poi">monaco.poi</a>             </td><td align="right">2026-10-26 16:25  </td><td align="right">776K</td><td>&nbsp;</td></tr>
          </table>
        </body></html>
    """.trimIndent()

    private val nginx = """
        <html>
        <head><title>Index of /brouter/segments4/</title></head>
        <body>
        <h1>Index of /brouter/segments4/</h1><hr><pre><a href="../">../</a>
        <a href="diff/">diff/</a>                                              12-Nov-2026 03:14                   -
        <a href="E0_N10.rd5">E0_N10.rd5</a>                                         12-Nov-2026 01:03            12241667
        <a href="E5_N40.rd5">E5_N40.rd5</a>                                         12-Nov-2026 01:03            69384516
        <a href="W5_N50.rd5">W5_N50.rd5</a>                                         12-Nov-2026 01:03           187000000
        </pre><hr></body>
        </html>
    """.trimIndent()

    @Test
    fun apacheListing() {
        val entries = DirectoryIndex.parse(apache)
        assertEquals(listOf("albania.map", "andorra.map", "germany", "monaco.poi"), entries.map { it.name })
        val andorra = entries[1]
        assertFalse(andorra.isDirectory)
        assertEquals((2.4 * 1024 * 1024).toLong(), andorra.bytes)
        assertFalse(andorra.bytesExact)
        assertEquals("2026-09-07 15:30", andorra.modified)
        assertTrue(entries[2].isDirectory)
        assertNull(entries[2].bytes)
        assertEquals(776L * 1024, entries[3].bytes)
    }

    @Test
    fun nginxListing() {
        val entries = DirectoryIndex.parse(nginx)
        assertEquals(listOf("diff", "E0_N10.rd5", "E5_N40.rd5", "W5_N50.rd5"), entries.map { it.name })
        assertTrue(entries[0].isDirectory)
        assertEquals(69384516L, entries[2].bytes)
        assertTrue(entries[2].bytesExact)
        assertEquals("12-Nov-2026 01:03", entries[2].modified)
    }

    @Test
    fun sizes() {
        assertEquals(43L * 1024 * 1024, DirectoryIndex.parseSize("43M").first)
        assertEquals((1.9 * 1024 * 1024 * 1024).toLong(), DirectoryIndex.parseSize("1.9G").first)
        assertNull(DirectoryIndex.parseSize("-").first)
        assertEquals(12L to true, DirectoryIndex.parseSize("12"))
    }

    @Test
    fun names() {
        assertEquals("Czech Republic", DirectoryIndex.displayName("czech-republic.map"))
        assertEquals("Ireland and Northern Ireland", DirectoryIndex.displayName("ireland-and-northern-ireland.map"))
        assertEquals("Europe", DirectoryIndex.displayName("europe"))
    }
}
