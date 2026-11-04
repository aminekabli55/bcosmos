package com.bcosmos.core.quality;

import static org.assertj.core.api.Assertions.assertThat;

import com.bcosmos.core.Findings;
import com.bcosmos.core.model.Finding;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FingerprintTest {

    @Test
    @DisplayName("sha256 of a known input is the real SHA-256 digest")
    void hashesWithSha256() {
        assertThat(Fingerprint.sha256Hex("a"))
                .isEqualTo("ca978112ca1bbdcafac231b39a23dc4da786eff8147c4e72b9807785afee48bb");
        assertThat(Fingerprint.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(Fingerprint.sha256Hex("")).hasSize(64);
    }

    @Test
    @DisplayName("the fingerprint is stable and only depends on rule, symbol and normalized snippet")
    void isStableAndSpecific() {
        String reference = Fingerprint.of("EmptyCatchBlock", "com.acme.UserService.load", "catch (Exception e) { }");

        assertThat(Fingerprint.of("EmptyCatchBlock", "com.acme.UserService.load", "catch (Exception e) { }"))
                .isEqualTo(reference);
        assertThat(Fingerprint.of("UnusedPrivateField", "com.acme.UserService.load", "catch (Exception e) { }"))
                .isNotEqualTo(reference);
        assertThat(Fingerprint.of("EmptyCatchBlock", "com.acme.Other.load", "catch (Exception e) { }"))
                .isNotEqualTo(reference);
        assertThat(Fingerprint.of("EmptyCatchBlock", "com.acme.UserService.load", "catch (Exception ex) { }"))
                .isNotEqualTo(reference);
    }

    @Test
    @DisplayName("line numbers are excluded, so moving code keeps the fingerprint (spec 4.2)")
    void ignoresLineNumbers() {
        Finding nearTheTop = Findings.pmd("EmptyCatchBlock", "com.acme.UserService.load", "catch (Exception e) { }", 10, 12);
        Finding farBelow = Findings.pmd("EmptyCatchBlock", "com.acme.UserService.load", "catch (Exception e) { }", 240, 242);

        assertThat(farBelow.fingerprint()).isEqualTo(nearTheTop.fingerprint());
    }

    @Test
    @DisplayName("reformatting the snippet does not change the fingerprint, because it is normalized first")
    void survivesReformatting() {
        Finding compact = Findings.pmd("EmptyCatchBlock", "com.acme.UserService.load", "catch (Exception e) { }", 10, 10);
        Finding spreadOverLines = Findings.pmd("EmptyCatchBlock", "com.acme.UserService.load",
                "catch (Exception e) {\n    \n}", 10, 12);

        assertThat(spreadOverLines.fingerprint()).isEqualTo(compact.fingerprint());
    }

    @Test
    @DisplayName("a null symbol or snippet still produces a deterministic fingerprint")
    void toleratesMissingParts() {
        assertThat(Fingerprint.of("EmptyCatchBlock", null, null))
                .isEqualTo(Fingerprint.of("EmptyCatchBlock", null, null));
        assertThat(Fingerprint.of("EmptyCatchBlock", null, null)).hasSize(64);
    }
}
