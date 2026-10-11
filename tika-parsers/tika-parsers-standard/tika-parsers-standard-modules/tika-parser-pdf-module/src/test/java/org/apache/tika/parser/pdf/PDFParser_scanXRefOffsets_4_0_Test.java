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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.PDF;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.updates.IsIncrementalUpdate;

class PDFParser_scanXRefOffsets_4_0_Test {

    @TempDir
    Path tempDir;

    private PDFParser parser;
    private Method scanXRefOffsets;

    @BeforeEach
    void setUp() throws Exception {
        parser = new PDFParser();
        scanXRefOffsets = PDFParser.class.getDeclaredMethod(
                "scanXRefOffsets", PDFParserConfig.class, TikaInputStream.class,
                Metadata.class, ParseContext.class);
        scanXRefOffsets.setAccessible(true);
    }

    private void invokeScan(PDFParserConfig config, TikaInputStream stream,
                            Metadata metadata, ParseContext context) throws Exception {
        scanXRefOffsets.invoke(parser, config, stream, metadata, context);
    }

    @Test
    void testScanXRefOffsetsNoIncrementalUpdates() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setParseIncrementalUpdates(false);
        config.setExtractIncrementalUpdateInfo(false);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        try (TikaInputStream stream = TikaInputStream.get(new byte[0])) {
            invokeScan(config, stream, metadata, context);
        }

        assertEquals(0, metadata.getValues(PDF.EOF_OFFSETS).length);
        assertNull(metadata.get(PDF.PDF_INCREMENTAL_UPDATE_COUNT));
    }

    @Test
    void testScanXRefOffsetsAlreadyIncrementalUpdate() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setParseIncrementalUpdates(true);
        config.setExtractIncrementalUpdateInfo(true);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        IsIncrementalUpdate incoming = mock(IsIncrementalUpdate.class);
        context.set(IsIncrementalUpdate.class, incoming);

        try (TikaInputStream stream = TikaInputStream.get(new byte[0])) {
            invokeScan(config, stream, metadata, context);
        }

        assertNull(context.get(IsIncrementalUpdate.class));
        assertEquals(0, metadata.getValues(PDF.EOF_OFFSETS).length);
        assertNull(metadata.get(PDF.PDF_INCREMENTAL_UPDATE_COUNT));
    }

    @Test
    void testScanXRefOffsetsEmptyInputRecordsZeroUpdates() throws Exception {
        PDFParserConfig config = new PDFParserConfig();
        config.setParseIncrementalUpdates(false);
        config.setExtractIncrementalUpdateInfo(true);
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();
        Path input = tempDir.resolve("empty.pdf");
        Files.write(input, new byte[0]);

        try (TikaInputStream stream = TikaInputStream.get(input)) {
            invokeScan(config, stream, metadata, context);
        }

        assertEquals(0, metadata.getValues(PDF.EOF_OFFSETS).length);
        assertEquals("0", metadata.get(PDF.PDF_INCREMENTAL_UPDATE_COUNT));
    }
}
