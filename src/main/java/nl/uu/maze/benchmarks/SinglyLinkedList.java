package nl.uu.maze.benchmarks;

/**
 * Benchmark class the implements a singly linked list.
 * <p>
 * This class introduces object references and pointer manipulation.
 * This primarily tests the search strategy's ability to handle things like
 * aliasing (which causes state splitting).
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
            add(value);
        }
    }

    private Node<T> head;

    public void add(T value) {
        Node<T> node = new Node<>(value);
        node.next = head;
        head = node;
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
}
