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

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSInteger;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDSignatureField;
import org.apache.pdfbox.rendering.ImageType;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.AccessPermissions;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.updates.IncrementalUpdateRecord;
import org.apache.tika.renderer.Renderer;
import org.apache.tika.renderer.pdf.pdfbox.PDFBoxRenderer;

/** Manual tests whose assertions are deliberately tied to surviving PDFParser mutants. */
class PDFParser_MutationCoverageTest {

    private static Calendar utcCalendar(int year, int month, int day,
                                        int hour, int minute, int second) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone("UTC"), Locale.ROOT);
        calendar.clear();
        calendar.set(year, month, day, hour, minute, second);
        return calendar;
    }

    private static Object invoke(PDFParser parser, String name, Class<?>[] types,
                                 Object... args) throws Exception {
        Method method = PDFParser.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(parser, args);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static boolean shouldSpool(PDFParserConfig config) throws Exception {
        return (boolean) invoke(new PDFParser(), "shouldSpool",
                new Class<?>[]{PDFParserConfig.class}, config);
    }

    @Test
    void shouldSpoolDistinguishesEveryConfigurationReason() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.NONE);
        config.setExtractIncrementalUpdateInfo(false);
        config.setParseIncrementalUpdates(false);
        config.getOcr().setStrategy(OcrConfig.Strategy.NO_OCR);
        assertFalse(shouldSpool(config));

        config.setExtractIncrementalUpdateInfo(true);
        assertTrue(shouldSpool(config));
        config.setExtractIncrementalUpdateInfo(false);
        config.setParseIncrementalUpdates(true);
        assertTrue(shouldSpool(config));
        config.setParseIncrementalUpdates(false);

        config.getOcr().setStrategy(OcrConfig.Strategy.OCR_ONLY);
        assertTrue(shouldSpool(config));
    }

    @Test
    void markedContentRecognizesNullEmptyDictionaryAndArrayStructures() throws Exception {
        PDFParser parser = new PDFParser();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            Class<?>[] documentOnly = {PDDocument.class};
            assertFalse((boolean) invoke(parser, "hasMarkedContent", documentOnly, document));

            PDStructureTreeRoot root = new PDStructureTreeRoot();
            document.getDocumentCatalog().setStructureTreeRoot(root);
            assertFalse((boolean) invoke(parser, "hasMarkedContent", documentOnly, document));

            COSDictionary dictionary = new COSDictionary();
            root.getCOSObject().setItem(COSName.K, dictionary);
            assertFalse((boolean) invoke(parser, "hasMarkedContent", documentOnly, document));
            dictionary.setInt(COSName.getPDFName("MCID"), 0);
            assertTrue((boolean) invoke(parser, "hasMarkedContent", documentOnly, document));

            COSArray array = new COSArray();
            root.getCOSObject().setItem(COSName.K, array);
            assertFalse((boolean) invoke(parser, "hasMarkedContent", documentOnly, document));
            array.add(COSInteger.ZERO);
            assertTrue((boolean) invoke(parser, "hasMarkedContent", documentOnly, document));

            Metadata metadata = new Metadata();
            assertTrue((boolean) invoke(parser, "hasMarkedContent",
                    new Class<?>[]{PDDocument.class, Metadata.class}, document, metadata));
            assertEquals("true", metadata.get(PDF.HAS_MARKED_CONTENT));

            document.getDocumentCatalog().setStructureTreeRoot(null);
            metadata = new Metadata();
            assertFalse((boolean) invoke(parser, "hasMarkedContent",
                    new Class<?>[]{PDDocument.class, Metadata.class}, document, metadata));
            assertEquals("false", metadata.get(PDF.HAS_MARKED_CONTENT));
        }
    }

    @Test
    void collectionDetectionSetsMetadataForBothOutcomes() throws Exception {
        PDFParser parser = new PDFParser();
        try (PDDocument document = new PDDocument()) {
            Metadata metadata = new Metadata();
            assertFalse((boolean) invoke(parser, "hasCollection",
                    new Class<?>[]{PDDocument.class, Metadata.class}, document, metadata));
            assertEquals("false", metadata.get(PDF.HAS_COLLECTION));

            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.COLLECTION, new COSDictionary());
            assertTrue((boolean) invoke(parser, "hasCollection",
                    new Class<?>[]{PDDocument.class, Metadata.class}, document, metadata));
            assertEquals("true", metadata.get(PDF.HAS_COLLECTION));
        }
    }

    @Test
    void extractMetadataPreservesEveryAccessPermissionAndDocumentInfoField() throws Exception {
        PDFParser parser = new PDFParser();
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            PDDocumentInformation info = new PDDocumentInformation();
            Calendar created = utcCalendar(2020, Calendar.JANUARY, 2, 3, 4, 5);
            Calendar modified = utcCalendar(2021, Calendar.FEBRUARY, 3, 4, 5, 6);
            info.setTitle("mutation-title");
            info.setAuthor("mutation-author");
            info.setCreator("mutation-tool");
            info.setKeywords("alpha beta");
            info.setProducer("mutation-producer");
            info.setSubject("mutation-subject");
            info.setTrapped("True");
            info.setCreationDate(created);
            info.setModificationDate(modified);
            document.setDocumentInformation(info);
            COSDictionary extensions = new COSDictionary();
            extensions.setItem(COSName.getPDFName("TEST"), new COSDictionary());
            document.getDocumentCatalog().getCOSObject()
                    .setItem(COSName.getPDFName("Extensions"), extensions);

            Metadata metadata = new Metadata();
            invoke(parser, "extractMetadata",
                    new Class<?>[]{PDDocument.class, Metadata.class, ParseContext.class},
                    document, metadata, new ParseContext());

            assertEquals("application/pdf", metadata.get(Metadata.CONTENT_TYPE));
            assertNotNull(metadata.get(AccessPermissions.ASSEMBLE_DOCUMENT));
            assertNotNull(metadata.get(AccessPermissions.FILL_IN_FORM));
            assertNotNull(metadata.get(AccessPermissions.CAN_MODIFY));
            assertNotNull(metadata.get(AccessPermissions.CAN_MODIFY_ANNOTATIONS));
            assertNotNull(metadata.get(AccessPermissions.CAN_PRINT));
            assertNotNull(metadata.get(AccessPermissions.CAN_PRINT_FAITHFUL));
            assertEquals("mutation-producer", metadata.get(PDF.PRODUCER));
            assertEquals("True", metadata.get(PDF.DOC_INFO_TRAPPED));
            assertNotNull(metadata.get(PDF.DOC_INFO_CREATED));
            assertNotNull(metadata.get(TikaCoreProperties.CREATED));
            assertNotNull(metadata.get(TikaCoreProperties.MODIFIED));
            assertEquals("TEST", metadata.get("pdf:found-non-adobe-extension-name"));
        }
    }

    @Test
    void hasXfaReturnsFalseAndRecordsTheResult() throws Exception {
        try (PDDocument document = new PDDocument()) {
            Metadata metadata = new Metadata();
            assertFalse((boolean) invoke(new PDFParser(), "hasXFA",
                    new Class<?>[]{PDDocument.class, Metadata.class}, document, metadata));
            assertEquals("false", metadata.get(PDF.HAS_XFA));
        }
    }

    @Test
    void extractSignaturesCopiesAllObservableSignatureProperties() throws Exception {
        PDFParser parser = new PDFParser();
        try (PDDocument document = new PDDocument()) {
            PDAcroForm form = new PDAcroForm(document);
            document.getDocumentCatalog().setAcroForm(form);
            PDSignatureField unsigned = new PDSignatureField(form);
            PDSignatureField signed = new PDSignatureField(form);
            PDSignature signature = new PDSignature();
            signature.setName("Alice");
            signature.setContactInfo("alice@example.test");
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setLocation("Montreal");
            signature.setReason("Mutation testing");
            signature.setSignDate(utcCalendar(2022, Calendar.MARCH, 4, 0, 0, 0));
            signed.setValue(signature);
            form.setFields(Arrays.asList(unsigned, signed));

            Metadata metadata = new Metadata();
            invoke(parser, "extractSignatures",
                    new Class<?>[]{PDDocument.class, Metadata.class}, document, metadata);

            assertEquals("true", metadata.get(PDF.HAS_SIGNATURE_FIELDS));
            assertEquals("true", metadata.get(TikaCoreProperties.HAS_SIGNATURE));
            assertEquals("Alice", metadata.get(TikaCoreProperties.SIGNATURE_NAME));
            assertNotNull(metadata.get(TikaCoreProperties.SIGNATURE_DATE));
            assertEquals("alice@example.test",
                    metadata.get(TikaCoreProperties.SIGNATURE_CONTACT_INFO));
            assertEquals("Adobe.PPKLite", metadata.get(TikaCoreProperties.SIGNATURE_FILTER));
            assertEquals("Montreal", metadata.get(TikaCoreProperties.SIGNATURE_LOCATION));
            assertEquals("Mutation testing", metadata.get(TikaCoreProperties.SIGNATURE_REASON));
        }
    }

    @Test
    void scanXRefOffsetsUsesRealIncrementalPdfAndCreatesRecord() throws Exception {
        Path path = Path.of(getClass().getResource(
                "/test-documents/testPDF_incrementalUpdates.pdf").toURI());
        PDFParserConfig config = new PDFParserConfig();
        config.setExtractIncrementalUpdateInfo(true);
        config.setParseIncrementalUpdates(true);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (TikaInputStream stream = TikaInputStream.get(path)) {
            invoke(new PDFParser(), "scanXRefOffsets",
                    new Class<?>[]{PDFParserConfig.class, TikaInputStream.class,
                            Metadata.class, ParseContext.class},
                    config, stream, metadata, context);
        }

        assertArrayEquals(new long[]{16242, 41226, 64872},
                metadata.getLongValues(PDF.EOF_OFFSETS));
        assertEquals(2, metadata.getInt(PDF.PDF_INCREMENTAL_UPDATE_COUNT));
        assertNotNull(context.get(IncrementalUpdateRecord.class));
    }

    @Test
    void initRendererKeepsSupportedRendererAndConfiguresFallback() throws Exception {
        PDFParser parser = new PDFParser();
        Renderer supported = mock(Renderer.class);
        when(supported.getSupportedTypes(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.singleton(PDFParser.MEDIA_TYPE));
        parser.setRenderer(supported);
        invoke(parser, "initRenderer",
                new Class<?>[]{PDFParserConfig.class, ParseContext.class},
                new PDFParserConfig(), new ParseContext());
        assertSame(supported, parser.getRenderer());

        PDFParserConfig config = new PDFParserConfig();
        config.getOcr().setDpi(144);
        config.getOcr().setImageType(OcrConfig.ImageType.RGB);
        config.getOcr().setImageFormat(OcrConfig.ImageFormat.JPEG);
        Renderer unsupported = mock(Renderer.class);
        when(unsupported.getSupportedTypes(org.mockito.ArgumentMatchers.any()))
                .thenReturn(Collections.emptySet());
        parser.setRenderer(unsupported);
        invoke(parser, "initRenderer",
                new Class<?>[]{PDFParserConfig.class, ParseContext.class},
                config, new ParseContext());

        PDFBoxRenderer renderer = (PDFBoxRenderer) parser.getRenderer();
        assertEquals(144, field(renderer, "defaultDPI"));
        assertEquals(ImageType.RGB, field(renderer, "defaultImageType"));
        assertEquals("jpeg", field(renderer, "defaultImageFormatName"));
        assertSame(parser.getPDFParserConfig(), parser.getDefaultConfig());
        assertNotNull(parser.getDefaultConfig());
    }
}
