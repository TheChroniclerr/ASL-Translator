package com.group_nine.asl_translator.bookmarks;

import android.content.Context;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.card.MaterialCardView;
import com.group_nine.asl_translator.R;

import java.util.ArrayList;
import java.util.List;

public class BookmarksAdapter extends RecyclerView.Adapter<BookmarksAdapter.ViewHolder> {
    public interface OnBookmarkChangedListener {
        void onBookmarkChanged();
    }

    private final List<BookmarkItem> displayedList;
    private final OnBookmarkChangedListener changeListener;

    public BookmarksAdapter(List<BookmarkItem> items, OnBookmarkChangedListener listener) {
        this.displayedList = new ArrayList<>(items);
        this.changeListener = listener;
    }

    public void updateList(List<BookmarkItem> newItems) {
        displayedList.clear();
        displayedList.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_bookmark_entry, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        BookmarkItem item = displayedList.get(position);
        Context context = holder.itemView.getContext();

        holder.textWord.setText(item.getWord());
        holder.handStrip.removeAllViews();

        float density = context.getResources().getDisplayMetrics().density;
        int cardSizePx = Math.round(64 * density);
        int marginPx = Math.round(4 * density);

        for (char c : item.getWord().toCharArray()) {
            if (Character.isWhitespace(c)) {
                View spacer = new View(context);
                LinearLayout.LayoutParams spaceParams = new LinearLayout.LayoutParams(Math.round(16 * density), cardSizePx);
                holder.handStrip.addView(spacer, spaceParams);
                continue;
            }

            char letter = Character.toUpperCase(c);
            int drawableId = resolveLetterDrawable(context, letter);

            MaterialCardView letterCard = new MaterialCardView(context);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(cardSizePx, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.setMargins(marginPx, 0, marginPx, 0);
            letterCard.setLayoutParams(cardParams);
            letterCard.setRadius(12 * density);
            letterCard.setStrokeWidth(Math.round(1 * density));
            letterCard.setStrokeColor(context.getColor(R.color.asl_outline));
            letterCard.setCardBackgroundColor(context.getColor(R.color.asl_surface));

            LinearLayout cardInner = new LinearLayout(context);
            cardInner.setOrientation(LinearLayout.VERTICAL);
            cardInner.setGravity(Gravity.CENTER);
            cardInner.setPadding(marginPx, marginPx, marginPx, marginPx);

            ImageView handImage = new ImageView(context);
            handImage.setLayoutParams(new LinearLayout.LayoutParams(Math.round(44 * density), Math.round(44 * density)));
            if (drawableId != 0) {
                handImage.setImageResource(drawableId);
            }
            handImage.setScaleType(ImageView.ScaleType.FIT_CENTER);
            cardInner.addView(handImage);

            TextView letterLabel = new TextView(context);
            letterLabel.setText(String.valueOf(letter));
            letterLabel.setTextSize(12);
            letterLabel.setTextColor(context.getColor(R.color.asl_primary));
            letterLabel.setGravity(Gravity.CENTER);
            cardInner.addView(letterLabel);

            letterCard.addView(cardInner);
            holder.handStrip.addView(letterCard);
        }

        holder.btnDelete.setOnClickListener(v -> {
            BookmarksRepository.getInstance().removeBookmark(item);
            Toast.makeText(context, "Removed \"" + item.getWord() + "\"", Toast.LENGTH_SHORT).show();
            if (changeListener != null) changeListener.onBookmarkChanged();
        });
    }

    private int resolveLetterDrawable(Context context, char letter) {
        String resourceName = "ic_asl_" + Character.toLowerCase(letter);
        int resId = context.getResources().getIdentifier(resourceName, "drawable", context.getPackageName());
        return (resId != 0) ? resId : android.R.drawable.ic_menu_help;
    }

    @Override
    public int getItemCount() {
        return displayedList.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView textWord;
        ImageView btnDelete;
        LinearLayout handStrip;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            textWord = itemView.findViewById(R.id.text_bookmark_word);
            btnDelete = itemView.findViewById(R.id.button_delete_bookmark);
            handStrip = itemView.findViewById(R.id.layout_hand_strip);
        }
    }
}