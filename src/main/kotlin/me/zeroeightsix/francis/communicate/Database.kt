package me.zeroeightsix.francis.communicate

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import me.zeroeightsix.francis.Config
import me.zeroeightsix.francis.Player
import org.intellij.lang.annotations.Language
import java.sql.Connection
import java.sql.PreparedStatement

object Database {
    lateinit var ds: HikariDataSource

    private fun connect(config: Config) {
        ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = config.dbUrl ?: "jdbc:mariadb://localhost/francis?allowMultiQueries=true"
            username = config.dbUser
            password = config.dbPassword
        })
    }

    fun init(config: Config) {
        connect(config)

        // create tables if not exists
        val con = ds.connection
        con.prepareStatement(
            """create table if not exists users
(
    uuid         varchar(36)                          not null,
    username     varchar(16)                          not null,
    first_seen   timestamp  default current_timestamp not null,
    opted_out    tinyint(1) default 0                 not null,
    join_message longtext                             null,
    discord      longtext                             null,
    balance      int        default 0                 not null,
    faith        float      default 0                 not null
);"""
        ).execute()
        con.prepareStatement("create index if not exists users_uuid on users (uuid);").execute()
        con.prepareStatement(
            """create table if not exists messages
(
    sender    varchar(36)          not null,
    recipient varchar(36)          not null,
    message   longtext             not null,
    delivered tinyint(1) default 0 not null,
    constraint fk_messages_recipient__uuid
        foreign key (recipient) references users (uuid),
    constraint fk_messages_sender__uuid
        foreign key (sender) references users (uuid)
);
"""
        ).execute()
    }

    fun assertUser(player: Player) {
        ds.connection.prepare(
            "insert into users (uuid, username) values (?1, ?2) on duplicate key update username=?2",
            player.uuid,
            player.username
        ).execute()
    }
}

fun Connection.prepare(@Language("SQL") sql: String, vararg params: Any): PreparedStatement {
    val stmt = prepareStatement(sql)
    for ((index, value) in params.withIndex()) {
        when (value) {
            is String -> stmt.setString(index, value)
            is Int -> stmt.setInt(index, value)
            else -> TODO()
        }
    }
    return stmt
}