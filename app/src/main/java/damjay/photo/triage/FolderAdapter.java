package damjay.photo.triage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.util.List;

public class FolderAdapter extends RecyclerView.Adapter<FolderAdapter.FolderViewHolder> {

    public interface OnFolderClickListener {
        void onFolderClick(File folder);
    }

    private final List<File> folders;
    private final OnFolderClickListener listener;

    public FolderAdapter(List<File> folders, OnFolderClickListener listener) {
        this.folders = folders;
        this.listener = listener;
    }

    @NonNull
    @Override
    public FolderViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_folder, parent, false);
        return new FolderViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull FolderViewHolder holder, int position) {
        File folder = folders.get(position);
        holder.tvName.setText(folder.getName().isEmpty() ? folder.getAbsolutePath() : folder.getName());
        holder.tvPath.setText(folder.getAbsolutePath());
        holder.itemView.setOnClickListener(v -> listener.onFolderClick(folder));
    }

    @Override
    public int getItemCount() {
        return folders == null ? 0 : folders.size();
    }

    static class FolderViewHolder extends RecyclerView.ViewHolder {
        TextView tvName, tvPath;
        FolderViewHolder(@NonNull View itemView) {
            super(itemView);
            tvName = itemView.findViewById(R.id.tvFolderName);
            tvPath = itemView.findViewById(R.id.tvFolderPath);
        }
    }
}
