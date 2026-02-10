package com.magenta.manager;

import com.magenta.agent.AgentMessage;
import com.magenta.agent.MessageQueue;
import com.magenta.session.SessionMeta;
import com.magenta.task.TaskWorkflow;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * AgentNetwork manages message routing between agents.
 * Thread-safe: uses ConcurrentHashMap.newKeySet() for agent registration.
 * Note: Session tracking is handled by SessionManager, not here.
 */
public class AgentNetwork {

    private final MessageQueue messageQueue;
    private final Set<SessionMeta> registeredAgents = ConcurrentHashMap.newKeySet();

    public AgentNetwork() {
        this.messageQueue = new MessageQueue();
    }

    // === Agent Registration ===

    /**
     * Register an agent with the network (for discovery/listing).
     */
    public void registerAgent(SessionMeta meta) {
        registeredAgents.add(meta);
    }

    /**
     * Unregister an agent from the network.
     */
    public void unregisterAgent(SessionMeta meta) {
        registeredAgents.remove(meta);
        messageQueue.clearMessages(meta.sessionAlias().value());
    }

    /**
     * List all registered agents in the network.
     */
    public Set<SessionMeta> listRegisteredAgents() {
        return Set.copyOf(registeredAgents);
    }

    // === Messaging ===

    /**
     * Send a direct message to another agent.
     */
    public void sendMessage(String from, String to, String message) {
        AgentMessage.Direct msg = new AgentMessage.Direct(from, to, message, LocalDateTime.now());
        messageQueue.send(msg);
    }

    /**
     * Broadcast a message to all agents.
     */
    public void broadcast(String from, String message) {
        AgentMessage.Broadcast msg = new AgentMessage.Broadcast(from, message, LocalDateTime.now());
        messageQueue.send(msg);
    }

    /**
     * Delegate a task to another agent.
     */
    public void delegateTask(String from, String to, TaskWorkflow task) {
        AgentMessage.Delegation msg = new AgentMessage.Delegation(from, to, task, LocalDateTime.now());
        messageQueue.send(msg);
    }

    /**
     * Get all pending messages for an agent.
     */
    public List<AgentMessage> getMessages(String agentAlias) {
        return messageQueue.receiveAll(agentAlias);
    }

    /**
     * Check if an agent has pending messages.
     */
    public boolean hasMessages(String agentAlias) {
        return messageQueue.hasMessages(agentAlias);
    }

    /**
     * Get the count of pending messages for an agent.
     */
    public int getMessageCount(String agentAlias) {
        return messageQueue.getMessageCount(agentAlias);
    }

    /**
     * Get the underlying message queue (for direct tool access).
     */
    public MessageQueue messageQueue() {
        return messageQueue;
    }
}
