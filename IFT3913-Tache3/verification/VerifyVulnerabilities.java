import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Comparator;
import java.util.List;

import org.xml.sax.InputSource;
import org.xml.sax.helpers.DefaultHandler;

import org.apache.tika.config.loader.TikaObjectMapperFactory;
import org.apache.tika.io.FilenameUtils;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.ExternalProcess;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.parser.external.ExternalParserConfig;
import org.apache.tika.utils.XMLReaderUtils;

/** Local educational probes: only temporary files and harmless stdout markers. */
public class VerifyVulnerabilities {
    public static class Marker {
        public static boolean constructed;
        public Marker() {
            constructed = true;
        }
    }

    private static void require(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError(label);
        }
        System.out.println("CONFIRME: " + label);
    }

    public static void main(String[] args) throws Exception {
        Path root = Files.createTempDirectory("task3-probes-");
        try {
            Path allowed = Files.createDirectory(root.resolve("allowed"));
            Path escaped = FilenameUtils.resolveWithin(allowed, "../outside.txt");
            require(!escaped.startsWith(allowed), "CWE-22: path escapes allowed directory");

            Path marker = Files.writeString(root.resolve("entity.txt"), "TASK3_XXE_MARKER");
            String xml = "<!DOCTYPE doc [<!ENTITY probe SYSTEM \"" + marker.toUri()
                    + "\">]><doc>&probe;</doc>";
            String text = XMLReaderUtils.getDocumentBuilder()
                    .parse(new InputSource(new StringReader(xml)))
                    .getDocumentElement().getTextContent();
            require(text.contains("TASK3_XXE_MARKER"), "CWE-611: local XML entity resolved");

            try (TemporaryResources resources = new TemporaryResources()) {
                resources.setTemporaryFileDirectory(root);
                Path temp = resources.createTempFile();
                require(Files.getPosixFilePermissions(temp).contains(PosixFilePermission.OTHERS_READ),
                        "CWE-732: temporary file has others-read permission");
            }

            ExternalParserConfig config = new ExternalParserConfig();
            config.setCommandLine(List.of("printf", "%s", "${INPUT_FILE}"));
            config.setReturnStdout(true);
            Path input = Files.writeString(root.resolve("input;printf TASK3_SHELL_MARKER;#.txt"), "test");
            Metadata metadata = new Metadata();
            try (TikaInputStream stream = TikaInputStream.get(input)) {
                new ExternalParser(config).parse(stream, new DefaultHandler(), metadata, new ParseContext());
            }
            String stdout = metadata.get(ExternalProcess.STD_OUT);
            require(stdout != null && stdout.endsWith("TASK3_SHELL_MARKER"),
                    "CWE-78: filename interpreted by shell (harmless printf)");

            Object value = TikaObjectMapperFactory.createMapper().readValue(
                    "[\"VerifyVulnerabilities$Marker\",{}]", Object.class);
            require(value instanceof Marker && Marker.constructed,
                    "CWE-502: input selects an application class outside a subtype allowlist");
        } finally {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(path);
                }
            }
        }
    }
}
