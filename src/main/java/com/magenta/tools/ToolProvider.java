package com.magenta.tools;

import com.magenta.context.ContextLimits;
import com.magenta.security.SecurityManager;
import com.magenta.session.SessionId;
import com.magenta.task.TodoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class ToolProvider {
    private static final Logger logger = LoggerFactory.getLogger(ToolProvider.class);

    private final TodoService todoService;
    private final SecurityManager securityManager;
    private final SessionId sessionId;
    private final ContextLimits contextLimits;

    public ToolProvider(TodoService todoService , SecurityManager securityManager, SessionId sessionId, ContextLimits contextLimits) {
        this.todoService = todoService;
        this.securityManager = securityManager;
        this.sessionId = sessionId;
        this.contextLimits = contextLimits;
    }



}
