package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.MultigameRoundEntity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자(System) 전용 조회/정리 단일 리포지터리.
 * 일반 플로우용 쿼리는 각 도메인 리포지터리에 두고,
 * 관리자 통계·정리용 쿼리는 이 파일에만 추가한다.
 */
@Repository
@RequiredArgsConstructor
public class SystemRepository {

	private final EntityManager em;

	// ---- Member / Notice / Device counts ----

	public List<Object[]> countMembersByRole() {
		return em.createQuery("select m.role, count(m) from Member m group by m.role", Object[].class)
				.getResultList();
	}

	public long countMembersCreatedSince(Instant since) {
		return em.createQuery("select count(m) from Member m where m.createdAt >= :since", Long.class)
				.setParameter("since", since)
				.getSingleResult();
	}

	public long countNotices() {
		return em.createQuery("select count(n) from NoticeEntity n", Long.class)
				.getSingleResult();
	}

	public long countDevices() {
		return em.createQuery("select count(d) from MemberDevice d", Long.class)
				.getSingleResult();
	}

	public long countActiveDevicesSince(Instant since) {
		return em.createQuery("select count(d) from MemberDevice d where d.lastAccessedAt >= :since", Long.class)
				.setParameter("since", since)
				.getSingleResult();
	}

	public List<Object[]> countDevicesByOs() {
		return em.createQuery(
						"select d.platformJsOs, count(d) from MemberDevice d "
								+ "where d.platformJsOs is not null and d.platformJsOs <> '' "
								+ "group by d.platformJsOs order by count(d) desc",
						Object[].class)
				.getResultList();
	}

	public List<Object[]> countDevicesByBrowser() {
		return em.createQuery(
						"select d.platformJsName, count(d) from MemberDevice d "
								+ "where d.platformJsName is not null and d.platformJsName <> '' "
								+ "group by d.platformJsName order by count(d) desc",
						Object[].class)
				.getResultList();
	}

	@Transactional
	public int deleteExpiredDevices(Instant now) {
		return em.createQuery("delete from MemberDevice d where d.expiresAt is not null and d.expiresAt < :now")
				.setParameter("now", now)
				.executeUpdate();
	}

	// ---- Course ----

	public long countCourses() {
		return em.createQuery("select count(c) from CourseEntity c", Long.class)
				.getSingleResult();
	}

	public List<Object[]> countCoursesByTerm() {
		return em.createQuery("SELECT c.term, count(c) FROM CourseEntity c GROUP BY c.term ORDER BY c.term DESC",
						Object[].class)
				.getResultList();
	}

	public long countDistinctCourseTerms() {
		return em.createQuery("SELECT count(DISTINCT c.term) FROM CourseEntity c", Long.class)
				.getSingleResult();
	}

	// ---- Exchange ----

	public long countExchangeIntents(String term) {
		return em.createQuery(
						"select count(e) from ExchangeIntentEntity e where e.term = :term and e.isDeleted = false",
						Long.class)
				.setParameter("term", term)
				.getSingleResult();
	}

	public long countExchangeRoomsByStatus(String term, String status) {
		return em.createQuery(
						"select count(r) from ExchangeRoomEntity r where r.term = :term and r.status = :status",
						Long.class)
				.setParameter("term", term)
				.setParameter("status", status)
				.getSingleResult();
	}

	public List<Object[]> countExchangeRoomsGroupByStatus(String term) {
		return em.createQuery("SELECT r.status, COUNT(r) FROM ExchangeRoomEntity r WHERE r.term = :term GROUP BY r.status",
						Object[].class)
				.setParameter("term", term)
				.getResultList();
	}

	public long countMatchedIntents(String term) {
		return em.createQuery(
						"SELECT COUNT(DISTINCT ri.intentId) FROM ExchangeRoomIntentEntity ri "
								+ "WHERE ri.term = :term AND ri.isDeleted = false",
						Long.class)
				.setParameter("term", term)
				.getSingleResult();
	}

	public long countExchangeMessages(String term) {
		return em.createQuery("select count(m) from ExchangeRoomMessageEntity m where m.term = :term", Long.class)
				.setParameter("term", term)
				.getSingleResult();
	}

	// ---- SingleGame ----

	public long countSingleGames() {
		return em.createQuery("select count(s) from SingleGameEntity s", Long.class)
				.getSingleResult();
	}

	public long countCompletedSingleGames() {
		return em.createQuery("select count(s) from SingleGameEntity s where s.isCompleted = true", Long.class)
				.getSingleResult();
	}

	public long countCompletedSingleGamesSince(Instant since) {
		return em.createQuery(
						"select count(s) from SingleGameEntity s where s.isCompleted = true and s.createdAt >= :since",
						Long.class)
				.setParameter("since", since)
				.getSingleResult();
	}

	public Double avgCompletedSingleGameTTotal() {
		return em.createQuery("SELECT AVG(s.tTotal) FROM SingleGameEntity s WHERE s.isCompleted = true", Double.class)
				.getSingleResult();
	}

	public Integer minCompletedSingleGameTTotal() {
		return em.createQuery("SELECT MIN(s.tTotal) FROM SingleGameEntity s WHERE s.isCompleted = true", Integer.class)
				.getSingleResult();
	}

	public List<Object[]> aggregateSingleGamesByTotalCourses() {
		return em.createQuery("""
						SELECT s.totalCourses,
						       COUNT(s),
						       SUM(CASE WHEN s.isCompleted = true THEN 1 ELSE 0 END),
						       AVG(CASE WHEN s.isCompleted = true THEN s.tTotal END),
						       MIN(CASE WHEN s.isCompleted = true THEN s.tTotal END)
						FROM SingleGameEntity s
						GROUP BY s.totalCourses
						ORDER BY s.totalCourses
						""", Object[].class)
				.getResultList();
	}

	// ---- Multigame ----

	public long countNonEmptyRounds() {
		return em.createQuery("select count(r) from MultigameRoundEntity r where r.participantCount > 0", Long.class)
				.getSingleResult();
	}

	public Optional<Integer> findMaxRoundParticipants() {
		Integer max = em.createQuery("SELECT MAX(r.participantCount) FROM MultigameRoundEntity r", Integer.class)
				.getSingleResult();
		return Optional.ofNullable(max);
	}

	public List<MultigameRoundEntity> findRecentRounds(int limit) {
		return em.createQuery("SELECT r FROM MultigameRoundEntity r ORDER BY r.startTime DESC",
						MultigameRoundEntity.class)
				.setMaxResults(limit)
				.getResultList();
	}

	@SuppressWarnings("unchecked")
	public List<Object[]> countRoundsByHour() {
		return em.createNativeQuery("""
				SELECT SUBSTRING(start_time FROM 9 FOR 2)::int AS hour, COUNT(*)::bigint AS cnt
				FROM multigame_round
				WHERE participant_count > 0
				GROUP BY hour
				ORDER BY hour
				""").getResultList();
	}

	@SuppressWarnings("unchecked")
	public List<Object[]> countRoundsByDayOfWeek() {
		return em.createNativeQuery("""
				SELECT EXTRACT(ISODOW FROM TO_TIMESTAMP(start_time, 'YYYYMMDDHH24MISS'))::int AS dow, COUNT(*)::bigint AS cnt
				FROM multigame_round
				WHERE participant_count > 0
				GROUP BY dow
				ORDER BY dow
				""").getResultList();
	}

	@SuppressWarnings("unchecked")
	public List<Object[]> countRoundsByDaySince(String from) {
		return em.createNativeQuery("""
				SELECT TO_CHAR(TO_TIMESTAMP(start_time, 'YYYYMMDDHH24MISS'), 'YYYY-MM-DD') AS day, COUNT(*)::bigint AS cnt
				FROM multigame_round
				WHERE participant_count > 0 AND start_time >= :from
				GROUP BY day
				ORDER BY day
				""").setParameter("from", from).getResultList();
	}

	public List<Object[]> aggregateMultigameOverall() {
		return em.createQuery("""
						SELECT SUM(CASE WHEN member.status = 'SUCCESS' THEN 1 ELSE 0 END),
						       SUM(CASE WHEN member.status <> 'SUCCESS' THEN 1 ELSE 0 END)
						FROM MultigameRoundMemberEntity member
						""", Object[].class)
				.getResultList();
	}

	public List<Object[]> aggregateMultigameByStartTimes(Collection<String> startTimes) {
		return em.createQuery("""
						SELECT member.startTime,
						       SUM(CASE WHEN member.status = 'SUCCESS' THEN 1 ELSE 0 END),
						       SUM(CASE WHEN member.status <> 'SUCCESS' THEN 1 ELSE 0 END)
						FROM MultigameRoundMemberEntity member
						WHERE member.startTime IN :startTimes
						GROUP BY member.startTime
						""", Object[].class)
				.setParameter("startTimes", startTimes)
				.getResultList();
	}
}
