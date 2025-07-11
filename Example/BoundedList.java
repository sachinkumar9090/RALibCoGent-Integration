import java.util.LinkedList;

public class BoundedList {
    private LinkedList<Object> list;
    private int maxSize;
    private static final int DEFAULT_SIZE = 3;

    public BoundedList() {
        maxSize = DEFAULT_SIZE;
        list = new LinkedList<Object>();
    }

    public void push(Object e) {
        if (maxSize > list.size()) {
            list.push(e);
        }
    }

    public Object pop() {
        return list.pop();
    }

    public boolean contains(Object e) {
        return list.contains(e);
    }

    public boolean isEmpty() {
        return list.size() == 0;
    }

    public boolean isFull() {
        return list.size() == maxSize;
    }
}

