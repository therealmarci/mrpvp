package at.marcelr.mrpvp;

import at.marcelr.mrpvp.util.LanguageChoice;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which language file a player gets for their Minecraft language - must match the client mod, which has de_de,
 * de_at, de_ch and en_us (Minecraft falls back to en_us for everything else).
 */
class LanguageChoiceTest {

    private static final Set<String> FILES = Set.of("de", "en");

    @Test
    void germanVariantsGetGerman() {
        assertEquals("de", LanguageChoice.resolve(FILES, "de_DE", "en"));
        assertEquals("de", LanguageChoice.resolve(FILES, "de_at", "en"));
        assertEquals("de", LanguageChoice.resolve(FILES, "de_ch", "en"));
    }

    @Test
    void everythingElseGetsTheFallback() {
        assertEquals("en", LanguageChoice.resolve(FILES, "en_us", "en"));
        assertEquals("en", LanguageChoice.resolve(FILES, "fr_fr", "en"));
        assertEquals("en", LanguageChoice.resolve(FILES, "nds_de", "en"));
    }

    @Test
    void ownLanguageFilesAreUsed() {
        assertEquals("fr", LanguageChoice.resolve(Set.of("de", "en", "fr"), "fr_ca", "en"));
    }

    @Test
    void configValuesWithAliases() {
        assertEquals("de", LanguageChoice.resolve(FILES, "Deutsch", "en"));
        assertEquals("en", LanguageChoice.resolve(FILES, "english", "de"));
        assertEquals("de", LanguageChoice.resolve(FILES, "", "de"));
    }
}
