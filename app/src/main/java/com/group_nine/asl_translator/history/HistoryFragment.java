package com.group_nine.asl_translator.history;

import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.group_nine.asl_translator.R;
import com.group_nine.asl_translator.bookmarks.BookmarksRepository;
import com.group_nine.asl_translator.database.ASLDataSource;

import java.util.ArrayList;
import java.util.List;

public class HistoryFragment extends Fragment {

    private ASLDataSource datasource;
    private ArrayAdapter<String> mAdapter;
    private final List<String> recentSearches = new ArrayList<>();
    private ListView mListView;
    private View emptyStateView;
    private View headerView;

    public HistoryFragment() {
        super(R.layout.fragment_history);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        BookmarksRepository.getInstance().init(requireContext());
        datasource = new ASLDataSource(requireContext());
        datasource.open();

        mListView = view.findViewById(R.id.list_recent_searches);
        emptyStateView = view.findViewById(R.id.layout_history_empty);
        headerView = view.findViewById(R.id.layout_history_header);
        MaterialButton btnClear = view.findViewById(R.id.btn_clear_history);

        mAdapter = new ArrayAdapter<>(
                requireContext(),
                android.R.layout.simple_list_item_1,
                recentSearches
        );
        mListView.setAdapter(mAdapter);

        mListView.setOnItemClickListener((parent, itemView, position, id) -> {
            String selectedQuery = recentSearches.get(position);
            showHistoryActionDialog(selectedQuery);
        });

        btnClear.setOnClickListener(v ->
                new MaterialAlertDialogBuilder(requireContext())
                        .setTitle("Clear Recent History")
                        .setMessage("Delete all recent searches and translations?")
                        .setPositiveButton("Clear All", (dialog, which) -> {
                            datasource.clearRecentSearches();
                            refreshHistory();
                            Toast.makeText(requireContext(), "History cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        refreshHistory();
    }

    private void showHistoryActionDialog(String queryText) {
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(queryText)
                .setMessage("What would you like to do with this recent entry?")
                .setPositiveButton("Open in Translator", (dialog, which) -> {
                    Fragment homeFragment = requireActivity().getSupportFragmentManager().findFragmentByTag("home");
                    if (homeFragment != null && homeFragment.getView() != null) {
                        EditText output = homeFragment.getView().findViewById(R.id.translation_output);
                        if (output != null) {
                            output.setText(queryText);
                            output.setSelection(queryText.length());
                        }
                    }
                    BottomNavigationView nav = requireActivity().findViewById(R.id.bottom_navigation);
                    if (nav != null) {
                        nav.setSelectedItemId(R.id.nav_home);
                    }
                })
                .setNeutralButton("Add to Bookmarks", (dialog, which) -> {
                    BookmarksRepository.getInstance().addBookmark(queryText);
                    Toast.makeText(requireContext(), "Bookmarked \"" + queryText + "\"", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Close", null)
                .show();
    }

    public void refreshHistory() {
        if (datasource == null || mAdapter == null) return;
        List<String> values = datasource.getRecentSearches();
        recentSearches.clear();
        recentSearches.addAll(values);
        mAdapter.notifyDataSetChanged();

        boolean isEmpty = recentSearches.isEmpty();
        if (emptyStateView != null) {
            emptyStateView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        }
        if (mListView != null) {
            mListView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
        if (headerView != null) {
            headerView.setVisibility(isEmpty ? View.GONE : View.VISIBLE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (datasource != null) {
            datasource.open();
            refreshHistory();
        }
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && datasource != null) {
            datasource.open();
            refreshHistory();
        }
    }

    @Override
    public void onPause() {
        if (datasource != null) {
            datasource.close();
        }
        super.onPause();
    }
}