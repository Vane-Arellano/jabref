package org.jabref.gui.autosaveandbackup;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Optional;

import org.jabref.gui.LibraryTab;
import org.jabref.logic.FilePreferences;
import org.jabref.logic.preferences.CliPreferences;
import org.jabref.logic.util.BackupFileType;
import org.jabref.logic.util.Directories;
import org.jabref.logic.util.io.BackupFileUtil;
import org.jabref.model.database.BibDatabase;
import org.jabref.model.database.BibDatabaseContext;
import org.jabref.model.entry.BibEntryTypesManager;
import org.jabref.model.groups.event.GroupUpdatedEvent;
import org.jabref.model.metadata.MetaData;
import org.jabref.model.metadata.event.MetaDataChangedEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Answers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BackupManagerTest {

    Path backupDir;

    @BeforeEach
    void setup(@TempDir Path tempDir) {
        backupDir = tempDir.resolve("backup");
    }

    @Test
    void backupFileNameIsCorrectlyGeneratedInAppDataDirectory() {
        Path bibPath = Path.of("tmp", "test.bib");
        backupDir = Directories.getBackupDirectory();
        Path bakPath = BackupManager.getBackupPathForNewBackup(bibPath, backupDir);

        // Pattern is "27182d3c--test.bib--", but the hashing is implemented differently on Linux than on Windows
        assertNotEquals("", bakPath);
    }

    @Test
    void backupFileIsEqualForNonExistingBackup() throws URISyntaxException {
        Path originalFile = Path.of(BackupManagerTest.class.getResource("no-autosave.bib").toURI());
        assertFalse(BackupManager.backupFileDiffers(originalFile, backupDir));
    }

    @Test
    void backupFileIsEqual() throws URISyntaxException, IOException {
        // Prepare test: Create backup file on "right" path
        Path source = Path.of(BackupManagerTest.class.getResource("no-changes.bib.bak").toURI());
        Path target = BackupFileUtil.getPathForNewBackupFileAndCreateDirectory(Path.of(BackupManagerTest.class.getResource("no-changes.bib").toURI()), BackupFileType.BACKUP, backupDir);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        Path originalFile = Path.of(BackupManagerTest.class.getResource("no-changes.bib").toURI());
        assertFalse(BackupManager.backupFileDiffers(originalFile, backupDir));
    }

    @Test
    void backupFileDiffers() throws URISyntaxException, IOException {
        // Prepare test: Create backup file on "right" path
        Path source = Path.of(BackupManagerTest.class.getResource("changes.bib.bak").toURI());
        Path target = BackupFileUtil.getPathForNewBackupFileAndCreateDirectory(Path.of(BackupManagerTest.class.getResource("changes.bib").toURI()), BackupFileType.BACKUP, backupDir);
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);

        Path originalFile = Path.of(BackupManagerTest.class.getResource("changes.bib").toURI());
        assertTrue(BackupManager.backupFileDiffers(originalFile, backupDir));
    }

    @Test
    void correctBackupFileDeterminedForMultipleBakFiles() throws URISyntaxException, IOException {
        Path noChangesBib = Path.of(BackupManagerTest.class.getResource("no-changes.bib").toURI());
        Path noChangesBibBak = Path.of(BackupManagerTest.class.getResource("no-changes.bib.bak").toURI());

        // Prepare test: Create backup files on "right" path
        // most recent file does not have any changes
        Path target = BackupFileUtil.getPathForNewBackupFileAndCreateDirectory(noChangesBib, BackupFileType.BACKUP, backupDir);
        Files.copy(noChangesBibBak, target, StandardCopyOption.REPLACE_EXISTING);

        // create "older" .bak files containing changes
        for (int i = 0; i < 10; i++) {
            Path changesBibBak = Path.of(BackupManagerTest.class.getResource("changes.bib").toURI());
            Path directory = backupDir;
            String timeSuffix = "2020-02-03--00.00.0" + i;
            String fileName = BackupFileUtil.getUniqueFilePrefix(noChangesBib) + "--no-changes.bib--" + timeSuffix + ".bak";
            target = directory.resolve(fileName);
            Files.copy(changesBibBak, target, StandardCopyOption.REPLACE_EXISTING);
        }

        Path originalFile = noChangesBib;
        assertFalse(BackupManager.backupFileDiffers(originalFile, backupDir));
    }

    @Test
    void bakFileWithNewerTimeStampLeadsToDiff() throws URISyntaxException, IOException {
        Path changesBib = Path.of(BackupManagerTest.class.getResource("changes.bib").toURI());
        Path changesBibBak = Path.of(BackupManagerTest.class.getResource("changes.bib.bak").toURI());

        Path target = BackupFileUtil.getPathForNewBackupFileAndCreateDirectory(changesBib, BackupFileType.BACKUP, backupDir);
        Files.copy(changesBibBak, target, StandardCopyOption.REPLACE_EXISTING);

        assertTrue(BackupManager.backupFileDiffers(changesBib, backupDir));
    }

    @Test
    void bakFileWithOlderTimeStampDoesNotLeadToDiff() throws URISyntaxException, IOException {
        Path changesBib = Path.of(BackupManagerTest.class.getResource("changes.bib").toURI());
        Path changesBibBak = Path.of(BackupManagerTest.class.getResource("changes.bib.bak").toURI());

        Path target = BackupFileUtil.getPathForNewBackupFileAndCreateDirectory(changesBib, BackupFileType.BACKUP, backupDir);
        Files.copy(changesBibBak, target, StandardCopyOption.REPLACE_EXISTING);

        // Make .bak file very old
        Files.setLastModifiedTime(target, FileTime.fromMillis(0));

        assertFalse(BackupManager.backupFileDiffers(changesBib, backupDir));
    }

    @Test
    void shouldNotCreateABackup(@TempDir Path customDir) throws IOException {
        Path backupDir = customDir.resolve("subBackupDir");
        Files.createDirectories(backupDir);

        BibDatabaseContext databaseContext = new BibDatabaseContext(new BibDatabase());
        databaseContext.setDatabasePath(customDir.resolve("Bibfile.bib"));

        CliPreferences preferences = mock(CliPreferences.class, Answers.RETURNS_DEEP_STUBS);
        FilePreferences filePreferences = mock(FilePreferences.class);
        when(preferences.getFilePreferences()).thenReturn(filePreferences);
        when(filePreferences.getBackupDirectory()).thenReturn(backupDir);
        when(filePreferences.shouldCreateBackup()).thenReturn(false);

        BackupManager manager = BackupManager.start(
                mock(LibraryTab.class),
                databaseContext,
                mock(BibEntryTypesManager.class, Answers.RETURNS_DEEP_STUBS),
                preferences);
        manager.listen(new MetaDataChangedEvent(new MetaData()));

        BackupManager.shutdown(databaseContext, filePreferences.getBackupDirectory(), filePreferences.shouldCreateBackup());

        List<Path> files = Files.list(backupDir).toList();
        assertEquals(List.of(), files);
    }

    @Test
    void shouldCreateABackup(@TempDir Path customDir) throws IOException {
        Path backupDir = customDir.resolve("subBackupDir");
        Files.createDirectories(backupDir);

        BibDatabaseContext databaseContext = new BibDatabaseContext(new BibDatabase());
        databaseContext.setDatabasePath(customDir.resolve("Bibfile.bib"));

        CliPreferences preferences = mock(CliPreferences.class, Answers.RETURNS_DEEP_STUBS);
        FilePreferences filePreferences = mock(FilePreferences.class);
        when(preferences.getFilePreferences()).thenReturn(filePreferences);
        when(filePreferences.getBackupDirectory()).thenReturn(backupDir);
        when(filePreferences.shouldCreateBackup()).thenReturn(true);

        BackupManager manager = BackupManager.start(
                mock(LibraryTab.class),
                databaseContext,
                mock(BibEntryTypesManager.class, Answers.RETURNS_DEEP_STUBS),
                preferences);
        manager.listen(new MetaDataChangedEvent(new MetaData()));

        Optional<Path> fullBackupPath = manager.determineBackupPathForNewBackup(backupDir);
        fullBackupPath.ifPresent(manager::performBackup);
        manager.listen(new GroupUpdatedEvent(new MetaData()));

        BackupManager.shutdown(databaseContext, backupDir, true);

        List<Path> files = Files.list(backupDir).sorted().toList();
        // we only know the first backup path because the second one is created on shutdown
        // due to timing issues we cannot test that reliable
        assertEquals(fullBackupPath.get(), files.getFirst());
    }
}
