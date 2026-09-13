package damjay.photo.triage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.io.File;
import java.util.List;

/**
 * Grid adapter for Resolution Studio – shows thumbnail, resolution, base/discard/override badges.
 */
public class StudioPhotoAdapter extends RecyclerView.Adapter<StudioPhotoAdapter.VH> {

    public interface Listener {
        void onClick(StudioPhoto photo, int pos);
        void onLongClick(StudioPhoto photo, int pos);
        void onBaseClick(StudioPhoto photo, int pos);
        void onDiscardToggle(StudioPhoto photo, int pos, boolean discard);
        void onSelectToggle(StudioPhoto photo, int pos, boolean selected);
    }

    private final List<StudioPhoto> photos;
    private final Listener listener;

    public StudioPhotoAdapter(List<StudioPhoto> photos, Listener listener) {
        this.photos = photos;
        this.listener = listener;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_studio_photo, parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(@NonNull VH h, int position) {
        StudioPhoto p = photos.get(position);
        File f = p.file;

        Glide.with(h.image.getContext()).load(f).centerCrop().placeholder(R.drawable.ic_triage_logo).into(h.image);

        h.tvName.setText(p.name);
        if (p.width > 0) {
            h.tvRes.setText(p.width + " × " + p.height + " • " + String.format("%.2f:1", p.aspect));
        } else {
            h.tvRes.setText("…");
        }

        // Badges
        h.badgeBase.setVisibility(p.isBase ? View.VISIBLE : View.GONE);
        h.badgeDiscard.setVisibility(p.discarded ? View.VISIBLE : View.GONE);
        h.badgeSplit.setVisibility(p.overrideSplit != null || p.hasOverride() ? View.VISIBLE : View.GONE);
        h.badgeOverride.setVisibility(p.hasOverride() ? View.VISIBLE : View.GONE);

        // Selection
        h.cbSelect.setOnCheckedChangeListener(null);
        h.cbSelect.setChecked(p.selected);
        h.cbSelect.setOnCheckedChangeListener((v, checked) -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) {
                p.selected = checked;
                listener.onSelectToggle(p, pos, checked);
            }
        });

        // Card alpha for discarded
        h.card.setAlpha(p.discarded ? 0.45f : 1f);
        h.card.setStrokeColor(p.isBase ? 0xFF006A60 : 0xFFBEC9C6);
        h.card.setStrokeWidth(p.isBase ? 3 : 1);

        h.image.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onClick(p, pos);
        });
        h.image.setOnLongClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onLongClick(p, pos);
            return true;
        });
        h.btnBase.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onBaseClick(p, pos);
        });
        h.btnDiscard.setOnClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) listener.onDiscardToggle(p, pos, !p.discarded);
        });

        // Hint
        String hint = "";
        if (p.isBase) hint = "★ Base • ";
        if (p.hasOverride()) hint += "Overridden • ";
        if (p.discarded) hint += "Discarded";
        else hint += "Tap • Long-press override";
        h.tvHint.setText(hint);
    }

    @Override
    public int getItemCount() { return photos == null ? 0 : photos.size(); }

    static class VH extends RecyclerView.ViewHolder {
        MaterialCardView card;
        ImageView image;
        TextView tvName, tvRes, tvHint;
        View badgeBase, badgeDiscard, badgeSplit, badgeOverride;
        MaterialCheckBox cbSelect;
        View btnBase, btnDiscard;
        VH(@NonNull View v) {
            super(v);
            card = (MaterialCardView) v;
            image = v.findViewById(R.id.ivStudioPhoto);
            tvName = v.findViewById(R.id.tvStudioName);
            tvRes = v.findViewById(R.id.tvStudioRes);
            tvHint = v.findViewById(R.id.tvStudioHint);
            badgeBase = v.findViewById(R.id.badgeBase);
            badgeDiscard = v.findViewById(R.id.badgeDiscard);
            badgeSplit = v.findViewById(R.id.badgeSplit);
            badgeOverride = v.findViewById(R.id.badgeOverride);
            cbSelect = v.findViewById(R.id.cbStudioSelect);
            btnBase = v.findViewById(R.id.btnStudioBase);
            btnDiscard = v.findViewById(R.id.btnStudioDiscard);
        }
    }
}
