package damjay.photo.triage;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;

import java.util.Set;

/**
 * Lets the user change everything that used to be hard-coded:
 * root folder, inbox folder, image extensions, sort order, move/copy and photo labels.
 */
public class SettingsActivity extends AppCompatActivity {

    private SettingsManager settings;
    private MaterialButton btnSortOrder;
    private MaterialButton btnFileOperation;
    private MaterialButton btnLabelMode;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        settings = new SettingsManager(this);

        final EditText etRootFolder = findViewById(R.id.etRootFolder);
        final EditText etInboxFolder = findViewById(R.id.etInboxFolder);
        final EditText etExtensions = findViewById(R.id.etExtensions);
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

        btnSortOrder.setOnClickListener(v -> showSortOrderDialog());
        btnFileOperation.setOnClickListener(v -> showFileOperationDialog());
        btnLabelMode.setOnClickListener(v -> showLabelModeDialog());
        findViewById(R.id.btnReset).setOnClickListener(v -> resetToDefaults());
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        refreshChoiceButtons();
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

    private void toast(int resId) {
        Toast.makeText(this, resId, Toast.LENGTH_SHORT).show();
    }
}
