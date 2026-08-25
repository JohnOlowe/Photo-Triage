package damjay.photo.triage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;

import java.io.File;
import java.util.List;

public class PhotoAdapter extends RecyclerView.Adapter<PhotoAdapter.PhotoViewHolder> {

    private List<File> photos;

    public PhotoAdapter(List<File> photos) {
        this.photos = photos;
    }

    @NonNull
    @Override
    public PhotoViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // Inflate the photo card layout
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_photo_card, parent, false);
        return new PhotoViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull PhotoViewHolder holder, int position) {
        File photoFile = photos.get(position);
        
        Glide.with(holder.imageView.getContext())
                .load(photoFile)
                .into(holder.imageView);

        String filename = photoFile.getName();
        // Look for the standard iPhone IMG_XXXX or IMG_EXXXX pattern
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)IMG_[A-Z]?(\\d+)").matcher(filename);
        
        // If it finds the iPhone number, use it. Otherwise, spit out the whole filename.
        String displayText = m.find() ? m.group(1) : filename;
        
        holder.tvCounter.setText(displayText);
    }

    @Override
    public int getItemCount() {
        return photos == null ? 0 : photos.size();
    }

    // Helper method so we can retrieve the current file list later
    public List<File> getPhotos() {
        return photos;
    }

    static class PhotoViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        android.widget.TextView tvCounter; // Add this

        PhotoViewHolder(@NonNull View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageViewPhoto);
            tvCounter = itemView.findViewById(R.id.tvCounter); // Add this
        }
    }
}
