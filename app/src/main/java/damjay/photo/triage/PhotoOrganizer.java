package damjay.photo.triage;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Generalised, reusable version of the old one-off "syncGitMessToHighRes" routine.
 *
 * <p>It maps folders whose names match a filter regex into clean destination names
 * (via configurable rename rules and strip/replace regexes), then pulls the matching
 * high-resolution photos out of an inbox folder into those destinations.</p>
 */
public class PhotoOrganizer {

    public static class Config {
        public File sourceDir;
        public File destinationDir;
        public File inboxDir;

        /** Only folders whose name matches this regex are processed. */
        public String folderFilterRegex = "^\\d+.*";
        /** Regex stripped from the start of a folder name. */
        public String folderPrefixRegex = "^\\d+_";
        /** Regex replaced with a space inside a folder name. */
        public String folderSeparatorRegex = "_";
        /** Explicit renames; each entry is {contains, replacement}. Applied first-match-wins. */
        public List<String[]> folderRenames = defaultFolderRenames();
        /** Regex stripped from the start of a file name. */
        public String filePrefixRegex = "^\\d+_";

        public Set<String> extensions;
        public boolean deleteExistingInDestination = true;
        public boolean move = true;
    }

    public static class Result {
        public boolean success;
        public int foldersProcessed;
        public int filesProcessed;
        public String message;
        public final List<String> scannedPaths = new ArrayList<>();
        public final List<String> errors = new ArrayList<>();
    }

    public static List<String[]> defaultFolderRenames() {
        List<String[]> renames = new ArrayList<>();
        renames.add(new String[]{"Sunday_School", "Sunday School"});
        renames.add(new String[]{"Sermon_Teaching", "Teaching"});
        renames.add(new String[]{"Choir_Ministration", "Ministration"});
        return renames;
    }

    public Result run(Config config) {
        Result result = new Result();

        if (config == null || config.sourceDir == null
                || config.destinationDir == null || config.inboxDir == null) {
            result.message = "Incomplete configuration.";
            return result;
        }
        if (!config.sourceDir.exists() || !config.sourceDir.isDirectory()) {
            result.message = "Source folder does not exist: " + config.sourceDir.getAbsolutePath();
            return result;
        }
        if (!config.inboxDir.exists() || !config.inboxDir.isDirectory()) {
            result.message = "High-res inbox folder does not exist: " + config.inboxDir.getAbsolutePath();
            return result;
        }
        if (!config.destinationDir.exists() && !config.destinationDir.mkdirs()) {
            result.message = "Could not create destination folder: " + config.destinationDir.getAbsolutePath();
            return result;
        }

        String folderFilter = orDefault(config.folderFilterRegex, "^\\d+.*");
        String folderPrefix = config.folderPrefixRegex == null ? "" : config.folderPrefixRegex;
        String folderSeparator = config.folderSeparatorRegex == null ? "" : config.folderSeparatorRegex;
        String filePrefix = config.filePrefixRegex == null ? "" : config.filePrefixRegex;

        // Validate regexes up front so a bad pattern never kills the worker thread.
        String[] regexes = {folderFilter, folderPrefix, folderSeparator, filePrefix};
        for (String regex : regexes) {
            if (!isValid(regex)) {
                result.message = "Invalid regular expression: " + regex;
                return result;
            }
        }

        File[] folders = config.sourceDir.listFiles();
        if (folders == null) {
            result.message = "Could not read source folder.";
            return result;
        }

        for (File folder : folders) {
            if (folder == null || !folder.isDirectory()) {
                continue;
            }
            if (!folder.getName().matches(folderFilter)) {
                continue;
            }

            result.foldersProcessed++;

            String destFolderName = mapFolderName(folder.getName(), folderPrefix, folderSeparator, config.folderRenames);
            if (destFolderName == null || destFolderName.isEmpty()) {
                result.errors.add("Skipped folder '" + folder.getName() + "' (empty name after mapping).");
                continue;
            }

            File destFolder = new File(config.destinationDir, destFolderName);
            if (!destFolder.exists()) {
                destFolder.mkdirs();
            } else if (config.deleteExistingInDestination) {
                File[] existing = destFolder.listFiles();
                if (existing != null) {
                    for (File file : existing) {
                        if (file.isFile() && FileUtils.hasExtension(file, config.extensions)) {
                            file.delete();
                        }
                    }
                }
            }

            File[] lowResFiles = folder.listFiles();
            if (lowResFiles == null) {
                continue;
            }

            for (File lowRes : lowResFiles) {
                if (lowRes == null || !lowRes.isFile()) {
                    continue;
                }
                String realName = lowRes.getName().replaceFirst(filePrefix, "");
                File highRes = new File(config.inboxDir, realName);
                if (!highRes.exists()) {
                    continue;
                }

                result.filesProcessed++;
                File target = FileUtils.moveOrCopy(highRes, destFolder, config.move);
                if (target != null) {
                    result.scannedPaths.add(target.getAbsolutePath());
                } else {
                    result.errors.add("Could not process '" + highRes.getName() + "' into '" + destFolderName + "'.");
                }
            }
        }

        if (result.foldersProcessed == 0) {
            result.message = "No matching folders found in source.";
        } else {
            String verb = config.move ? "moved" : "copied";
            result.message = "Processed " + result.foldersProcessed + " folder(s), "
                    + result.scannedPaths.size() + " photo(s) " + verb + ".";
        }
        result.success = result.errors.isEmpty();
        return result;
    }

    private static String mapFolderName(String rawName, String folderPrefix, String folderSeparator, List<String[]> renames) {
        // Generic transformation first, mirroring the original behaviour.
        String name = rawName;
        if (!folderPrefix.isEmpty()) {
            name = name.replaceFirst(folderPrefix, "");
        }
        if (!folderSeparator.isEmpty()) {
            name = name.replaceAll(folderSeparator, " ");
        }
        // Explicit renames take precedence when the raw name contains the key.
        if (renames != null) {
            for (String[] rule : renames) {
                if (rule == null || rule.length < 2) {
                    continue;
                }
                String key = rule[0];
                String value = rule[1];
                if (key == null || value == null) {
                    continue;
                }
                if (rawName.toLowerCase().contains(key.toLowerCase())) {
                    name = value;
                    break;
                }
            }
        }
        return name.trim();
    }

    private static boolean isValid(String regex) {
        if (regex == null || regex.isEmpty()) {
            return true;
        }
        try {
            Pattern.compile(regex);
            return true;
        } catch (PatternSyntaxException e) {
            return false;
        }
    }

    private static String orDefault(String value, String fallback) {
        return value == null || value.trim().isEmpty() ? fallback : value;
    }
}
