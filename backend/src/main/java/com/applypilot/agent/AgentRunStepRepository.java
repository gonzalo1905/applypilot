package com.applypilot.agent;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentRunStepRepository extends JpaRepository<AgentRunStep, UUID> {

	List<AgentRunStep> findByRunIdOrderByStepOrder(UUID runId);

}
