package damjay.photo.triage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.util.Arrays;
import java.util.HashSet;

/**
 * JVM tests for the configurable "Reorganize photos" tool — the flexible successor to
 * the old one-off syncGitMessToHighRes() method.
 */
public class PhotoOrganizerTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private File write(File file, String content) throws Exception {
        try (FileWriter w = new FileWriter(file)) {
            w.write(content);
        }
        return file;
    }

    private PhotoOrganizer.Config baseConfig(File source, File dest, File inbox) {
        PhotoOrganizer.Config config = new PhotoOrganizer.Config();
        config.sourceDir = source;
        config.destinationDir = dest;
        config.inboxDir = inbox;
        config.folderFilterRegex = "^\\d+.*";
        config.folderPrefixRegex = "^\\d+_";
        config.folderSeparatorRegex = "_";
        config.filePrefixRegex = "^\\d+_";
        config.extensions = new HashSet<>(Arrays.asList("jpg"));
        config.deleteExistingInDestination = true;
        config.move = true;
        return config;
    }

    @Test
    public void mapsNumberedFolderAndMovesMatchingHighResPhoto() throws Exception {
        File source = tmp.newFolder("src");
        File dest = tmp.newFolder("dest");
        File inbox = tmp.newFolder("dest", "Ordered Photos");

        File numbered = tmp.newFolder("src", "01_Sunday_School");
        write(new File(numbered, "01_IMG_1234.jpg"), "low-res");
        write(new File(inbox, "IMG_1234.jpg"), "high-res");

        PhotoOrganizer.Result result = new PhotoOrganizer().run(baseConfig(source, dest, inbox));

        assertEquals(1, result.foldersProcessed);
        assertEquals(1, result.filesProcessed);
        assertEquals(1, result.scannedPaths.size());
        assertTrue("result should be successful", result.success);

        assertTrue("expected file in Sunday School",
                new File(dest, "Sunday School/IMG_1234.jpg").exists());
        assertFalse("inbox should be emptied after a move",
                new File(inbox, "IMG_1234.jpg").exists());
    }

    @Test
    public void explicitRenameRulesOverrideGenericMapping() throws Exception {
        File source = tmp.newFolder("src");
        File dest = tmp.newFolder("dest");
        File inbox = tmp.newFolder("dest", "Ordered Photos");

        File numbered = tmp.newFolder("src", "02_Sermon_Teaching");
        write(new File(numbered, "02_IMG_2000.jpg"), "low-res");
        write(new File(inbox, "IMG_2000.jpg"), "high-res");

        new PhotoOrganizer().run(baseConfig(source, dest, inbox));

        assertTrue("explicit rename rule should map Sermon_Teaching to Teaching",
                new File(dest, "Teaching/IMG_2000.jpg").exists());
    }

    @Test
    public void copyModeKeepsTheInboxFile() throws Exception {
        File source = tmp.newFolder("src");
        File dest = tmp.newFolder("dest");
        File inbox = tmp.newFolder("dest", "Ordered Photos");

        File numbered = tmp.newFolder("src", "03_Choir_Ministration");
        write(new File(numbered, "03_IMG_3000.jpg"), "low-res");
        write(new File(inbox, "IMG_3000.jpg"), "high-res");

        PhotoOrganizer.Config config = baseConfig(source, dest, inbox);
        config.move = false;

        PhotoOrganizer.Result result = new PhotoOrganizer().run(config);

        assertTrue("destination should get a copy",
                new File(dest, "Ministration/IMG_3000.jpg").exists());
        assertTrue("inbox file should remain after a copy",
                new File(inbox, "IMG_3000.jpg").exists());
        assertEquals(1, result.scannedPaths.size());
    }

    @Test
    public void invalidRegexFailsGracefully() throws Exception {
        File source = tmp.newFolder("src");
        File dest = tmp.newFolder("dest");
        File inbox = tmp.newFolder("dest", "Ordered Photos");

        PhotoOrganizer.Config config = baseConfig(source, dest, inbox);
        config.folderFilterRegex = "[";

        PhotoOrganizer.Result result = new PhotoOrganizer().run(config);

        assertFalse(result.success);
        assertTrue(result.message.contains("Invalid regular expression"));
    }

    @Test
    public void missingSourceDirReportsError() throws Exception {
        File source = new File(tmp.getRoot(), "does-not-exist");
        File dest = tmp.newFolder("dest");
        File inbox = tmp.newFolder("dest", "Ordered Photos");

        PhotoOrganizer.Result result = new PhotoOrganizer().run(baseConfig(source, dest, inbox));

        assertFalse(result.success);
        assertTrue(result.message.contains("Source folder does not exist"));
    }
}
