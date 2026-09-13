package damjay.photo.triage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;

import java.io.File;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

    // Look for the standard iPhone IMG_XXXX or IMG_EXXXXX pattern.
    private static final Pattern IPHONE_NUMBER_PATTERN = Pattern.compile("(?i)IMG_[A-Z]?(\\d+)");

    private final List<File> photos;
    private final String labelMode;

    public PhotoAdapter(List<File> photos, String labelMode) {
        this.photos = photos;
        this.labelMode = labelMode == null ? SettingsManager.LABEL_AUTO : labelMode;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo_card, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        File photoFile = photos.get(position);

        // Use fitCenter by default so wide / panoramic photos are fully visible.
        // CenterCrop would hide >50% of wide images. FitCenter letterboxes but shows all.
        Glide.with(holder.imageView.getContext())
                .load(photoFile)
                .transition(DrawableTransitionOptions.withCrossFade(160))
                .placeholder(R.drawable.ic_triage_logo)
                .error(R.drawable.ic_triage_logo)
                .into(holder.imageView);

        holder.tvCounter.setText(labelFor(photoFile, position));

        // Tap to toggle between fitting the whole photo and filling the card.
        // This keeps the swipe theme but lets users inspect wide photos without deflection.
        holder.imageView.setOnClickListener(v -> {
            ImageView.ScaleType current = holder.imageView.getScaleType();
            if (current == ImageView.ScaleType.FIT_CENTER) {
                holder.imageView.setScaleType(ImageView.ScaleType.CENTER_CROP);
            } else {
                holder.imageView.setScaleType(ImageView.ScaleType.FIT_CENTER);
            }
        });
    }

    private String labelFor(File photoFile, int position) {
        if (SettingsManager.LABEL_FILENAME.equals(labelMode)) {
            return photoFile.getName();
        }
        if (SettingsManager.LABEL_INDEX.equals(labelMode)) {
            return String.valueOf(position + 1);
        }
        // Auto: extract the iPhone number, falling back to the full filename.
        Matcher matcher = IPHONE_NUMBER_PATTERN.matcher(photoFile.getName());
        return matcher.find() ? matcher.group(1) : photoFile.getName();
    }

    @Override
    public int getItemCount() {
        return photos == null ? 0 : photos.size();
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        android.widget.TextView tvCounter;
        android.widget.TextView tvHint;

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageViewPhoto);
            tvCounter = itemView.findViewById(R.id.tvCounter);
            tvHint = itemView.findViewById(R.id.tvImageHint);
        }
    }
}
