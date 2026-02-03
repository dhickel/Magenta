package com.magenta.agent;

import com.magenta.session.SessionMeta;
import com.magenta.task.TaskWorkflow;

import java.time.LocalDateTime;
import java.util.*;

/**
 * AgentNetwork manages message routing between agents.
 * Singleton pattern ensures single network instance per application.
 * Note: Session tracking is handled by SessionManager, not here.
 */
public class AgentNetwork {
    private static AgentNetwork instance;

    private final MessageQueue messageQueue;
    private final Set<SessionMeta> registeredAgents = new HashSet<>();

    private AgentNetwork() {
        this.messageQueue = new MessageQueue();
    }

    public static void initialize() {
        if (instance == null) {
            instance = new AgentNetwork();
        }
    }

    public static AgentNetwork getInstance() {
        if (instance == null) {
            throw new IllegalStateException("AgentNetwork not initialized. Call initialize() first.");
        }
        return instance;
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
        return new HashSet<>(registeredAgents);
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
