import "types/types.ql"
import "util/date.ql"
import "util/list.ql"
import "util/date.ql"
import "music/play_redirect.ql"
import "string.ql"
import "background.ql"
import "breakpoint.ql"

/**
 * Prints a string representation of the given object to the console, followed by a newline character.
 *
 * @param obj The object to print, of any type.
 */
native fun print(obj)

/**
 * Reads a line of input from the console.
 *
 * @returns The line of input read from the console
 */
native fun readLine()

/**
 * Prints a given string to the console, followed by a newline character. The string is formatted with `string.format`
 * using the parameters provided.
 *
 * @param obj The object to print, of any type.
 */
fun printf(obj, formatParams) {
    print(obj.format(formatParams))
}

/**
 * Gets the environment variable of a given name. This will return a string, or throw an error if it can't be found.
 *
 * @param[@type string] name The name of the environment variable to get
 * @returns The value of the environment variable
 */
native fun getEnv(name)

/**
 * Gets the environment variable of a given name. If the value is not found, a default value is returned.
 *
 * @param[@type string] name The name of the environment variable to get
 * @param[@type string] defaultValue The default value to return if the environment variable is not found
 * @returns The value of the environment variable
 */
fun getEnv(name, default) {
    if (hasEnv(name)) {
        return getEnv(name)
    } else {
        return default
    }
}

/**
 * Checks if an environment variable is set.
 *
 * @param[@type string] name The name of the environment variable to check for
 * @returns[@type boolean] true if the environment variable is set, false if otherwise
 */
native fun hasEnv(name)

/**
 * Gets the current time in milliseconds
 *
 * @returns[@type int] The current time in milliseconds
 */
native fun currentTimeMillis()

/**
 * Exits the program with the given exit code.
 *
 * @param[@type int] code The exit code to exit with
 */
native fun exit(code)
