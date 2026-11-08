package dev.qilletni.impl.debug;

/**
 * A variable for display. Building one never runs Qilletni code.
 *
 * @param name               The variable name
 * @param value              One line of display text
 * @param type               The Qilletni type as spelled in source, such as {@code int[]} or an entity name
 * @param evaluateName       An expression that evaluates back to this value
 * @param variablesReference The reference to request its children with, or 0 if it has none
 * @param indexedVariables   The number of list items among its children, or 0 if it isn't a list
 * @param property           If it is an entity field
 */
public record DebugVariable(String name, String value, String type, String evaluateName, int variablesReference, int indexedVariables, boolean property) {
}
