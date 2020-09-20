package com.howtographql.scala.sangria

// Context is an object flowing across whole execution
// Main resposibility = provide data and utils needed to fufil the query
// Can put DAO on context, so all queries will have access to database
// Can also put authentication data here
case class MyContext(dao: DAO) {

}
