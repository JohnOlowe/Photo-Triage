package damjay.photo.triage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Creative, flexible image processing for Resolution Studio.
 * Handles: ratio, crop (top/side/both), stretch, split (before/after), mixed resolutions, letterbox.
 */
public class ImageProcessor {

    public static class Dimensions {
        public int width, height;
        public Dimensions(int w, int h) { width = w; height = h; }
    }

    public static Dimensions getDimensions(File file) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        return new Dimensions(opts.outWidth, opts.outHeight);
    }

    public static Bitmap loadBitmap(File file, int maxDim) {
        BitmapFactory.Options opts = new BitmapFactory.Options();
        opts.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        int w = opts.outWidth, h = opts.outHeight;
        int sample = 1;
        while (w / sample > maxDim || h / sample > maxDim) sample *= 2;
        opts.inJustDecodeBounds = false;
        opts.inSampleSize = sample;
        return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
    }

    /**
     * Core: transform bitmap to target ratio & resolution using fitMode and cropGravity.
     * Returns a new bitmap (caller must recycle if needed).
     */
    public static Bitmap transform(Bitmap src, StudioConfig cfg, int srcW, int srcH) {
        if (src == null) return null;
        float srcRatio = (float) srcW / srcH;
        float targetRatio = cfg.targetRatio;

        // Free ratio → keep original ratio (unless stretch/letterbox forces a resolution)
        if (targetRatio <= 0) {
            if (cfg.keepOriginalResolution) return src;
            // Force to targetWidth/Height via stretch/letterbox/crop with original ratio? For free we just scale to target.
            return scaleToResolution(src, cfg, srcW, srcH);
        }

        // If mixed resolutions OFF, we will later force to targetWidth/Height. For now we handle ratio.

        switch (cfg.fitMode) {
            case STRETCH:
                return stretchToRatio(src, targetRatio, cfg);
            case LETTERBOX:
                return letterboxToRatio(src, targetRatio, cfg);
            case CROP:
            default:
                return cropToRatio(src, cfg, srcW, srcH, targetRatio);
        }
    }

    private static Bitmap scaleToResolution(Bitmap src, StudioConfig cfg, int srcW, int srcH) {
        if (cfg.keepOriginalResolution) return src;
        // Scale to targetWidth/Height preserving ratio? For free ratio we stretch to exact.
        Bitmap out = Bitmap.createScaledBitmap(src, cfg.targetWidth, cfg.targetHeight, true);
        if (out != src) src.recycle();
        return out;
    }

    private static Bitmap stretchToRatio(Bitmap src, float targetRatio, StudioConfig cfg) {
        int w = src.getWidth(), h = src.getHeight();
        float srcRatio = (float) w / h;
        if (Math.abs(srcRatio - targetRatio) < 0.01) {
            if (cfg.keepOriginalResolution) return src;
            Bitmap out = Bitmap.createScaledBitmap(src, cfg.targetWidth, cfg.targetHeight, true);
            if (out != src) src.recycle();
            return out;
        }
        // Compute new dimensions that match targetRatio but keep area similar, then stretch
        int newW, newH;
        if (cfg.keepOriginalResolution) {
            // Keep larger dimension, stretch the other
            if (srcRatio > targetRatio) {
                // source wider → need taller (increase h)
                newW = w;
                newH = Math.round(w / targetRatio);
            } else {
                newH = h;
                newW = Math.round(h * targetRatio);
            }
        } else {
            newW = cfg.targetWidth;
            newH = cfg.targetHeight;
        }
        Bitmap stretched = Bitmap.createScaledBitmap(src, newW, newH, true);
        if (stretched != src) src.recycle();
        return stretched;
    }

    private static Bitmap letterboxToRatio(Bitmap src, float targetRatio, StudioConfig cfg) {
        int w = src.getWidth(), h = src.getHeight();
        float srcRatio = (float) w / h;
        if (Math.abs(srcRatio - targetRatio) < 0.01) {
            if (cfg.keepOriginalResolution) return src;
            return Bitmap.createScaledBitmap(src, cfg.targetWidth, cfg.targetHeight, true);
        }
        int outW, outH;
        if (cfg.keepOriginalResolution) {
            // Keep src's larger dimension, pad the other
            if (srcRatio > targetRatio) {
                outW = w;
                outH = Math.round(w / targetRatio);
            } else {
                outH = h;
                outW = Math.round(h * targetRatio);
            }
        } else {
            outW = cfg.targetWidth;
            outH = cfg.targetHeight;
        }
        Bitmap out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        c.drawColor(cfg.letterboxColor);
        // Center src inside out
        Rect srcRect = new Rect(0, 0, w, h);
        int left = (outW - w) / 2;
        int top = (outH - h) / 2;
        // If we are forcing resolution, scale src to fit inside out while preserving ratio
        if (!cfg.keepOriginalResolution) {
            // Scale src to fit inside out
            float scale = Math.min((float) outW / w, (float) outH / h);
            int sw = Math.round(w * scale);
            int sh = Math.round(h * scale);
            left = (outW - sw) / 2;
            top = (outH - sh) / 2;
            Rect dst = new Rect(left, top, left + sw, top + sh);
            c.drawBitmap(src, srcRect, dst, null);
            src.recycle();
        } else {
            c.drawBitmap(src, left, top, null);
            if (out != src) src.recycle();
        }
        return out;
    }

    private static Bitmap cropToRatio(Bitmap src, StudioConfig cfg, int srcW, int srcH, float targetRatio) {
        int w = src.getWidth(), h = src.getHeight();
        float srcRatio = (float) w / h;

        if (Math.abs(srcRatio - targetRatio) < 0.01) {
            if (cfg.keepOriginalResolution) return src;
            Bitmap out = Bitmap.createScaledBitmap(src, cfg.targetWidth, cfg.targetHeight, true);
            if (out != src) src.recycle();
            return out;
        }

        // Determine crop rect
        Rect crop = computeCropRect(w, h, targetRatio, cfg.cropGravity, cfg);
        Bitmap cropped = Bitmap.createBitmap(src, crop.left, crop.top, crop.width(), crop.height());
        src.recycle();

        if (!cfg.keepOriginalResolution) {
            // Scale cropped to target resolution
            Bitmap scaled = Bitmap.createScaledBitmap(cropped, cfg.targetWidth, cfg.targetHeight, true);
            cropped.recycle();
            return scaled;
        }
        return cropped;
    }

    public static Rect computeCropRect(int w, int h, float targetRatio, StudioConfig.CropGravity gravity, StudioConfig cfg) {
        float srcRatio = (float) w / h;
        int cw, ch;
        int left = 0, top = 0;

        if (srcRatio > targetRatio) {
            // Source wider → crop width
            ch = h;
            cw = Math.round(h * targetRatio);
        } else {
            // Source taller → crop height
            cw = w;
            ch = Math.round(w / targetRatio);
        }

        // Handle custom
        if (gravity == StudioConfig.CropGravity.CUSTOM) {
            left = Math.round(cfg.customCropLeft * (w - cw));
            top = Math.round(cfg.customCropTop * (h - ch));
            left = Math.max(0, Math.min(left, w - cw));
            top = Math.max(0, Math.min(top, h - ch));
            return new Rect(left, top, left + cw, top + ch);
        }

        // Gravity mapping – creative: both sides / both top-bottom
        switch (gravity) {
            case CENTER:
                left = (w - cw) / 2;
                top = (h - ch) / 2;
                break;
            case TOP:
                left = (w - cw) / 2;
                top = 0;
                break;
            case BOTTOM:
                left = (w - cw) / 2;
                top = h - ch;
                break;
            case LEFT:
                left = 0;
                top = (h - ch) / 2;
                break;
            case RIGHT:
                left = w - cw;
                top = (h - ch) / 2;
                break;
            case TOP_LEFT:
                left = 0; top = 0; break;
            case TOP_RIGHT:
                left = w - cw; top = 0; break;
            case BOTTOM_LEFT:
                left = 0; top = h - ch; break;
            case BOTTOM_RIGHT:
                left = w - cw; top = h - ch; break;
            case BOTH_SIDES:
                // Crop equally from both sides – same as center horizontally, but if vertical crop needed, keep center
                left = (w - cw) / 2;
                top = (h - ch) / 2;
                // For BOTH_SIDES, we emphasize horizontal cropping: if source is wider, we already do that; if taller, we still center
                break;
            case BOTH_TOP_BOTTOM:
                left = (w - cw) / 2;
                top = (h - ch) / 2;
                break;
            default:
                left = (w - cw) / 2;
                top = (h - ch) / 2;
                break;
        }
        return new Rect(left, top, left + cw, top + ch);
    }

    /**
     * Split bitmap into 2 or 3 parts.
     * Vertical: left/right, Horizontal: top/bottom
     */
    public static List<Bitmap> split(Bitmap src, StudioConfig cfg) {
        List<Bitmap> out = new ArrayList<>();
        if (src == null || cfg.splitMode == StudioConfig.SplitMode.NONE) {
            out.add(src);
            return out;
        }
        int w = src.getWidth(), h = src.getHeight();
        float r = cfg.splitRatio;
        r = Math.max(0.15f, Math.min(0.85f, r));

        switch (cfg.splitMode) {
            case VERTICAL_2: {
                int w1 = Math.round(w * r);
                int w2 = w - w1 - cfg.gapPx;
                if (w2 < 10) w2 = w - w1;
                Bitmap a = Bitmap.createBitmap(src, 0, 0, w1, h);
                Bitmap b = Bitmap.createBitmap(src, w1 + cfg.gapPx, 0, w2, h);
                out.add(a); out.add(b);
                src.recycle();
                break;
            }
            case HORIZONTAL_2: {
                int h1 = Math.round(h * r);
                int h2 = h - h1 - cfg.gapPx;
                Bitmap a = Bitmap.createBitmap(src, 0, 0, w, h1);
                Bitmap b = Bitmap.createBitmap(src, 0, h1 + cfg.gapPx, w, h2);
                out.add(a); out.add(b);
                src.recycle();
                break;
            }
            case VERTICAL_3: {
                int w1 = Math.round(w * r / 2);
                int w2 = Math.round(w * (1 - r));
                int w3 = w - w1 - w2 - 2 * cfg.gapPx;
                Bitmap a = Bitmap.createBitmap(src, 0, 0, w1, h);
                Bitmap b = Bitmap.createBitmap(src, w1 + cfg.gapPx, 0, w2, h);
                Bitmap c = Bitmap.createBitmap(src, w1 + w2 + 2 * cfg.gapPx, 0, w3, h);
                out.add(a); out.add(b); out.add(c);
                src.recycle();
                break;
            }
            case HORIZONTAL_3: {
                int h1 = Math.round(h * r / 2);
                int h2 = Math.round(h * (1 - r));
                int h3 = h - h1 - h2 - 2 * cfg.gapPx;
                Bitmap a = Bitmap.createBitmap(src, 0, 0, w, h1);
                Bitmap b = Bitmap.createBitmap(src, 0, h1 + cfg.gapPx, w, h2);
                Bitmap c = Bitmap.createBitmap(src, 0, h1 + h2 + 2 * cfg.gapPx, w, h3);
                out.add(a); out.add(b); out.add(c);
                src.recycle();
                break;
            }
            default:
                out.add(src);
        }
        return out;
    }

    /**
     * Full pipeline: handles order, mixed resolutions, discard, and writes files.
     */
    public static List<File> processAndSave(File input, File outputDir, StudioConfig cfg, int index) {
        List<File> written = new ArrayList<>();
        if (input == null || !input.exists() || outputDir == null) return written;
        if (!outputDir.exists() && !outputDir.mkdirs()) return written;

        Dimensions dim = getDimensions(input);
        Bitmap bmp = loadBitmap(input, 2048);
        if (bmp == null) return written;

        // Auto panorama: if wide and auto enabled, override to BOTH_SIDES + suggest split
        StudioConfig use = cfg;
        if (cfg.autoPanorama && dim.width > dim.height * 2) {
            use = cfg.copy();
            use.cropGravity = StudioConfig.CropGravity.BOTH_SIDES;
        }

        List<Bitmap> toProcess = new ArrayList<>();
        toProcess.add(bmp);

        // Handle split order
        List<Bitmap> afterFirstStage = new ArrayList<>();
        if (use.splitOrder == StudioConfig.SplitOrder.SPLIT_THEN_CROP) {
            // Split first
            List<Bitmap> splitFirst = new ArrayList<>();
            for (Bitmap b : toProcess) {
                splitFirst.addAll(split(b, use));
            }
            // Then crop each
            for (Bitmap b : splitFirst) {
                afterFirstStage.add(transform(b, use, b.getWidth(), b.getHeight()));
            }
        } else {
            // Crop then split
            for (Bitmap b : toProcess) {
                Bitmap cropped = transform(b, use, dim.width, dim.height);
                afterFirstStage.addAll(split(cropped, use));
            }
        }

        // If mixed resolutions OFF, force all splits to same dimensions (use targetWidth/Height)
        // Already handled by transform's keepOriginalResolution flag.

        String baseName = input.getName();
        int dot = baseName.lastIndexOf('.');
        String name = dot > 0 ? baseName.substring(0, dot) : baseName;
        String ext = dot > 0 ? baseName.substring(dot) : ".jpg";

        for (int i = 0; i < afterFirstStage.size(); i++) {
            Bitmap b = afterFirstStage.get(i);
            String suffix = afterFirstStage.size() == 1 ? "_adjusted" : (i == 0 ? "_a" : i == 1 ? "_b" : "_c");
            File outFile = FileUtils.uniqueFile(outputDir, name + suffix + ext);
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                b.compress(Bitmap.CompressFormat.JPEG, 92, fos);
                written.add(outFile);
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                b.recycle();
            }
        }
        return written;
    }

    public static File saveBitmap(Bitmap bmp, File outFile) {
        try (FileOutputStream fos = new FileOutputStream(outFile)) {
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, fos);
            return outFile;
        } catch (Exception e) { return null; }
    }
}
