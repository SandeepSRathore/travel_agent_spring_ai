package com.example.travelagent.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Switches for the chat advisors, bound from {@code travel-agent.advisors.*}.
 */
@ConfigurationProperties("travel-agent.advisors")
public record AdvisorProperties(@DefaultValue("60s") Duration chatTimeout, @DefaultValue Moderation moderation,
		@DefaultValue PiiRedaction piiRedaction) {

	public record Moderation(@DefaultValue("true") boolean enabled, @DefaultValue("true") boolean failOpen,
			@DefaultValue("3s") Duration timeout) {
	}

	public record PiiRedaction(@DefaultValue("true") boolean enabled) {
	}

}
