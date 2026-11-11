package com.group_nine.asl_translator.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.group_nine.asl_translator.R;

import java.util.ArrayList;
import java.util.List;

public class HandshapeAdapter extends RecyclerView.Adapter<HandshapeAdapter.ViewHolder> {
    private final List<HandshapeItem> items = new ArrayList<>();

    public void setItems(List<HandshapeItem> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_handshape, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        HandshapeItem item = items.get(position);
        holder.textLetter.setText(String.valueOf(item.getLetter()));

        if (item.getDrawableResId() != 0) {
            holder.imageHandshape.setImageResource(item.getDrawableResId());
            holder.imageHandshape.setVisibility(View.VISIBLE);
        } else {
            holder.imageHandshape.setImageDrawable(null);
            holder.imageHandshape.setVisibility(View.INVISIBLE);
        }
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
            imageHandshape = itemView.findViewById(R.id.image_handshape);
            textLetter = itemView.findViewById(R.id.text_handshape_letter);
        }
    }
}