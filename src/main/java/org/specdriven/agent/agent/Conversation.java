package org.specdriven.agent.agent;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Thread-safe container for managing a conversation's message history.
 */
public class Conversation {

    private final CopyOnWriteArrayList<Message> messages = new CopyOnWriteArrayList<>();

    /**
     * Appends a message to the conversation history.
     */
    public void append(Message message) {
        messages.add(message);
    }

    /**
     * Returns the message at the given index.
     *
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public Message get(int index) {
        return messages.get(index);
    }

    /**
     * Returns an unmodifiable view of all messages in insertion order.
     */
    public List<Message> history() {
        return Collections.unmodifiableList(messages);
    }

    /**
     * Returns the number of messages in the conversation.
     */
    public int size() {
        return messages.size();
    }

    /**
     * Removes all messages from the conversation.
     */
    public void clear() {
        messages.clear();
    }
}
