package com.group_nine.asl_translator.alphabet;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.group_nine.asl_translator.R;

import java.util.List;

public class AlphabetAdapter extends RecyclerView.Adapter<AlphabetAdapter.ViewHolder> {
    private final List<AlphabetItem> items;

    public AlphabetAdapter(List<AlphabetItem> items) {
        this.items = items;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alphabet, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        AlphabetItem item = items.get(position);
        holder.textLetter.setText(item.getLetter());
        holder.imageHandshape.setImageResource(item.getDrawableResId());
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final ImageView imageHandshape;
        final TextView textLetter;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            imageHandshape = itemView.findViewById(R.id.image_alphabet_handshape);
            textLetter = itemView.findViewById(R.id.text_alphabet_letter);
        }
    }
}