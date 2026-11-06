package com.recipeagent.flyer;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Getting flyer text from a file or the web (F10). The web test uses a small local server. */
class FlyerSourcesTest {

    @TempDir
    Path tmp;

    @Test
    void fileSourceReadsTextAndTidiesIt() throws Exception {
        Path f = tmp.resolve("nofrills.txt");
        Files.writeString(f, "﻿No Frills   weekly flyer\r\n\r\n\r\n\r\nMilk 4L   $5.49\r\n", StandardCharsets.UTF_8);
        FileFlyerSource src = new FileFlyerSource(f);
        assertEquals("No Frills weekly flyer\n\nMilk 4L $5.49", src.fetch("No Frills").getText());
        assertFalse(src.fetch("No Frills").isPdf());
        assertEquals("the file nofrills.txt", src.describe());
    }

    @Test
    void fileSourceTurnsASavedWebPageIntoText() throws Exception {
        Path f = tmp.resolve("metro.html");
        Files.writeString(f, "<html><head><style>p{color:red}</style><script>alert(1)</script></head>"
                + "<body><h1>Metro</h1><p>Bananas &amp; apples</p><div>$0.69/lb</div></body></html>");
        String text = new FileFlyerSource(f).fetch("Metro").getText();
        assertTrue(text.contains("Bananas & apples"));
        assertTrue(text.contains("$0.69/lb"));
        assertFalse(text.contains("alert"));
        assertFalse(text.contains("<"));
    }

    @Test
    void fileSourceRejectsMissingEmptyAndBinaryFiles() throws Exception {
        FlyerException missing = assertThrows(FlyerException.class,
                () -> new FileFlyerSource(tmp.resolve("nope.txt")).fetch("X"));
        assertEquals("The file nope.txt was not found.", missing.getMessage());
        assertFalse(missing.isUploadInstead());

        Path empty = tmp.resolve("empty.txt");
        Files.writeString(empty, "  \n ");
        assertEquals("The file empty.txt is empty.",
                assertThrows(FlyerException.class, () -> new FileFlyerSource(empty).fetch("X")).getMessage());

        Path image = tmp.resolve("flyer.png");
        Files.write(image, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 1});
        assertTrue(assertThrows(FlyerException.class, () -> new FileFlyerSource(image).fetch("X"))
                .getMessage().contains("not a text or PDF file"));
    }

    @Test
    void fileSourcePassesPdfsOnAsTheyAre() throws Exception {
        byte[] bytes = "%PDF-1.7\n1 0 obj << /Type /Page >> endobj\n\u0000\u0001 binary".getBytes(StandardCharsets.ISO_8859_1);
        Path pdf = tmp.resolve("No Frills week 45.pdf");
        Files.write(pdf, bytes);
        FlyerContent c = new FileFlyerSource(pdf).fetch("No Frills");
        assertTrue(c.isPdf());
        assertNull(c.getText());
        assertArrayEquals(bytes, c.getPdf());
        assertEquals("No Frills week 45.pdf", c.getFileName());
        assertTrue(c.toString().startsWith("PDF No Frills week 45.pdf"));

        Path locked = tmp.resolve("locked.pdf");
        Files.write(locked, "%PDF-1.4\ntrailer << /Root 1 0 R /Encrypt 5 0 R >>".getBytes(StandardCharsets.US_ASCII));
        assertTrue(assertThrows(FlyerException.class, () -> new FileFlyerSource(locked).fetch("X"))
                .getMessage().contains("password-protected"));

        Path big = tmp.resolve("big.pdf");
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(big.toFile(), "rw")) {
            raf.write("%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
            raf.setLength(FileFlyerSource.MAX_PDF_BYTES + 1);                  // sparse, so this is quick
        }
        assertTrue(assertThrows(FlyerException.class, () -> new FileFlyerSource(big).fetch("X"))
                .getMessage().contains("over 32 MB"));

        Path bigText = tmp.resolve("big.txt");
        try (java.io.RandomAccessFile raf = new java.io.RandomAccessFile(bigText.toFile(), "rw")) {
            raf.write("Milk $5".getBytes(StandardCharsets.US_ASCII));
            raf.setLength(FileFlyerSource.MAX_BYTES + 1);
        }
        assertTrue(assertThrows(FlyerException.class, () -> new FileFlyerSource(bigText).fetch("X"))
                .getMessage().contains("over 2 MB"));

        assertTrue(FileFlyerSource.isPdf("%PDF-1.3".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(FileFlyerSource.isPdf("%PD".getBytes(StandardCharsets.US_ASCII)));
        assertFalse(FileFlyerSource.isEncrypted(bytes));
        assertThrows(IllegalArgumentException.class, () -> FlyerContent.pdf(new byte[0], "x.pdf"));
        assertThrows(IllegalArgumentException.class, () -> FlyerContent.text(" "));
    }

    @Test
    void fileSourceReadsWindowsTextToo() throws Exception {
        Path f = tmp.resolve("loblaws.txt");
        Files.write(f, "Crème fraîche $3.99".getBytes("windows-1252"));
        assertEquals("Crème fraîche $3.99", new FileFlyerSource(f).fetch("Loblaws").getText());
    }

    @Test
    void onlineSourceAsksForAnUploadWhenNotSetUp() {
        OnlineFlyerSource src = new OnlineFlyerSource(" ");
        assertFalse(src.isConfigured());
        FlyerException e = assertThrows(FlyerException.class, () -> src.fetch("Metro"));
        assertTrue(e.isUploadInstead());
        assertTrue(e.getMessage().contains("upload the Metro flyer as a file"));
    }

    @Test
    void onlineSourceFetchesAPageAndReportsErrors() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/flyers/", ex -> {
            String path = ex.getRequestURI().getPath();
            boolean ok = path.endsWith("/No%20Frills.html") || path.endsWith("/No Frills.html");
            boolean pdf = path.endsWith("/FreshCo.html");
            byte[] body = pdf ? "%PDF-1.7 flyer pages".getBytes(StandardCharsets.US_ASCII)
                    : (ok ? "<html><body><p>Eggs 12 pack $3.99</p></body></html>" : "missing")
                    .getBytes(StandardCharsets.UTF_8);
            ok |= pdf;
            ex.sendResponseHeaders(ok ? 200 : 404, body.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(body);
            }
        });
        server.start();
        try {
            String template = "http://127.0.0.1:" + server.getAddress().getPort() + "/flyers/{store}.html";
            OnlineFlyerSource src = new OnlineFlyerSource(template, java.net.http.HttpClient.newHttpClient());
            assertTrue(src.isConfigured());
            assertTrue(src.urlFor("No Frills").endsWith("/flyers/No%20Frills.html"));
            assertEquals("Eggs 12 pack $3.99", src.fetch("No Frills").getText());
            FlyerContent pdf = src.fetch("FreshCo");                          // the address gave a PDF
            assertTrue(pdf.isPdf());
            assertEquals("FreshCo flyer.pdf", pdf.getFileName());
            FlyerException e = assertThrows(FlyerException.class, () -> src.fetch("Metro"));
            assertTrue(e.getMessage().contains("error 404"));
            assertTrue(e.isUploadInstead());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void longTextIsCut() {
        String text = FlyerText.tidy("a".repeat(FlyerText.MAX_CHARS + 50));
        assertEquals(FlyerText.MAX_CHARS, text.length());
    }

    @Test
    void pdfPagesAreCountedAndLimited() throws Exception {
        String three = "%PDF-1.7 1 0 obj <</Type /Page>> 2 0 obj <</Type/Page /Rotate 0>> "
                + "3 0 obj <</Type\n/Page>> 4 0 obj <</Type /Pages /Count 3>> 5 0 obj <</Type /PageLabel>>";
        assertEquals(3, FlyerContent.countPages(three.getBytes(StandardCharsets.US_ASCII)));
        assertEquals(24, FlyerContent.countPages("%PDF-1.7 <</Count 24>> <</Count 3>>".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(-1, FlyerContent.countPages("%PDF-1.7 nothing".getBytes(StandardCharsets.US_ASCII)));
        assertTrue(FlyerContent.pdf(three.getBytes(StandardCharsets.US_ASCII), "a.pdf").toString().contains("3 pages"));

        StringBuilder many = new StringBuilder("%PDF-1.7\n");
        for (int i = 0; i <= FileFlyerSource.MAX_PDF_PAGES; i++) {
            many.append("<< /Type /Page >>\n");
        }
        Path big = tmp.resolve("catalogue.pdf");
        Files.writeString(big, many.toString(), StandardCharsets.US_ASCII);
        FlyerException e = assertThrows(FlyerException.class, () -> new FileFlyerSource(big).fetch("X"));
        assertTrue(e.getMessage().contains("has 101 pages; the app reads at most 100"));
    }
}
