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
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.sax.WriteOutContentHandler;

/** Tests generated with ChatUniTest and Code Llama, with documented corrections. */
public class RegexCaptureParserChatUniTestTest {

    @Test
    public void testGetSupportedTypes() {
        RegexCaptureParser parser = new RegexCaptureParser();

        Set<MediaType> supportedTypes = parser.getSupportedTypes(new ParseContext());

        assertEquals(Set.of(MediaType.TEXT_PLAIN), supportedTypes);
    }

    @Test
    public void testParseCapturesMatchesAndWritesContent() throws Exception {
        String input =
                "This is a test string with some regex capture groups: (test1) and (test2).";

        Map<String, String> captureMap = new HashMap<>();
        captureMap.put("capture1", "\\((test1)\\)");
        captureMap.put("capture2", "\\((test2)\\)");

        Map<String, String> matchMap = new HashMap<>();
        matchMap.put("match1", "\\(test1\\)");
        matchMap.put("match2", "\\(test2\\)");

        RegexCaptureParserConfig config = new RegexCaptureParserConfig();
        config.setCaptureMap(captureMap);
        config.setMatchMap(matchMap);
        config.setWriteContent(true);

        RegexCaptureParser parser = new RegexCaptureParser(config);
        Metadata metadata = new Metadata();
        WriteOutContentHandler handler = new WriteOutContentHandler();

        try (TikaInputStream stream =
                TikaInputStream.get(input.getBytes(StandardCharsets.UTF_8))) {
            parser.parse(stream, handler, metadata, new ParseContext());
        }

        assertEquals(input, handler.toString());
        assertEquals("test1", metadata.get("capture1"));
        assertEquals("test2", metadata.get("capture2"));
        assertEquals("true", metadata.get("match1"));
        assertEquals("true", metadata.get("match2"));
    }
}
