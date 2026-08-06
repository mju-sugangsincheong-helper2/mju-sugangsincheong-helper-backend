package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
import com.mjusugangsincheonghelper.database.repository.ExchangeRoomRepository;
import com.mjusugangsincheonghelper.exchange.dto.CycleDetectionMessage;
import com.mjusugangsincheonghelper.global.config.PgmqProperties;
import com.mjusugangsincheonghelper.global.config.PgmqService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExchangeCycleDetector {

	private final PgmqService pgmqService;
	private final ExchangeIntentRepository intentRepository;
	private final ExchangeRoomRepository roomRepository;
	private final ExchangeRoomCreationService roomCreationService;
	private final PgmqProperties pgmqProperties;

	public void enqueueCycleDetection(CycleDetectionMessage message) {
		pgmqService.send(pgmqProperties.getCycleDetection().getQueueName(), message);
	}

	public void detectCyclesAndCreateRooms(String term, Long intentId, Long memberId, String giveCourseNo, String wantCourseNo) {
		ExchangeIntentEntity triggerIntent = intentRepository.findById(new ExchangeIntentEntity.ExchangeIntentId(term, intentId)).orElse(null);

		if (triggerIntent == null || triggerIntent.isDeleted()) {
			log.debug("Intent was deleted before cycle detection, skipping. intentId={}", intentId);
			return;
		}

		List<ExchangeIntentEntity> allActive = intentRepository.findByTermAndIsDeletedFalse(term);

		Map<String, List<ExchangeIntentEntity>> adjacency = new HashMap<>();
		for (ExchangeIntentEntity intent : allActive) {
			adjacency.computeIfAbsent(intent.getGiveCourseNo(), k -> new ArrayList<>()).add(intent);
		}

		List<List<ExchangeIntentEntity>> cycles = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		List<ExchangeIntentEntity> path = new ArrayList<>();
		path.add(triggerIntent);

		dfs(wantCourseNo, giveCourseNo, adjacency, visited, path, cycles);

		for (List<ExchangeIntentEntity> cycle : cycles) {
			String cycleHash = computeCycleHash(cycle);
			if (roomRepository.findByTermAndCycleHash(term, cycleHash).isEmpty()) {
				roomCreationService.createRoom(term, cycle, cycleHash);
			} else {
				log.debug("Cycle already matched, skipping room creation. term={}, cycleHash={}", term, cycleHash);
			}
		}
	}

	private void dfs(
			String current,
			String target,
			Map<String, List<ExchangeIntentEntity>> adjacency,
			Set<String> visited,
			List<ExchangeIntentEntity> path,
			List<List<ExchangeIntentEntity>> cycles
	) {
		if (!visited.add(current)) {
			return;
		}

		List<ExchangeIntentEntity> edges = adjacency.get(current);
		if (edges == null) {
			visited.remove(current);
			return;
		}

		for (ExchangeIntentEntity edge : edges) {
			path.add(edge);
			String next = edge.getWantCourseNo();

			if (next.equals(target)) {
				cycles.add(new ArrayList<>(path));
			} else {
				dfs(next, target, adjacency, visited, path, cycles);
			}

			path.remove(path.size() - 1);
		}

		visited.remove(current);
	}

	public String computeCycleHash(List<ExchangeIntentEntity> cycle) {
		String sortedIds = cycle.stream()
				.map(ExchangeIntentEntity::getId)
				.sorted()
				.map(String::valueOf)
				.collect(Collectors.joining(","));

		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(sortedIds.getBytes(StandardCharsets.UTF_8));
			StringBuilder hexString = new StringBuilder();
			for (byte b : hash) {
				String hex = Integer.toHexString(0xff & b);
				if (hex.length() == 1) {
					hexString.append('0');
				}
				hexString.append(hex);
			}
			return hexString.toString();
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException("SHA-256 algorithm not available", e);
		}
	}
}
