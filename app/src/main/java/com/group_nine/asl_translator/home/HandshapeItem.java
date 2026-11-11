package com.group_nine.asl_translator.home;

public class HandshapeItem {
    private final char letter;
    private final int drawableResId;

    public HandshapeItem(char letter, int drawableResId) {
        this.letter = letter;
        this.drawableResId = drawableResId;
    }

    public char getLetter() {
        return letter;
    }

    public int getDrawableResId() {
        return drawableResId;
    }
}