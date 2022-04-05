package me.zeroeightsix.francis.communicate

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import me.zeroeightsix.francis.Config
import me.zeroeightsix.francis.Player
import me.zeroeightsix.francis.PlayerID
import org.intellij.lang.annotations.Language
import java.sql.Connection
import java.sql.PreparedStatement

object Database {
    
    private lateinit var ds: HikariDataSource
    val connection: Connection
        get() = ds.connection

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
    uuid         varchar(36)                          not null
        primary key,
    username     varchar(16)                          not null,
    first_seen   timestamp  default current_timestamp not null,
    opted_out    tinyint(1) default 0                 not null,
    join_message tinytext                             null,
    discord      tinytext                             null,
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
    message   tinytext             not null,
    delivered tinyint(1) default 0 not null,
    constraint fk_messages_recipient__uuid
        foreign key (recipient) references users (uuid),
    constraint fk_messages_sender__uuid
        foreign key (sender) references users (uuid)
);
"""
        ).execute()
    }

    /**
     * Try inserting a player into the users database, updating their username if they were already present
     *
     * This ensures commands can count on the player being in the DB, and that their username is up-to-date
     */
    fun assertUser(player: Player) {
        ds.connection.use {
            it.prepare(
                "insert into users (uuid, username) values (?, ?) on duplicate key update username=?",
                player.uuid,
                player.username,
                player.username,
            ).execute()
        }
    }

    fun Connection.getBalance(uuid: PlayerID): Int {
        return prepare("select balance from users where uuid=?", uuid)
            .executeQuery()
            .run { if (next()) getInt("balance") else 0 }
    }

    fun Connection.getBalanceFaith(uuid: PlayerID): Pair<Int, Float>? {
        return prepare("select balance, faith from users where uuid=?", uuid)
            .executeQuery()
            .run { if (next()) getInt("balance") to getFloat("faith") else null }
    }

    fun Connection.getJoinMessage(uuid: PlayerID): String? {
        return prepare("select join_message from users where uuid=?", uuid)
            .executeQuery()
            .run { if (next()) getString("join_message") else null }
    }

    fun Connection.getUUID(username: String): PlayerID? {
        return prepare("select uuid from users where username=?", username)
            .executeQuery()
            .run { if (next()) getString("uuid") else null }
    }

    fun Connection.prepare(@Language("SQL") sql: String, vararg params: Any): PreparedStatement {
        val stmt = prepareStatement(sql)
        for ((index, value) in params.withIndex()) {
            when (value) {
                is String -> stmt.setString(index + 1, value)
                is Int -> stmt.setInt(index + 1, value)
                is Float -> stmt.setFloat(index + 1, value)
                else -> TODO()
            }
        }
        return stmt
    }
}
