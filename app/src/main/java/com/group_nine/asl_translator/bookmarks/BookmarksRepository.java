package com.group_nine.asl_translator.bookmarks;

import android.content.Context;

import com.group_nine.asl_translator.database.ASLDataSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class BookmarksRepository {
    private static BookmarksRepository instance;
    private final List<BookmarkItem> savedBookmarks = new ArrayList<>();
    private ASLDataSource dataSource;

    private BookmarksRepository() {
    }

    public static synchronized BookmarksRepository getInstance() {
        if (instance == null) {
            instance = new BookmarksRepository();
        }
        return instance;
    }

    public synchronized void init(Context context) {
        if (dataSource == null && context != null) {
            dataSource = new ASLDataSource(context.getApplicationContext());
        }
    }

    public List<BookmarkItem> getBookmarks() {
        if (dataSource != null) {
            dataSource.open();
            List<BookmarkItem> items = dataSource.getAllBookmarks();
            dataSource.close();
            return Collections.unmodifiableList(items);
        }
        return Collections.unmodifiableList(new ArrayList<>(savedBookmarks));
    }

    public void addBookmark(String word) {
        if (word == null || word.trim().isEmpty()) return;
        long now = System.currentTimeMillis();
        if (dataSource != null) {
            dataSource.open();
            dataSource.createBookmark(word.trim(), now);
            dataSource.close();
        } else {
            savedBookmarks.add(0, new BookmarkItem(word.trim(), now));
        }
    }

    public void removeBookmark(BookmarkItem item) {
        if (dataSource != null) {
            dataSource.open();
            dataSource.deleteBookmark(item);
            dataSource.close();
        } else {
            savedBookmarks.remove(item);
        }
    }

    public void clearAll() {
        if (dataSource != null) {
            dataSource.open();
            dataSource.deleteAllBookmarks();
            dataSource.close();
        } else {
            savedBookmarks.clear();
        }
    }
}