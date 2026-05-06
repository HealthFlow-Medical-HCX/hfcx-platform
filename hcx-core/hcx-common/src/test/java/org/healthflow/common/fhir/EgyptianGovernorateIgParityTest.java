package org.healthflow.common.fhir;

import org.healthflow.hcx.enums.EgyptianGovernorate;
import org.junit.Assume;
import org.junit.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Parity test guarding that the Egyptian-governorate FSH CodeSystem in the
 * HealthFlow Egyptian FHIR IG (fhir/egyptian-ig/input/fsh/codesystems/EgyptianGovernorate.fsh)
 * stays in lock-step with the {@link EgyptianGovernorate} Java enum.
 *
 * <p>If the FSH file is not reachable from the test working directory (e.g. the
 * module is being built in isolation or in a slim CI matrix), the test is
 * skipped via {@link Assume#assumeTrue(boolean)} rather than failing. This
 * keeps unit-test runs hermetic while still catching drift whenever the
 * full repository tree is present.
 */
public class EgyptianGovernorateIgParityTest {

    /** Lines like {@code * #cairo "Cairo"}. */
    private static final Pattern CODE_LINE = Pattern.compile("^\\*\\s+#(\\S+)\\s+\".*\"\\s*$");

    /**
     * Locate the FSH file relative to {@code user.dir}. We may be invoked
     * from {@code hcx-core/hcx-common/}, from {@code hcx-core/}, or from
     * the repo root, so try several plausible roots.
     */
    private static Path locateFshFile() {
        String userDir = System.getProperty("user.dir", ".");
        String[] candidates = new String[]{
                "fhir/egyptian-ig/input/fsh/codesystems/EgyptianGovernorate.fsh",
                "../fhir/egyptian-ig/input/fsh/codesystems/EgyptianGovernorate.fsh",
                "../../fhir/egyptian-ig/input/fsh/codesystems/EgyptianGovernorate.fsh",
                "../../../fhir/egyptian-ig/input/fsh/codesystems/EgyptianGovernorate.fsh",
        };
        for (String c : candidates) {
            Path p = Paths.get(userDir, c).normalize();
            if (Files.exists(p)) {
                return p;
            }
        }
        return null;
    }

    private static String enumNameToFshCode(EgyptianGovernorate gov) {
        // Java enum names use UPPER_SNAKE_CASE; FSH codes use lower-kebab-case.
        return gov.name().toLowerCase().replace('_', '-');
    }

    @Test
    public void fshCodeSystemMatchesJavaEnum() throws IOException {
        Path fsh = locateFshFile();
        Assume.assumeTrue(
                "Skipping: EgyptianGovernorate.fsh not found relative to user.dir="
                        + System.getProperty("user.dir"),
                fsh != null);

        Set<String> fshCodes = new HashSet<>();
        try (Stream<String> lines = Files.lines(fsh)) {
            lines.forEach(line -> {
                Matcher m = CODE_LINE.matcher(line);
                if (m.matches()) {
                    fshCodes.add(m.group(1));
                }
            });
        }

        Set<String> enumCodes = Stream.of(EgyptianGovernorate.values())
                .map(EgyptianGovernorateIgParityTest::enumNameToFshCode)
                .collect(Collectors.toCollection(HashSet::new));

        assertFalse("No codes parsed from FSH file " + fsh, fshCodes.isEmpty());
        assertEquals(
                "Egyptian-governorate codes drifted between Java enum and FSH CodeSystem. "
                        + "FSH-only=" + diff(fshCodes, enumCodes)
                        + ", Enum-only=" + diff(enumCodes, fshCodes),
                enumCodes,
                fshCodes);
        assertEquals("Expected exactly 27 governorates", 27, fshCodes.size());
    }

    private static Set<String> diff(Set<String> a, Set<String> b) {
        Set<String> out = new HashSet<>(a);
        out.removeAll(b);
        return out;
    }
}
