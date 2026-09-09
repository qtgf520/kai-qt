package com.qtkai.zhong.data

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.qtkai.zhong.db.KaiDatabase

actual fun createConversationSqlDriver(): SqlDriver? = NativeSqliteDriver(KaiDatabase.Schema, "conversations.db")
