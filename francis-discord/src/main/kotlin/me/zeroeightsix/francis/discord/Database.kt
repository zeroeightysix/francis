package me.zeroeightsix.francis.discord

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.intellij.lang.annotations.Language
import java.sql.Connection
import java.sql.PreparedStatement

object Database {

    private lateinit var ds: HikariDataSource
    val connection: Connection
        get() = ds.connection

    fun connect(config: Config) {
        ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.dbUrl ?: "jdbc:mariadb://localhost/francis?allowMultiQueries=true"
            username = config.dbUser
            password = config.dbPassword
        })
    }

    fun Connection.prepare(@Language("SQL") sql: String, vararg params: Any): PreparedStatement {
        val stmt = prepareStatement(sql)
        for ((index, value) in params.withIndex()) {
            when (value) {
                is String -> stmt.setString(index + 1, value)
                is Int -> stmt.setInt(index + 1, value)
                is Float -> stmt.setFloat(index + 1, value)
                is Long -> stmt.setLong(index + 1, value)
                else -> TODO()
            }
        }
        return stmt
    }
}