/*
*************************************************************************************
* Copyright 2011 Normation SAS
*************************************************************************************
*
* This file is part of Rudder.
*
* Rudder is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 3 of the License, or
* (at your option) any later version.
*
* In accordance with the terms of section 7 (7. Additional Terms.) of
* the GNU General Public License version 3, the copyright holders add
* the following Additional permissions:
* Notwithstanding to the terms of section 5 (5. Conveying Modified Source
* Versions) and 6 (6. Conveying Non-Source Forms.) of the GNU General
* Public License version 3, when you create a Related Module, this
* Related Module is not considered as a part of the work and may be
* distributed under the license agreement of your choice.
* A "Related Module" means a set of sources files including their
* documentation that, without modification of the Source Code, enables
* supplementary functions or services in addition to those offered by
* the Software.
*
* Rudder is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU General Public License
* along with Rudder.  If not, see <http://www.gnu.org/licenses/>.

*
*************************************************************************************
*/

package com.normation.inventory.ldap.core

import com.unboundid.ldap.sdk.DN
import com.normation.ldap.sdk._
import BuildFilter.{EQ,OR}
import com.normation.inventory.domain._
import com.normation.inventory.services.core.ReadOnlySoftwareDAO
import LDAPConstants._
import net.liftweb.common._
import Box._
import com.normation.utils.Control.sequence

class ReadOnlySoftwareDAOImpl(
  inventoryDitService:InventoryDitService,
  ldap:LDAPConnectionProvider[RoLDAPConnection],
  mapper:InventoryMapper
) extends ReadOnlySoftwareDAO {

  private[this] def search(con: RoLDAPConnection, ids: Seq[SoftwareUuid]) = {
    sequence(con.searchOne(inventoryDitService.getSoftwareBaseDN, OR(ids map {x:SoftwareUuid => EQ(A_SOFTWARE_UUID,x.value) }:_*))) { entry =>
      mapper.softwareFromEntry(entry) ?~! s"Error when mapping LDAP entry '${entry.dn}' to a software. Entry details: ${entry}"
    }
  }

  override def getSoftware(ids:Seq[SoftwareUuid]) : Box[Seq[Software]] = {
    if(ids.isEmpty) Full(Seq())
    else for {
      con   <- ldap
      softs <- search(con, ids)
    } yield {
      softs
    }
  }


  /**
   * softwares
   */
  override def getSoftwareByNode(nodeIds: Set[NodeId], status: InventoryStatus): Box[Map[NodeId, Seq[Software]]] = {

    val dit = inventoryDitService.getDit(status)

    for {
      con            <- ldap
      nodeEntries    =  con.searchOne(dit.NODES.dn, BuildFilter.ALL, Seq(A_NODE_UUID, A_SOFTWARE_DN):_*)
      softwareByNode =  (nodeEntries.flatMap { e =>
                          e(A_NODE_UUID).map { id =>
                            (NodeId(id), e.valuesFor(A_SOFTWARE_DN).flatMap(dn => inventoryDitService.getDit(AcceptedInventory).SOFTWARE.SOFT.idFromDN(dn)))
                          }
                        }).toMap
      softwareIds    =  softwareByNode.values.flatten.toSeq
      software       <- search(con, softwareIds)
    } yield {
      softwareByNode.mapValues { ids => software.filter(s => ids.contains(s.id)) }
    }
  }
}
