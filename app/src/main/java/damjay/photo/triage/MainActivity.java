package damjay.photo.triage;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.yuyakaido.android.cardstackview.CardStackLayoutManager;
import com.yuyakaido.android.cardstackview.CardStackListener;
import com.yuyakaido.android.cardstackview.CardStackView;
import com.yuyakaido.android.cardstackview.Direction;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_STORAGE_PERMISSION = 100;

    private CardStackView cardStackView;
    private CardStackLayoutManager layoutManager;
    private PhotoAdapter adapter;
    private List<File> photoList;

    private RecyclerView recyclerCategories;
    private CategoryAdapter categoryAdapter;
    private List<String> categoryList;

    private SettingsManager settings;
    private String currentSourceFolder;

    private View emptyState;
    private TextView tvProgress;
    private TextView tvNoCategories;
    private View btnUndoView;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Deque<SwipeRecord> undoStack = new ArrayDeque<>();

    private static class SwipeRecord {
        File source;
        String category;
        File destFile;
        boolean wasMove;
        SwipeRecord(File s, String c, File d, boolean m) {
            source = s; category = c; destFile = d; wasMove = m;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        settings = new SettingsManager(this);

        cardStackView = findViewById(R.id.cardStackView);
        recyclerCategories = findViewById(R.id.recyclerCategories);
        emptyState = findViewById(R.id.emptyState);
        tvProgress = findViewById(R.id.tvProgress);
        tvNoCategories = findViewById(R.id.tvNoCategories);
        btnUndoView = findViewById(R.id.btnUndo);

        // Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) setSupportActionBar(toolbar);

        loadCategories();

        categoryAdapter = new CategoryAdapter(categoryList, new CategoryAdapter.OnCategoryInteractionListener() {
            @Override
            public void onCategoryClick(String categoryName) {
                processPhoto(categoryName);
            }

            @Override
            public void onCategoryLongClick(String categoryName, int position) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle(R.string.remove_folder_title)
                        .setMessage(getString(R.string.remove_folder_message, categoryName))
                        .setPositiveButton(R.string.remove, (dialog, which) -> {
                            int idx = categoryList.indexOf(categoryName);
                            if (idx == -1) idx = position;
                            if (idx < 0 || idx >= categoryList.size()) {
                                Toast.makeText(MainActivity.this, R.string.error_empty_value, Toast.LENGTH_SHORT).show();
                                return;
                            }
                            categoryList.remove(idx);
                            saveCategories();
                            categoryAdapter.notifyItemRemoved(idx);
                            if (idx < categoryList.size()) {
                                categoryAdapter.notifyItemRangeChanged(idx, categoryList.size() - idx);
                            }
                            if (categoryList.isEmpty()) {
                                categoryAdapter.notifyDataSetChanged();
                            }
                            updateCategoriesEmptyState();
                            updateSourceButton();
                        })
                        .setNegativeButton(R.string.cancel, null)
                        .show();
            }
        });
        recyclerCategories.setAdapter(categoryAdapter);
        updateCategoriesEmptyState();

        findViewById(R.id.btnSkip).setOnClickListener(v -> processPhoto(null));
        findViewById(R.id.btnReload).setOnClickListener(v -> loadPhotos());
        View btnEmptyReload = findViewById(R.id.btnEmptyReload);
        if (btnEmptyReload != null) btnEmptyReload.setOnClickListener(v -> loadPhotos());
        findViewById(R.id.btnAddCategory).setOnClickListener(v -> showAddCategoryDialog());
        findViewById(R.id.btnChangeSource).setOnClickListener(v -> showChangeSourceDialog());
        // Toolbar icons may not exist if using menu; handle both
        View btnSettings = findViewById(R.id.btnSettings);
        if (btnSettings != null) btnSettings.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, SettingsActivity.class)));
        View btnTools = findViewById(R.id.btnTools);
        if (btnTools != null) btnTools.setOnClickListener(v ->
                startActivity(new Intent(MainActivity.this, ToolsActivity.class)));
        View btnStudio = findViewById(R.id.btnStudio);
        if (btnStudio != null) btnStudio.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, ResolutionStudioActivity.class);
            i.putExtra("folder", currentSourceFolder);
            startActivity(i);
        });
        if (btnUndoView != null) btnUndoView.setOnClickListener(v -> undoLastSwipe());
        updateUndoVisibility();

        View btnToggle = findViewById(R.id.btnToggleCategories);
        if (btnToggle != null) {
            btnToggle.setOnClickListener(v -> {
                boolean expanded = recyclerCategories.getVisibility() == View.VISIBLE;
                int newVis = expanded ? View.GONE : View.VISIBLE;
                recyclerCategories.setVisibility(newVis);
                if (tvNoCategories != null) {
                    boolean showEmpty = newVis == View.VISIBLE && (categoryList == null || categoryList.isEmpty());
                    tvNoCategories.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
                }
                v.setRotation(expanded ? 90 : 270);
                v.setContentDescription(expanded ? "Expand folders" : "Collapse folders");
            });
        }

        currentSourceFolder = settings.getInboxFolder().getAbsolutePath();
        updateSourceButton();

        checkPermissionsAndLoad();
    }

    private void updateCategoriesEmptyState() {
        if (tvNoCategories != null) {
            boolean expanded = recyclerCategories == null || recyclerCategories.getVisibility() == View.VISIBLE;
            boolean show = expanded && (categoryList == null || categoryList.isEmpty());
            tvNoCategories.setVisibility(show ? View.VISIBLE : View.GONE);
        }
    }

    private void updateUndoVisibility() {
        if (btnUndoView == null) return;
        boolean has = !undoStack.isEmpty() && layoutManager != null && layoutManager.getTopPosition() > 0;
        // Also show if topPosition==0 but stack has something and we can still rewind? CardStackView rewind needs top>0
        // Show only when we can rewind
        btnUndoView.setVisibility(has ? View.VISIBLE : View.GONE);
        btnUndoView.setAlpha(has ? 1f : 0.5f);
    }

    private void undoLastSwipe() {
        if (undoStack.isEmpty()) {
            Toast.makeText(this, "Nothing to undo", Toast.LENGTH_SHORT).show();
            return;
        }
        SwipeRecord rec = undoStack.pop();
        updateUndoVisibility();
        // If file was moved, move it back
        if (rec.category != null && rec.destFile != null && rec.destFile.exists()) {
            executorService.execute(() -> {
                File sourceParent = rec.source.getParentFile();
                if (sourceParent != null) {
                    if (!sourceParent.exists()) sourceParent.mkdirs();
                    File movedBack = FileUtils.moveOrCopy(rec.destFile, sourceParent, true);
                    if (movedBack != null) {
                        FileUtils.scanMedia(MainActivity.this, Collections.singletonList(movedBack.getAbsolutePath()));
                        FileUtils.scanMedia(MainActivity.this, Collections.singletonList(rec.destFile.getAbsolutePath()));
                    } else {
                        runOnUiThread(() -> Toast.makeText(MainActivity.this, R.string.file_operation_failed, Toast.LENGTH_SHORT).show());
                    }
                }
                runOnUiThread(() -> {
                    if (layoutManager != null && layoutManager.getTopPosition() > 0) {
                        cardStackView.rewind();
                    }
                    updateUndoVisibility();
                    updateProgress();
                    Toast.makeText(MainActivity.this, "Restacked: " + rec.source.getName(), Toast.LENGTH_SHORT).show();
                });
            });
        } else {
            if (layoutManager != null && layoutManager.getTopPosition() > 0) {
                cardStackView.rewind();
            }
            updateUndoVisibility();
            updateProgress();
            Toast.makeText(this, rec.category == null ? "Restacked" : "Restacked: " + rec.source.getName(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateSourceButton() {
        View v = findViewById(R.id.btnChangeSource);
        if (v == null) return;
        File source = new File(currentSourceFolder);
        String name = source.getName();
        if (name == null || name.isEmpty()) {
            name = currentSourceFolder;
        }
        String label = getString(R.string.source_button_format, name);
        if (v instanceof TextView) {
            ((TextView) v).setText(label);
        }
        v.setContentDescription(currentSourceFolder);
    }

    private void showChangeSourceDialog() {
        List<String> options = new ArrayList<>();
        options.add(settings.getInboxFolderName());
        options.addAll(categoryList);
        options.add(getString(R.string.custom_path_option));

        String[] optionsArray = options.toArray(new String[0]);

        new AlertDialog.Builder(this)
                .setTitle(R.string.select_source_title)
                .setItems(optionsArray, (dialog, which) -> {
                    if (which == optionsArray.length - 1) {
                        showCustomSourceDialog();
                        return;
                    }
                    String selected = optionsArray[which];
                    currentSourceFolder = new File(settings.getRootFolder(), selected).getAbsolutePath();
                    updateSourceButton();
                    loadPhotos();
                })
                .show();
    }

    private void showCustomSourceDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_path_with_browser, null);
        TextInputLayout til = view.findViewById(R.id.tilPath);
        TextInputEditText et = view.findViewById(R.id.etPath);
        MaterialButton btnBrowse = view.findViewById(R.id.btnBrowse);
        MaterialButton btnPaste = view.findViewById(R.id.btnPaste);
        if (til != null) til.setHint(getString(R.string.pref_root_folder_hint));
        et.setText(currentSourceFolder);
        et.setSelection(et.getText() != null ? et.getText().length() : 0);

        FolderPickerDialog.attach(this, et, btnBrowse, btnPaste);

        new AlertDialog.Builder(this)
                .setTitle(R.string.custom_source_title)
                .setView(view)
                .setPositiveButton(R.string.use, (dialog, which) -> {
                    String path = et.getText() != null ? et.getText().toString().trim() : "";
                    if (path.isEmpty()) {
                        Toast.makeText(this, R.string.error_empty_value, Toast.LENGTH_SHORT).show();
                        return;
                    }
                    currentSourceFolder = path;
                    updateSourceButton();
                    loadPhotos();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void loadCategories() {
        categoryList = settings.getCategories();
    }

    private void saveCategories() {
        settings.setCategories(categoryList);
    }

    private void showAddCategoryDialog() {
        View view = LayoutInflater.from(this).inflate(R.layout.dialog_add_category, null);
        TextInputLayout til = view.findViewById(R.id.tilCategoryName);
        TextInputEditText et = view.findViewById(R.id.etCategoryName);

        new AlertDialog.Builder(this)
                .setTitle(R.string.add_folder_title)
                .setView(view)
                .setPositiveButton(R.string.add, (dialog, which) -> {
                    String newCategory = et.getText() != null ? et.getText().toString().trim() : "";
                    if (!newCategory.isEmpty() && !categoryList.contains(newCategory)) {
                        categoryList.add(newCategory);
                        saveCategories();
                        categoryAdapter.notifyItemInserted(categoryList.size() - 1);
                        if (categoryList.size() > 1) {
                            categoryAdapter.notifyItemRangeChanged(0, categoryList.size());
                        }
                        recyclerCategories.smoothScrollToPosition(categoryList.size() - 1);
                        updateCategoriesEmptyState();
                        updateSourceButton();
                    } else {
                        Toast.makeText(this, R.string.empty_or_duplicate, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();

        File current = new File(currentSourceFolder);
        if (!current.exists() || !current.isDirectory()) {
            File inbox = settings.getInboxFolder();
            if (!currentSourceFolder.equals(inbox.getAbsolutePath())) {
                updateSourceButton();
            }
        } else {
            updateSourceButton();
        }

        if (canReadStorage() && (adapter == null || adapter.getItemCount() == 0)) {
            loadPhotos();
        }
        updateCategoriesEmptyState();
    }

    private boolean canReadStorage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        }
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                    startActivity(intent);
                    Toast.makeText(this, R.string.grant_all_files_access, Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                }
            } else {
                loadPhotos();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE,
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, REQUEST_STORAGE_PERMISSION);
            } else {
                loadPhotos();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_PERMISSION && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadPhotos();
        } else {
            Toast.makeText(this, R.string.permission_denied, Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPhotos() {
        undoStack.clear();
        updateUndoVisibility();
        photoList = new ArrayList<>();
        File directory = new File(currentSourceFolder);

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && settings.isSupportedImage(file)) {
                        photoList.add(file);
                    }
                }
                photoList.sort(settings.getFileComparator());
            }
        } else {
            Toast.makeText(this, R.string.folder_missing, Toast.LENGTH_SHORT).show();
        }

        adapter = new PhotoAdapter(photoList, settings.getLabelMode());
        layoutManager = new CardStackLayoutManager(this, new CardStackListener() {
            @Override public void onCardDragging(Direction direction, float ratio) {}
            @Override public void onCardSwiped(Direction direction) {
                // Handle manual swipe (gesture) that wasn't via processPhoto
                int swipedPos = layoutManager.getTopPosition() - 1;
                if (swipedPos >= 0 && swipedPos < photoList.size()) {
                    File swipedFile = photoList.get(swipedPos);
                    boolean alreadyRecorded = false;
                    if (!undoStack.isEmpty()) {
                        SwipeRecord top = undoStack.peek();
                        if (top != null && top.source.equals(swipedFile)) alreadyRecorded = true;
                    }
                    if (!alreadyRecorded) {
                        undoStack.push(new SwipeRecord(swipedFile, null, null, false));
                    }
                }
                updateProgress();
                updateUndoVisibility();
            }
            @Override public void onCardRewound() {
                updateProgress();
                updateUndoVisibility();
            }
            @Override public void onCardCanceled() {}
            @Override public void onCardAppeared(View view, int position) { updateProgress(); updateUndoVisibility(); }
            @Override public void onCardDisappeared(View view, int position) {}
        });
        // Sensible swipe settings
        layoutManager.setVisibleCount(3);
        layoutManager.setTranslationInterval(8f);
        layoutManager.setScaleInterval(0.95f);
        layoutManager.setSwipeThreshold(0.3f);
        layoutManager.setMaxDegree(20f);
        cardStackView.setLayoutManager(layoutManager);
        cardStackView.setAdapter(adapter);
        updateEmptyState();
        updateProgress();
        updateUndoVisibility();
    }

    private void updateEmptyState() {
        boolean empty = photoList == null || photoList.isEmpty();
        if (emptyState != null) emptyState.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (cardStackView != null) cardStackView.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        if (tvProgress != null) tvProgress.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (empty) updateUndoVisibility();
    }

    private void updateProgress() {
        if (tvProgress == null || layoutManager == null || photoList == null || photoList.isEmpty()) {
            if (tvProgress != null) tvProgress.setVisibility(View.GONE);
            return;
        }
        int top = layoutManager.getTopPosition();
        int total = photoList.size();
        int current = Math.min(top + 1, total);
        tvProgress.setText(current + " / " + total);
        tvProgress.setVisibility(View.VISIBLE);
        updateEmptyState();
    }

    private void processPhoto(String categoryName) {
        if (layoutManager == null || photoList == null) {
            return;
        }

        int currentPosition = layoutManager.getTopPosition();

        if (currentPosition < photoList.size()) {
            File currentFile = photoList.get(currentPosition);

            // Record for undo before swipe
            File destFile = null;
            boolean wasMove = settings.isMoveOperation();
            if (categoryName != null) {
                File destDir = settings.getFolderForCategory(categoryName);
                destFile = new File(destDir, currentFile.getName());
                undoStack.push(new SwipeRecord(currentFile, categoryName, destFile, wasMove));
                executorService.execute(() -> performFileOperation(currentFile, categoryName));
            } else {
                undoStack.push(new SwipeRecord(currentFile, null, null, false));
            }
            updateUndoVisibility();

            cardStackView.swipe();
        } else {
            Toast.makeText(this, R.string.folder_missing, Toast.LENGTH_SHORT).show();
        }
    }

    private void performFileOperation(File sourceFile, String categoryName) {
        File destDir = settings.getFolderForCategory(categoryName);
        File target = FileUtils.moveOrCopy(sourceFile, destDir, settings.isMoveOperation());
        if (target != null) {
            FileUtils.scanMedia(this, Collections.singletonList(target.getAbsolutePath()));
        } else {
            runOnUiThread(() ->
                    Toast.makeText(MainActivity.this, R.string.file_operation_failed, Toast.LENGTH_SHORT).show());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (executorService != null) {
            executorService.shutdown();
        }
    }
}
