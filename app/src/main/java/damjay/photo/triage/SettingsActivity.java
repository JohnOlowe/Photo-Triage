package damjay.photo.triage;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.google.android.material.button.MaterialButton;

import java.util.Set;

/**
 * Lets the user change everything that used to be hard-coded:
 * root folder, inbox folder, image extensions, sort order, move/copy and photo labels.
 * Now with integrated browse + paste controls for every path field.
 */
public class SettingsActivity extends AppCompatActivity {

    private SettingsManager settings;
    private MaterialButton btnSortOrder;
    private MaterialButton btnFileOperation;
    private MaterialButton btnLabelMode;

    private EditText etRootFolder;
    private EditText etInboxFolder;
    private EditText etExtensions;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settings = new SettingsManager(this);

        Toolbar toolbar = findViewById(R.id.toolbarSettings);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
            if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            toolbar.setNavigationOnClickListener(v -> finish());
        }

        etRootFolder = findViewById(R.id.etRootFolder);
        etInboxFolder = findViewById(R.id.etInboxFolder);
        etExtensions = findViewById(R.id.etExtensions);
        btnSortOrder = findViewById(R.id.btnSortOrder);
        btnFileOperation = findViewById(R.id.btnFileOperation);
        btnLabelMode = findViewById(R.id.btnLabelMode);

        etRootFolder.setText(settings.getRootFolder());
        etInboxFolder.setText(settings.getInboxFolderName());
        etExtensions.setText(SettingsManager.joinExtensions(settings.getImageExtensions()));

        etRootFolder.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String value = etRootFolder.getText().toString().trim();
                if (value.isEmpty()) {
                    etRootFolder.setText(settings.getRootFolder());
                    toast(R.string.error_empty_value);
                } else {
                    settings.setRootFolder(value);
                }
            }
        });

        etInboxFolder.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                String value = etInboxFolder.getText().toString().trim();
                if (value.isEmpty()) {
                    etInboxFolder.setText(settings.getInboxFolderName());
                    toast(R.string.error_empty_value);
                } else {
                    settings.setInboxFolderName(value);
                }
            }
        });

        etExtensions.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                Set<String> parsed = SettingsManager.parseExtensions(etExtensions.getText().toString());
                if (parsed.isEmpty()) {
                    etExtensions.setText(SettingsManager.joinExtensions(settings.getImageExtensions()));
                    toast(R.string.error_empty_value);
                } else {
                    settings.setImageExtensions(parsed);
                }
            }
        });

        // Browse + Paste integration — creative merge of picker and clipboard
        FolderPickerDialog.attach(this, etRootFolder, findViewById(R.id.btnBrowseRoot), findViewById(R.id.btnPasteRoot));
        FolderPickerDialog.attach(this, etInboxFolder, findViewById(R.id.btnBrowseInbox), findViewById(R.id.btnPasteInbox));
        // Also save on browse selection immediately — override attach's browse to persist
        if (findViewById(R.id.btnBrowseRoot) != null) {
            findViewById(R.id.btnBrowseRoot).setOnClickListener(v -> FolderPickerDialog.show(this, etRootFolder.getText().toString(), path -> {
                etRootFolder.setText(path);
                settings.setRootFolder(path);
                Toast.makeText(this, getString(R.string.folder_selected, path), Toast.LENGTH_SHORT).show();
            }));
        }
        if (findViewById(R.id.btnBrowseInbox) != null) {
            findViewById(R.id.btnBrowseInbox).setOnClickListener(v -> FolderPickerDialog.show(this, etInboxFolder.getText().toString(), path -> {
                String name = extractInboxName(path);
                etInboxFolder.setText(name);
                settings.setInboxFolderName(name);
                Toast.makeText(this, getString(R.string.folder_selected, name), Toast.LENGTH_SHORT).show();
            }));
        }
        // Paste for inbox should also extract folder name if full path pasted
        View pasteInbox = findViewById(R.id.btnPasteInbox);
        if (pasteInbox != null) {
            pasteInbox.setOnClickListener(v -> {
                android.content.ClipboardManager cm = (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()) {
                    android.content.ClipData clip = cm.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence t = clip.getItemAt(0).coerceToText(this);
                        if (t != null) {
                            String pasted = t.toString().trim();
                            String name = pasted.contains("/") ? extractInboxName(pasted) : pasted;
                            etInboxFolder.setText(name);
                            if (!name.isEmpty()) settings.setInboxFolderName(name);
                            Toast.makeText(this, R.string.path_pasted, Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    Toast.makeText(this, R.string.no_clipboard, Toast.LENGTH_SHORT).show();
                }
            });
        }

        btnSortOrder.setOnClickListener(v -> showSortOrderDialog());
        btnFileOperation.setOnClickListener(v -> showFileOperationDialog());
        btnLabelMode.setOnClickListener(v -> showLabelModeDialog());
        findViewById(R.id.btnReset).setOnClickListener(v -> resetToDefaults());
        View back = findViewById(R.id.btnBack);
        if (back != null) back.setOnClickListener(v -> finish());

        refreshChoiceButtons();
    }

    @Override
    protected void onPause() {
        super.onPause();
        // Persist current values even if focus didn't change
        String root = etRootFolder.getText().toString().trim();
        if (!root.isEmpty()) settings.setRootFolder(root);
        String inbox = etInboxFolder.getText().toString().trim();
        if (!inbox.isEmpty()) settings.setInboxFolderName(inbox);
        Set<String> parsed = SettingsManager.parseExtensions(etExtensions.getText().toString());
        if (!parsed.isEmpty()) settings.setImageExtensions(parsed);
    }

    private void refreshChoiceButtons() {
        btnSortOrder.setText(getString(R.string.pref_sort_order_value, sortOrderLabel(settings.getSortOrder())));
        btnFileOperation.setText(getString(R.string.pref_file_operation_value, operationLabel(settings.getFileOperation())));
        btnLabelMode.setText(getString(R.string.pref_label_mode_value, labelModeLabel(settings.getLabelMode())));
    }

    private String sortOrderLabel(String value) {
        if (SettingsManager.SORT_NAME_DESC.equals(value)) {
            return getString(R.string.sort_name_desc);
        }
        if (SettingsManager.SORT_DATE_ASC.equals(value)) {
            return getString(R.string.sort_date_asc);
        }
        if (SettingsManager.SORT_DATE_DESC.equals(value)) {
            return getString(R.string.sort_date_desc);
        }
        return getString(R.string.sort_name_asc);
    }

    private String operationLabel(String value) {
        return SettingsManager.OP_COPY.equals(value) ? getString(R.string.op_copy) : getString(R.string.op_move);
    }

    private String labelModeLabel(String value) {
        if (SettingsManager.LABEL_FILENAME.equals(value)) {
            return getString(R.string.label_filename);
        }
        if (SettingsManager.LABEL_INDEX.equals(value)) {
            return getString(R.string.label_index);
        }
        return getString(R.string.label_auto);
    }

    private void showSortOrderDialog() {
        final String[] values = {
                SettingsManager.SORT_NAME_ASC,
                SettingsManager.SORT_NAME_DESC,
                SettingsManager.SORT_DATE_ASC,
                SettingsManager.SORT_DATE_DESC
        };
        final String[] labels = {
                getString(R.string.sort_name_asc),
                getString(R.string.sort_name_desc),
                getString(R.string.sort_date_asc),
                getString(R.string.sort_date_desc)
        };
        showChoiceDialog(R.string.sort_order_title, values, labels, settings.getSortOrder(),
                value -> settings.setSortOrder(value));
    }

    private void showFileOperationDialog() {
        final String[] values = {SettingsManager.OP_MOVE, SettingsManager.OP_COPY};
        final String[] labels = {getString(R.string.op_move), getString(R.string.op_copy)};
        showChoiceDialog(R.string.file_operation_title, values, labels, settings.getFileOperation(),
                value -> settings.setFileOperation(value));
    }

    private void showLabelModeDialog() {
        final String[] values = {
                SettingsManager.LABEL_AUTO,
                SettingsManager.LABEL_FILENAME,
                SettingsManager.LABEL_INDEX
        };
        final String[] labels = {
                getString(R.string.label_auto),
                getString(R.string.label_filename),
                getString(R.string.label_index)
        };
        showChoiceDialog(R.string.label_mode_title, values, labels, settings.getLabelMode(),
                value -> settings.setLabelMode(value));
    }

    private interface ChoiceHandler {
        void onChoice(String value);
    }

    private void showChoiceDialog(int titleRes, final String[] values, final String[] labels, String current, final ChoiceHandler handler) {
        int selectedIndex = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                selectedIndex = i;
                break;
            }
        }
        new AlertDialog.Builder(this)
                .setTitle(titleRes)
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    handler.onChoice(values[which]);
                    dialog.dismiss();
                    refreshChoiceButtons();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void resetToDefaults() {
        settings.resetAll();
        recreate();
    }

    private String extractInboxName(String path) {
        if (path == null) return "";
        String name = path.trim();
        int slash = name.lastIndexOf('/');
        if (slash >= 0 && slash < name.length() - 1) {
            String root = settings.getRootFolder();
            if (name.startsWith(root)) {
                name = name.substring(root.length());
                if (name.startsWith("/")) name = name.substring(1);
                if (name.contains("/")) name = name.substring(0, name.indexOf('/'));
            } else {
                name = name.substring(slash + 1);
            }
        }
        return name;
    }

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
