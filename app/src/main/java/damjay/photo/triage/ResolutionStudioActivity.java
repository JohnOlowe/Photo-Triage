package damjay.photo.triage;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Environment;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Resolution Studio — post-triage flexible studio for mixed resolutions.
 * Creative, highly flexible: base-photo ratio, crop anchors (top/side/both),
 * stretch vs letterbox, split before/after with seamless preview, per-photo overrides, discard, mixed resolutions.
 */
public class ResolutionStudioActivity extends AppCompatActivity {

    private SettingsManager settings;
    private StudioConfig config = new StudioConfig();
    private List<StudioPhoto> photos = new ArrayList<>();
    private StudioPhoto basePhoto = null;

    private TextInputEditText etFolder, etCustomW, etCustomH, etTargetW, etTargetH, etOutput;
    private TextView tvCount, tvBaseInfo, tvSelectedCount, tvStatus, tvSplitRatioValue;
    private ChipGroup chipGroupRatio, chipGroupSplit;
    private MaterialButtonToggleGroup toggleFit, toggleSplitOrder;
    private SwitchMaterial switchKeepOriginal, switchMixed, switchAutoPanorama, switchSeamless;
    private Slider sliderSplit;
    private RecyclerView rvPhotos, rvSplitPreview;
    private View noPhotos;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ExecutorService bgDimExecutor = Executors.newFixedThreadPool(3);

    private StudioPhotoAdapter adapter;
    private SplitPreviewAdapter splitPreviewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_resolution_studio);

        settings = new SettingsManager(this);
        config.outputFolder = new File(settings.getRootFolder(), "Adjusted");

        Toolbar toolbar = findViewById(R.id.toolbarStudio);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        toolbar.setNavigationOnClickListener(v -> finish());

        // Find views
        etFolder = findViewById(R.id.etStudioFolder);
        tvCount = findViewById(R.id.tvStudioCount);
        tvBaseInfo = findViewById(R.id.tvBaseInfo);
        etCustomW = findViewById(R.id.etCustomW);
        etCustomH = findViewById(R.id.etCustomH);
        etTargetW = findViewById(R.id.etTargetW);
        etTargetH = findViewById(R.id.etTargetH);
        etOutput = findViewById(R.id.etOutputFolder);
        chipGroupRatio = findViewById(R.id.chipGroupRatio);
        chipGroupSplit = findViewById(R.id.chipGroupSplit);
        toggleFit = findViewById(R.id.toggleFitMode);
        toggleSplitOrder = findViewById(R.id.toggleSplitOrder);
        switchKeepOriginal = findViewById(R.id.switchKeepOriginal);
        switchMixed = findViewById(R.id.switchMixed);
        switchAutoPanorama = findViewById(R.id.switchAutoPanorama);
        switchSeamless = findViewById(R.id.switchSeamless);
        sliderSplit = findViewById(R.id.sliderSplitRatio);
        tvSplitRatioValue = findViewById(R.id.tvSplitRatioValue);
        rvPhotos = findViewById(R.id.rvStudioPhotos);
        rvSplitPreview = findViewById(R.id.rvSplitPreview);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        tvStatus = findViewById(R.id.tvStudioStatus);
        noPhotos = findViewById(R.id.tvNoPhotos);

        // Folder: honor intent extra "folder" (from MainActivity/Tools) then fallback
        File defaultFolder = settings.getInboxFolder();
        String intentFolder = getIntent() != null ? getIntent().getStringExtra("folder") : null;
        if (intentFolder != null && !intentFolder.trim().isEmpty()) {
            File f = new File(intentFolder.trim());
            if (f.exists()) defaultFolder = f;
            else defaultFolder = new File(intentFolder.trim());
        } else if (!defaultFolder.exists()) {
            List<String> cats = settings.getCategories();
            if (!cats.isEmpty()) defaultFolder = new File(settings.getRootFolder(), cats.get(0));
        }
        etFolder.setText(defaultFolder.getAbsolutePath());
        etOutput.setText(config.outputFolder.getAbsolutePath());

        FolderPickerDialog.attach(this, etFolder, findViewById(R.id.btnBrowseStudioFolder), findViewById(R.id.btnPasteStudioFolder));
        FolderPickerDialog.attach(this, etOutput, findViewById(R.id.btnBrowseOutput), findViewById(R.id.btnPasteOutput));

        findViewById(R.id.btnStudioReload).setOnClickListener(v -> loadPhotos());
        findViewById(R.id.btnClearBase).setOnClickListener(v -> clearBase());
        findViewById(R.id.btnApplyCustomRatio).setOnClickListener(v -> applyCustomRatio());
        findViewById(R.id.btnSelectAll).setOnClickListener(v -> selectAll(true));
        findViewById(R.id.btnDiscardSelected).setOnClickListener(v -> discardSelected());
        findViewById(R.id.btnResetOverrides).setOnClickListener(v -> resetOverrides());
        findViewById(R.id.btnExport).setOnClickListener(v -> export());

        // Ratio chips
        chipGroupRatio.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip c = group.findViewById(checkedIds.get(0));
            String txt = c.getText().toString();
            if ("Free".equals(txt)) {
                config.targetRatio = 0;
                config.ratioLabel = "Free";
                basePhoto = null;
                updateBaseInfo();
            } else {
                float r = StudioConfig.ratioForLabel(txt);
                if (r > 0) {
                    config.targetRatio = r;
                    config.ratioLabel = txt;
                    // Clear base if preset chosen (user wants preset, not base)
                    if (basePhoto != null) {
                        basePhoto.isBase = false;
                        basePhoto = null;
                    }
                    updateBaseInfo();
                }
            }
            refreshAll();
        });

        // Keep original
        switchKeepOriginal.setOnCheckedChangeListener((v, checked) -> {
            config.keepOriginalResolution = checked;
            View tilW = findViewById(R.id.tilTargetW);
            View tilH = findViewById(R.id.tilTargetH);
            if (tilW != null) tilW.setEnabled(!checked);
            if (tilH != null) tilH.setEnabled(!checked);
            if (!checked) {
                try {
                    config.targetWidth = Integer.parseInt(etTargetW.getText().toString().trim());
                    config.targetHeight = Integer.parseInt(etTargetH.getText().toString().trim());
                } catch (Exception ignored) {}
            }
            refreshAll();
        });

        // Target W/H editors
        TextWatcher resWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (!switchKeepOriginal.isChecked()) {
                    try {
                        config.targetWidth = Integer.parseInt(etTargetW.getText().toString().trim());
                        config.targetHeight = Integer.parseInt(etTargetH.getText().toString().trim());
                        if (config.targetWidth > 0 && config.targetHeight > 0) {
                            // If free ratio, update ratio to match target
                            if (config.targetRatio == 0) {
                                config.targetRatio = (float) config.targetWidth / config.targetHeight;
                                config.ratioLabel = StudioConfig.labelForRatio(config.targetRatio);
                            }
                        }
                    } catch (Exception ignored) {}
                    refreshAll();
                }
            }
        };
        etTargetW.addTextChangedListener(resWatcher);
        etTargetH.addTextChangedListener(resWatcher);

        // Mixed
        switchMixed.setOnCheckedChangeListener((v, c) -> {
            config.allowMixedResolutions = c;
            refreshAll();
        });
        switchAutoPanorama.setOnCheckedChangeListener((v, c) -> {
            config.autoPanorama = c;
            refreshAll();
        });
        switchSeamless.setOnCheckedChangeListener((v, c) -> {
            config.seamlessGap = c;
            config.gapPx = c ? 0 : 2;
            refreshAll();
        });

        // Fit mode
        toggleFit.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            if (id == R.id.btnFitCrop) config.fitMode = StudioConfig.FitMode.CROP;
            else if (id == R.id.btnFitStretch) config.fitMode = StudioConfig.FitMode.STRETCH;
            else if (id == R.id.btnFitLetterbox) config.fitMode = StudioConfig.FitMode.LETTERBOX;
            refreshAll();
        });
        toggleFit.check(R.id.btnFitCrop);

        // Crop gravity: wire all 11 buttons
        setupCropButtons();

        // Split
        chipGroupSplit.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            Chip c = g.findViewById(ids.get(0));
            String t = c.getText().toString();
            if (t.contains("None")) config.splitMode = StudioConfig.SplitMode.NONE;
            else if (t.contains("Vertical → 2")) config.splitMode = StudioConfig.SplitMode.VERTICAL_2;
            else if (t.contains("Horizontal → 2")) config.splitMode = StudioConfig.SplitMode.HORIZONTAL_2;
            else if (t.contains("Vertical → 3")) config.splitMode = StudioConfig.SplitMode.VERTICAL_3;
            refreshAll();
        });
        toggleSplitOrder.addOnButtonCheckedListener((g, id, checked) -> {
            if (!checked) return;
            if (id == R.id.btnOrderCropSplit) config.splitOrder = StudioConfig.SplitOrder.CROP_THEN_SPLIT;
            else config.splitOrder = StudioConfig.SplitOrder.SPLIT_THEN_CROP;
            refreshAll();
        });
        toggleSplitOrder.check(R.id.btnOrderCropSplit);
        sliderSplit.addOnChangeListener((slider, value, fromUser) -> {
            if (!fromUser) return;
            config.splitRatio = value / 100f;
            tvSplitRatioValue.setText(Math.round(value) + "% / " + (100 - Math.round(value)) + "%");
            refreshAll();
        });

        // Photos grid
        rvPhotos.setLayoutManager(new GridLayoutManager(this, 2));
        rvPhotos.setNestedScrollingEnabled(false);
        adapter = new StudioPhotoAdapter(photos, new StudioPhotoAdapter.Listener() {
            @Override public void onClick(StudioPhoto p, int pos) { showPreview(p); }
            @Override public void onLongClick(StudioPhoto p, int pos) { showPerPhotoEdit(p, pos); }
            @Override public void onBaseClick(StudioPhoto p, int pos) { setBase(p); }
            @Override public void onDiscardToggle(StudioPhoto p, int pos, boolean discard) {
                p.discarded = discard;
                adapter.notifyItemChanged(pos);
                updateCounts();
            }
            @Override public void onSelectToggle(StudioPhoto p, int pos, boolean selected) {
                updateCounts();
            }
        });
        rvPhotos.setAdapter(adapter);

        rvSplitPreview.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        splitPreviewAdapter = new SplitPreviewAdapter(new ArrayList<>());
        rvSplitPreview.setAdapter(splitPreviewAdapter);

        // Initial load
        loadPhotos();
        updateBaseInfo();
    }

    private void setupCropButtons() {
        View.OnClickListener l = v -> {
            int id = v.getId();
            StudioConfig.CropGravity g = StudioConfig.CropGravity.CENTER;
            if (id == R.id.btnCropCenter) g = StudioConfig.CropGravity.CENTER;
            else if (id == R.id.btnCropTop) g = StudioConfig.CropGravity.TOP;
            else if (id == R.id.btnCropBottom) g = StudioConfig.CropGravity.BOTTOM;
            else if (id == R.id.btnCropLeft) g = StudioConfig.CropGravity.LEFT;
            else if (id == R.id.btnCropRight) g = StudioConfig.CropGravity.RIGHT;
            else if (id == R.id.btnCropTopLeft) g = StudioConfig.CropGravity.TOP_LEFT;
            else if (id == R.id.btnCropTopRight) g = StudioConfig.CropGravity.TOP_RIGHT;
            else if (id == R.id.btnCropBottomLeft) g = StudioConfig.CropGravity.BOTTOM_LEFT;
            else if (id == R.id.btnCropBottomRight) g = StudioConfig.CropGravity.BOTTOM_RIGHT;
            else if (id == R.id.btnCropBothSides) g = StudioConfig.CropGravity.BOTH_SIDES;
            else if (id == R.id.btnCropBothTB) g = StudioConfig.CropGravity.BOTH_TOP_BOTTOM;
            config.cropGravity = g;
            highlightCrop(g);
            refreshAll();
        };
        int[] ids = {R.id.btnCropCenter, R.id.btnCropTop, R.id.btnCropBottom, R.id.btnCropLeft, R.id.btnCropRight,
                R.id.btnCropTopLeft, R.id.btnCropTopRight, R.id.btnCropBottomLeft, R.id.btnCropBottomRight,
                R.id.btnCropBothSides, R.id.btnCropBothTB};
        for (int id : ids) {
            View b = findViewById(id);
            if (b != null) b.setOnClickListener(l);
        }
        highlightCrop(config.cropGravity);
    }

    private void highlightCrop(StudioConfig.CropGravity g) {
        // Reset all to outlined, then highlight selected as tonal
        int[] ids = {R.id.btnCropCenter, R.id.btnCropTop, R.id.btnCropBottom, R.id.btnCropLeft, R.id.btnCropRight,
                R.id.btnCropTopLeft, R.id.btnCropTopRight, R.id.btnCropBottomLeft, R.id.btnCropBottomRight,
                R.id.btnCropBothSides, R.id.btnCropBothTB};
        for (int id : ids) {
            View v = findViewById(id);
            if (v instanceof MaterialButton) {
                ((MaterialButton) v).setStrokeWidth(1);
            }
        }
        int sel = R.id.btnCropCenter;
        switch (g) {
            case TOP: sel = R.id.btnCropTop; break;
            case BOTTOM: sel = R.id.btnCropBottom; break;
            case LEFT: sel = R.id.btnCropLeft; break;
            case RIGHT: sel = R.id.btnCropRight; break;
            case TOP_LEFT: sel = R.id.btnCropTopLeft; break;
            case TOP_RIGHT: sel = R.id.btnCropTopRight; break;
            case BOTTOM_LEFT: sel = R.id.btnCropBottomLeft; break;
            case BOTTOM_RIGHT: sel = R.id.btnCropBottomRight; break;
            case BOTH_SIDES: sel = R.id.btnCropBothSides; break;
            case BOTH_TOP_BOTTOM: sel = R.id.btnCropBothTB; break;
            default: sel = R.id.btnCropCenter; break;
        }
        View sv = findViewById(sel);
        if (sv instanceof MaterialButton) {
            ((MaterialButton) sv).setStrokeWidth(3);
        }
    }

    private void loadPhotos() {
        String path = etFolder.getText().toString().trim();
        if (path.isEmpty()) {
            Toast.makeText(this, R.string.error_empty_value, Toast.LENGTH_SHORT).show();
            return;
        }
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            Toast.makeText(this, R.string.folder_missing, Toast.LENGTH_SHORT).show();
            photos.clear();
            adapter.notifyDataSetChanged();
            updateCounts();
            return;
        }
        tvStatus.setText(R.string.status_running);
        executor.execute(() -> {
            List<File> files = FileUtils.collectImages(dir, false, settings.getImageExtensions());
            files.sort(settings.getFileComparator());
            List<StudioPhoto> newList = new ArrayList<>();
            for (File f : files) newList.add(new StudioPhoto(f));

            // Preload dimensions in background pool
            for (StudioPhoto p : newList) {
                bgDimExecutor.execute(() -> {
                    try {
                        ImageProcessor.Dimensions d = ImageProcessor.getDimensions(p.file);
                        p.setDimensions(d.width, d.height);
                        runOnUiThread(() -> {
                            adapter.notifyDataSetChanged();
                            updateCounts();
                            updateSplitPreview();
                        });
                    } catch (Exception ignored) {}
                });
            }

            runOnUiThread(() -> {
                photos.clear();
                photos.addAll(newList);
                // Preserve base if still exists
                if (basePhoto != null) {
                    boolean found = false;
                    for (StudioPhoto p : photos) if (p.file.equals(basePhoto.file)) { p.isBase = true; basePhoto = p; found = true; break; }
                    if (!found) basePhoto = null;
                }
                adapter.notifyDataSetChanged();
                updateCounts();
                updateSplitPreview();
                tvStatus.setText(getString(R.string.tool_scan_result, photos.size()));
                if (photos.isEmpty()) Toast.makeText(this, R.string.studio_no_photos, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private void setBase(StudioPhoto p) {
        if (basePhoto != null) basePhoto.isBase = false;
        if (p == basePhoto) { // toggle off
            basePhoto = null;
            config.targetRatio = 0;
            config.ratioLabel = "Free";
            chipGroupRatio.check(R.id.chipRatioFree);
        } else {
            p.isBase = true;
            basePhoto = p;
            if (p.aspect > 0) {
                config.targetRatio = p.aspect;
                config.ratioLabel = StudioConfig.labelForRatio(p.aspect);
                // Update chips: select no preset or custom? Show base info, uncheck group?
                chipGroupRatio.clearCheck();
            }
        }
        updateBaseInfo();
        adapter.notifyDataSetChanged();
        refreshAll();
    }

    private void clearBase() {
        if (basePhoto != null) basePhoto.isBase = false;
        basePhoto = null;
        config.targetRatio = 0;
        config.ratioLabel = "Free";
        chipGroupRatio.check(R.id.chipRatioFree);
        updateBaseInfo();
        adapter.notifyDataSetChanged();
        refreshAll();
    }

    private void updateBaseInfo() {
        if (basePhoto != null && basePhoto.width > 0) {
            tvBaseInfo.setText("Base: " + basePhoto.name + " • " + basePhoto.width + "×" + basePhoto.height + " • " + StudioConfig.labelForRatio(basePhoto.aspect));
        } else if (config.targetRatio > 0) {
            tvBaseInfo.setText("Target: " + config.ratioLabel + " • " + String.format("%.2f:1", config.targetRatio));
        } else {
            tvBaseInfo.setText(R.string.studio_no_base);
        }
    }

    private void applyCustomRatio() {
        try {
            int w = Integer.parseInt(etCustomW.getText().toString().trim());
            int h = Integer.parseInt(etCustomH.getText().toString().trim());
            if (w <= 0 || h <= 0) throw new NumberFormatException();
            float r = (float) w / h;
            config.targetRatio = r;
            config.ratioLabel = w + ":" + h;
            if (basePhoto != null) { basePhoto.isBase = false; basePhoto = null; }
            chipGroupRatio.clearCheck();
            updateBaseInfo();
            refreshAll();
            Toast.makeText(this, "Ratio " + w + ":" + h, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, R.string.error_empty_value, Toast.LENGTH_SHORT).show();
        }
    }

    private void updateCounts() {
        if (photos.isEmpty()) {
            tvCount.setText("0 photos");
            noPhotos.setVisibility(View.VISIBLE);
            rvPhotos.setVisibility(View.GONE);
        } else {
            // Count varied resolutions
            int varied = 0;
            String first = photos.get(0).width + "x" + photos.get(0).height;
            for (StudioPhoto p : photos) if (!(p.width + "x" + p.height).equals(first)) varied++;
            tvCount.setText(getString(R.string.studio_photos_count, photos.size(), varied));
            noPhotos.setVisibility(View.GONE);
            rvPhotos.setVisibility(View.VISIBLE);
        }
        int sel = 0, discarded = 0;
        for (StudioPhoto p : photos) { if (p.selected) sel++; if (p.discarded) discarded++; }
        tvSelectedCount.setText(getString(R.string.studio_selected_count, sel) + (discarded > 0 ? " • " + discarded + " trashed" : ""));
    }

    private void selectAll(boolean sel) {
        for (StudioPhoto p : photos) p.selected = sel;
        adapter.notifyDataSetChanged();
        updateCounts();
    }

    private void discardSelected() {
        for (StudioPhoto p : photos) if (p.selected) p.discarded = true;
        adapter.notifyDataSetChanged();
        updateCounts();
        Toast.makeText(this, "Marked selected as discarded", Toast.LENGTH_SHORT).show();
    }

    private void resetOverrides() {
        for (StudioPhoto p : photos) p.clearOverrides();
        adapter.notifyDataSetChanged();
        Toast.makeText(this, "Overrides cleared", Toast.LENGTH_SHORT).show();
    }

    private void refreshAll() {
        adapter.notifyDataSetChanged();
        updateSplitPreview();
    }

    private void updateSplitPreview() {
        if (photos.isEmpty() || config.splitMode == StudioConfig.SplitMode.NONE) {
            rvSplitPreview.setVisibility(View.GONE);
            return;
        }
        rvSplitPreview.setVisibility(View.VISIBLE);
        // Pick first non-discarded photo for preview, or first
        StudioPhoto preview = null;
        for (StudioPhoto p : photos) if (!p.discarded) { preview = p; break; }
        if (preview == null) preview = photos.get(0);
        File f = preview.file;
        List<File> halves = new ArrayList<>();
        halves.add(f);
        halves.add(f);
        splitPreviewAdapter.update(halves);
    }

    private void showPreview(StudioPhoto p) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_studio_preview, null);
        ImageView ivBefore = v.findViewById(R.id.ivPreviewBefore);
        ImageView ivAfter = v.findViewById(R.id.ivPreviewAfter);
        TextView tvInfo = v.findViewById(R.id.tvPreviewInfo);
        TextView tvTitle = v.findViewById(R.id.tvPreviewTitle);
        tvTitle.setText(p.name);

        Glide.with(this).load(p.file).into(ivBefore);

        // Generate after preview via processor (on background)
        tvInfo.setText(p.width + "×" + p.height + " → processing…");
        new Thread(() -> {
            try {
                Bitmap bmp = ImageProcessor.loadBitmap(p.file, 800);
                if (bmp != null) {
                    StudioConfig eff = p.effectiveConfig(config);
                    // Use dimensions for processing
                    ImageProcessor.Dimensions d = ImageProcessor.getDimensions(p.file);
                    Bitmap after = ImageProcessor.transform(bmp, eff, d.width, d.height);
                    List<Bitmap> splits = ImageProcessor.split(after, eff);
                    Bitmap display = splits.get(0);
                    // If split, maybe show first half? For preview we show first half
                    runOnUiThread(() -> {
                        ivAfter.setImageBitmap(display);
                        String info = d.width + "×" + d.height + " → " + display.getWidth() + "×" + display.getHeight()
                                + " • " + eff.cropGravity.name() + " • " + eff.fitMode.name()
                                + (eff.splitMode != StudioConfig.SplitMode.NONE ? " • Split " + eff.splitMode.name() : "");
                        tvInfo.setText(info);
                        // Halves recycler
                        View halvesLayout = v.findViewById(R.id.layoutSplitHalves);
                        RecyclerView rvHalves = v.findViewById(R.id.rvPreviewHalves);
                        if (splits.size() > 1) {
                            halvesLayout.setVisibility(View.VISIBLE);
                            rvHalves.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
                            PreviewHalvesAdapter ha = new PreviewHalvesAdapter(splits);
                            rvHalves.setAdapter(ha);
                        }
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> tvInfo.setText("Preview failed: " + e.getMessage()));
            }
        }).start();

        new AlertDialog.Builder(this).setView(v).setPositiveButton(R.string.cancel, null).show();
    }

    private void showPerPhotoEdit(StudioPhoto p, int pos) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_studio_photo_edit, null);
        TextView tvName = v.findViewById(R.id.tvEditPhotoName);
        tvName.setText(p.name + " • " + p.width + "×" + p.height);

        ChipGroup cgCrop = v.findViewById(R.id.chipGroupEditCrop);
        ChipGroup cgFit = v.findViewById(R.id.chipGroupEditFit);
        ChipGroup cgSplit = v.findViewById(R.id.chipGroupEditSplit);
        View btnDiscard = v.findViewById(R.id.btnEditDiscard);
        View btnClear = v.findViewById(R.id.btnEditClear);

        // Pre-select
        // Crop
        int cropIdx = 0;
        if (p.overrideCrop != null) {
            switch (p.overrideCrop) {
                case CENTER: cropIdx = 1; break;
                case TOP: cropIdx = 2; break;
                case BOTTOM: cropIdx = 3; break;
                case LEFT: cropIdx = 4; break;
                case RIGHT: cropIdx = 5; break;
                case BOTH_SIDES: cropIdx = 6; break;
                case BOTH_TOP_BOTTOM: cropIdx = 7; break;
                default: cropIdx = 1; break;
            }
            ((Chip) cgCrop.getChildAt(cropIdx)).setChecked(true);
        } else {
            ((Chip) cgCrop.getChildAt(0)).setChecked(true);
        }
        // Fit
        int fitIdx = 0;
        if (p.overrideFit != null) {
            switch (p.overrideFit) {
                case CROP: fitIdx = 1; break;
                case STRETCH: fitIdx = 2; break;
                case LETTERBOX: fitIdx = 3; break;
            }
            ((Chip) cgFit.getChildAt(fitIdx)).setChecked(true);
        }
        // Split
        int splitIdx = 0;
        if (p.overrideSplit != null) {
            switch (p.overrideSplit) {
                case NONE: splitIdx = 1; break;
                case VERTICAL_2: splitIdx = 2; break;
                case HORIZONTAL_2: splitIdx = 3; break;
            }
            ((Chip) cgSplit.getChildAt(splitIdx)).setChecked(true);
        }

        AlertDialog dlg = new AlertDialog.Builder(this).setView(v)
                .setPositiveButton(R.string.use, (d, w) -> {
                    // Apply
                    Chip sc = null;
                    for (int i=0;i<cgCrop.getChildCount();i++) {
                        Chip c = (Chip) cgCrop.getChildAt(i);
                        if (c.isChecked()) { sc=c; break; }
                    }
                    if (sc != null) {
                        String t = sc.getText().toString();
                        if ("Use global".equals(t)) p.overrideCrop = null;
                        else if ("Center".equals(t)) p.overrideCrop = StudioConfig.CropGravity.CENTER;
                        else if ("Top".equals(t)) p.overrideCrop = StudioConfig.CropGravity.TOP;
                        else if ("Bottom".equals(t)) p.overrideCrop = StudioConfig.CropGravity.BOTTOM;
                        else if ("Left".equals(t)) p.overrideCrop = StudioConfig.CropGravity.LEFT;
                        else if ("Right".equals(t)) p.overrideCrop = StudioConfig.CropGravity.RIGHT;
                        else if ("Both sides".equals(t)) p.overrideCrop = StudioConfig.CropGravity.BOTH_SIDES;
                        else if ("Both T&B".equals(t)) p.overrideCrop = StudioConfig.CropGravity.BOTH_TOP_BOTTOM;
                    }
                    Chip sf = null;
                    for (int i=0;i<cgFit.getChildCount();i++) {
                        Chip c = (Chip) cgFit.getChildAt(i);
                        if (c.isChecked()) { sf=c; break; }
                    }
                    if (sf != null) {
                        String t = sf.getText().toString();
                        if ("Global".equals(t)) p.overrideFit = null;
                        else if ("Crop".equals(t)) p.overrideFit = StudioConfig.FitMode.CROP;
                        else if ("Stretch".equals(t)) p.overrideFit = StudioConfig.FitMode.STRETCH;
                        else if ("Letterbox".equals(t)) p.overrideFit = StudioConfig.FitMode.LETTERBOX;
                    }
                    Chip ss = null;
                    for (int i=0;i<cgSplit.getChildCount();i++) {
                        Chip c = (Chip) cgSplit.getChildAt(i);
                        if (c.isChecked()) { ss=c; break; }
                    }
                    if (ss != null) {
                        String t = ss.getText().toString();
                        if ("Global".equals(t)) p.overrideSplit = null;
                        else if ("None".equals(t)) p.overrideSplit = StudioConfig.SplitMode.NONE;
                        else if ("V 2".equals(t)) p.overrideSplit = StudioConfig.SplitMode.VERTICAL_2;
                        else if ("H 2".equals(t)) p.overrideSplit = StudioConfig.SplitMode.HORIZONTAL_2;
                    }
                    adapter.notifyItemChanged(pos);
                    refreshAll();
                })
                .setNegativeButton(R.string.cancel, null)
                .create();

        btnDiscard.setOnClickListener(x -> {
            p.discarded = !p.discarded;
            adapter.notifyItemChanged(pos);
            updateCounts();
        });
        btnClear.setOnClickListener(x -> {
            p.clearOverrides();
            adapter.notifyItemChanged(pos);
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show();
        });

        dlg.show();
    }

    private void export() {
        String outPath = etOutput.getText().toString().trim();
        if (outPath.isEmpty()) {
            Toast.makeText(this, R.string.error_empty_value, Toast.LENGTH_SHORT).show();
            return;
        }
        File outDir = new File(outPath);
        if (!outDir.exists() && !outDir.mkdirs()) {
            Toast.makeText(this, R.string.invalid_path, Toast.LENGTH_SHORT).show();
            return;
        }
        config.outputFolder = outDir;
        if (!switchKeepOriginal.isChecked()) {
            try {
                config.targetWidth = Integer.parseInt(etTargetW.getText().toString().trim());
                config.targetHeight = Integer.parseInt(etTargetH.getText().toString().trim());
            } catch (Exception e) {
                Toast.makeText(this, "Invalid target W/H", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        // Filter non-discarded
        List<StudioPhoto> toExport = new ArrayList<>();
        for (StudioPhoto p : photos) if (!p.discarded) toExport.add(p);
        if (toExport.isEmpty()) {
            Toast.makeText(this, "No photos to export (all discarded)", Toast.LENGTH_SHORT).show();
            return;
        }
        tvStatus.setText(R.string.studio_exporting);
        findViewById(R.id.btnExport).setEnabled(false);

        executor.execute(() -> {
            int exported = 0, splits = 0, discarded = photos.size() - toExport.size();
            List<String> scanned = new ArrayList<>();
            for (StudioPhoto p : toExport) {
                StudioConfig eff = p.effectiveConfig(config);
                // Respect mixed resolutions: if allowMixed false, keepOriginal is false and target is forced – already handled
                // If allowMixed true, keepOriginal true will keep varied, but if targetRatio is set we still crop to ratio individually
                List<File> outs = ImageProcessor.processAndSave(p.file, outDir, eff, exported);
                for (File f : outs) scanned.add(f.getAbsolutePath());
                exported++;
                if (outs.size() > 1) splits += outs.size() - 1;
            }
            FileUtils.scanMedia(this, scanned);
            int fExported = exported, fSplits = splits, fDiscarded = discarded;
            runOnUiThread(() -> {
                String msg = getString(R.string.studio_exported, fExported, fSplits, fDiscarded);
                tvStatus.setText(msg);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                findViewById(R.id.btnExport).setEnabled(true);
            });
        });
    }

    // Simple split preview adapter that just shows the same image twice (placeholder)
    static class SplitPreviewAdapter extends RecyclerView.Adapter<SplitPreviewAdapter.VH> {
        List<File> files;
        SplitPreviewAdapter(List<File> f) { files = f; }
        void update(List<File> f) { files = f; notifyDataSetChanged(); }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_split_preview, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            File f = files.get(pos % files.size());
            Glide.with(h.image.getContext()).load(f).centerCrop().into(h.image);
        }
        @Override public int getItemCount() { return Math.min(files.size(), 2); }
        static class VH extends RecyclerView.ViewHolder {
            ImageView image;
            VH(View v) { super(v); image = v.findViewById(R.id.ivSplit); }
        }
    }

    static class PreviewHalvesAdapter extends RecyclerView.Adapter<PreviewHalvesAdapter.VH> {
        List<Bitmap> bitmaps;
        PreviewHalvesAdapter(List<Bitmap> b) { bitmaps = b; }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_split_preview, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            h.image.setImageBitmap(bitmaps.get(pos));
        }
        @Override public int getItemCount() { return bitmaps.size(); }
        static class VH extends RecyclerView.ViewHolder {
            ImageView image;
            VH(View v) { super(v); image = v.findViewById(R.id.ivSplit); }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        executor.shutdown();
        bgDimExecutor.shutdown();
    }
}
