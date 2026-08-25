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
        
        // Tap to move photo
        holder.button.setOnClickListener(v -> listener.onCategoryClick(category));
        
        // Long press to delete category
        holder.button.setOnLongClickListener(v -> {
            listener.onCategoryLongClick(category, position);
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
