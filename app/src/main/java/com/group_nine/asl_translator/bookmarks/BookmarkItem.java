package com.group_nine.asl_translator.bookmarks;

public class BookmarkItem {
    private final String word;
    private final long timestamp;

    public BookmarkItem(String word, long timestamp) {
        this.word = word;
        this.timestamp = timestamp;
    }

    public String getWord() {
        return word;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
