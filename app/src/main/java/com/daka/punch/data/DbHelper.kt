// 目标路径: app/src/main/java/com/daka/punch/data/DbHelper.kt
package com.daka.punch.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/** 本机私有数据库（不备份、不上传、不联网） */
class DbHelper(context: Context) :
    SQLiteOpenHelper(context.applicationContext, "punch_daka.db", null, 1) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS monitored (
                pkg     TEXT PRIMARY KEY,
                label   TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sessions (
                id       INTEGER PRIMARY KEY AUTOINCREMENT,
                pkg      TEXT NOT NULL,
                start_ms INTEGER NOT NULL,
                end_ms   INTEGER NOT NULL DEFAULT -1,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                day      TEXT NOT NULL,
                crash    INTEGER NOT NULL DEFAULT 0,
                reason   TEXT NOT NULL DEFAULT ''
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_day ON sessions(day)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_sessions_pkg ON sessions(pkg)")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reminders (
                id      INTEGER PRIMARY KEY AUTOINCREMENT,
                hour    INTEGER NOT NULL,
                minute  INTEGER NOT NULL,
                label   TEXT NOT NULL,
                enabled INTEGER NOT NULL DEFAULT 1,
                days    INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 首个版本，暂无迁移
    }
}
