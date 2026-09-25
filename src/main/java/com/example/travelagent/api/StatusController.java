package com.example.travelagent.api;

import java.time.Instant;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class StatusController {

	@GetMapping("/status")
	public Status status() {
		return new Status("travel-agent", "UP", Instant.now());
	}

	public record Status(String name, String status, Instant timestamp) {
	}

}
