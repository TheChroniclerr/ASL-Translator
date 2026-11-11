package com.group_nine.asl_translator.alphabet;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.group_nine.asl_translator.R;

import java.util.ArrayList;
import java.util.List;

public class AlphabetFragment extends Fragment {

    public AlphabetFragment() {
        super(R.layout.fragment_alphabet);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        RecyclerView recyclerAlphabet = view.findViewById(R.id.recycler_alphabet);
        recyclerAlphabet.setLayoutManager(new GridLayoutManager(requireContext(), 2));

        List<AlphabetItem> letters = new ArrayList<>();
        letters.add(new AlphabetItem("A", R.drawable.ic_asl_a));
        letters.add(new AlphabetItem("E", R.drawable.ic_asl_e));
        letters.add(new AlphabetItem("I", R.drawable.ic_asl_i));
        letters.add(new AlphabetItem("O", R.drawable.ic_asl_o));
        letters.add(new AlphabetItem("U", R.drawable.ic_asl_u));

        recyclerAlphabet.setAdapter(new AlphabetAdapter(letters));
    }
}