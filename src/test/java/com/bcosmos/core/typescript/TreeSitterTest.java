package com.bcosmos.core.typescript;

import com.bcosmos.core.model.Symbol;
import com.bcosmos.core.model.SymbolKind;
import org.junit.jupiter.api.Test;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterTypescript;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TreeSitterTest {

    @Test
    void extractsExpectedSymbols() {
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterTypescript());

        String code = """
                export interface User {
                    id: number;
                    name: string;
                }

                export class UserService {
                    private cached: any;

                    constructor(private http: any) {}

                    public findUser(id: number): string {
                        return 'ok';
                    }
                }

                export function helper(): void {}
                """;

        TSTree tree = parser.parseString(null, code);
        List<Symbol> symbols = TypeScriptSymbolExtractor.extract(tree, code);

        assertThat(symbols).extracting(Symbol::name)
                .contains("User", "id", "name", "UserService", "cached", "constructor", "findUser", "helper");

        assertThat(symbols).extracting(Symbol::kind)
                .contains(SymbolKind.INTERFACE, SymbolKind.CLASS, SymbolKind.CONSTRUCTOR, SymbolKind.METHOD, SymbolKind.FIELD);
    }
}
