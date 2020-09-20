package com.howtographql.scala.sangria

import com.howtographql.scala.sangria.models.Link
import sangria.schema._


// What we are able to query for
// Interprets how data fetched and which data source it could use

object GraphQLSchema {

  // Definition of ObectType for Link Class
  val LinkType = ObjectType[Unit, Link](
    "Link", // name of schema
    fields[Unit, Link](
      Field("id", IntType, resolve = _.value.id),
      Field("url", StringType, resolve = _.value.url),
      Field("description", StringType, resolve = _.value.description)
    )
    // fields = name of fields, function you want to expose
    // every field has to contain a resolve function
    // resolve = how to retrieve data for field
  )

  //QueryType is a top level object of schema

  val Id = Argument("id", IntType)
  val Ids = Argument("ids", ListInputType(IntType))

  val QueryType = ObjectType(
    "Query",
    fields[MyContext, Unit](
      Field("allLinks",
        ListType(LinkType),
        resolve = c => c.ctx.dao.allLinks
      ),
      Field("link",
        OptionType(LinkType), // expected output type
        arguments = Id :: Nil,  // Arguments = list of expected arguments defined by name and type, // expecting id argument of type int
        resolve = c => c.ctx.dao.getLink(c.arg(Id))
      ),
      Field("links",
        ListType(LinkType),
        arguments = Ids :: Nil, // InputType are used to passed incoming data, ObjectType 9mostly) for outgoing data
        resolve = c => c.ctx.dao.getLinks(c.arg(Ids)) // Pass argument to resolver
      )
    )
  )

  val SchemaDefinition = Schema(QueryType)
}
