package com.bcosmos.core.language;

import com.bcosmos.core.typescript.TypeScriptAdapter;
import com.bcosmos.core.typescript.TypeScriptParsedFile;

import java.nio.file.Path;

class TypeScriptAdapterContractTest extends LanguageAdapterContractTest<TypeScriptParsedFile> {

    @Override
    protected LanguageAdapter<TypeScriptParsedFile> createAdapter() {
        return new TypeScriptAdapter();
    }

    @Override
    protected String expectedLanguageId() {
        return "typescript";
    }

    @Override
    protected Path validFilePath() {
        return Path.of("src/app/user.service.ts");
    }

    @Override
    protected String validSourceCode() {
        return """
                export interface User {
                    id: number;
                    name: string;
                }

                export class UserService {
                    private userName: string = 'alice';

                    public getUser(): string {
                        return this.userName;
                    }
                }
                """;
    }

    @Override
    protected Path alternativeExtensionFilePath() {
        return Path.of("src/components/Button.tsx");
    }

    @Override
    protected Path unsupportedFilePath() {
        return Path.of("src/app/user.service.python");
    }

    @Override
    protected Path invalidFilePath() {
        return Path.of("src/app/broken.ts");
    }

    @Override
    protected String invalidSourceCode() {
        return "export class Broken { public def( {";
    }

    @Override
    protected String sourceWithViolation() {
        return """
                export class TestService {
                    public execute(): void {
                        try {
                            const x = 1;
                        } catch (e) {
                            // empty catch block
                        }
                    }
                }
                """;
    }

    @Override
    protected String expectedRuleId() {
        return "no-empty";
    }
}
