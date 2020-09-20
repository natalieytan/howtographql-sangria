package com.howtographql.scala.sangria
import DBSchema._
import com.howtographql.scala.sangria.models.Link
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

class DAO(db: Database) {
  def allLinks = db.run(Links.result)

  def getLinks(ids: Seq[Int]) = db.run(
    Links.filter(_.id inSet ids).result
  )}
