package com.howtographql.scala.sangria
import DBSchema._
import com.howtographql.scala.sangria.models.{Link, User}
import slick.jdbc.H2Profile.api._

import scala.concurrent.Future

class DAO(db: Database) {
  def allLinks = db.run(Links.result)

  def getUsers(ids: Seq[Int]): Future[Seq[User]] = {
    db.run(
      Users.filter(_.id inSet ids).result
    )
  }

  def getLinks(ids: Seq[Int]) = db.run(
    Links.filter(_.id inSet ids).result
  )
}
