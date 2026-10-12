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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PDFParser_shouldHandleXFAOnly_21_0_Test {

    @Test
    public void testShouldHandleXFAOnly_withXFAAndConfigSetToTrue() throws Exception {
        PDFParser parser = new PDFParser();
        PDFParserConfig config = mock(PDFParserConfig.class);
        when(config.isIfXFAExtractOnlyXFA()).thenReturn(true);
        Method method = PDFParser.class.getDeclaredMethod("shouldHandleXFAOnly", boolean.class, PDFParserConfig.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(parser, true, config);
        assertTrue(result);
    }

    @Test
    public void testShouldHandleXFAOnly_withXFAAndConfigSetToFalse() throws Exception {
        PDFParser parser = new PDFParser();
        PDFParserConfig config = mock(PDFParserConfig.class);
        when(config.isIfXFAExtractOnlyXFA()).thenReturn(false);
        Method method = PDFParser.class.getDeclaredMethod("shouldHandleXFAOnly", boolean.class, PDFParserConfig.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(parser, true, config);
        assertFalse(result);
    }

    @Test
    public void testShouldHandleXFAOnly_withoutXFAAndConfigSetToTrue() throws Exception {
        PDFParser parser = new PDFParser();
        PDFParserConfig config = mock(PDFParserConfig.class);
        when(config.isIfXFAExtractOnlyXFA()).thenReturn(true);
        Method method = PDFParser.class.getDeclaredMethod("shouldHandleXFAOnly", boolean.class, PDFParserConfig.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(parser, false, config);
        assertFalse(result);
    }

    @Test
    public void testShouldHandleXFAOnly_withoutXFAAndConfigSetToFalse() throws Exception {
        PDFParser parser = new PDFParser();
        PDFParserConfig config = mock(PDFParserConfig.class);
        when(config.isIfXFAExtractOnlyXFA()).thenReturn(false);
        Method method = PDFParser.class.getDeclaredMethod("shouldHandleXFAOnly", boolean.class, PDFParserConfig.class);
        method.setAccessible(true);
        boolean result = (boolean) method.invoke(parser, false, config);
        assertFalse(result);
    }
}
