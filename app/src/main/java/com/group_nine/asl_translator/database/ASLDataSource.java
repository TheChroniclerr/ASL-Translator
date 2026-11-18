package com.group_nine.asl_translator.database;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.group_nine.asl_translator.bookmarks.BookmarkItem;

import java.util.ArrayList;
import java.util.List;

public class ASLDataSource {

    public static class UserAccount {
        public final String name;
        public final String email;
        public final String password;
        public final long memberSince;

        public UserAccount(String name, String email, String password, long memberSince) {
            this.name = name;
            this.email = email;
            this.password = password;
            this.memberSince = memberSince;
        }
    }

    private SQLiteDatabase database;
    private final ASLDatabaseHelper dbOpenHelper;

    private final String[] bookmarkColumns = {
            ASLDatabaseHelper.COL_BOOKMARK_ID,
            ASLDatabaseHelper.COL_BOOKMARK_WORD,
            ASLDatabaseHelper.COL_BOOKMARK_TIMESTAMP
    };

    private final String[] searchColumns = {
            ASLDatabaseHelper.COL_SEARCH_ID,
            ASLDatabaseHelper.COL_SEARCH_QUERY,
            ASLDatabaseHelper.COL_SEARCH_TIMESTAMP
    };

    public ASLDataSource(Context context) {
        dbOpenHelper = new ASLDatabaseHelper(context.getApplicationContext());
    }

    public void open() throws SQLException {
        database = dbOpenHelper.getWritableDatabase();
    }

    public void close() {
        dbOpenHelper.close();
    }

    // ==========================================
    // 1. USER LOGIN, PROFILE & ACCOUNT METHODS
    // ==========================================

    public long registerUser(String name, String email, String password, long memberSince) {
        ContentValues values = new ContentValues();
        values.put(ASLDatabaseHelper.COL_USER_NAME, name);
        values.put(ASLDatabaseHelper.COL_USER_EMAIL, email);
        values.put(ASLDatabaseHelper.COL_USER_PASSWORD, password);
        values.put(ASLDatabaseHelper.COL_USER_MEMBER_SINCE, memberSince);
        return database.insert(ASLDatabaseHelper.TABLE_USERS, null, values);
    }

    public UserAccount authenticateUser(String email, String password) {
        String[] columns = {
                ASLDatabaseHelper.COL_USER_NAME,
                ASLDatabaseHelper.COL_USER_EMAIL,
                ASLDatabaseHelper.COL_USER_PASSWORD,
                ASLDatabaseHelper.COL_USER_MEMBER_SINCE
        };

        Cursor cursor = database.query(
                ASLDatabaseHelper.TABLE_USERS,
                columns,
                ASLDatabaseHelper.COL_USER_EMAIL + " = ? AND " + ASLDatabaseHelper.COL_USER_PASSWORD + " = ?",
                new String[]{email, password},
                null, null, null
        );

        UserAccount account = null;
        if (cursor.moveToFirst()) {
            account = new UserAccount(
                    cursor.getString(0),
                    cursor.getString(1),
                    cursor.getString(2),
                    cursor.getLong(3)
            );
        }
        cursor.close();
        return account;
    }

    public int updateUserName(String email, String newName) {
        ContentValues values = new ContentValues();
        values.put(ASLDatabaseHelper.COL_USER_NAME, newName);
        return database.update(
                ASLDatabaseHelper.TABLE_USERS,
                values,
                ASLDatabaseHelper.COL_USER_EMAIL + " = ?",
                new String[]{email}
        );
    }

    public int updateUserPassword(String email, String newPassword) {
        ContentValues values = new ContentValues();
        values.put(ASLDatabaseHelper.COL_USER_PASSWORD, newPassword);
        return database.update(
                ASLDatabaseHelper.TABLE_USERS,
                values,
                ASLDatabaseHelper.COL_USER_EMAIL + " = ?",
                new String[]{email}
        );
    }

    public int deleteUser(String email) {
        return database.delete(
                ASLDatabaseHelper.TABLE_USERS,
                ASLDatabaseHelper.COL_USER_EMAIL + " = ?",
                new String[]{email}
        );
    }

    // ==========================================
    // 2. BOOKMARKS METHODS
    // ==========================================

    public BookmarkItem createBookmark(String word, long timestamp) {
        ContentValues values = new ContentValues();
        values.put(ASLDatabaseHelper.COL_BOOKMARK_WORD, word);
        values.put(ASLDatabaseHelper.COL_BOOKMARK_TIMESTAMP, timestamp);
        database.insert(ASLDatabaseHelper.TABLE_BOOKMARKS, null, values);
        return new BookmarkItem(word, timestamp);
    }

    public void deleteBookmark(BookmarkItem item) {
        database.delete(
                ASLDatabaseHelper.TABLE_BOOKMARKS,
                ASLDatabaseHelper.COL_BOOKMARK_WORD + " = ? AND " + ASLDatabaseHelper.COL_BOOKMARK_TIMESTAMP + " = ?",
                new String[]{item.getWord(), String.valueOf(item.getTimestamp())}
        );
    }

    public void deleteAllBookmarks() {
        database.delete(ASLDatabaseHelper.TABLE_BOOKMARKS, null, null);
    }

    public List<BookmarkItem> getAllBookmarks() {
        List<BookmarkItem> items = new ArrayList<>();
        Cursor cursor = database.query(
                ASLDatabaseHelper.TABLE_BOOKMARKS,
                bookmarkColumns,
                null, null, null, null,
                ASLDatabaseHelper.COL_BOOKMARK_TIMESTAMP + " DESC"
        );

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            BookmarkItem item = cursorToBookmark(cursor);
            items.add(item);
            cursor.moveToNext();
        }
        cursor.close();
        return items;
    }

    private BookmarkItem cursorToBookmark(Cursor cursor) {
        String word = cursor.getString(1);
        long timestamp = cursor.getLong(2);
        return new BookmarkItem(word, timestamp);
    }

    // ==========================================
    // 3. RECENT SEARCHES METHODS
    // ==========================================

    public void addRecentSearch(String queryText) {
        if (queryText == null || queryText.trim().isEmpty()) return;
        String cleanQuery = queryText.trim();

        database.delete(
                ASLDatabaseHelper.TABLE_SEARCHES,
                ASLDatabaseHelper.COL_SEARCH_QUERY + " = ?",
                new String[]{cleanQuery}
        );

        ContentValues values = new ContentValues();
        values.put(ASLDatabaseHelper.COL_SEARCH_QUERY, cleanQuery);
        values.put(ASLDatabaseHelper.COL_SEARCH_TIMESTAMP, System.currentTimeMillis());
        database.insert(ASLDatabaseHelper.TABLE_SEARCHES, null, values);
    }

    public List<String> getRecentSearches() {
        List<String> searches = new ArrayList<>();
        Cursor cursor = database.query(
                ASLDatabaseHelper.TABLE_SEARCHES,
                searchColumns,
                null, null, null, null,
                ASLDatabaseHelper.COL_SEARCH_TIMESTAMP + " DESC",
                "10"
        );

        cursor.moveToFirst();
        while (!cursor.isAfterLast()) {
            searches.add(cursor.getString(1));
            cursor.moveToNext();
        }
        cursor.close();
        return searches;
    }

    public void clearRecentSearches() {
        database.delete(ASLDatabaseHelper.TABLE_SEARCHES, null, null);
    }
}