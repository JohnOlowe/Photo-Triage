package damjay.photo.triage;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Single source of truth for every user-configurable value in the app.
 *
 * <p>Nothing is hard-coded in the activities any more: each value lives here with a
 * sensible default, is persisted in {@link SharedPreferences} and can be changed at
 * runtime from the Settings screen.</p>
 */
@SuppressWarnings("deprecation")
public class SettingsManager {

    public static final String PREFS_NAME = "PhotoTriagePrefs";

    // Sort order values.
    public static final String SORT_NAME_ASC = "name_asc";
    public static final String SORT_NAME_DESC = "name_desc";
    public static final String SORT_DATE_ASC = "date_asc";
    public static final String SORT_DATE_DESC = "date_desc";

    // File operation values.
    public static final String OP_MOVE = "move";
    public static final String OP_COPY = "copy";

    // Photo label modes.
    public static final String LABEL_AUTO = "auto";
    public static final String LABEL_FILENAME = "filename";
    public static final String LABEL_INDEX = "index";

    private static final String KEY_ROOT_FOLDER = "RootFolder";
    private static final String KEY_INBOX_FOLDER = "InboxFolder";
    private static final String KEY_IMAGE_EXTENSIONS = "ImageExtensions";
    private static final String KEY_SORT_ORDER = "SortOrder";
    private static final String KEY_FILE_OPERATION = "FileOperation";
    private static final String KEY_LABEL_MODE = "LabelMode";

    // New, order-preserving category key.
    private static final String KEY_CATEGORIES_ORDERED = "CategoriesOrdered";
    // Legacy key kept for backwards compatibility with previously saved data.
    private static final String KEY_CATEGORIES_LEGACY = "SavedCategories";

    private final SharedPreferences prefs;

    public SettingsManager(Context context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------------------------------------
    // Defaults
    // ---------------------------------------------------------------------------------------------

    /** Default root folder, derived from the device's real Pictures directory. */
    public static String defaultRootFolder() {
        File pictures = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES);
        return new File(pictures, "FSFUI Photos").getAbsolutePath();
    }

    public static List<String> defaultCategories() {
        return new ArrayList<>(Arrays.asList("Worship", "Prayer", "Choir Ministration", "Sermon", "Drama"));
    }

    public static Set<String> defaultImageExtensions() {
        return new LinkedHashSet<>(Arrays.asList("jpg", "jpeg", "png"));
    }

    // ---------------------------------------------------------------------------------------------
    // Root folder
    // ---------------------------------------------------------------------------------------------

    public String getRootFolder() {
        return prefs.getString(KEY_ROOT_FOLDER, defaultRootFolder());
    }

    public void setRootFolder(String path) {
        prefs.edit().putString(KEY_ROOT_FOLDER, normalizePath(path)).apply();
    }

    // ---------------------------------------------------------------------------------------------
    // Inbox folder
    // ---------------------------------------------------------------------------------------------

    public String getInboxFolderName() {
        return prefs.getString(KEY_INBOX_FOLDER, "Ordered Photos");
    }

    public void setInboxFolderName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (!trimmed.isEmpty()) {
            prefs.edit().putString(KEY_INBOX_FOLDER, trimmed).apply();
        }
    }

    /** The absolute path of the folder photos are triaged out of. */
    public File getInboxFolder() {
        return new File(getRootFolder(), getInboxFolderName());
    }

    // ---------------------------------------------------------------------------------------------
    // Categories (the folders photos are sorted into)
    // ---------------------------------------------------------------------------------------------

    public List<String> getCategories() {
        String stored = prefs.getString(KEY_CATEGORIES_ORDERED, null);
        if (stored == null) {
            // Migrate from the legacy StringSet if present.
            Set<String> legacy = prefs.getStringSet(KEY_CATEGORIES_LEGACY, null);
            if (legacy != null && !legacy.isEmpty()) {
                List<String> migrated = new ArrayList<>(legacy);
                Collections.sort(migrated);
                setCategories(migrated);
                return migrated;
            }
            List<String> defaults = defaultCategories();
            setCategories(defaults);
            return defaults;
        }

        List<String> list = new ArrayList<>();
        for (String entry : stored.split("\\n")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                list.add(trimmed);
            }
        }
        if (list.isEmpty()) {
            list = defaultCategories();
            setCategories(list);
        }
        return list;
    }

    public void setCategories(List<String> categories) {
        StringBuilder sb = new StringBuilder();
        Set<String> legacySet = new HashSet<>();
        if (categories != null) {
            for (String category : categories) {
                if (category == null) {
                    continue;
                }
                String trimmed = category.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(trimmed);
                legacySet.add(trimmed);
            }
        }
        prefs.edit()
                .putString(KEY_CATEGORIES_ORDERED, sb.toString())
                .putStringSet(KEY_CATEGORIES_LEGACY, legacySet)
                .apply();
    }

    /** Absolute path of the folder used for a given category. */
    public File getFolderForCategory(String category) {
        return new File(getRootFolder(), category);
    }

    // ---------------------------------------------------------------------------------------------
    // Supported image extensions
    // ---------------------------------------------------------------------------------------------

    public Set<String> getImageExtensions() {
        Set<String> saved = prefs.getStringSet(KEY_IMAGE_EXTENSIONS, null);
        if (saved == null || saved.isEmpty()) {
            Set<String> defaults = defaultImageExtensions();
            setImageExtensions(defaults);
            return defaults;
        }
        Set<String> normalized = normalizeExtensions(saved);
        if (normalized.isEmpty()) {
            normalized = defaultImageExtensions();
            setImageExtensions(normalized);
        }
        return normalized;
    }

    public void setImageExtensions(Set<String> extensions) {
        prefs.edit().putStringSet(KEY_IMAGE_EXTENSIONS, normalizeExtensions(extensions)).apply();
    }

    public boolean isSupportedImage(File file) {
        if (file == null) {
            return false;
        }
        String name = file.getName().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return false;
        }
        return getImageExtensions().contains(name.substring(dot + 1));
    }

    private static Set<String> normalizeExtensions(Set<String> extensions) {
        Set<String> normalized = new LinkedHashSet<>();
        if (extensions != null) {
            for (String extension : extensions) {
                if (extension == null) {
                    continue;
                }
                String trimmed = extension.trim().toLowerCase().replace(".", "");
                if (!trimmed.isEmpty()) {
                    normalized.add(trimmed);
                }
            }
        }
        return normalized;
    }

    /** Parses "jpg, JPG, png, .webp" style input into a clean extension set. */
    public static Set<String> parseExtensions(String text) {
        Set<String> extensions = new LinkedHashSet<>();
        if (text == null) {
            return extensions;
        }
        for (String part : text.split("[,\\s]+")) {
            String trimmed = part.trim().toLowerCase().replace(".", "");
            if (!trimmed.isEmpty()) {
                extensions.add(trimmed);
            }
        }
        return extensions;
    }

    public static String joinExtensions(Set<String> extensions) {
        StringBuilder sb = new StringBuilder();
        if (extensions != null) {
            for (String extension : extensions) {
                if (sb.length() > 0) {
                    sb.append(", ");
                }
                sb.append(extension);
            }
        }
        return sb.toString();
    }

    // ---------------------------------------------------------------------------------------------
    // Sort order
    // ---------------------------------------------------------------------------------------------

    public String getSortOrder() {
        return prefs.getString(KEY_SORT_ORDER, SORT_NAME_ASC);
    }

    public void setSortOrder(String order) {
        prefs.edit().putString(KEY_SORT_ORDER, order).apply();
    }

    public Comparator<File> getFileComparator() {
        final String order = getSortOrder();
        switch (order) {
            case SORT_NAME_DESC:
                return (f1, f2) -> f2.getName().compareToIgnoreCase(f1.getName());
            case SORT_DATE_ASC:
                return (f1, f2) -> Long.compare(f1.lastModified(), f2.lastModified());
            case SORT_DATE_DESC:
                return (f1, f2) -> Long.compare(f2.lastModified(), f1.lastModified());
            case SORT_NAME_ASC:
            default:
                return (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // File operation (move vs copy)
    // ---------------------------------------------------------------------------------------------

    public String getFileOperation() {
        String operation = prefs.getString(KEY_FILE_OPERATION, OP_MOVE);
        return OP_COPY.equals(operation) ? OP_COPY : OP_MOVE;
    }

    public void setFileOperation(String operation) {
        prefs.edit().putString(KEY_FILE_OPERATION, OP_COPY.equals(operation) ? OP_COPY : OP_MOVE).apply();
    }

    public boolean isMoveOperation() {
        return OP_MOVE.equals(getFileOperation());
    }

    // ---------------------------------------------------------------------------------------------
    // Photo label mode
    // ---------------------------------------------------------------------------------------------

    public String getLabelMode() {
        String mode = prefs.getString(KEY_LABEL_MODE, LABEL_AUTO);
        if (LABEL_FILENAME.equals(mode) || LABEL_INDEX.equals(mode)) {
            return mode;
        }
        return LABEL_AUTO;
    }

    public void setLabelMode(String mode) {
        String normalized = LABEL_FILENAME.equals(mode) || LABEL_INDEX.equals(mode) ? mode : LABEL_AUTO;
        prefs.edit().putString(KEY_LABEL_MODE, normalized).apply();
    }

    // ---------------------------------------------------------------------------------------------
    // Reset
    // ---------------------------------------------------------------------------------------------

    public void resetAll() {
        prefs.edit().clear().apply();
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "";
        }
        String trimmed = path.trim();
        while (trimmed.endsWith("/") && trimmed.length() > 1) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }
}
