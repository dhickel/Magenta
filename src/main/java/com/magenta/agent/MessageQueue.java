package com.magenta.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Thread-safe message queue for asynchronous agent-to-agent communication.
 * Uses concurrent collections for safe multi-agent access.
 */
public class MessageQueue {
    // Map of agent alias -> queue of messages
    private final Map<String, Queue<AgentMessage>> queues = new ConcurrentHashMap<>();

    // Broadcast queue shared by all agents
    private final Queue<AgentMessage> broadcastQueue = new ConcurrentLinkedQueue<>();

    /**
     * Send a message to the queue.
     */
    public void send(AgentMessage message) {
        switch (message) {
            case AgentMessage.Broadcast b -> broadcastQueue.add(b);
            case AgentMessage.Direct d ->
                queues.computeIfAbsent(d.to(), k -> new ConcurrentLinkedQueue<>()).add(d);
            case AgentMessage.Delegation del ->
                queues.computeIfAbsent(del.to(), k -> new ConcurrentLinkedQueue<>()).add(del);
        }
    }

    /**
     * Receive all pending messages for an agent (non-blocking).
     */
    public List<AgentMessage> receiveAll(String agentAlias) {
        List<AgentMessage> messages = new ArrayList<>();

        // Get directed messages
        Queue<AgentMessage> queue = queues.get(agentAlias);
        if (queue != null) {
            AgentMessage msg;
            while ((msg = queue.poll()) != null) {
                messages.add(msg);
            }
        }

        // Get broadcast messages (all agents receive)
        AgentMessage broadcast;
        while ((broadcast = broadcastQueue.poll()) != null) {
            messages.add(broadcast);
        }

        return messages;
    }

    /**
     * Check if an agent has pending messages.
     */
    public boolean hasMessages(String agentAlias) {
        Queue<AgentMessage> queue = queues.get(agentAlias);
        return (queue != null && !queue.isEmpty()) || !broadcastQueue.isEmpty();
    }

    /**
     * Get the count of pending messages for an agent.
     */
    public int getMessageCount(String agentAlias) {
        Queue<AgentMessage> queue = queues.get(agentAlias);
        int count = queue != null ? queue.size() : 0;
        count += broadcastQueue.size();
        return count;
    }

    /**
     * Clear all messages for an agent.
     */
    public void clearMessages(String agentAlias) {
        queues.remove(agentAlias);
    }
}
