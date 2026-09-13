package damjay.photo.triage;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * A file-system folder browser that works with absolute File paths (requires
 * MANAGE_EXTERNAL_STORAGE). It merges browsing + pasting in one sheet:
 * - Type or paste a path directly in the top field
 * - Browse visually through subfolders
 * - Paste from clipboard with one tap
 */
public class FolderPickerDialog {

    public interface OnFolderSelectedListener {
        void onFolderSelected(String path);
    }

    public static void show(Activity activity, String initialPath, OnFolderSelectedListener listener) {
        View view = LayoutInflater.from(activity).inflate(R.layout.dialog_folder_browser, null);
        TextInputLayout tilPath = view.findViewById(R.id.tilBrowserPath);
        EditText etPath = view.findViewById(R.id.etBrowserPath);
        MaterialButton btnUp = view.findViewById(R.id.btnUp);
        MaterialButton btnPaste = view.findViewById(R.id.btnPastePath);
        MaterialButton btnGo = view.findViewById(R.id.btnGo);
        RecyclerView rv = view.findViewById(R.id.rvFolders);
        TextView tvEmpty = view.findViewById(R.id.tvEmptyFolders);

        rv.setLayoutManager(new LinearLayoutManager(activity));

        // Normalize initial path
        final File[] current = new File[1];
        String start = initialPath != null && !initialPath.trim().isEmpty()
                ? initialPath.trim()
                : new SettingsManager(activity).getRootFolder();
        File startFile = new File(start);
        if (!startFile.exists() || !startFile.isDirectory()) {
            // Fall back to Pictures or external storage
            File fallback = activity.getExternalFilesDir(null);
            if (fallback != null) startFile = fallback;
            else startFile = new File("/storage/emulated/0");
            // Try Pictures
            File pictures = new File("/storage/emulated/0/Pictures");
            if (pictures.exists()) startFile = pictures;
        }
        current[0] = startFile;
        etPath.setText(current[0].getAbsolutePath());

        // Adapter holder
        final FolderAdapter[] adapterRef = new FolderAdapter[1];
        final Runnable[] refreshHolder = new Runnable[1];
        Runnable refresh = new Runnable() {
            @Override
            public void run() {
                File cur = current[0];
                etPath.setText(cur.getAbsolutePath());
                etPath.setSelection(etPath.getText().length());
                List<File> folders = listFolders(cur);
                if (folders.isEmpty()) {
                    tvEmpty.setVisibility(View.VISIBLE);
                    rv.setVisibility(View.GONE);
                } else {
                    tvEmpty.setVisibility(View.GONE);
                    rv.setVisibility(View.VISIBLE);
                }
                FolderAdapter adapter = new FolderAdapter(folders, folder -> {
                    current[0] = folder;
                    if (refreshHolder[0] != null) refreshHolder[0].run();
                });
                adapterRef[0] = adapter;
                rv.setAdapter(adapter);
                btnUp.setEnabled(cur.getParentFile() != null);
            }
        };
        refreshHolder[0] = refresh;
        refresh.run();

        btnUp.setOnClickListener(v -> {
            File parent = current[0].getParentFile();
            if (parent != null) {
                current[0] = parent;
                refresh.run();
            }
        });

        btnPaste.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm != null && cm.hasPrimaryClip()) {
                ClipData clip = cm.getPrimaryClip();
                if (clip != null && clip.getItemCount() > 0) {
                    CharSequence text = clip.getItemAt(0).coerceToText(activity);
                    if (text != null) {
                        String pasted = text.toString().trim();
                        etPath.setText(pasted);
                        etPath.setSelection(etPath.getText().length());
                        File f = new File(pasted);
                        if (f.exists() && f.isDirectory()) {
                            current[0] = f;
                            refresh.run();
                        } else if (f.exists() && f.isFile()) {
                            File parent = f.getParentFile();
                            if (parent != null) {
                                current[0] = parent;
                                refresh.run();
                            }
                        }
                        Toast.makeText(activity, R.string.path_pasted, Toast.LENGTH_SHORT).show();
                    }
                }
            } else {
                Toast.makeText(activity, R.string.no_clipboard, Toast.LENGTH_SHORT).show();
            }
        });

        View.OnClickListener goAction = v -> {
            String path = etPath.getText().toString().trim();
            if (path.isEmpty()) {
                Toast.makeText(activity, R.string.invalid_path, Toast.LENGTH_SHORT).show();
                return;
            }
            File f = new File(path);
            if (f.exists() && f.isDirectory()) {
                current[0] = f;
                refresh.run();
            } else if (f.exists() && f.isFile() && f.getParentFile() != null) {
                current[0] = f.getParentFile();
                refresh.run();
            } else {
                // Allow non-existing path to be selected if parent exists
                File parent = f.getParentFile();
                if (parent != null && parent.exists()) {
                    // Keep etPath as typed but don't navigate
                    Toast.makeText(activity, "Path will be created if needed", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(activity, R.string.invalid_path, Toast.LENGTH_SHORT).show();
                }
            }
        };
        btnGo.setOnClickListener(goAction);
        etPath.setOnEditorActionListener((v, actionId, event) -> {
            goAction.onClick(null);
            return true;
        });

        new AlertDialog.Builder(activity)
                .setTitle(R.string.select_folder)
                .setView(view)
                .setPositiveButton(R.string.use, (dialog, which) -> {
                    String finalPath = etPath.getText().toString().trim();
                    if (finalPath.isEmpty()) finalPath = current[0].getAbsolutePath();
                    listener.onFolderSelected(finalPath);
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.paste, (dialog, which) -> {
                    // Paste and use directly
                    ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null && cm.hasPrimaryClip()) {
                        ClipData clip = cm.getPrimaryClip();
                        if (clip != null && clip.getItemCount() > 0) {
                            CharSequence text = clip.getItemAt(0).coerceToText(activity);
                            if (text != null) {
                                listener.onFolderSelected(text.toString().trim());
                                return;
                            }
                        }
                    }
                    Toast.makeText(activity, R.string.no_clipboard, Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private static List<File> listFolders(File dir) {
        List<File> result = new ArrayList<>();
        if (dir == null || !dir.isDirectory()) return result;
        File[] files = dir.listFiles();
        if (files == null) return result;
        for (File f : files) {
            if (f.isDirectory() && !f.isHidden()) {
                result.add(f);
            }
        }
        Collections.sort(result, Comparator.comparing(f -> f.getName().toLowerCase()));
        return result;
    }

    /** Helper to attach browse+paste to any EditText + two buttons */
    public static void attach(Activity activity, EditText editText, View btnBrowse, View btnPaste) {
        if (btnBrowse != null) {
            btnBrowse.setOnClickListener(v -> show(activity, editText.getText().toString(), path -> {
                editText.setText(path);
                editText.setSelection(editText.getText().length());
                // Trigger focus change to save if needed
                editText.clearFocus();
                // For SettingsActivity, manually trigger save via callback? Caller handles.
                Toast.makeText(activity, activity.getString(R.string.folder_selected, path), Toast.LENGTH_SHORT).show();
            }));
        }
        if (btnPaste != null) {
            btnPaste.setOnClickListener(v -> {
                ClipboardManager cm = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                if (cm != null && cm.hasPrimaryClip()) {
                    ClipData clip = cm.getPrimaryClip();
                    if (clip != null && clip.getItemCount() > 0) {
                        CharSequence text = clip.getItemAt(0).coerceToText(activity);
                        if (text != null) {
                            String pasted = text.toString().trim();
                            editText.setText(pasted);
                            editText.setSelection(editText.getText().length());
                            Toast.makeText(activity, R.string.path_pasted, Toast.LENGTH_SHORT).show();
                        }
                    }
                } else {
                    Toast.makeText(activity, R.string.no_clipboard, Toast.LENGTH_SHORT).show();
                }
            });
        }
    }
}
