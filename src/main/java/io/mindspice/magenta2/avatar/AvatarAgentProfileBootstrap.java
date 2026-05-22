package io.mindspice.magenta2.avatar;

import java.util.List;

import io.mindspice.magenta2.ai.orchestration.agents.AgentProfile;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileRepository;
import io.mindspice.magenta2.ai.orchestration.agents.AgentProfileStatus;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
public class AvatarAgentProfileBootstrap {
    public static final String AVATAR_AGENT_ID = "avatar";
    public static final String AVATAR_AGENT_NAME = "Avatar";

    private final AgentProfileRepository repository;

    public AvatarAgentProfileBootstrap(AgentProfileRepository repository) {
        this.repository = repository;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reserveAvatarProfile() {
        AgentProfile byId = repository.findById(AVATAR_AGENT_ID).orElse(null);
        AgentProfile byName = repository.findByName(AVATAR_AGENT_NAME).orElse(null);

        if (byId != null && !AVATAR_AGENT_NAME.equals(byId.name())) {
            throw new IllegalStateException("Agent id 'avatar' is already reserved by profile name: " + byId.name());
        }
        if (byName != null && !AVATAR_AGENT_ID.equals(byName.id())) {
            throw new IllegalStateException("Agent name 'Avatar' is already reserved by profile id: " + byName.id());
        }
        if (byId != null) {
            return;
        }

        repository.save(new AgentProfile(
            AVATAR_AGENT_ID,
            AVATAR_AGENT_NAME,
            AgentProfileStatus.DISABLED,
            null,
            "You are Avatar, Magenta's personal dashboard assistant. Stay dormant until Avatar chat behavior is enabled.",
            List.of(),
            List.of(),
            false,
            null,
            null
        ));
    }
}
