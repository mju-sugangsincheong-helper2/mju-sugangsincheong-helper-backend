package com.mjusugangsincheonghelper.exchange.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.CycleDetail;
import com.mjusugangsincheonghelper.exchange.dto.MainResponse.RoomSummary;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ExchangeRedisService {

	private final StringRedisTemplate redisTemplate;
	private final ObjectMapper objectMapper = new ObjectMapper();

	private String key(String term, String suffix) {
		return "exchange::" + term + "::" + suffix;
	}

	public void addIntentToFeed(String term, Long intentId, String giveCourseNo, String wantCourseNo, String createdAt) {
		String value;
		try {
			value = objectMapper.writeValueAsString(Map.of(
					"intentId", intentId,
					"give", giveCourseNo,
					"want", wantCourseNo,
					"time", createdAt
			));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize intent feed item", e);
		}
		redisTemplate.opsForZSet().add(key(term, "recent_intents"), value, intentId);
		redisTemplate.opsForZSet().removeRange(key(term, "recent_intents"), 0, -1001);
	}

	public void removeIntentFromFeed(String term, Long intentId) {
		redisTemplate.opsForZSet().remove(key(term, "recent_intents"), intentId);
	}

	public void addGraphEdge(String term, Long memberId, String giveCourseNo, String wantCourseNo) {
		String value = memberId + ":" + giveCourseNo + ":" + wantCourseNo;
		redisTemplate.opsForZSet().add(key(term, "graph::" + giveCourseNo), value, 0);
	}

	public void removeGraphEdge(String term, Long memberId, String giveCourseNo, String wantCourseNo) {
		String value = memberId + ":" + giveCourseNo + ":" + wantCourseNo;
		redisTemplate.opsForZSet().remove(key(term, "graph::" + giveCourseNo), value);
	}

	public void setRoomSummary(String term, RoomSummary summary, List<CycleDetail> cycleDetails) {
		try {
			String summaryJson = objectMapper.writeValueAsString(summary);
			String detailsJson = objectMapper.writeValueAsString(cycleDetails);
			String baseKey = key(term, "rooms");
			redisTemplate.opsForHash().put(baseKey, "summary:" + summary.getRoomId(), summaryJson);
			redisTemplate.opsForHash().put(baseKey, "cycles:" + summary.getRoomId(), detailsJson);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to serialize room summary", e);
		}
	}

	public RoomSummary getRoomSummary(String term, Long roomId) {
		Object json = redisTemplate.opsForHash().get(key(term, "rooms"), "summary:" + roomId);
		if (json == null) {
			return null;
		}
		try {
			return objectMapper.readValue(json.toString(), RoomSummary.class);
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to deserialize room summary", e);
		}
	}

	public List<CycleDetail> getCycleDetails(String term, Long roomId) {
		Object json = redisTemplate.opsForHash().get(key(term, "rooms"), "cycles:" + roomId);
		if (json == null) {
			return Collections.emptyList();
		}
		try {
			return objectMapper.readValue(json.toString(), objectMapper.getTypeFactory().constructCollectionType(List.class, CycleDetail.class));
		} catch (JsonProcessingException e) {
			throw new IllegalStateException("Failed to deserialize cycle details", e);
		}
	}

	public void addRoomToMember(String term, Long memberId, Long roomId) {
		redisTemplate.opsForSet().add(key(term, "member::" + memberId + "::rooms"), String.valueOf(roomId));
	}

	public Set<String> getMemberRooms(String term, Long memberId) {
		return redisTemplate.opsForSet().members(key(term, "member::" + memberId + "::rooms"));
	}

	public void incrementUnread(String term, Long memberId, Long roomId) {
		redisTemplate.opsForHash().increment(key(term, "member::" + memberId + "::unread"), String.valueOf(roomId), 1);
	}

	public void clearUnread(String term, Long memberId, Long roomId) {
		redisTemplate.opsForHash().delete(key(term, "member::" + memberId + "::unread"), String.valueOf(roomId));
	}

	public int getUnreadCount(String term, Long memberId, Long roomId) {
		Object count = redisTemplate.opsForHash().get(key(term, "member::" + memberId + "::unread"), String.valueOf(roomId));
		return count == null ? 0 : Integer.parseInt(count.toString());
	}

	public boolean hasCycleHash(String term, String cycleHash) {
		return Boolean.TRUE.equals(redisTemplate.opsForSet().isMember(key(term, "cycle::" + cycleHash), "1"));
	}

	public void markCycleHash(String term, String cycleHash) {
		redisTemplate.opsForSet().add(key(term, "cycle::" + cycleHash), "1");
	}
}
