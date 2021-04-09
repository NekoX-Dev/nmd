package io.nekohasekai.nmd.database

import org.jetbrains.exposed.sql.Table

class Sessions : Table("sessions") {

    // encoded q of ec public key
    val key = binary("key", 33)

    override val primaryKey = PrimaryKey(key)

}