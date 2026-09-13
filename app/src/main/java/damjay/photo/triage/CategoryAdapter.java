package damjay.photo.triage;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.List;

public class CategoryAdapter extends RecyclerView.Adapter<CategoryAdapter.CategoryViewHolder> {

    private final List<String> categories;
    private final OnCategoryInteractionListener listener;

    public interface OnCategoryInteractionListener {
        void onCategoryClick(String categoryName);
        void onCategoryLongClick(String categoryName, int position);
    }

    public CategoryAdapter(List<String> categories, OnCategoryInteractionListener listener) {
        this.categories = categories;
        this.listener = listener;
    }

    @NonNull
    @Override
    public CategoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_category_button, parent, false);
        return new CategoryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull CategoryViewHolder holder, int position) {
        String category = categories.get(position);
        holder.button.setText(category);

        // Tap to move photo — use current binding position at click time to avoid stale positions
        holder.button.setOnClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && pos < categories.size()) {
                listener.onCategoryClick(categories.get(pos));
            } else {
                listener.onCategoryClick(category);
            }
        });

        // Long press to delete category — resolve position at interaction time
        holder.button.setOnLongClickListener(v -> {
            int pos = holder.getBindingAdapterPosition();
            if (pos == RecyclerView.NO_POSITION) return true;
            if (pos < 0 || pos >= categories.size()) return true;
            String name = categories.get(pos);
            listener.onCategoryLongClick(name, pos);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return categories == null ? 0 : categories.size();
    }

    static class CategoryViewHolder extends RecyclerView.ViewHolder {
        MaterialButton button;

        CategoryViewHolder(@NonNull View itemView) {
            super(itemView);
            button = itemView.findViewById(R.id.btnDynamicCategory);
        }
    }
}
