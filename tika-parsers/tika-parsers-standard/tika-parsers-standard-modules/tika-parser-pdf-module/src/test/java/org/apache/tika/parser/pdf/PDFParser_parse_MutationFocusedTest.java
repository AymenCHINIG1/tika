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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.updates.IncrementalUpdateRecord;
import org.apache.tika.renderer.pdf.pdfbox.PDFRenderingState;

/**
 * Human-written complementary tests focused on surviving PIT mutations in PDFParser.parse.
 * The existing PDFParserTest and generated/edited tests remain unchanged.
 */
class PDFParser_parse_MutationFocusedTest {

    private byte[] singlePagePdf() throws IOException {
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(output);
            return output.toByteArray();
        }
    }

    @Test
    void parseRestoresOriginalContextObjectsAndClearsIncrementalRecordTemporarily()
            throws Exception {
        ParseContext context = spy(new ParseContext());
        OCRPageCounter oldCounter = new OCRPageCounter();
        IncrementalUpdateRecord oldRecord = mock(IncrementalUpdateRecord.class);
        context.set(OCRPageCounter.class, oldCounter);
        context.set(IncrementalUpdateRecord.class, oldRecord);
        clearInvocations(context); // Exclude test setup from interaction assertions.
        Metadata metadata = new Metadata();

        PDFParser parser = new PDFParser();
        try (TikaInputStream stream = TikaInputStream.get(singlePagePdf())) {
            parser.parse(stream, null, metadata, context);
        }

        assertNotNull(parser.getRenderer());
        assertSame(oldCounter, context.get(OCRPageCounter.class));
        assertSame(oldRecord, context.get(IncrementalUpdateRecord.class));
        verify(context).set(IncrementalUpdateRecord.class, null);
        verify(context).set(OCRPageCounter.class, oldCounter);
        verify(context).set(IncrementalUpdateRecord.class, oldRecord);
        assertNotNull(metadata.get(PDF.OCR_PAGE_COUNT));
    }

    @Test
    void parseRestoresOriginalRenderingState() throws Exception {
        ParseContext context = spy(new ParseContext());
        PDFRenderingState oldState = mock(PDFRenderingState.class);
        context.set(PDFRenderingState.class, oldState);
        clearInvocations(context);

        try (TikaInputStream stream = TikaInputStream.get(singlePagePdf())) {
            new PDFParser().parse(stream, null, new Metadata(), context);
        }

        assertSame(oldState, context.get(PDFRenderingState.class));
        verify(context).set(PDFRenderingState.class, oldState);
    }

    @Test
    void parseCreatesTemporaryRenderingStateWhenSpoolingIsRequired() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setImageStrategy(PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_AT_PAGE_END);
        ParseContext context = spy(new ParseContext());
        context.set(PDFParserConfig.class, config);

        try (TikaInputStream stream = TikaInputStream.get(singlePagePdf())) {
            new PDFParser().parse(stream, null, new Metadata(), context);
        }

        verify(context, atLeastOnce()).set(org.mockito.ArgumentMatchers.eq(PDFRenderingState.class),
                any(PDFRenderingState.class));
        verify(context, atLeastOnce()).set(org.mockito.ArgumentMatchers.eq(PDFRenderingState.class),
                isNull());
    }

    @Test
    void parseHonorsKcmsConfiguration() throws Exception {
        String propertyName = "sun.java2d.cmm";
        String previous = System.getProperty(propertyName);
        try {
            System.clearProperty(propertyName);
            PDFParserConfig config = new PDFParserConfig();
            config.setSetKCMS(true);
            ParseContext context = new ParseContext();
            context.set(PDFParserConfig.class, config);
            try (TikaInputStream stream = TikaInputStream.get(singlePagePdf())) {
                new PDFParser().parse(stream, null, new Metadata(), context);
            }
            assertEquals("sun.java2d.cmm.kcms.KcmsServiceProvider",
                    System.getProperty(propertyName));
        } finally {
            if (previous == null) {
                System.clearProperty(propertyName);
            } else {
                System.setProperty(propertyName, previous);
            }
        }
    }

    @Test
    void parseAcceptsMainMemoryOnlySetting() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setMaxMainMemoryBytes(-1);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        Metadata metadata = new Metadata();
        try (TikaInputStream stream = TikaInputStream.get(singlePagePdf())) {
            new PDFParser().parse(stream, null, metadata, context);
        }
        assertNotNull(metadata.get(PDF.OCR_PAGE_COUNT));
    }

    @Test
    void parseAcceptsZeroMemoryThresholdBoundary() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setMaxMainMemoryBytes(0);
        ParseContext context = new ParseContext();
        context.set(PDFParserConfig.class, config);
        Metadata metadata = new Metadata();
        try (TikaInputStream stream = TikaInputStream.get(singlePagePdf())) {
            new PDFParser().parse(stream, null, metadata, context);
        }
        assertNotNull(metadata.get(PDF.OCR_PAGE_COUNT));
    }
}
