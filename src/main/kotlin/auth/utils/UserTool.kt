package auth.utils

import auth.AuthService

fun main(args: Array<String>) {

    if (args.size < 2 || args[0] != "create-user") {
        println("Usage: create-user <username>")
        return
    }

    val username = args[1]
    print("Password: ")
    val password = System.console()?.readPassword() ?: readlnOrNull()?.toCharArray()
    if (password == null) {
        println("No password provided")
        return
    }
    val auth = AuthService()
    val ok = auth.addUser(username, password)
    if (ok) {
        println("User '$username' created and saved to config/users.properties")
    } else {
        println("User '$username' already exists")
    }
}