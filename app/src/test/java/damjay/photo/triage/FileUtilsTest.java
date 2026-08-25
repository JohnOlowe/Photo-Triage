package damjay.photo.triage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.HashSet;

/**
 * JVM tests for the file helpers shared by the main triage flow and the Tools screen.
 */
public class FileUtilsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void moveRemovesSourceAndKeepsContent() throws Exception {
        File src = tmp.newFile("a.jpg");
        try (FileWriter w = new FileWriter(src)) {
            w.write("hello");
        }
        File dest = tmp.newFolder("dest");

        File target = FileUtils.moveOrCopy(src, dest, true);

        assertNotNull(target);
        assertTrue("target should exist", target.exists());
        assertFalse("source should be gone after a move", src.exists());
        assertEquals("hello", new String(Files.readAllBytes(target.toPath())));
    }

    @Test
    public void copyKeepsSource() throws Exception {
        File src = tmp.newFile("a.jpg");
        try (FileWriter w = new FileWriter(src)) {
            w.write("hello");
        }
        File dest = tmp.newFolder("dest");

        File target = FileUtils.moveOrCopy(src, dest, false);

        assertNotNull(target);
        assertTrue("target should exist", target.exists());
        assertTrue("source should remain after a copy", src.exists());
        assertEquals("hello", new String(Files.readAllBytes(target.toPath())));
    }

    @Test
    public void moveOrCopyResolvesNameCollisions() throws Exception {
        File src = tmp.newFile("a.jpg");
        File dest = tmp.newFolder("dest");
        assertTrue(new File(dest, "a.jpg").createNewFile());

        File target = FileUtils.moveOrCopy(src, dest, true);

        assertNotNull(target);
        assertEquals("a (1).jpg", target.getName());
        assertTrue("collision target should exist", target.exists());
    }

    @Test
    public void alreadyInDestinationIsNoOp() throws Exception {
        File dest = tmp.newFolder("dest");
        File src = new File(dest, "a.jpg");
        assertTrue(src.createNewFile());

        File target = FileUtils.moveOrCopy(src, dest, true);

        assertEquals(src, target);
    }

    @Test
    public void hasExtensionMatchesConfiguredSet() {
        HashSet<String> exts = new HashSet<>(Arrays.asList("jpg", "png"));
        assertTrue(FileUtils.hasExtension(new File("/x/photo.JPG"), exts));
        assertTrue(FileUtils.hasExtension(new File("/x/photo.png"), exts));
        assertFalse(FileUtils.hasExtension(new File("/x/photo.gif"), exts));
        assertFalse(FileUtils.hasExtension(new File("/x/noextension"), exts));
    }
}
