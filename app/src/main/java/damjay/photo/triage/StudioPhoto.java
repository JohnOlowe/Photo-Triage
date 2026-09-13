package damjay.photo.triage;

import java.io.File;

/**
 * Single photo in Resolution Studio with per-photo state.
 */
public class StudioPhoto {
    public final File file;
    public final String name;
    public int width = 0;  // original dimensions (0 if not yet decoded)
    public int height = 0;
    public float aspect = 0f; // width/height
    public boolean isBase = false;
    public boolean discarded = false;
    public boolean selected = false;

    // Per-photo overrides (null = use global config)
    public StudioConfig.CropGravity overrideCrop = null;
    public StudioConfig.FitMode overrideFit = null;
    public StudioConfig.SplitMode overrideSplit = null;
    public Float overrideSplitRatio = null;
    public Boolean overrideStretch = null; // not used directly, maps to fit mode

    // Computed preview info
    public String badge = "";

    public StudioPhoto(File file) {
        this.file = file;
        this.name = file.getName();
    }

    public void setDimensions(int w, int h) {
        this.width = w;
        this.height = h;
        this.aspect = h == 0 ? 0 : (float) w / h;
    }

    public boolean hasOverride() {
        return overrideCrop != null || overrideFit != null || overrideSplit != null || overrideSplitRatio != null;
    }

    public void clearOverrides() {
        overrideCrop = null;
        overrideFit = null;
        overrideSplit = null;
        overrideSplitRatio = null;
        overrideStretch = null;
    }

    public StudioConfig effectiveConfig(StudioConfig global) {
        if (!hasOverride()) return global;
        StudioConfig c = global.copy();
        if (overrideCrop != null) c.cropGravity = overrideCrop;
        if (overrideFit != null) c.fitMode = overrideFit;
        if (overrideSplit != null) c.splitMode = overrideSplit;
        if (overrideSplitRatio != null) c.splitRatio = overrideSplitRatio;
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof StudioPhoto)) return false;
        return file.equals(((StudioPhoto) o).file);
    }

    @Override
    public int hashCode() {
        return file.hashCode();
    }
}
