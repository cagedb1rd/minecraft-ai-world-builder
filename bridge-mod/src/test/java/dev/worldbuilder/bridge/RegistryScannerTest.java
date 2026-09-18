package dev.worldbuilder.bridge;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RegistryScannerTest {
    @Test void matchesRegistryIdOrLocalizedNameCaseInsensitively(){assertTrue(RegistryScanner.matches("example:black_roof_tile","Black Japanese Tile","roof"));assertTrue(RegistryScanner.matches("example:block","Dark Timber Beam","TIMBER"));assertFalse(RegistryScanner.matches("example:block","Plain Block","window"));}
}
