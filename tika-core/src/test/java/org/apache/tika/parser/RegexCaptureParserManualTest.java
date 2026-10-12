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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.JsonConfig;

/** Manually written test targeting the mutant left uncovered after AI test generation. */
public class RegexCaptureParserManualTest {

    @Test
    public void testGetConfigReturnsExactConfigurationInstance() {
        RegexCaptureParserConfig config = new RegexCaptureParserConfig();
        RegexCaptureParser parser = new RegexCaptureParser(config);

        assertSame(config, parser.getConfig());
    }

    @Test
    public void testJsonConfigConstructorBuildsParserConfiguration() {
        JsonConfig jsonConfig = () -> """
                {
                  "captureMap": {"captured": "^Value: (.+)$"},
                  "matchMap": {"matched": "^Value:"},
                  "writeContent": true
                }
                """;

        RegexCaptureParser parser = new RegexCaptureParser(jsonConfig);
        RegexCaptureParserConfig config = parser.getConfig();

        assertEquals("^Value: (.+)$", config.getCaptureMap().get("captured"));
        assertEquals("^Value:", config.getMatchMap().get("matched"));
        assertTrue(config.isWriteContent());
    }
}
