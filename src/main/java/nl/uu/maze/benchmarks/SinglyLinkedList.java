package nl.uu.maze.benchmarks;

/**
 * Benchmark class the implements a singly linked list.
 * <p>
 * This class introduces object references and pointer manipulation.
 * This primarily tests the search strategy's ability to handle things like
 * aliasing (which causes state splitting).
 * For instance, the add method checks if some value is already in the list,
 * with reference equality in case of non-primitive types.
 * Thus, for a search strategy to reach that point, it would need to call the
 * add method with a value that is already in the list.
 */
public class SinglyLinkedList<T> {
    private static class Node<T> {
        T value;
        Node<T> next;

        Node(T v) {
            value = v;
        }
    }

    public SinglyLinkedList(T[] values) {
        for (T value : values) {
            Node<T> node = new Node<>(value);
            node.next = head;
            head = node;
        }
    }

    private Node<T> head;

    public boolean add(T value) {
        // Check if the value is a reference to an existing node's value (e.g., a cycle)
        if (head != null) {
            Node<T> curr = head;
            while (curr != null) {
                if (curr.value == value) {
                    return false; // Value already exists in the list
                }
                curr = curr.next;
            }
        }

        Node<T> node = new Node<>(value);
        node.next = head;
        head = node;
        return true;
    }

    public boolean delete(T value) {
        if (head == null)
            return false;
        if (head.value == value) {
            head = head.next;
            return true;
        }
        Node<T> curr = head;
        while (curr.next != null) {
            if (curr.next.value == value) {
                curr.next = curr.next.next;
                return true;
            }
            curr = curr.next;
        }
        return false;
    }

    public void reverse() {
        Node<T> prev = null;
        Node<T> curr = head;
        while (curr != null) {
            Node<T> next = curr.next;
            curr.next = prev;
            prev = curr;
            curr = next;
        }
        head = prev;
    }

    public boolean hasCycle() {
        Node<T> slow = head, fast = head;
        while (fast != null && fast.next != null) {
            slow = slow.next;
            fast = fast.next.next;
            if (slow == fast)
                return true;
        }
        return false;
    }

    public boolean hasDuplicates() {
        Node<T> curr = head;
        while (curr != null) {
            Node<T> innerCurr = curr.next;
            while (innerCurr != null) {
                if (curr.value == innerCurr.value) {
                    return true;
                }
                innerCurr = innerCurr.next;
            }
            curr = curr.next;
        }
        return false;
    }
}
