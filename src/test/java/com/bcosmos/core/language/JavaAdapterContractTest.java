package com.bcosmos.core.language;

import com.bcosmos.core.java.JavaAdapter;
import com.bcosmos.core.java.JavaParsedFile;

import java.nio.file.Path;

class JavaAdapterContractTest extends LanguageAdapterContractTest<JavaParsedFile> {

    @Override
    protected LanguageAdapter<JavaParsedFile> createAdapter() {
        return new JavaAdapter();
    }

    @Override
    protected String expectedLanguageId() {
        return "java";
    }

    @Override
    protected Path validFilePath() {
        return Path.of("src/com/acme/OrderService.java");
    }

    @Override
    protected String validSourceCode() {
        return """
                package com.acme;
                public class OrderService {
                    private String orderId;
                    public String getOrderId() { return orderId; }
                }
                """;
    }

    @Override
    protected Path alternativeExtensionFilePath() {
        return Path.of("src/com/acme/OrderService.java");
    }

    @Override
    protected Path unsupportedFilePath() {
        return Path.of("src/com/acme/OrderService.python");
    }

    @Override
    protected Path invalidFilePath() {
        return Path.of("src/com/acme/Broken.java");
    }

    @Override
    protected String invalidSourceCode() {
        return "public class Broken { public void broken( {";
    }

    @Override
    protected String sourceWithViolation() {
        return """
                package com.acme;
                public class OrderService {
                    public void run() {
                        try {
                            int x = 1;
                        } catch (IllegalStateException e) {
                            // empty catch block
                        }
                    }
                }
                """;
    }

    @Override
    protected String expectedRuleId() {
        return "EmptyCatchBlock";
    }
}
