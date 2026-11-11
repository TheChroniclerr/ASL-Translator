package com.group_nine.asl_translator.bookmarks;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BookmarksRepository {
    private static BookmarksRepository instance;
    private final List<BookmarkItem> savedBookmarks = new ArrayList<>();

    private BookmarksRepository() {
    }

    public static synchronized BookmarksRepository getInstance() {
        if (instance == null) {
            instance = new BookmarksRepository();
        }
        return instance;
    }

    public List<BookmarkItem> getBookmarks() {
        return Collections.unmodifiableList(new ArrayList<>(savedBookmarks));
    }

    public void addBookmark(String word) {
        if (word == null || word.trim().isEmpty()) return;
        savedBookmarks.add(0, new BookmarkItem(word.trim(), System.currentTimeMillis()));
    }

    public void removeBookmark(BookmarkItem item) {
        savedBookmarks.remove(item);
    }

    public void clearAll() {
        savedBookmarks.clear();
    }
}

