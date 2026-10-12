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

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.updates.IncrementalUpdateRecord;

class PDFParser_parse_ContextRestorationTest {

    @Test
    void restoresExistingContextEntriesAfterSuccessfulParse() throws Exception {
        PDFParser parser = new PDFParser();
        ParseContext context = new ParseContext();
        OCRPageCounter originalCounter = new OCRPageCounter();
        IncrementalUpdateRecord originalRecord = mock(IncrementalUpdateRecord.class);
        context.set(OCRPageCounter.class, originalCounter);
        context.set(IncrementalUpdateRecord.class, originalRecord);
        Metadata metadata = new Metadata();

        byte[] pdfBytes;
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new org.apache.pdfbox.pdmodel.PDPage());
            document.save(output);
            pdfBytes = output.toByteArray();
        }

        try (TikaInputStream stream = TikaInputStream.get(pdfBytes)) {
            parser.parse(stream, null, metadata, context);
        }

        assertSame(originalCounter, context.get(OCRPageCounter.class));
        assertSame(originalRecord, context.get(IncrementalUpdateRecord.class));
    }

    @Test
    void restoresExistingContextEntriesWhenParsingFails() throws Exception {
        PDFParser parser = new PDFParser();
        ParseContext context = new ParseContext();
        OCRPageCounter originalCounter = new OCRPageCounter();
        IncrementalUpdateRecord originalRecord = mock(IncrementalUpdateRecord.class);
        context.set(OCRPageCounter.class, originalCounter);
        context.set(IncrementalUpdateRecord.class, originalRecord);

        try (TikaInputStream stream = TikaInputStream.get(new byte[0])) {
            assertThrows(IOException.class,
                    () -> parser.parse(stream, null, new Metadata(), context));
        }

        assertSame(originalCounter, context.get(OCRPageCounter.class));
        assertSame(originalRecord, context.get(IncrementalUpdateRecord.class));
    }

    @Test
    void restoresNullContextEntriesWhenParsingFails() throws Exception {
        PDFParser parser = new PDFParser();
        ParseContext context = new ParseContext();

        try (TikaInputStream stream = TikaInputStream.get(new byte[0])) {
            assertThrows(IOException.class,
                    () -> parser.parse(stream, null, new Metadata(), context));
        }

        assertNull(context.get(OCRPageCounter.class));
        assertNull(context.get(IncrementalUpdateRecord.class));
    }
}
