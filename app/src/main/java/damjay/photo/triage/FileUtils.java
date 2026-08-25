package damjay.photo.triage;

import android.content.Context;
import android.media.MediaScannerConnection;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Small, reusable file helpers shared by the main triage flow and the Tools screen.
 */
public final class FileUtils {

    private FileUtils() {
    }

    /** Collects files with a supported extension, optionally walking subfolders. */
    public static List<File> collectImages(File root, boolean recursive, Set<String> extensions) {
        List<File> result = new ArrayList<>();
        if (root == null || !root.isDirectory()) {
            return result;
        }
        collectInto(root, recursive, extensions, result);
        return result;
    }

    private static void collectInto(File dir, boolean recursive, Set<String> extensions, List<File> out) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                if (recursive) {
                    collectInto(file, true, extensions, out);
                }
            } else if (file.isFile() && hasExtension(file, extensions)) {
                out.add(file);
            }
        }
    }

    public static boolean hasExtension(File file, Set<String> extensions) {
        if (file == null || extensions == null) {
            return false;
        }
        String name = file.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return extensions.contains(name.substring(dot + 1));
    }

    /**
     * Moves or copies {@code source} into {@code destDir}, resolving name collisions.
     *
     * @return the resulting target file, or {@code null} on failure.
     */
    public static File moveOrCopy(File source, File destDir, boolean move) {
        if (source == null || destDir == null || !source.exists() || !source.isFile()) {
            return null;
        }
        if (!destDir.exists() && !destDir.mkdirs()) {
            return null;
        }

        File sourceParent = source.getParentFile();
        if (sourceParent != null && sourceParent.equals(destDir)) {
            // Already in the destination folder - nothing to do.
            return source;
        }

        File target = uniqueFile(destDir, source.getName());
        boolean ok;
        if (move) {
            ok = source.renameTo(target);
            if (!ok) {
                // Cross-filesystem move: copy first, then delete the original.
                ok = copyFile(source, target);
                if (ok) {
                    source.delete();
                }
            }
        } else {
            ok = copyFile(source, target);
        }
        return ok ? target : null;
    }

    /** Returns a non-existing sibling file by appending " (n)" before the extension. */
    public static File uniqueFile(File dir, String name) {
        File candidate = new File(dir, name);
        if (!candidate.exists()) {
            return candidate;
        }
        int dot = name.lastIndexOf('.');
        String base = dot > 0 ? name.substring(0, dot) : name;
        String extension = dot > 0 ? name.substring(dot) : "";
        int index = 1;
        while (true) {
            candidate = new File(dir, base + " (" + index + ")" + extension);
            if (!candidate.exists()) {
                return candidate;
            }
            index++;
        }
    }

    public static boolean copyFile(File source, File target) {
        try (FileInputStream in = new FileInputStream(source);
             FileOutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
            out.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /** Converts a list of files to their absolute paths. */
    public static List<String> toStrings(List<File> files) {
        List<String> paths = new ArrayList<>();
        if (files != null) {
            for (File file : files) {
                if (file != null) {
                    paths.add(file.getAbsolutePath());
                }
            }
        }
        return paths;
    }

    /** Asks the media scanner to index the given absolute paths. */
    public static void scanMedia(Context context, Collection<String> paths) {
        if (context == null || paths == null || paths.isEmpty()) {
            return;
        }
        List<String> filtered = new ArrayList<>();
        for (String path : paths) {
            if (path != null && !path.isEmpty()) {
                filtered.add(path);
            }
        }
        if (filtered.isEmpty()) {
            return;
        }
        MediaScannerConnection.scanFile(context, filtered.toArray(new String[0]), null, null);
    }
}
