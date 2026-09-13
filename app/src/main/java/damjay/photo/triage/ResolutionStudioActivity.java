package damjay.photo.triage;

import android.app.AlertDialog;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.bumptech.glide.Glide;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.slider.Slider;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ResolutionStudioActivity extends AppCompatActivity {

    private SettingsManager settings;
    private StudioConfig config = new StudioConfig();
    private final List<StudioPhoto> photos = new ArrayList<>();
    private StudioPhoto basePhoto = null;

    private TextInputEditText etFolder, etCustomW, etCustomH, etTargetW, etTargetH, etOutput;
    private TextView tvStudioCount, tvBaseInfo, tvSelectedCount, tvPagerCount, tvPagerRatio, tvPagerRes, tvPagerBaseBadge, tvPagerDiscardBadge, tvSplitRatioValue, tvStudioStatus;
    private ChipGroup chipGroupRatio, chipGroupSplit;
    private MaterialButtonToggleGroup toggleFit, toggleSplitOrder;
    private SwitchMaterial switchKeepOriginal, switchMixed, switchAutoPanorama, switchSeamless;
    private Slider sliderSplit;
    private View noPhotos;
    private View panelContainer, panelRatio, panelCrop, panelFit, panelSplit, panelMore;
    private ViewPager2 vpStudio;
    private RecyclerView rvThumbs, rvSplitPreview, rvStudioPhotosHidden;
    private StudioPagerAdapter pagerAdapter;
    private ThumbsAdapter thumbsAdapter;
    private SplitPreviewAdapter splitPreviewAdapter;

    private final ExecutorService bgExecutor = Executors.newSingleThreadExecutor();
    private final ExecutorService dimExecutor = Executors.newFixedThreadPool(2);
    private int currentPos = 0;
    private View lastToolButton = null;

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

        etFolder = findViewById(R.id.etStudioFolder);
        tvStudioCount = findViewById(R.id.tvStudioCount);
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
        tvPagerCount = findViewById(R.id.tvPagerCount);
        tvPagerRatio = findViewById(R.id.tvPagerRatio);
        tvPagerRes = findViewById(R.id.tvPagerRes);
        tvPagerBaseBadge = findViewById(R.id.tvPagerBaseBadge);
        tvPagerDiscardBadge = findViewById(R.id.tvPagerDiscardBadge);
        tvBaseInfo = findViewById(R.id.tvBaseInfo);
        tvSelectedCount = findViewById(R.id.tvSelectedCount);
        tvStudioStatus = findViewById(R.id.tvStudioStatus);
        noPhotos = findViewById(R.id.tvNoPhotos);
        panelContainer = findViewById(R.id.panelContainer);
        panelRatio = findViewById(R.id.panelRatio);
        panelCrop = findViewById(R.id.panelCrop);
        panelFit = findViewById(R.id.panelFit);
        panelSplit = findViewById(R.id.panelSplit);
        panelMore = findViewById(R.id.panelMore);
        vpStudio = findViewById(R.id.vpStudio);
        rvThumbs = findViewById(R.id.rvThumbs);
        rvSplitPreview = findViewById(R.id.rvSplitPreview);
        rvStudioPhotosHidden = findViewById(R.id.rvStudioPhotos);

        File defaultFolder = settings.getInboxFolder();
        String intentFolder = getIntent() != null ? getIntent().getStringExtra("folder") : null;
        if (intentFolder != null && !intentFolder.trim().isEmpty()) {
            File f = new File(intentFolder.trim());
            defaultFolder = f.exists() ? f : new File(intentFolder.trim());
        } else if (!defaultFolder.exists()) {
            List<String> cats = settings.getCategories();
            if (!cats.isEmpty()) defaultFolder = new File(settings.getRootFolder(), cats.get(0));
        }
        if (etFolder != null) etFolder.setText(defaultFolder.getAbsolutePath());
        if (etOutput != null) etOutput.setText(config.outputFolder.getAbsolutePath());

        FolderPickerDialog.attach(this, etFolder, findViewById(R.id.btnBrowseStudioFolder), findViewById(R.id.btnPasteStudioFolder));
        FolderPickerDialog.attach(this, etOutput, findViewById(R.id.btnBrowseOutput), findViewById(R.id.btnPasteOutput));

        pagerAdapter = new StudioPagerAdapter();
        vpStudio.setAdapter(pagerAdapter);
        vpStudio.setOffscreenPageLimit(1);
        vpStudio.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                currentPos = position;
                syncThumbsToPager(position);
                updatePagerOverlay(position);
            }
        });

        thumbsAdapter = new ThumbsAdapter();
        rvThumbs.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        rvThumbs.setAdapter(thumbsAdapter);

        if (rvSplitPreview != null) {
            rvSplitPreview.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
            splitPreviewAdapter = new SplitPreviewAdapter(new ArrayList<>());
            rvSplitPreview.setAdapter(splitPreviewAdapter);
        }

        View btnReload = findViewById(R.id.btnStudioReload);
        if (btnReload != null) btnReload.setOnClickListener(v -> loadPhotos());

        View btnExport = findViewById(R.id.btnExport);
        if (btnExport != null) btnExport.setOnClickListener(v -> export());

        setupToolBar();
        setupRatioControls();
        setupFitControls();
        setupCropControls();
        setupSplitControls();
        setupMoreControls();

        View btnPagerBase = findViewById(R.id.btnPagerBase);
        if (btnPagerBase != null) btnPagerBase.setOnClickListener(v -> {
            StudioPhoto p = getCurrentPhoto();
            if (p != null) setBase(p);
        });
        View btnPagerDiscard = findViewById(R.id.btnPagerDiscard);
        if (btnPagerDiscard != null) btnPagerDiscard.setOnClickListener(v -> {
            StudioPhoto p = getCurrentPhoto();
            if (p != null) {
                p.discarded = !p.discarded;
                pagerAdapter.notifyItemChanged(currentPos);
                thumbsAdapter.notifyItemChanged(currentPos);
                updatePagerOverlay(currentPos);
                updateCounts();
            }
        });

        loadPhotos();
        updateBaseInfo();
        updatePagerOverlay(0);
    }

    private void setupToolBar() {
        View r = findViewById(R.id.btnToolRatio);
        View c = findViewById(R.id.btnToolCrop);
        View f = findViewById(R.id.btnToolFit);
        View s = findViewById(R.id.btnToolSplit);
        View m = findViewById(R.id.btnToolMore);
        if (r != null) r.setOnClickListener(v -> togglePanel(panelRatio, v));
        if (c != null) c.setOnClickListener(v -> togglePanel(panelCrop, v));
        if (f != null) f.setOnClickListener(v -> togglePanel(panelFit, v));
        if (s != null) s.setOnClickListener(v -> togglePanel(panelSplit, v));
        if (m != null) m.setOnClickListener(v -> togglePanel(panelMore, v));
    }

    private void togglePanel(View panel, View button) {
        if (panel == null || panelContainer == null) return;
        boolean isVisible = panel.getVisibility() == View.VISIBLE && panelContainer.getVisibility() == View.VISIBLE;
        if (panelRatio != null) panelRatio.setVisibility(View.GONE);
        if (panelCrop != null) panelCrop.setVisibility(View.GONE);
        if (panelFit != null) panelFit.setVisibility(View.GONE);
        if (panelSplit != null) panelSplit.setVisibility(View.GONE);
        if (panelMore != null) panelMore.setVisibility(View.GONE);
        resetToolButtons();
        if (isVisible) {
            panelContainer.setVisibility(View.GONE);
            lastToolButton = null;
        } else {
            panel.setVisibility(View.VISIBLE);
            panelContainer.setVisibility(View.VISIBLE);
            if (button instanceof MaterialButton) {
                ((MaterialButton) button).setStrokeWidth(2);
                ((MaterialButton) button).setStrokeColor(getColor(R.color.light_primary));
            }
            lastToolButton = button;
        }
    }

    private void resetToolButtons() {
        int[] ids = {R.id.btnToolRatio, R.id.btnToolCrop, R.id.btnToolFit, R.id.btnToolSplit, R.id.btnToolMore};
        for (int id : ids) {
            View v = findViewById(id);
            if (v instanceof MaterialButton) ((MaterialButton) v).setStrokeWidth(0);
        }
    }

    private void setupRatioControls() {
        if (chipGroupRatio == null) return;
        chipGroupRatio.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (checkedIds.isEmpty()) return;
            Chip chip = group.findViewById(checkedIds.get(0));
            if (chip == null) return;
            String txt = chip.getText().toString();
            if ("Free".equals(txt)) {
                config.targetRatio = 0;
                config.ratioLabel = "Free";
                if (basePhoto != null) { basePhoto.isBase = false; basePhoto = null; }
                updateBaseInfo();
            } else {
                float r = StudioConfig.ratioForLabel(txt);
                if (r > 0) {
                    config.targetRatio = r;
                    config.ratioLabel = txt;
                    if (basePhoto != null) { basePhoto.isBase = false; basePhoto = null; }
                    updateBaseInfo();
                }
            }
            refreshCurrentOnly();
        });
        View apply = findViewById(R.id.btnApplyCustomRatio);
        if (apply != null) apply.setOnClickListener(v -> {
            try {
                int w = Integer.parseInt(etCustomW.getText().toString().trim());
                int h = Integer.parseInt(etCustomH.getText().toString().trim());
                if (w <= 0 || h <= 0) throw new NumberFormatException();
                config.targetRatio = (float) w / h;
                config.ratioLabel = w + ":" + h;
                if (basePhoto != null) { basePhoto.isBase = false; basePhoto = null; }
                if (chipGroupRatio != null) chipGroupRatio.clearCheck();
                updateBaseInfo();
                refreshCurrentOnly();
                Toast.makeText(this, "Ratio " + w + ":" + h, Toast.LENGTH_SHORT).show();
            } catch (Exception e) {
                Toast.makeText(this, R.string.error_empty_value, Toast.LENGTH_SHORT).show();
            }
        });
        View clear = findViewById(R.id.btnClearBase);
        if (clear != null) clear.setOnClickListener(v -> clearBase());
    }

    private void setupFitControls() {
        if (toggleFit == null) return;
        toggleFit.addOnButtonCheckedListener((group, id, checked) -> {
            if (!checked) return;
            if (id == R.id.btnFitCrop) config.fitMode = StudioConfig.FitMode.CROP;
            else if (id == R.id.btnFitStretch) config.fitMode = StudioConfig.FitMode.STRETCH;
            else if (id == R.id.btnFitLetterbox) config.fitMode = StudioConfig.FitMode.LETTERBOX;
            refreshCurrentOnly();
        });
        toggleFit.check(R.id.btnFitCrop);
    }

    private void setupCropControls() {
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
            refreshCurrentOnly();
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
        int[] ids = {R.id.btnCropCenter, R.id.btnCropTop, R.id.btnCropBottom, R.id.btnCropLeft, R.id.btnCropRight,
                R.id.btnCropTopLeft, R.id.btnCropTopRight, R.id.btnCropBottomLeft, R.id.btnCropBottomRight,
                R.id.btnCropBothSides, R.id.btnCropBothTB};
        for (int id : ids) {
            View v = findViewById(id);
            if (v instanceof MaterialButton) ((MaterialButton) v).setStrokeWidth(1);
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
        if (sv instanceof MaterialButton) ((MaterialButton) sv).setStrokeWidth(3);
    }

    private void setupSplitControls() {
        if (chipGroupSplit == null) return;
        chipGroupSplit.setOnCheckedStateChangeListener((g, ids) -> {
            if (ids.isEmpty()) return;
            Chip c = g.findViewById(ids.get(0));
            if (c == null) return;
            String t = c.getText().toString();
            if (t.contains("None")) config.splitMode = StudioConfig.SplitMode.NONE;
            else if (t.contains("Vertical") && t.contains("2")) config.splitMode = StudioConfig.SplitMode.VERTICAL_2;
            else if (t.contains("Horizontal")) config.splitMode = StudioConfig.SplitMode.HORIZONTAL_2;
            else if (t.contains("Vertical") && t.contains("3")) config.splitMode = StudioConfig.SplitMode.VERTICAL_3;
            refreshCurrentOnly();
            updateSplitPreviewLazy();
        });
        if (toggleSplitOrder != null) {
            toggleSplitOrder.addOnButtonCheckedListener((g, id, checked) -> {
                if (!checked) return;
                if (id == R.id.btnOrderCropSplit) config.splitOrder = StudioConfig.SplitOrder.CROP_THEN_SPLIT;
                else config.splitOrder = StudioConfig.SplitOrder.SPLIT_THEN_CROP;
                refreshCurrentOnly();
                updateSplitPreviewLazy();
            });
            toggleSplitOrder.check(R.id.btnOrderCropSplit);
        }
        if (sliderSplit != null) {
            sliderSplit.addOnChangeListener((slider, value, fromUser) -> {
                if (!fromUser) return;
                config.splitRatio = value / 100f;
                if (tvSplitRatioValue != null) tvSplitRatioValue.setText(Math.round(value) + "% / " + (100 - Math.round(value)) + "%");
                refreshCurrentOnly();
                updateSplitPreviewLazy();
            });
        }
        if (switchSeamless != null) {
            switchSeamless.setOnCheckedChangeListener((v, c) -> {
                config.seamlessGap = c;
                config.gapPx = c ? 0 : 2;
                refreshCurrentOnly();
                updateSplitPreviewLazy();
            });
        }
    }

    private void setupMoreControls() {
        if (switchKeepOriginal != null) {
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
                refreshCurrentOnly();
            });
        }
        TextWatcher resWatcher = new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {}
            @Override public void afterTextChanged(Editable s) {
                if (switchKeepOriginal != null && !switchKeepOriginal.isChecked()) {
                    try {
                        config.targetWidth = Integer.parseInt(etTargetW.getText().toString().trim());
                        config.targetHeight = Integer.parseInt(etTargetH.getText().toString().trim());
                        if (config.targetWidth > 0 && config.targetHeight > 0 && config.targetRatio == 0) {
                            config.targetRatio = (float) config.targetWidth / config.targetHeight;
                            config.ratioLabel = StudioConfig.labelForRatio(config.targetRatio);
                            updateBaseInfo();
                        }
                    } catch (Exception ignored) {}
                    refreshCurrentOnly();
                }
            }
        };
        if (etTargetW != null) etTargetW.addTextChangedListener(resWatcher);
        if (etTargetH != null) etTargetH.addTextChangedListener(resWatcher);

        if (switchMixed != null) switchMixed.setOnCheckedChangeListener((v, c) -> { config.allowMixedResolutions = c; refreshCurrentOnly(); });
        if (switchAutoPanorama != null) switchAutoPanorama.setOnCheckedChangeListener((v, c) -> { config.autoPanorama = c; refreshCurrentOnly(); });

        View selAll = findViewById(R.id.btnSelectAll);
        if (selAll != null) selAll.setOnClickListener(v -> { for (StudioPhoto p : photos) p.selected = true; thumbsAdapter.notifyDataSetChanged(); updateCounts(); });
        View discSel = findViewById(R.id.btnDiscardSelected);
        if (discSel != null) discSel.setOnClickListener(v -> {
            for (StudioPhoto p : photos) if (p.selected) p.discarded = true;
            pagerAdapter.notifyDataSetChanged();
            thumbsAdapter.notifyDataSetChanged();
            updatePagerOverlay(currentPos);
            updateCounts();
        });
        View reset = findViewById(R.id.btnResetOverrides);
        if (reset != null) reset.setOnClickListener(v -> {
            for (StudioPhoto p : photos) p.clearOverrides();
            pagerAdapter.notifyDataSetChanged();
            thumbsAdapter.notifyDataSetChanged();
            refreshCurrentOnly();
        });
    }

    private void loadPhotos() {
        String path = etFolder != null ? etFolder.getText().toString().trim() : "";
        if (path.isEmpty()) {
            Toast.makeText(this, R.string.error_empty_value, Toast.LENGTH_SHORT).show();
            return;
        }
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            Toast.makeText(this, R.string.folder_missing, Toast.LENGTH_SHORT).show();
            photos.clear();
            pagerAdapter.notifyDataSetChanged();
            thumbsAdapter.notifyDataSetChanged();
            updateCounts();
            updatePagerOverlay(0);
            return;
        }
        if (tvStudioStatus != null) tvStudioStatus.setText(R.string.status_running);
        bgExecutor.execute(() -> {
            List<File> files = FileUtils.collectImages(dir, false, settings.getImageExtensions());
            files.sort(settings.getFileComparator());
            List<StudioPhoto> newList = new ArrayList<>();
            for (File f : files) newList.add(new StudioPhoto(f));
            runOnUiThread(() -> {
                photos.clear();
                photos.addAll(newList);
                if (basePhoto != null) {
                    boolean found = false;
                    for (StudioPhoto p : photos) if (p.file.equals(basePhoto.file)) { p.isBase = true; basePhoto = p; found = true; break; }
                    if (!found) basePhoto = null;
                }
                currentPos = 0;
                pagerAdapter.notifyDataSetChanged();
                thumbsAdapter.notifyDataSetChanged();
                if (!photos.isEmpty() && vpStudio != null) vpStudio.setCurrentItem(0, false);
                updateCounts();
                updatePagerOverlay(0);
                updateSplitPreviewLazy();
                if (tvStudioStatus != null) tvStudioStatus.setText(getString(R.string.tool_scan_result, photos.size()));
                if (photos.isEmpty()) Toast.makeText(this, R.string.studio_no_photos, Toast.LENGTH_SHORT).show();
            });
        });
    }

    private StudioPhoto getCurrentPhoto() {
        if (photos.isEmpty() || currentPos < 0 || currentPos >= photos.size()) return null;
        return photos.get(currentPos);
    }

    private void setBase(StudioPhoto p) {
        if (basePhoto != null) basePhoto.isBase = false;
        if (p == basePhoto) {
            basePhoto = null;
            config.targetRatio = 0;
            config.ratioLabel = "Free";
            if (chipGroupRatio != null) chipGroupRatio.check(R.id.chipRatioFree);
        } else {
            p.isBase = true;
            basePhoto = p;
            if (p.aspect == 0 && p.width == 0) {
                dimExecutor.execute(() -> {
                    try {
                        ImageProcessor.Dimensions d = ImageProcessor.getDimensions(p.file);
                        p.setDimensions(d.width, d.height);
                        runOnUiThread(() -> {
                            config.targetRatio = p.aspect;
                            config.ratioLabel = StudioConfig.labelForRatio(p.aspect);
                            if (chipGroupRatio != null) chipGroupRatio.clearCheck();
                            updateBaseInfo();
                            refreshCurrentOnly();
                        });
                    } catch (Exception ignored) {}
                });
                return;
            }
            if (p.aspect > 0) {
                config.targetRatio = p.aspect;
                config.ratioLabel = StudioConfig.labelForRatio(p.aspect);
                if (chipGroupRatio != null) chipGroupRatio.clearCheck();
            }
        }
        updateBaseInfo();
        pagerAdapter.notifyDataSetChanged();
        thumbsAdapter.notifyDataSetChanged();
        refreshCurrentOnly();
    }

    private void clearBase() {
        if (basePhoto != null) basePhoto.isBase = false;
        basePhoto = null;
        config.targetRatio = 0;
        config.ratioLabel = "Free";
        if (chipGroupRatio != null) chipGroupRatio.check(R.id.chipRatioFree);
        updateBaseInfo();
        pagerAdapter.notifyDataSetChanged();
        thumbsAdapter.notifyDataSetChanged();
        refreshCurrentOnly();
    }

    private void updateBaseInfo() {
        if (tvBaseInfo == null) return;
        if (basePhoto != null && basePhoto.width > 0) {
            tvBaseInfo.setText("Base: " + basePhoto.name + " • " + basePhoto.width + "×" + basePhoto.height + " • " + StudioConfig.labelForRatio(basePhoto.aspect));
        } else if (config.targetRatio > 0) {
            tvBaseInfo.setText("Target: " + config.ratioLabel + " • " + String.format("%.2f:1", config.targetRatio));
        } else {
            tvBaseInfo.setText(R.string.studio_no_base);
        }
    }

    private void updateCounts() {
        if (tvStudioCount != null) {
            if (photos.isEmpty()) tvStudioCount.setText("0");
            else {
                tvStudioCount.setText(String.valueOf(photos.size()));
                String first = photos.size() > 0 && photos.get(0).width > 0 ? photos.get(0).width + "x" + photos.get(0).height : "";
                int varied = 0;
                for (StudioPhoto p : photos) if (p.width > 0 && !(p.width + "x" + p.height).equals(first)) varied++;
                if (varied > 0) tvStudioCount.setText(photos.size() + " • " + varied + " varied");
            }
        }
        if (tvSelectedCount != null) {
            int sel = 0, discarded = 0;
            for (StudioPhoto p : photos) { if (p.selected) sel++; if (p.discarded) discarded++; }
            tvSelectedCount.setText(getString(R.string.studio_selected_count, sel) + (discarded > 0 ? " • " + discarded + " trashed" : ""));
        }
        if (noPhotos != null) {
            boolean empty = photos.isEmpty();
            noPhotos.setVisibility(empty ? View.VISIBLE : View.GONE);
            if (vpStudio != null) vpStudio.setVisibility(empty ? View.INVISIBLE : View.VISIBLE);
        }
    }

    private void updatePagerOverlay(int pos) {
        if (photos.isEmpty() || pos < 0 || pos >= photos.size()) {
            if (tvPagerCount != null) tvPagerCount.setText("0 / 0");
            if (tvPagerRatio != null) tvPagerRatio.setText("Free");
            if (tvPagerRes != null) tvPagerRes.setText("-");
            if (tvPagerBaseBadge != null) tvPagerBaseBadge.setVisibility(View.GONE);
            if (tvPagerDiscardBadge != null) tvPagerDiscardBadge.setVisibility(View.GONE);
            return;
        }
        StudioPhoto p = photos.get(pos);
        if (tvPagerCount != null) tvPagerCount.setText((pos + 1) + " / " + photos.size());
        StudioConfig eff = p.effectiveConfig(config);
        String ratioLabel = eff.targetRatio > 0 ? StudioConfig.labelForRatio(eff.targetRatio) : "Free";
        if (tvPagerRatio != null) tvPagerRatio.setText(ratioLabel + (p.hasOverride() ? " • " + getString(R.string.studio_overridden) : ""));
        if (p.width > 0) {
            if (tvPagerRes != null) tvPagerRes.setText(p.width + "×" + p.height);
        } else {
            if (tvPagerRes != null) tvPagerRes.setText("…");
            dimExecutor.execute(() -> {
                try {
                    ImageProcessor.Dimensions d = ImageProcessor.getDimensions(p.file);
                    p.setDimensions(d.width, d.height);
                    runOnUiThread(() -> {
                        if (currentPos == pos) {
                            if (tvPagerRes != null) tvPagerRes.setText(d.width + "×" + d.height);
                            if (p.isBase) updateBaseInfo();
                            thumbsAdapter.notifyItemChanged(pos);
                        }
                    });
                } catch (Exception ignored) {}
            });
        }
        if (tvPagerBaseBadge != null) tvPagerBaseBadge.setVisibility(p.isBase ? View.VISIBLE : View.GONE);
        if (tvPagerDiscardBadge != null) tvPagerDiscardBadge.setVisibility(p.discarded ? View.VISIBLE : View.GONE);
    }

    private void syncThumbsToPager(int pos) {
        if (rvThumbs != null && thumbsAdapter != null) {
            RecyclerView.LayoutManager lm = rvThumbs.getLayoutManager();
            if (lm instanceof LinearLayoutManager) {
                ((LinearLayoutManager) lm).scrollToPositionWithOffset(pos, 100);
            }
            thumbsAdapter.setSelected(pos);
        }
    }

    private void refreshCurrentOnly() {
        if (pagerAdapter != null && !photos.isEmpty() && currentPos >= 0 && currentPos < photos.size()) {
            pagerAdapter.notifyItemChanged(currentPos);
            updatePagerOverlay(currentPos);
        }
        if (thumbsAdapter != null && currentPos >= 0 && currentPos < photos.size()) thumbsAdapter.notifyItemChanged(currentPos);
        updateSplitPreviewLazy();
    }

    private void updateSplitPreviewLazy() {
        if (rvSplitPreview == null || splitPreviewAdapter == null) return;
        StudioPhoto cur = getCurrentPhoto();
        if (cur == null || config.splitMode == StudioConfig.SplitMode.NONE) {
            rvSplitPreview.setVisibility(View.GONE);
            return;
        }
        rvSplitPreview.setVisibility(View.VISIBLE);
        List<File> halves = new ArrayList<>();
        halves.add(cur.file);
        halves.add(cur.file);
        splitPreviewAdapter.update(halves);
    }

    private void showPerPhotoEdit(StudioPhoto p, int pos) {
        View v = LayoutInflater.from(this).inflate(R.layout.dialog_studio_photo_edit, null);
        TextView tvName = v.findViewById(R.id.tvEditPhotoName);
        tvName.setText(p.name + (p.width > 0 ? " • " + p.width + "×" + p.height : ""));

        ChipGroup cgCrop = v.findViewById(R.id.chipGroupEditCrop);
        ChipGroup cgFit = v.findViewById(R.id.chipGroupEditFit);
        ChipGroup cgSplit = v.findViewById(R.id.chipGroupEditSplit);
        View btnDiscard = v.findViewById(R.id.btnEditDiscard);
        View btnClear = v.findViewById(R.id.btnEditClear);

        if (p.overrideCrop != null) {
            int idx = 0;
            switch (p.overrideCrop) {
                case CENTER: idx = 1; break;
                case TOP: idx = 2; break;
                case BOTTOM: idx = 3; break;
                case LEFT: idx = 4; break;
                case RIGHT: idx = 5; break;
                case BOTH_SIDES: idx = 6; break;
                case BOTH_TOP_BOTTOM: idx = 7; break;
                default: idx = 1; break;
            }
            if (cgCrop != null && cgCrop.getChildCount() > idx) ((Chip) cgCrop.getChildAt(idx)).setChecked(true);
        } else if (cgCrop != null && cgCrop.getChildCount() > 0) ((Chip) cgCrop.getChildAt(0)).setChecked(true);

        if (p.overrideFit != null) {
            int idx = 0;
            switch (p.overrideFit) {
                case CROP: idx = 1; break;
                case STRETCH: idx = 2; break;
                case LETTERBOX: idx = 3; break;
            }
            if (cgFit != null && cgFit.getChildCount() > idx) ((Chip) cgFit.getChildAt(idx)).setChecked(true);
        }

        if (p.overrideSplit != null) {
            int idx = 0;
            switch (p.overrideSplit) {
                case NONE: idx = 1; break;
                case VERTICAL_2: idx = 2; break;
                case HORIZONTAL_2: idx = 3; break;
            }
            if (cgSplit != null && cgSplit.getChildCount() > idx) ((Chip) cgSplit.getChildAt(idx)).setChecked(true);
        }

        new AlertDialog.Builder(this).setView(v)
                .setPositiveButton(R.string.use, (d, w) -> {
                    Chip sc = null;
                    if (cgCrop != null) for (int i = 0; i < cgCrop.getChildCount(); i++) {
                        Chip c = (Chip) cgCrop.getChildAt(i);
                        if (c.isChecked()) { sc = c; break; }
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
                    if (cgFit != null) for (int i = 0; i < cgFit.getChildCount(); i++) {
                        Chip c = (Chip) cgFit.getChildAt(i);
                        if (c.isChecked()) { sf = c; break; }
                    }
                    if (sf != null) {
                        String t = sf.getText().toString();
                        if ("Global".equals(t)) p.overrideFit = null;
                        else if ("Crop".equals(t)) p.overrideFit = StudioConfig.FitMode.CROP;
                        else if ("Stretch".equals(t)) p.overrideFit = StudioConfig.FitMode.STRETCH;
                        else if ("Letterbox".equals(t)) p.overrideFit = StudioConfig.FitMode.LETTERBOX;
                    }
                    Chip ss = null;
                    if (cgSplit != null) for (int i = 0; i < cgSplit.getChildCount(); i++) {
                        Chip c = (Chip) cgSplit.getChildAt(i);
                        if (c.isChecked()) { ss = c; break; }
                    }
                    if (ss != null) {
                        String t = ss.getText().toString();
                        if ("Global".equals(t)) p.overrideSplit = null;
                        else if ("None".equals(t)) p.overrideSplit = StudioConfig.SplitMode.NONE;
                        else if ("V 2".equals(t)) p.overrideSplit = StudioConfig.SplitMode.VERTICAL_2;
                        else if ("H 2".equals(t)) p.overrideSplit = StudioConfig.SplitMode.HORIZONTAL_2;
                    }
                    pagerAdapter.notifyItemChanged(pos);
                    thumbsAdapter.notifyItemChanged(pos);
                    refreshCurrentOnly();
                })
                .setNegativeButton(R.string.cancel, null)
                .create()
                .show();

        if (btnDiscard != null) btnDiscard.setOnClickListener(x -> {
            p.discarded = !p.discarded;
            pagerAdapter.notifyItemChanged(pos);
            thumbsAdapter.notifyItemChanged(pos);
            updatePagerOverlay(currentPos);
            updateCounts();
        });
        if (btnClear != null) btnClear.setOnClickListener(x -> {
            p.clearOverrides();
            pagerAdapter.notifyItemChanged(pos);
            thumbsAdapter.notifyItemChanged(pos);
            refreshCurrentOnly();
            Toast.makeText(this, "Cleared", Toast.LENGTH_SHORT).show();
        });
    }

    private void export() {
        String outPath = etOutput != null ? etOutput.getText().toString().trim() : "";
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
        if (switchKeepOriginal != null && !switchKeepOriginal.isChecked()) {
            try {
                config.targetWidth = Integer.parseInt(etTargetW.getText().toString().trim());
                config.targetHeight = Integer.parseInt(etTargetH.getText().toString().trim());
            } catch (Exception e) {
                Toast.makeText(this, "Invalid target W/H", Toast.LENGTH_SHORT).show();
                return;
            }
        }
        List<StudioPhoto> toExport = new ArrayList<>();
        for (StudioPhoto p : photos) if (!p.discarded) toExport.add(p);
        if (toExport.isEmpty()) {
            Toast.makeText(this, "No photos to export (all discarded)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (tvStudioStatus != null) tvStudioStatus.setText(R.string.studio_exporting);
        View btnExport = findViewById(R.id.btnExport);
        if (btnExport != null) btnExport.setEnabled(false);

        bgExecutor.execute(() -> {
            int exported = 0, splits = 0, discarded = photos.size() - toExport.size();
            List<String> scanned = new ArrayList<>();
            for (StudioPhoto p : toExport) {
                StudioConfig eff = p.effectiveConfig(config);
                List<File> outs = ImageProcessor.processAndSave(p.file, outDir, eff, exported);
                for (File f : outs) scanned.add(f.getAbsolutePath());
                exported++;
                if (outs.size() > 1) splits += outs.size() - 1;
            }
            FileUtils.scanMedia(this, scanned);
            int fExported = exported, fSplits = splits, fDiscarded = discarded;
            runOnUiThread(() -> {
                String msg = getString(R.string.studio_exported, fExported, fSplits, fDiscarded);
                if (tvStudioStatus != null) tvStudioStatus.setText(msg);
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show();
                View be = findViewById(R.id.btnExport);
                if (be != null) be.setEnabled(true);
            });
        });
    }

    class StudioPagerAdapter extends RecyclerView.Adapter<StudioPagerAdapter.VH> {
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_studio_pager, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            StudioPhoto p = photos.get(pos);
            Glide.with(h.image.getContext()).load(p.file).centerCrop().into(h.image);
            h.image.setOnLongClickListener(v -> { showPerPhotoEdit(p, pos); return true; });
            h.image.setOnTouchListener(new View.OnTouchListener() {
                @Override public boolean onTouch(View v, MotionEvent event) { return false; }
            });
            if (p.width == 0) {
                dimExecutor.execute(() -> {
                    try {
                        ImageProcessor.Dimensions d = ImageProcessor.getDimensions(p.file);
                        p.setDimensions(d.width, d.height);
                        runOnUiThread(() -> {
                            if (pos == currentPos) updatePagerOverlay(currentPos);
                            thumbsAdapter.notifyItemChanged(pos);
                        });
                    } catch (Exception ignored) {}
                });
            }
        }
        @Override public int getItemCount() { return photos.size(); }
        class VH extends RecyclerView.ViewHolder {
            ImageView image;
            VH(View v) { super(v); image = v.findViewById(R.id.ivPagerPhoto); }
        }
    }

    class ThumbsAdapter extends RecyclerView.Adapter<ThumbsAdapter.VH> {
        private int selected = 0;
        void setSelected(int pos) {
            int old = selected;
            selected = pos;
            if (old >= 0) notifyItemChanged(old);
            notifyItemChanged(pos);
        }
        @Override public VH onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_studio_thumb, parent, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            StudioPhoto p = photos.get(pos);
            Glide.with(h.image.getContext()).load(p.file).centerCrop().into(h.image);
            boolean isSel = pos == selected;
            h.selectedOverlay.setVisibility(isSel ? View.VISIBLE : View.GONE);
            View card = (View) h.itemView;
            if (card instanceof com.google.android.material.card.MaterialCardView) {
                ((com.google.android.material.card.MaterialCardView) card).setStrokeWidth(isSel ? 3 : 0);
                ((com.google.android.material.card.MaterialCardView) card).setStrokeColor(getColor(R.color.light_primary));
            }
            h.badge.setVisibility(p.isBase ? View.VISIBLE : View.GONE);
            h.discardBar.setVisibility(p.discarded ? View.VISIBLE : View.GONE);
            h.image.setAlpha(p.discarded ? 0.5f : 1f);
            h.itemView.setOnClickListener(v -> {
                if (vpStudio != null) vpStudio.setCurrentItem(pos, true);
            });
            h.itemView.setOnLongClickListener(v -> { showPerPhotoEdit(p, pos); return true; });
        }
        @Override public int getItemCount() { return photos.size(); }
        class VH extends RecyclerView.ViewHolder {
            ImageView image;
            View selectedOverlay, discardBar;
            TextView badge;
            VH(View v) {
                super(v);
                image = v.findViewById(R.id.ivThumb);
                selectedOverlay = v.findViewById(R.id.vThumbSelected);
                badge = v.findViewById(R.id.tvThumbBadge);
                discardBar = v.findViewById(R.id.vThumbDiscard);
            }
        }
    }

    static class SplitPreviewAdapter extends RecyclerView.Adapter<SplitPreviewAdapter.VH> {
        List<File> files;
        SplitPreviewAdapter(List<File> f) { files = f; }
        void update(List<File> f) { files = f; notifyDataSetChanged(); }
        @Override public VH onCreateViewHolder(ViewGroup p, int t) {
            View v = LayoutInflater.from(p.getContext()).inflate(R.layout.item_split_preview, p, false);
            return new VH(v);
        }
        @Override public void onBindViewHolder(VH h, int pos) {
            File f = files.get(pos % Math.max(1, files.size()));
            Glide.with(h.image.getContext()).load(f).centerCrop().into(h.image);
        }
        @Override public int getItemCount() { return Math.min(files.size(), 2); }
        static class VH extends RecyclerView.ViewHolder {
            ImageView image;
            VH(View v) { super(v); image = v.findViewById(R.id.ivSplit); }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        bgExecutor.shutdown();
        dimExecutor.shutdown();
    }
}
