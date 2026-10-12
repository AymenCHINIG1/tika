/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.pdf;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSDocument;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.cos.COSObject;
import org.apache.pdfbox.cos.COSString;
import org.apache.pdfbox.io.RandomAccessStreamCache;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.renderer.RenderRequest;
import org.apache.tika.renderer.RenderResult;
import org.apache.tika.renderer.RenderResults;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.renderer.pdf.pdfbox.PDFRenderingState;
import org.apache.tika.sax.ToXMLContentHandler;

/** Integration-style tests for resource handling and rendering branches in PDFParser.parse. */
class PDFParser_ParseResourceTest {

    private static Object invoke(PDFParser parser, String name, Class<?>[] types,
                                 Object... args) throws Exception {
        Method method = PDFParser.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(parser, args);
    }

    private static byte[] onePagePdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    private static PDFParserConfig noSpoolConfig() {
        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.NONE);
        config.setExtractIncrementalUpdateInfo(false);
        config.setParseIncrementalUpdates(false);
        config.getOcr().setStrategy(OcrConfig.Strategy.NO_OCR);
        return config;
    }

    @Test
    void parseClosesDocumentRenderResultsAndRestoresIncomingState() throws Exception {
        TrackingDocument document = new TrackingDocument();
        document.addPage(new PDPage());
        TrackingParser parser = new TrackingParser(document);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, noSpoolConfig());
        PDFRenderingState state = new PDFRenderingState(null);
        RenderResults results = mock(RenderResults.class);
        state.setRenderResults(results);
        context.set(PDFRenderingState.class, state);

        try (TikaInputStream stream = TikaInputStream.get(onePagePdf())) {
            parser.parse(stream, null, new Metadata(), context);
        }

        assertTrue(document.closed);
        verify(results).close();
        assertSame(state, context.get(PDFRenderingState.class));
    }

    @Test
    void zeroMemoryBoundarySelectsMixedCache() throws Exception {
        TrackingDocument document = new TrackingDocument();
        document.addPage(new PDPage());
        TrackingParser parser = new TrackingParser(document);
        PDFParserConfig config = noSpoolConfig();
        config.setMaxMainMemoryBytes(0);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);

        try (TikaInputStream stream = TikaInputStream.get(onePagePdf())) {
            parser.parse(stream, null, new Metadata(), context);
        }

        assertNotNull(parser.cacheFunction);
        Object cache = parser.cacheFunction.create();
        Field useScratchFile = cache.getClass().getDeclaredField("useScratchFile");
        useScratchFile.setAccessible(true);
        assertTrue((boolean) useScratchFile.get(cache));
        ((AutoCloseable) cache).close();
    }

    @Test
    void renderingStateWithoutResultsIsNotClosedAndIsRestoredExactlyOnce() throws Exception {
        TrackingDocument document = new TrackingDocument();
        document.addPage(new PDPage());
        TrackingParser parser = new TrackingParser(document);
        ParseContext context = org.mockito.Mockito.spy(new ParseContext());
        context.set(PDFParserConfig.class, noSpoolConfig());
        PDFRenderingState state = new PDFRenderingState(null);
        context.set(PDFRenderingState.class, state);
        org.mockito.Mockito.clearInvocations(context);
        try (TikaInputStream stream = TikaInputStream.get(onePagePdf())) {
            parser.parse(stream, null, new Metadata(), context);
        }
        assertSame(state, context.get(PDFRenderingState.class));
        verify(context, org.mockito.Mockito.times(1)).set(PDFRenderingState.class, state);
    }

    @Test
    void exceptionalParseWithNullRenderResultsPreservesOriginalSaxFailure() throws Exception {
        TrackingDocument document = new TrackingDocument();
        document.addPage(new PDPage());
        TrackingParser parser = new TrackingParser(document);
        ParseContext context = org.mockito.Mockito.spy(new ParseContext());
        context.set(PDFParserConfig.class, noSpoolConfig());
        PDFRenderingState state = new PDFRenderingState(null);
        context.set(PDFRenderingState.class, state);
        org.mockito.Mockito.clearInvocations(context);

        try (TikaInputStream stream = TikaInputStream.get(onePagePdf())) {
            assertThrows(SAXException.class,
                    () -> parser.parse(stream, new ThrowingHandler(), new Metadata(), context));
        }
        assertSame(state, context.get(PDFRenderingState.class));
        verify(context, org.mockito.Mockito.times(1)).set(PDFRenderingState.class, state);
    }

    @Test
    void exceptionalParseClosesIncomingRenderResults() throws Exception {
        TrackingDocument document = new TrackingDocument();
        document.addPage(new PDPage());
        TrackingParser parser = new TrackingParser(document);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, noSpoolConfig());
        PDFRenderingState state = new PDFRenderingState(null);
        RenderResults results = mock(RenderResults.class);
        state.setRenderResults(results);
        context.set(PDFRenderingState.class, state);

        try (TikaInputStream stream = TikaInputStream.get(onePagePdf())) {
            assertThrows(SAXException.class,
                    () -> parser.parse(stream, new ThrowingHandler(), new Metadata(), context));
        }
        verify(results).close();
        assertSame(state, context.get(PDFRenderingState.class));
    }

    @Test
    void renderingStateIsRestoredWhenCleanupFailsAfterSuccessfulBody() throws Exception {
        ThrowingCloseDocument document = new ThrowingCloseDocument();
        document.addPage(new PDPage());
        TrackingParser parser = new TrackingParser(document);
        ParseContext context = org.mockito.Mockito.spy(new ParseContext());
        context.set(PDFParserConfig.class, noSpoolConfig());
        PDFRenderingState state = new PDFRenderingState(null);
        context.set(PDFRenderingState.class, state);
        org.mockito.Mockito.clearInvocations(context);

        try (TikaInputStream stream = TikaInputStream.get(onePagePdf())) {
            assertThrows(IOException.class,
                    () -> parser.parse(stream, null, new Metadata(), context));
        }
        assertSame(state, context.get(PDFRenderingState.class));
        verify(context, org.mockito.Mockito.times(1)).set(PDFRenderingState.class, state);
    }

    @Test
    void renderingStateIsRestoredWhenBodyAndCleanupBothFail() throws Exception {
        ThrowingCloseDocument document = new ThrowingCloseDocument();
        document.addPage(new PDPage());
        TrackingParser parser = new TrackingParser(document);
        ParseContext context = org.mockito.Mockito.spy(new ParseContext());
        context.set(PDFParserConfig.class, noSpoolConfig());
        PDFRenderingState state = new PDFRenderingState(null);
        context.set(PDFRenderingState.class, state);
        org.mockito.Mockito.clearInvocations(context);

        try (TikaInputStream stream = TikaInputStream.get(onePagePdf())) {
            assertThrows(IOException.class,
                    () -> parser.parse(stream, new ThrowingHandler(), new Metadata(), context));
        }
        assertSame(state, context.get(PDFRenderingState.class));
        verify(context, org.mockito.Mockito.times(1)).set(PDFRenderingState.class, state);
    }

    @Test
    void getPdDocumentUsesFileAndStreamBranchesAndBalancesCloseShield() throws Exception {
        BranchParser parser = new BranchParser();
        Path path = Path.of(getClass().getResource("/test-documents/testPDF.pdf").toURI());
        try (TikaInputStream stream = TikaInputStream.get(path)) {
            assertNotNull(parser.getPDDocument(stream, "", null,
                    new Metadata(), new ParseContext()));
        }
        assertEquals(1, parser.pathCalls);
        assertEquals(0, parser.streamCalls);

        TrackingInputStream source = new TrackingInputStream(onePagePdf());
        try (TikaInputStream stream = TikaInputStream.get(source)) {
            assertNotNull(parser.getPDDocument(stream, "", null,
                    new Metadata(), new ParseContext()));
            assertFalse(source.closed);
        }
        assertTrue(source.closed);
        assertEquals(1, parser.pathCalls);
        assertEquals(1, parser.streamCalls);
    }

    @Test
    void ocrOnlyBranchEmitsAnXhtmlDocumentEvenForZeroPages() throws Exception {
        TrackingDocument document = new TrackingDocument();
        TrackingParser parser = new TrackingParser(document);
        PDFParserConfig config = noSpoolConfig();
        config.getOcr().setStrategy(OcrConfig.Strategy.OCR_ONLY);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        CountingHandler handler = new CountingHandler();
        try (TikaInputStream stream = TikaInputStream.get(onePagePdf())) {
            parser.parse(stream, handler, new Metadata(), context);
        }
        assertEquals(1, handler.starts);
        assertEquals(1, handler.ends);
    }

    @Test
    void markedContentBranchProducesStructuralHeading() throws Exception {
        Path path = Path.of(getClass().getResource("/test-documents/testJournalParser.pdf").toURI());
        PDFParserConfig config = noSpoolConfig();
        config.setExtractMarkedContent(true);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        ToXMLContentHandler handler = new ToXMLContentHandler();
        try (TikaInputStream stream = TikaInputStream.get(path)) {
            new PDFParser().parse(stream, handler, new Metadata(), context);
        }
        assertTrue(handler.toString().contains("<h1>I. INTRODUCTION</h1>"));
    }

    @Test
    void renderBeforeParseStoresResultsAndDelegatesSuccessfulImage() throws Exception {
        PDFParser parser = new PDFParser();
        Renderer renderer = mock(Renderer.class);
        when(renderer.getSupportedTypes(any())).thenReturn(Collections.singleton(MediaType.application("pdf")));
        RenderResults results = mock(RenderResults.class);
        RenderResult result = mock(RenderResult.class);
        Metadata renderedMetadata = new Metadata();
        when(result.getStatus()).thenReturn(RenderResult.STATUS.SUCCESS);
        when(result.getMetadata()).thenReturn(renderedMetadata);
        when(result.getInputStream()).thenReturn(TikaInputStream.get(new byte[0]));
        when(results.getResults()).thenReturn(Collections.singletonList(result));
        when(renderer.render(any(), any(), any(), any(RenderRequest.class))).thenReturn(results);
        parser.setRenderer(renderer);

        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_BEFORE_PARSE);
        config.setExtractIncrementalUpdateInfo(false);
        config.setParseIncrementalUpdates(false);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        EmbeddedDocumentExtractor extractor = mock(EmbeddedDocumentExtractor.class);
        when(extractor.shouldParseEmbedded(renderedMetadata)).thenReturn(true);
        context.set(EmbeddedDocumentExtractor.class, extractor);

        try (TikaInputStream stream = TikaInputStream.get(onePagePdf())) {
            parser.parse(stream, null, new Metadata(), context);
        }

        verify(extractor).parseEmbedded(any(), any(), any(), any(), anyBoolean());
        verify(results).close();
    }

    @Test
    void renderPdfMarksRenderedResourceAsPdfAndReturnsRendererResult() throws Exception {
        PDFParser parser = new PDFParser();
        Renderer renderer = mock(Renderer.class);
        RenderResults expected = mock(RenderResults.class);
        when(renderer.render(any(), any(), any(), any(RenderRequest.class))).thenAnswer(invocation -> {
            Metadata metadata = invocation.getArgument(1);
            assertEquals("application/pdf", metadata.get(TikaCoreProperties.TYPE));
            return expected;
        });
        parser.setRenderer(renderer);
        try (TikaInputStream stream = TikaInputStream.get(onePagePdf())) {
            Object actual = invoke(parser, "renderPDF",
                    new Class<?>[]{TikaInputStream.class, ParseContext.class,
                            PDFParserConfig.class},
                    stream, new ParseContext(), new PDFParserConfig());
            assertSame(expected, actual);
        }
    }

    @Test
    void renderFailureIsRecordedInParentMetadata() throws Exception {
        PDFParser parser = new PDFParser();
        Renderer renderer = mock(Renderer.class);
        when(renderer.render(any(), any(), any(), any(RenderRequest.class)))
                .thenThrow(new IOException("render failed"));
        parser.setRenderer(renderer);
        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_BEFORE_PARSE);
        Metadata metadata = new Metadata();
        try (TikaInputStream stream = TikaInputStream.get(onePagePdf())) {
            invoke(parser, "renderPagesBeforeParse",
                    new Class<?>[]{TikaInputStream.class, org.xml.sax.ContentHandler.class,
                            Metadata.class, ParseContext.class, PDFParserConfig.class},
                    stream, null, metadata, new ParseContext(), config);
        }
        assertNotNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    void embeddedRenderingFailureIsRecordedInParentMetadata() throws Exception {
        PDFParser parser = new PDFParser();
        Renderer renderer = mock(Renderer.class);
        RenderResults results = mock(RenderResults.class);
        RenderResult result = mock(RenderResult.class);
        Metadata renderedMetadata = new Metadata();
        when(result.getStatus()).thenReturn(RenderResult.STATUS.SUCCESS);
        when(result.getMetadata()).thenReturn(renderedMetadata);
        when(result.getInputStream()).thenReturn(TikaInputStream.get(new byte[0]));
        when(results.getResults()).thenReturn(Collections.singletonList(result));
        when(renderer.render(any(), any(), any(), any(RenderRequest.class))).thenReturn(results);
        parser.setRenderer(renderer);
        EmbeddedDocumentExtractor extractor = mock(EmbeddedDocumentExtractor.class);
        when(extractor.shouldParseEmbedded(renderedMetadata)).thenReturn(true);
        doThrow(new IOException("embedded failed")).when(extractor)
                .parseEmbedded(any(), any(), any(), any(), anyBoolean());
        ParseContext context = new ParseContext();
        context.set(PDFRenderingState.class, new PDFRenderingState(null));
        context.set(EmbeddedDocumentExtractor.class, extractor);
        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_BEFORE_PARSE);
        Metadata metadata = new Metadata();

        try (TikaInputStream stream = TikaInputStream.get(onePagePdf())) {
            invoke(parser, "renderPagesBeforeParse",
                    new Class<?>[]{TikaInputStream.class, org.xml.sax.ContentHandler.class,
                            Metadata.class, ParseContext.class, PDFParserConfig.class},
                    stream, null, metadata, context, config);
        }
        assertNotNull(metadata.get(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING));
    }

    @Test
    void encryptedPayloadMessageUsesUnicodeAndFallbackFileNames() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setThrowOnEncryptedPayload(true);
        assertEquals("unicode.bin", encryptedPayloadName(config, COSName.UF, "unicode.bin"));
        assertEquals("fallback.bin", encryptedPayloadName(config, COSName.F, "fallback.bin"));
    }

    private String encryptedPayloadName(PDFParserConfig config, COSName nameKey, String name)
            throws Exception {
        COSDictionary fileSpec = new COSDictionary();
        fileSpec.setItem(COSName.TYPE, COSName.FILESPEC);
        fileSpec.setItem(COSName.getPDFName("AFRelationship"),
                COSName.getPDFName("EncryptedPayload"));
        fileSpec.setItem(nameKey, new COSString(name));
        COSDocument cosDocument = mock(COSDocument.class);
        when(cosDocument.getObjectsByType(COSName.FILESPEC))
                .thenReturn(Collections.singletonList(new COSObject(fileSpec)));
        PDDocument document = mock(PDDocument.class);
        when(document.getDocument()).thenReturn(cosDocument);
        Exception exception = assertThrows(Exception.class,
                () -> invoke(new PDFParser(), "checkEncryptedPayload",
                        new Class<?>[]{PDDocument.class, boolean.class, PDFParserConfig.class},
                        document, true, config));
        Throwable cause = exception.getCause();
        assertTrue(cause instanceof EncryptedDocumentException);
        String message = cause.getMessage();
        assertTrue(message.contains(name));
        return name;
    }

    @Test
    void xfaOnlyEndsTheSaxDocument() throws Exception {
        Path path = Path.of(getClass().getResource(
                "/test-documents/testPDF_XFA_govdocs1_258578.pdf").toURI());
        PDFParserConfig config = new PDFParserConfig();
        config.setIfXFAExtractOnlyXFA(true);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        CountingHandler handler = new CountingHandler();
        try (TikaInputStream stream = TikaInputStream.get(path)) {
            new PDFParser().parse(stream, handler, new Metadata(), context);
        }
        assertEquals(1, handler.starts);
        assertEquals(1, handler.ends);
    }

    private static class TrackingParser extends PDFParser {
        private final PDDocument document;
        private RandomAccessStreamCache.StreamCacheCreateFunction cacheFunction;

        private TrackingParser(PDDocument document) {
            this.document = document;
        }

        @Override
        protected PDDocument getPDDocument(TikaInputStream stream, String password,
                                           RandomAccessStreamCache.StreamCacheCreateFunction cache,
                                           Metadata metadata, ParseContext context) {
            cacheFunction = cache;
            return document;
        }
    }

    private static class TrackingDocument extends PDDocument {
        private boolean closed;

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static class ThrowingCloseDocument extends TrackingDocument {
        @Override
        public void close() throws IOException {
            super.close();
            throw new IOException("close failed");
        }
    }

    private static class BranchParser extends PDFParser {
        private int pathCalls;
        private int streamCalls;

        @Override
        protected PDDocument getPDDocument(Path path, String password,
                                           RandomAccessStreamCache.StreamCacheCreateFunction cache,
                                           Metadata metadata, ParseContext context) {
            pathCalls++;
            return mock(PDDocument.class);
        }

        @Override
        protected PDDocument getPDDocumentFromStream(InputStream input, String password,
                                                     RandomAccessStreamCache.StreamCacheCreateFunction cache,
                                                     Metadata metadata, ParseContext context)
                throws IOException {
            streamCalls++;
            input.close();
            return mock(PDDocument.class);
        }
    }

    private static class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        private TrackingInputStream(byte[] bytes) {
            super(bytes);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static class CountingHandler extends DefaultHandler {
        private int starts;
        private int ends;

        @Override
        public void startDocument() {
            starts++;
        }

        @Override
        public void endDocument() {
            ends++;
        }
    }

    private static class ThrowingHandler extends DefaultHandler {
        @Override
        public void startDocument() throws SAXException {
            throw new SAXException("handler failed");
        }
    }
}
