package damjay.photo.triage;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.yuyakaido.android.cardstackview.CardStackLayoutManager;
import com.yuyakaido.android.cardstackview.CardStackView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private CardStackView cardStackView;
    private CardStackLayoutManager layoutManager;
    private PhotoAdapter adapter;
    private List<File> photoList;
    
    private RecyclerView recyclerCategories;
    private CategoryAdapter categoryAdapter;
    private List<String> categoryList;
    private SharedPreferences prefs;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    private static final String DEST_BASE_FOLDER = "/storage/emulated/0/Pictures/FSFUI Photos/";
    // Make this dynamic instead of a constant
    private String currentSourceFolder = DEST_BASE_FOLDER + "Ordered Photos";
    
    private static final String PREFS_NAME = "PhotoTriagePrefs";
    private static final String KEY_CATEGORIES = "SavedCategories";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        cardStackView = findViewById(R.id.cardStackView);
        recyclerCategories = findViewById(R.id.recyclerCategories);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        loadCategories();

        categoryAdapter = new CategoryAdapter(categoryList, new CategoryAdapter.OnCategoryInteractionListener() {
            @Override
            public void onCategoryClick(String categoryName) {
                processPhoto(categoryName);
            }

            @Override
            public void onCategoryLongClick(String categoryName, int position) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Remove Folder?")
                        .setMessage("Hide '" + categoryName + "' from this list?")
                        .setPositiveButton("Remove", (dialog, which) -> {
                            categoryList.remove(position);
                            saveCategories();
                            categoryAdapter.notifyItemRemoved(position);
                        })
                        .setNegativeButton("Cancel", null)
                        .show();
            }
        });
        recyclerCategories.setAdapter(categoryAdapter);

        findViewById(R.id.btnSkip).setOnClickListener(v -> processPhoto(null));
        findViewById(R.id.btnReload).setOnClickListener(v -> loadPhotos());
        findViewById(R.id.btnAddCategory).setOnClickListener(v -> showAddCategoryDialog());
        findViewById(R.id.btnChangeSource).setOnClickListener(v -> showChangeSourceDialog());
        
        checkPermissionsAndLoad();
        // syncGitMessToHighRes();
    }
    
    private void showChangeSourceDialog() {
        List<String> options = new ArrayList<>();
        options.add("Ordered Photos"); // The original root folder
        options.addAll(categoryList); // All your custom folders
        
        String[] optionsArray = options.toArray(new String[0]);
        
        new AlertDialog.Builder(this)
                .setTitle("Select Source Folder")
                .setItems(optionsArray, (dialog, which) -> {
                    String selected = optionsArray[which];
                    currentSourceFolder = DEST_BASE_FOLDER + selected;
                    
                    MaterialButton btnSource = findViewById(R.id.btnChangeSource);
                    btnSource.setText("Source: " + selected);
                    
                    loadPhotos(); // Reload the deck from the new folder
                })
                .show();
    }

    private void loadCategories() {
        Set<String> savedCategories = prefs.getStringSet(KEY_CATEGORIES, null);
        categoryList = new ArrayList<>();
        
        if (savedCategories == null || savedCategories.isEmpty()) {
            categoryList.addAll(Arrays.asList("Worship", "Prayer", "Choir Ministration", "Sermon", "Drama"));
            saveCategories();
        } else {
            categoryList.addAll(savedCategories);
        }
    }

    private void saveCategories() {
        Set<String> set = new HashSet<>(categoryList);
        prefs.edit().putStringSet(KEY_CATEGORIES, set).apply();
    }
    
    private void showAddCategoryDialog() {
        final EditText input = new EditText(this);
        input.setHint("e.g. Testimonies");

        new AlertDialog.Builder(this)
                .setTitle("Add New Folder")
                .setView(input)
                .setPositiveButton("Add", (dialog, which) -> {
                    String newCategory = input.getText().toString().trim();
                    if (!newCategory.isEmpty() && !categoryList.contains(newCategory)) {
                        categoryList.add(newCategory);
                        saveCategories();
                        categoryAdapter.notifyItemInserted(categoryList.size() - 1);
                        recyclerCategories.smoothScrollToPosition(categoryList.size() - 1);
                    } else {
                        Toast.makeText(this, "Empty or duplicate name", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            if (adapter == null || adapter.getItemCount() == 0) {
                loadPhotos();
            }
        }
    }

    private void checkPermissionsAndLoad() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.DEFAULT");
                    intent.setData(Uri.parse(String.format("package:%s", getPackageName())));
                    startActivity(intent);
                    Toast.makeText(this, "Please grant All Files Access and press back", Toast.LENGTH_LONG).show();
                } catch (Exception e) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            } else {
                loadPhotos();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{
                        Manifest.permission.WRITE_EXTERNAL_STORAGE, 
                        Manifest.permission.READ_EXTERNAL_STORAGE
                }, 100);
            } else {
                loadPhotos();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadPhotos();
        } else {
            Toast.makeText(this, "Permission denied.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPhotos() {
        photoList = new ArrayList<>();
        File directory = new File(currentSourceFolder);

        if (directory.exists() && directory.isDirectory()) {
            File[] files = directory.listFiles();
            if (files != null) {
                java.util.Arrays.sort(files, (f1, f2) -> f1.getName().compareToIgnoreCase(f2.getName()));

                for (File file : files) {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png")) {
                        photoList.add(file);
                    }
                }
            }
        } else {
            Toast.makeText(this, "Folder is empty or missing.", Toast.LENGTH_SHORT).show();
        }

        adapter = new PhotoAdapter(photoList);
        layoutManager = new CardStackLayoutManager(this);
        cardStackView.setLayoutManager(layoutManager);
        cardStackView.setAdapter(adapter);
    }

    private void processPhoto(String categoryName) {
        if (layoutManager == null || photoList == null) return;
        
        int currentPosition = layoutManager.getTopPosition();
        
        if (currentPosition < photoList.size()) {
            File currentFile = photoList.get(currentPosition);

            if (categoryName != null) {
                executorService.execute(() -> moveFile(currentFile, categoryName));
            }
            
            cardStackView.swipe();
        }
    }

    private void moveFile(File sourceFile, String categoryName) {
        File destDir = new File(DEST_BASE_FOLDER + categoryName);
        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        File destFile = new File(destDir, sourceFile.getName());
        
        // Don't try to move the file if you're already in that folder, genius.
        if (sourceFile.getAbsolutePath().equals(destFile.getAbsolutePath())) {
            return;
        }

        boolean success = sourceFile.renameTo(destFile);

        if (success) {
            MediaScannerConnection.scanFile(this, new String[]{destFile.getAbsolutePath()}, null, null);
        }
    }
    
    private void syncGitMessToHighRes() {
        File gitBaseDir = new File("/storage/emulated/0/FSFUI-Photos");
        File destBaseDir = new File("/storage/emulated/0/Pictures/FSFUI Photos");
        File orderedDir = new File(destBaseDir, "Ordered Photos");

        if (!gitBaseDir.exists() || !orderedDir.exists()) {
            android.widget.Toast.makeText(this, "Directories missing.", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }

        java.util.List<String> filesToScan = new java.util.ArrayList<>();
        File[] gitFolders = gitBaseDir.listFiles();
        if (gitFolders == null) return;

        for (File gFolder : gitFolders) {
            if (gFolder.isDirectory() && gFolder.getName().matches("^\\d+.*")) {
                
                // Map the folders properly
                String destFolderName = gFolder.getName().replaceFirst("^\\d+_", "").replace("_", " ");
                if (gFolder.getName().contains("Sunday_School")) destFolderName = "Sunday School";
                if (gFolder.getName().contains("Sermon_Teaching")) destFolderName = "Teaching";
                if (gFolder.getName().contains("Choir_Ministration")) destFolderName = "Ministration";
                
                File destFolder = new File(destBaseDir, destFolderName);

                if (!destFolder.exists()) {
                    destFolder.mkdirs();
                } else {
                    File[] existingFiles = destFolder.listFiles();
                    if (existingFiles != null) {
                        for (File f : existingFiles) {
                            if (f.isFile() && f.getName().toLowerCase().endsWith(".jpg")) {
                                f.delete();
                            }
                        }
                    }
                }

                File[] lowResFiles = gFolder.listFiles();
                if (lowResFiles != null) {
                    for (File lowRes : lowResFiles) {
                        if (lowRes.isFile()) {
                            // Strip the leading numbers and underscore from the Git filename
                            String realName = lowRes.getName().replaceFirst("^\\d+_", "");
                            File highRes = new File(orderedDir, realName);
                            
                            if (highRes.exists()) {
                                // Save it with the clean, original name
                                File targetHighRes = new File(destFolder, realName);
                                if (highRes.renameTo(targetHighRes)) {
                                    filesToScan.add(targetHighRes.getAbsolutePath());
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!filesToScan.isEmpty()) {
            android.media.MediaScannerConnection.scanFile(this, filesToScan.toArray(new String[0]), null, null);
            android.widget.Toast.makeText(this, "Cleaned up and moved " + filesToScan.size() + " photos.", android.widget.Toast.LENGTH_LONG).show();
        } else {
            android.widget.Toast.makeText(this, "No matches found. Check your file names again.", android.widget.Toast.LENGTH_LONG).show();
        }
    }
    
    private void fixGitPhotoMess() {
        // Notice the hyphen, matching your screenshot
        File baseDir = new File("/storage/emulated/0/Pictures/FSFUI-Photos");
        java.util.List<String> paths = new java.util.ArrayList<>();

        File[] folders = baseDir.listFiles();
        if (folders == null) return;

        for (File folder : folders) {
            // Only look at folders starting with a number (ignores .git and README.md)
            if (folder.isDirectory() && folder.getName().matches("^\\d+.*")) {
                File[] images = folder.listFiles();
                if (images != null) {
                    for (File img : images) {
                        paths.add(img.getAbsolutePath());
                    }
                }
            }
        }

        if (!paths.isEmpty()) {
            // Blast the whole array to the media scanner at once
            android.media.MediaScannerConnection.scanFile(this, paths.toArray(new String[0]), null, null);
            android.widget.Toast.makeText(this, "Scanned " + paths.size() + " files. Check Instagram.", android.widget.Toast.LENGTH_LONG).show();
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
