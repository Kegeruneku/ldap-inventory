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

package com.normation.inventory.ldap.provisioning

import com.normation.inventory.services.provisioning._

import com.normation.ldap.sdk._
import BuildFilter._
import com.normation.inventory.ldap.core._
import LDAPConstants._
import com.normation.inventory.domain._

import scala.collection.mutable.Buffer

import net.liftweb.common.{Box,Empty,Full}

import org.slf4j.LoggerFactory
import NameAndVersionIdFinder._

object NameAndVersionIdFinder {
  val logger = LoggerFactory.getLogger(classOf[NameAndVersionIdFinder])
}


/*
 * Retrieve the id from the Mother Board Id
 */
class NameAndVersionIdFinder(
    val name              : String
  , ldapConnectionProvider: LDAPConnectionProvider[RoLDAPConnection]
  , mapper                : InventoryMapper
  , dit                   : InventoryDit
) extends SoftwareDNFinderAction {

  //the onlyTypes is an AND filter
  override def tryWith(entities:Set[Software]) : Box[MergedSoftware] = {


    val filter = OR(entities.map { entity =>
      //create filter for software name and version
      val nameFilter = entity.name match {
        case None => NOT(HAS(A_NAME))
        case Some(x) => EQ(A_NAME,x)
      }

      val versionFilter = entity.version match {
        case None => NOT(HAS(A_SOFT_VERSION))
        case Some(x) => EQ(A_SOFT_VERSION,x.value)
      }

      AND(nameFilter, versionFilter)
    }.toSeq:_*)


    ldapConnectionProvider.map { con =>
      //get potential entries, and only get the one with a A_SOFTWARE_UUID
      //return the list of A_SOFTWARE_UUID sorted
      val merged = con.searchOne(dit.SOFTWARE.dn, filter, A_SOFTWARE_UUID, A_NAME, A_SOFT_VERSION).flatMap(e => mapper.softwareFromEntry(e) )

      //now merge back
      (MergedSoftware(Set(),Set())/: entities) { case(ms, s) =>
        merged.find { x => x.name == s.name && x.version == s.version } match {
          case None => ms.copy(newSoftware = ms.newSoftware+s)
          case Some(x) => ms.copy(alreadySavedSoftware = ms.alreadySavedSoftware+x)
        }
      }
    }
  }
}

