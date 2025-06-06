package org.jabref.logic.exporter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import javafx.collections.FXCollections;

import org.jabref.logic.FilePreferences;
import org.jabref.logic.bibtex.FieldPreferences;
import org.jabref.logic.importer.ImportFormatPreferences;
import org.jabref.logic.importer.fileformat.pdf.PdfEmbeddedBibFileImporter;
import org.jabref.logic.journals.JournalAbbreviationRepository;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.database.BibDatabaseMode;
import org.jabref.model.entry.BibEntry;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.entry.LinkedFile;
import org.jabref.model.entry.Month;
import org.jabref.model.entry.field.StandardField;
import org.jabref.model.entry.types.StandardEntryType;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Answers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EmbeddedBibFilePdfExporterTest {

    @TempDir static Path tempDir;

    private static BibEntry olly2018 = new BibEntry(StandardEntryType.Article);
    private static BibEntry toral2006 = new BibEntry(StandardEntryType.Article);
    private static BibEntry vapnik2000 = new BibEntry(StandardEntryType.Article);

    private EmbeddedBibFilePdfExporter exporter;

    private PdfEmbeddedBibFileImporter importer;

    private BibDatabaseContext databaseContext;
    private JournalAbbreviationRepository abbreviationRepository;
    private FilePreferences filePreferences;

    private static void initBibEntries() throws IOException {
        olly2018.setCitationKey("Olly2018");
        olly2018.setField(StandardField.AUTHOR, "Olly and Johannes");
        olly2018.setField(StandardField.TITLE, "Stefan's palace");
        olly2018.setField(StandardField.JOURNAL, "Test Journal");
        olly2018.setField(StandardField.VOLUME, "1");
        olly2018.setField(StandardField.NUMBER, "1");
        olly2018.setField(StandardField.PAGES, "1-2");
        olly2018.setMonth(Month.MARCH);
        olly2018.setField(StandardField.ISSN, "978-123-123");
        olly2018.setField(StandardField.NOTE, "NOTE");
        olly2018.setField(StandardField.ABSTRACT, "ABSTRACT");
        olly2018.setField(StandardField.COMMENT, "COMMENT");
        olly2018.setField(StandardField.DOI, "10/3212.3123");
        olly2018.setField(StandardField.FILE, ":article_dublinCore.pdf:PDF");
        olly2018.setField(StandardField.GROUPS, "NO");
        olly2018.setField(StandardField.HOWPUBLISHED, "online");
        olly2018.setField(StandardField.KEYWORDS, "k1, k2");
        olly2018.setField(StandardField.OWNER, "me");
        olly2018.setField(StandardField.REVIEW, "review");
        olly2018.setField(StandardField.URL, "https://www.olly2018.edu");

        LinkedFile linkedFile = createDefaultLinkedFile("existing.pdf", tempDir);
        olly2018.setFiles(List.of(linkedFile));

        toral2006.setField(StandardField.AUTHOR, "Toral, Antonio and Munoz, Rafael");
        toral2006.setField(StandardField.TITLE, "A proposal to automatically build and maintain gazetteers for Named Entity Recognition by using Wikipedia");
        toral2006.setField(StandardField.BOOKTITLE, "Proceedings of EACL");
        toral2006.setField(StandardField.PAGES, "56--61");
        toral2006.setField(StandardField.EPRINTTYPE, "asdf");
        toral2006.setField(StandardField.OWNER, "Ich");
        toral2006.setField(StandardField.URL, "www.url.de");

        toral2006.setFiles(List.of(new LinkedFile("non-existing", "path/to/nowhere.pdf", "PDF")));

        vapnik2000.setCitationKey("vapnik2000");
        vapnik2000.setField(StandardField.TITLE, "The Nature of Statistical Learning Theory");
        vapnik2000.setField(StandardField.PUBLISHER, "Springer Science + Business Media");
        vapnik2000.setField(StandardField.AUTHOR, "Vladimir N. Vapnik");
        vapnik2000.setField(StandardField.DOI, "10.1007/978-1-4757-3264-1");
        vapnik2000.setField(StandardField.OWNER, "Ich");
    }

    /**
     * Create a temporary PDF-file with a single empty page.
     */
    @BeforeEach
    void setUp() throws IOException {
        abbreviationRepository = mock(JournalAbbreviationRepository.class);

        filePreferences = mock(FilePreferences.class);
        when(filePreferences.getUserAndHost()).thenReturn(tempDir.toAbsolutePath().toString());
        when(filePreferences.shouldStoreFilesRelativeToBibFile()).thenReturn(false);

        BibDatabaseMode bibDatabaseMode = BibDatabaseMode.BIBTEX;
        BibEntryTypesManager bibEntryTypesManager = new BibEntryTypesManager();
        FieldPreferences fieldPreferences = new FieldPreferences(
                true,
                List.of(StandardField.MONTH),
                List.of());

        exporter = new EmbeddedBibFilePdfExporter(bibDatabaseMode, bibEntryTypesManager, fieldPreferences);

        ImportFormatPreferences importFormatPreferences = mock(ImportFormatPreferences.class, Answers.RETURNS_DEEP_STUBS);
        when(importFormatPreferences.fieldPreferences().getNonWrappableFields()).thenReturn(FXCollections.emptyObservableList());
        importer = new PdfEmbeddedBibFileImporter(importFormatPreferences);

        databaseContext = new BibDatabaseContext();
        BibDatabase dataBase = databaseContext.getDatabase();

        initBibEntries();
        dataBase.insertEntry(olly2018);
        dataBase.insertEntry(toral2006);
        dataBase.insertEntry(vapnik2000);
    }

    @ParameterizedTest
    @MethodSource("provideBibEntriesWithValidPdfFileLinks")
    void successfulExportToAllFilesOfEntry(BibEntry bibEntryWithValidPdfFileLink) throws IOException, SaveException, ParserConfigurationException, TransformerException {
        assertTrue(exporter.exportToAllFilesOfEntry(databaseContext, filePreferences, bibEntryWithValidPdfFileLink, List.of(olly2018), abbreviationRepository));
    }

    @ParameterizedTest
    @MethodSource("provideBibEntriesWithInvalidPdfFileLinks")
    void unsuccessfulExportToAllFilesOfEntry(BibEntry bibEntryWithValidPdfFileLink) throws IOException, SaveException, ParserConfigurationException, TransformerException {
        assertFalse(exporter.exportToAllFilesOfEntry(databaseContext, filePreferences, bibEntryWithValidPdfFileLink, List.of(olly2018), abbreviationRepository));
    }

    public static Stream<Arguments> provideBibEntriesWithValidPdfFileLinks() {
        return Stream.of(Arguments.of(olly2018));
    }

    public static Stream<Arguments> provideBibEntriesWithInvalidPdfFileLinks() {
        return Stream.of(Arguments.of(vapnik2000), Arguments.of(toral2006));
    }

    @ParameterizedTest
    @MethodSource("providePathsToValidPDFs")
    void successfulExportToFileByPath(Path path) throws IOException, SaveException, ParserConfigurationException, TransformerException {
        assertTrue(exporter.exportToFileByPath(databaseContext, filePreferences, path, abbreviationRepository));
    }

    @ParameterizedTest
    @MethodSource("providePathsToInvalidPDFs")
    void unsuccessfulExportToFileByPath(Path path) throws IOException, SaveException, ParserConfigurationException, TransformerException {
        assertFalse(exporter.exportToFileByPath(databaseContext, filePreferences, path, abbreviationRepository));
    }

    public static Stream<Arguments> providePathToNewPDFs() {
        return Stream.of(Arguments.of(tempDir.resolve("original.pdf").toAbsolutePath()));
    }

    public static Stream<Arguments> providePathsToValidPDFs() {
        return Stream.of(Arguments.of(tempDir.resolve("existing.pdf").toAbsolutePath()));
    }

    public static Stream<Arguments> providePathsToInvalidPDFs() throws IOException {
        LinkedFile existingFileThatIsNotLinked = createDefaultLinkedFile("notlinked.pdf", tempDir);
        return Stream.of(
                Arguments.of(Path.of("")),
                Arguments.of(tempDir.resolve("path/to/nowhere.pdf").toAbsolutePath()),
                Arguments.of(Path.of(existingFileThatIsNotLinked.getLink())));
    }

    private static LinkedFile createDefaultLinkedFile(String fileName, Path tempDir) throws IOException {
        Path pdfFile = tempDir.resolve(fileName);
        try (PDDocument pdf = new PDDocument()) {
            pdf.addPage(new PDPage());
            pdf.save(pdfFile.toAbsolutePath().toString());
        }

        return new LinkedFile("A linked pdf", pdfFile, "PDF");
    }

    @ParameterizedTest
    @MethodSource("providePathToNewPDFs")
    void roundtripExportImport(Path path) throws IOException {
        BibEntry expected = new BibEntry(StandardEntryType.Misc)
                .withCitationKey("test")
                .withField(StandardField.AUTHOR, "Test Author")
                .withField(StandardField.TITLE, "Test Title")
                .withField(StandardField.URL, "http://example.com")
                .withField(StandardField.DATE, "2020-10-14");
        expected.setChanged(true);

        List<BibEntry> expectedEntries = List.of(expected);

        exporter.export(databaseContext, path, expectedEntries);

        List<BibEntry> importedEntries = importer.importDatabase(path).getDatabase().getEntries();

        assertEquals(expectedEntries, importedEntries);
    }
}
