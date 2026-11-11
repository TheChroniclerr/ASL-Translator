package com.group_nine.asl_translator.alphabet;

public class AlphabetItem {
    private final String letter;
    private final int drawableResId;

    public AlphabetItem(String letter, int drawableResId) {
        this.letter = letter;
        this.drawableResId = drawableResId;
    }

    public String getLetter() {
        return letter;
    }

    public int getDrawableResId() {
        return drawableResId;
    }
}