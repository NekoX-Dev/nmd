package io.nekohasekai.nmd.database

import org.jetbrains.exposed.sql.Table

object Sessions : Table("sessions") {

    // encoded q of ec public key
    val key = binary("key", 33)
    val status = integer("status")

    override val primaryKey = PrimaryKey(key)

}