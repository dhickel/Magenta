package com.magenta.session;

import com.magenta.agent.NetworkId;

public record SessionMeta(
        SessionId sessionId,
        SessionAlias sessionAlias,
        NetworkId networkId
) { }
