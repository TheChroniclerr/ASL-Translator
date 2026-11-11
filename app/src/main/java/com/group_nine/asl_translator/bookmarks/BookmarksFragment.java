package com.group_nine.asl_translator.bookmarks;

import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.group_nine.asl_translator.R;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BookmarksFragment extends Fragment {
    private BookmarksAdapter adapter;
    private View emptyStateView;
    private String currentQuery = "";

    public BookmarksFragment() {
        super(R.layout.fragment_bookmarks);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerView = view.findViewById(R.id.recycler_bookmarks);
        emptyStateView = view.findViewById(R.id.layout_empty_state);
        EditText searchEdit = view.findViewById(R.id.edit_search);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new BookmarksAdapter(new ArrayList<>(), this::refreshData);
        recyclerView.setAdapter(adapter);

        if (searchEdit != null) {
            searchEdit.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    currentQuery = s != null ? s.toString().trim() : "";
                    applyFilters();
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });
        }

        refreshData();
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshData();
    }

    public void refreshData() {
        applyFilters();
    }

    private void applyFilters() {
        List<BookmarkItem> allItems = BookmarksRepository.getInstance().getBookmarks();
        List<BookmarkItem> filtered = new ArrayList<>();

        for (BookmarkItem item : allItems) {
            if (currentQuery.isEmpty() || item.getWord().toLowerCase(Locale.ROOT).contains(currentQuery.toLowerCase(Locale.ROOT))) {
                filtered.add(item);
            }
        }

        if (adapter != null) {
            adapter.updateList(filtered);
        }
        if (emptyStateView != null) {
            emptyStateView.setVisibility(filtered.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    public void showAddBookmarkDialog(String initialText) {
        View dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_add_bookmark, null);
        EditText inputWord = dialogView.findViewById(R.id.input_bookmark_word);

        if (initialText != null && !initialText.trim().isEmpty()) {
            inputWord.setText(initialText.trim());
        }

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle("Add to Bookmarks")
                .setMessage("Save this word to view its sign language handshapes anytime.")
                .setView(dialogView)
                .setPositiveButton("Save", (dialog, which) -> {
                    String word = inputWord.getText().toString().trim();
                    if (word.isEmpty()) {
                        Toast.makeText(requireContext(), "Enter a word to bookmark", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    BookmarksRepository.getInstance().addBookmark(word);
                    refreshData();
                    Toast.makeText(requireContext(), "Added \"" + word + "\" to Bookmarks", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
}