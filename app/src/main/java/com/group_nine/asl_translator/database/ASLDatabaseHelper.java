package com.group_nine.asl_translator.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

public class ASLDatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "asl_translator.db";
    private static final int DATABASE_VERSION = 1;

    // Users Table
    public static final String TABLE_USERS = "users";
    public static final String COL_USER_ID = "_id";
    public static final String COL_USER_NAME = "name";
    public static final String COL_USER_EMAIL = "email";
    public static final String COL_USER_PASSWORD = "password";
    public static final String COL_USER_MEMBER_SINCE = "memberSince";

    // Bookmarks Table
    public static final String TABLE_BOOKMARKS = "bookmarks";
    public static final String COL_BOOKMARK_ID = "_id";
    public static final String COL_BOOKMARK_WORD = "word";
    public static final String COL_BOOKMARK_TIMESTAMP = "timestamp";

    // Recent Searches Table
    public static final String TABLE_SEARCHES = "recent_searches";
    public static final String COL_SEARCH_ID = "_id";
    public static final String COL_SEARCH_QUERY = "searchQuery";
    public static final String COL_SEARCH_TIMESTAMP = "timestamp";

    private static final String CREATE_TABLE_USERS = "create table "
            + TABLE_USERS + " ("
            + COL_USER_ID + " integer primary key autoincrement, "
            + COL_USER_NAME + " text not null, "
            + COL_USER_EMAIL + " text not null unique, "
            + COL_USER_PASSWORD + " text not null, "
            + COL_USER_MEMBER_SINCE + " integer not null);";

    private static final String CREATE_TABLE_BOOKMARKS = "create table "
            + TABLE_BOOKMARKS + " ("
            + COL_BOOKMARK_ID + " integer primary key autoincrement, "
            + COL_BOOKMARK_WORD + " text not null, "
            + COL_BOOKMARK_TIMESTAMP + " integer not null);";

    private static final String CREATE_TABLE_SEARCHES = "create table "
            + TABLE_SEARCHES + " ("
            + COL_SEARCH_ID + " integer primary key autoincrement, "
            + COL_SEARCH_QUERY + " text not null, "
            + COL_SEARCH_TIMESTAMP + " integer not null);";

    public ASLDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(CREATE_TABLE_USERS);
        database.execSQL(CREATE_TABLE_BOOKMARKS);
        database.execSQL(CREATE_TABLE_SEARCHES);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SEARCHES);
        onCreate(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.w(ASLDatabaseHelper.class.getName(),
                "Downgrading database from version " + newVersion + " to " + oldVersion);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_USERS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_BOOKMARKS);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_SEARCHES);
        onCreate(db);
    }
}