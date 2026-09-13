package damjay.photo.triage;

import java.io.File;

/**
 * Flexible configuration for Resolution Studio.
 * All fields are mutable and designed to be tweaked live with preview.
 */
public class StudioConfig {

    // Target ratio: width / height, e.g. 1.0 = 1:1, 1.33 = 4:3, 1.77 = 16:9
    // If 0 or <0, means "free" / keep original ratio (unless stretch forces)
    public float targetRatio = 0f;
    public String ratioLabel = "Free";

    // Target resolution (when not keeping original)
    public boolean keepOriginalResolution = true;
    public int targetWidth = 1920;
    public int targetHeight = 1080;

    // Fit mode
    public enum FitMode { CROP, STRETCH, LETTERBOX }
    public FitMode fitMode = FitMode.CROP;

    // Crop gravity – creative flexibility: 9-grid + both sides / both top-bottom
    public enum CropGravity {
        CENTER,
        TOP, BOTTOM, LEFT, RIGHT,
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
        BOTH_SIDES,      // crop equally from left+right to reach ratio (good for wide panoramas)
        BOTH_TOP_BOTTOM, // crop equally from top+bottom (good for tall portraits)
        CUSTOM
    }
    public CropGravity cropGravity = CropGravity.CENTER;

    // Optional custom crop rect normalized (0..1) when gravity == CUSTOM
    public float customCropLeft = 0f, customCropTop = 0f, customCropRight = 1f, customCropBottom = 1f;

    // Split
    public enum SplitMode { NONE, VERTICAL_2, HORIZONTAL_2, VERTICAL_3, HORIZONTAL_3 }
    public SplitMode splitMode = SplitMode.NONE;
    // Order
    public enum SplitOrder { CROP_THEN_SPLIT, SPLIT_THEN_CROP }
    public SplitOrder splitOrder = SplitOrder.CROP_THEN_SPLIT;
    // Split position: 0.2 .. 0.8, 0.5 = equal halves. For 3-way, this is first cut, second is 2*ratio etc.
    public float splitRatio = 0.5f;
    public boolean seamlessGap = true;
    public int gapPx = 0;

    // Mixed resolutions
    public boolean allowMixedResolutions = true;

    // Letterbox background
    public int letterboxColor = 0xFF000000; // black

    // Auto panorama helper
    public boolean autoPanorama = false;

    // Output folder
    public File outputFolder = null;

    // Per-photo overrides allowed
    public boolean perPhotoOverrides = true;

    public StudioConfig copy() {
        StudioConfig c = new StudioConfig();
        c.targetRatio = this.targetRatio;
        c.ratioLabel = this.ratioLabel;
        c.keepOriginalResolution = this.keepOriginalResolution;
        c.targetWidth = this.targetWidth;
        c.targetHeight = this.targetHeight;
        c.fitMode = this.fitMode;
        c.cropGravity = this.cropGravity;
        c.customCropLeft = this.customCropLeft;
        c.customCropTop = this.customCropTop;
        c.customCropRight = this.customCropRight;
        c.customCropBottom = this.customCropBottom;
        c.splitMode = this.splitMode;
        c.splitOrder = this.splitOrder;
        c.splitRatio = this.splitRatio;
        c.seamlessGap = this.seamlessGap;
        c.gapPx = this.gapPx;
        c.allowMixedResolutions = this.allowMixedResolutions;
        c.letterboxColor = this.letterboxColor;
        c.autoPanorama = this.autoPanorama;
        c.outputFolder = this.outputFolder;
        c.perPhotoOverrides = this.perPhotoOverrides;
        return c;
    }

    public static float ratioForLabel(String label) {
        if (label == null) return 0f;
        label = label.trim();
        // Handle generic W:H like "7:5" or "2:3" — parse integers
        if (label.contains(":")) {
            String[] parts = label.split(":");
            if (parts.length == 2) {
                try {
                    String left = parts[0].trim();
                    String right = parts[1].trim();
                    // Remove any floating part? handle like "1.5:1" not needed, but integer
                    int w = Integer.parseInt(left);
                    int h = Integer.parseInt(right);
                    if (w > 0 && h > 0) return (float) w / h;
                } catch (NumberFormatException ignored) {
                    // Try floating prefix like "1.33:1"
                    try {
                        if (parts[1].trim().equals("1")) {
                            return Float.parseFloat(parts[0].trim());
                        }
                    } catch (Exception e) {}
                }
            }
        }
        switch (label) {
            case "1:1": return 1f;
            case "4:3": return 4f/3f;
            case "3:2": return 3f/2f;
            case "16:9": return 16f/9f;
            case "9:16": return 9f/16f;
            case "21:9": return 21f/9f;
            case "4:5": return 4f/5f;
            case "3:4": return 3f/4f;
            case "2:1": return 2f;
            case "1:2": return 0.5f;
            default: return 0f;
        }
    }

    private static int gcd(int a, int b) {
        a = Math.abs(a); b = Math.abs(b);
        while (b != 0) { int t = b; b = a % b; a = t; }
        return a == 0 ? 1 : a;
    }

    public static String labelForRatio(float r) {
        if (r <= 0) return "Free";
        // Check presets first with tight tolerance
        if (Math.abs(r - 1f) < 0.01) return "1:1";
        if (Math.abs(r - 4f/3f) < 0.02) return "4:3";
        if (Math.abs(r - 3f/2f) < 0.02) return "3:2";
        if (Math.abs(r - 16f/9f) < 0.02) return "16:9";
        if (Math.abs(r - 9f/16f) < 0.02) return "9:16";
        if (Math.abs(r - 21f/9f) < 0.03) return "21:9";
        if (Math.abs(r - 4f/5f) < 0.02) return "4:5";
        if (Math.abs(r - 3f/4f) < 0.02) return "3:4";
        if (Math.abs(r - 2f) < 0.02) return "2:1";
        if (Math.abs(r - 0.5f) < 0.02) return "1:2";
        // Try to represent as least integer ratio W:H with small numbers
        // Example 0.666 -> 2:3, 1.5 -> 3:2, 0.75 -> 3:4
        int bestW = -1, bestH = -1;
        float bestErr = Float.MAX_VALUE;
        for (int h = 1; h <= 20; h++) {
            int w = Math.round(r * h);
            if (w < 1 || w > 30) continue;
            float err = Math.abs(r - (float) w / h);
            if (err < 0.015 && err < bestErr) {
                // Prefer smaller numbers and lower error
                bestErr = err;
                bestW = w;
                bestH = h;
            }
        }
        if (bestW > 0 && bestH > 0) {
            int g = gcd(bestW, bestH);
            int rw = bestW / g;
            int rh = bestH / g;
            // If reduced numbers are still reasonable (<= 30) and not too large, use integer
            if (rw <= 30 && rh <= 30) {
                // Avoid extremely verbose like 17:13 for near-random ratios - check if error is small
                // Also avoid ratios that look weird like 19:7 with large error; we already filtered err <0.015
                return rw + ":" + rh;
            }
        }
        return String.format("%.2f:1", r);
    }
}
