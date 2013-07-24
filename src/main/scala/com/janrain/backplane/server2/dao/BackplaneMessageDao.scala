package com.janrain.backplane.server2.dao

import com.janrain.backplane.server2.model.BackplaneMessage
import com.janrain.backplane.dao.Dao
import com.janrain.backplane2.server.Scope

/**
 * @author Johnny Bufu
 */
trait BackplaneMessageDao extends Dao[BackplaneMessage] {

  def messageCount(channel: String): Long

  /** @return - List of messages matching scope
    *         - boolean flag if more messages exist than are returned
    *         - the ID of the last message in the DB at the time when the transaction completed
    */
  def retrieveMessagesPerScope(scope: Scope, since: String): (List[BackplaneMessage], Boolean, Option[String])

}