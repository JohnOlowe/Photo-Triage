package damjay.photo.triage;

import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Home for the maintenance tools that used to be hard-coded, one-off methods on
 * {@link MainActivity}: "reorganize photos" (the old syncGitMessToHighRes) and
 * "scan folder into gallery" (the old fixGitPhotoMess). Both are now fully
 * configurable with integrated browse+paste.
 */
@SuppressWarnings("deprecation")
public class ToolsActivity extends AppCompatActivity {

    private SettingsManager settings;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_tools);

        settings = new SettingsManager(this);
        statusText = findViewById(R.id.tvToolStatus);

        Toolbar toolbar = findViewById(R.id.toolbarTools);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnOrganizeTool).setOnClickListener(v -> showOrganizeDialog());
        findViewById(R.id.btnScanTool).setOnClickListener(v -> showScanDialog());
        View btnStudio = findViewById(R.id.btnStudio);
        if (btnStudio != null) btnStudio.setOnClickListener(v ->
                startActivity(new android.content.Intent(ToolsActivity.this, ResolutionStudioActivity.class)));
    }

    // ---------------------------------------------------------------------------------------------
    // Scan folder into gallery (previously fixGitPhotoMess)
    // ---------------------------------------------------------------------------------------------

    private void showScanDialog() {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_scan, null);
        final EditText etFolder = form.findViewById(R.id.etScanFolder);
        final CheckBox cbRecursive = form.findViewById(R.id.cbScanRecursive);
        final EditText etExtensions = form.findViewById(R.id.etScanExtensions);
        View btnBrowse = form.findViewById(R.id.btnBrowseScan);
        View btnPaste = form.findViewById(R.id.btnPasteScan);

        etFolder.setText(settings.getRootFolder());
        etExtensions.setText(SettingsManager.joinExtensions(settings.getImageExtensions()));

        FolderPickerDialog.attach(this, etFolder, btnBrowse, btnPaste);

        new AlertDialog.Builder(this)
                .setTitle(R.string.tool_scan_title)
                .setView(form)
                .setPositiveButton(R.string.run, (dialog, which) -> {
                    String path = etFolder.getText().toString().trim();
                    if (path.isEmpty()) {
                        toast(R.string.error_empty_value);
                        return;
                    }
                    Set<String> extensions = SettingsManager.parseExtensions(etExtensions.getText().toString());
                    if (extensions.isEmpty()) {
                        toast(R.string.error_empty_value);
                        return;
                    }
                    runScan(new File(path), cbRecursive.isChecked(), extensions);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void runScan(final File root, final boolean recursive, final Set<String> extensions) {
        setStatus(R.string.status_running);
        executor.execute(() -> {
            final List<File> files = FileUtils.collectImages(root, recursive, extensions);
            FileUtils.scanMedia(this, FileUtils.toStrings(files));
            final String message = getString(R.string.tool_scan_result, files.size());
            runOnUiThread(() -> {
                setStatus(message);
                toast(message);
            });
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Reorganize photos (previously syncGitMessToHighRes)
    // ---------------------------------------------------------------------------------------------

    private void showOrganizeDialog() {
        View form = LayoutInflater.from(this).inflate(R.layout.dialog_organize, null);

        final EditText etSourceDir = form.findViewById(R.id.etSourceDir);
        final EditText etDestDir = form.findViewById(R.id.etDestDir);
        final EditText etInboxDir = form.findViewById(R.id.etInboxDir);
        final EditText etFolderFilter = form.findViewById(R.id.etFolderFilter);
        final EditText etFolderPrefix = form.findViewById(R.id.etFolderPrefix);
        final EditText etFolderSeparator = form.findViewById(R.id.etFolderSeparator);
        final EditText etFolderRenames = form.findViewById(R.id.etFolderRenames);
        final EditText etFilePrefix = form.findViewById(R.id.etFilePrefix);
        final CheckBox cbDeleteExisting = form.findViewById(R.id.cbDeleteExisting);
        final CheckBox cbCopyInstead = form.findViewById(R.id.cbCopyInstead);

        // Browse + Paste for folder fields
        FolderPickerDialog.attach(this, etSourceDir, form.findViewById(R.id.btnBrowseSourceDir), form.findViewById(R.id.btnPasteSourceDir));
        FolderPickerDialog.attach(this, etDestDir, form.findViewById(R.id.btnBrowseDestDir), form.findViewById(R.id.btnPasteDestDir));
        // Inbox is just a name, but allow paste
        View pasteInbox = form.findViewById(R.id.btnPasteInboxDir);
        if (pasteInbox != null) {
            pasteInbox.setOnClickListener(v -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()) {
                    android.content.ClipData clip = cm.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence t = clip.getItemAt(0).coerceToText(this);
                        if (t != null) {
                            String p = t.toString().trim();
                            // If full path pasted, extract folder name
                            int slash = p.lastIndexOf('/');
                            if (slash >= 0) p = p.substring(slash + 1);
                            etInboxDir.setText(p);
                            Toast.makeText(this, R.string.path_pasted, Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            });
        }

        etSourceDir.setText(new File(Environment.getExternalStorageDirectory(), "FSFUI-Photos").getAbsolutePath());
        etDestDir.setText(settings.getRootFolder());
        etInboxDir.setText(settings.getInboxFolderName());
        etFolderFilter.setText("^\\d+.*");
        etFolderPrefix.setText("^\\d+_");
        etFolderSeparator.setText("_");
        etFolderRenames.setText(joinRenames(PhotoOrganizer.defaultFolderRenames()));
        etFilePrefix.setText("^\\d+_");
        cbDeleteExisting.setChecked(true);
        cbCopyInstead.setChecked(false);

        new AlertDialog.Builder(this)
                .setTitle(R.string.tool_organize_title)
                .setView(form)
                .setPositiveButton(R.string.run, (dialog, which) -> {
                    PhotoOrganizer.Config config = buildConfig(
                            etSourceDir, etDestDir, etInboxDir, etFolderFilter, etFolderPrefix,
                            etFolderSeparator, etFolderRenames, etFilePrefix,
                            cbDeleteExisting, cbCopyInstead);
                    if (config != null) {
                        runOrganize(config);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private PhotoOrganizer.Config buildConfig(EditText etSourceDir, EditText etDestDir, EditText etInboxDir,
                                              EditText etFolderFilter, EditText etFolderPrefix,
                                              EditText etFolderSeparator, EditText etFolderRenames,
                                              EditText etFilePrefix, CheckBox cbDeleteExisting,
                                              CheckBox cbCopyInstead) {
        String source = etSourceDir.getText().toString().trim();
        String dest = etDestDir.getText().toString().trim();
        String inboxName = etInboxDir.getText().toString().trim();

        if (source.isEmpty() || dest.isEmpty() || inboxName.isEmpty()) {
            toast(R.string.error_empty_value);
            return null;
        }

        String folderFilter = etFolderFilter.getText().toString().trim();
        String folderPrefix = etFolderPrefix.getText().toString().trim();
        String folderSeparator = etFolderSeparator.getText().toString().trim();
        String filePrefix = etFilePrefix.getText().toString().trim();

        String[] regexes = {folderFilter, folderPrefix, folderSeparator, filePrefix};
        for (String regex : regexes) {
            if (!isValidRegex(regex)) {
                toast(getString(R.string.invalid_regex, regex));
                return null;
            }
        }

        PhotoOrganizer.Config config = new PhotoOrganizer.Config();
        config.sourceDir = new File(source);
        config.destinationDir = new File(dest);
        config.inboxDir = new File(dest, inboxName);
        config.folderFilterRegex = folderFilter;
        config.folderPrefixRegex = folderPrefix;
        config.folderSeparatorRegex = folderSeparator;
        config.folderRenames = parseRenames(etFolderRenames.getText().toString());
        config.filePrefixRegex = filePrefix;
        config.extensions = settings.getImageExtensions();
        config.deleteExistingInDestination = cbDeleteExisting.isChecked();
        config.move = !cbCopyInstead.isChecked();
        return config;
    }

    private void runOrganize(final PhotoOrganizer.Config config) {
        setStatus(R.string.status_running);
        executor.execute(() -> {
            PhotoOrganizer.Result result = new PhotoOrganizer().run(config);
            if (!result.scannedPaths.isEmpty()) {
                FileUtils.scanMedia(this, result.scannedPaths);
            }
            final String summary = result.message;
            runOnUiThread(() -> {
                if (result.errors.isEmpty()) {
                    setStatus(summary);
                } else {
                    setStatus(summary + "\n" + getString(R.string.errors_prefix, result.errors.size()));
                }
                toast(summary);
            });
        });
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private boolean isValidRegex(String regex) {
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

    private List<String[]> parseRenames(String text) {
        List<String[]> renames = new ArrayList<>();
        if (text == null) {
            return renames;
        }
        for (String line : text.split("\\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            String[] parts = trimmed.split("=>", 2);
            if (parts.length != 2) {
                parts = trimmed.split("->", 2);
            }
            if (parts.length == 2) {
                String key = parts[0].trim();
                String value = parts[1].trim();
                if (!key.isEmpty() && !value.isEmpty()) {
                    renames.add(new String[]{key, value});
                }
            }
        }
        return renames;
    }

    private String joinRenames(List<String[]> renames) {
        StringBuilder sb = new StringBuilder();
        for (String[] rule : renames) {
            if (rule == null || rule.length < 2) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(rule[0]).append(" => ").append(rule[1]);
        }
        return sb.toString();
    }

    private void setStatus(int resId) {
        statusText.setText(resId);
    }

    private void setStatus(String text) {
        statusText.setText(text);
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executor != null) {
            executor.shutdown();
        }
    }
}
