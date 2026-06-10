package com.mjusugangsincheonghelper.exchange.service;

import com.mjusugangsincheonghelper.database.entity.ExchangeIntentEntity;
import com.mjusugangsincheonghelper.database.repository.ExchangeIntentRepository;
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
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ExchangeCycleDetector {

	private final ExchangeIntentRepository intentRepository;

	public List<List<ExchangeIntentEntity>> detectCycles(String term, ExchangeIntentEntity newIntent) {
		List<ExchangeIntentEntity> allActive = intentRepository.findByTermAndIsDeletedFalse(term);

		Map<String, List<ExchangeIntentEntity>> adjacency = new HashMap<>();
		for (ExchangeIntentEntity intent : allActive) {
			adjacency.computeIfAbsent(intent.getGiveCourseNo(), k -> new ArrayList<>()).add(intent);
		}

		List<List<ExchangeIntentEntity>> cycles = new ArrayList<>();
		Set<String> visited = new HashSet<>();
		List<ExchangeIntentEntity> path = new ArrayList<>();

		dfs(newIntent.getWantCourseNo(), newIntent.getGiveCourseNo(), adjacency, visited, path, cycles);

		return cycles;
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
