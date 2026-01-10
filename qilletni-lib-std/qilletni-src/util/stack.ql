/**
 * A Stack implementation, can hold any type.
 */
entity Stack {

    any[] _stack = []
    
    /**
     * Creates a new stack from a list of elements.
     *
     * @param[@type list] list A list of elements
     * @returns[@type core.Stack] A new stack with the given elements
     */
    static fun fromList(list) {
        Stack stack = new Stack()
        stack._stack = list
        return stack
    }
    
    /**
     * Pushes an element onto the stack.
     *
     * @param element The element to push onto the stack
     */
    fun push(element) {
        _stack.add(element)
    }
    
    /**
     * Pops an element from the stack.
     *
     * @returns The element popped from the stack
     * @errors If the stack is empty
     */
    fun pop() {
        return _stack.remove(_stack.size() - 1)
    }

    /**
     * Peeks at the top element of the stack.
     *
     * @returns The top element of the stack
     * @errors If the stack is empty
     */
    fun peek() {
        return _stack[_stack.size() - 1]
    }
    
    /**
     * Checks if the stack is empty.
     *
     * @returns[@type boolean] true if the stack is empty, false if otherwise
     */
    fun isEmpty() {
        return _stack.isEmpty()
    }
    
    /**
     * Gets the size of the stack.
     *
     * @returns[@type int] The size of the stack
     */
    fun size() {
        return _stack.size()
    }
    
    /**
     * Converts the stack to a list.
     *
     * @returns[@type list] A list of all elements in the stack
     */
    fun toList() {
        return _stack
    }
}