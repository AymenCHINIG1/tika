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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class PDFParser_shouldSpool_8_0_Test {

    private final PDFParser pdfParser = new PDFParser();

    @Mock
    private PDFParserConfig config;

    @Test
    public void testShouldSpool_WithRenderPagesBeforeParse() throws Exception {
        when(config.getImageStrategy()).thenReturn(PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_BEFORE_PARSE);
        Method method = PDFParser.class.getDeclaredMethod("shouldSpool", PDFParserConfig.class);
        method.setAccessible(true);
        assertTrue((boolean) method.invoke(pdfParser, config));
    }


    @Test
    public void testShouldSpool_WithRenderPagesAtPageEnd() throws Exception {
        when(config.getImageStrategy())
            .thenReturn(PDFParserConfig.IMAGE_STRATEGY.RENDER_PAGES_AT_PAGE_END);

        Method method = PDFParser.class.getDeclaredMethod(
            "shouldSpool", PDFParserConfig.class
        );
        method.setAccessible(true);

        boolean result = (boolean) method.invoke(pdfParser, config);

        assertTrue(result);
    }
}
