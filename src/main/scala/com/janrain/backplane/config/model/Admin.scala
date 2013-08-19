package com.janrain.backplane.config.model

import com.janrain.backplane.common.model.{UserFieldEnum, User}
import com.janrain.backplane.server2.dao.LegacySupport
import scala.collection.JavaConversions._

/**
 * @author Johnny Bufu
 */
class Admin(data: Map[String,String]) extends User("admin", data, AdminFields.values)
  with Password[AdminFields.EnumVal, Admin]
with LegacySupport[com.janrain.backplane2.server.config.User] {

  def this(username: String, pwdhash: String) = this(Map(
    AdminFields.USER.name -> username,
    AdminFields.PWDHASH.name -> pwdhash
  ))

  def idField = AdminFields.USER

  def pwdHashField = AdminFields.PWDHASH

  def asLegacy = new com.janrain.backplane2.server.config.User(mapAsJavaMap(this))
}

object AdminFields extends UserFieldEnum {
  trait AdminEnumVal extends EnumVal
  // no new, admin-specific fields
}
