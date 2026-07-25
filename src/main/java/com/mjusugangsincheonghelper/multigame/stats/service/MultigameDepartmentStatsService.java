package com.mjusugangsincheonghelper.multigame.stats.service;

import com.mjusugangsincheonghelper.database.entity.Member;
import com.mjusugangsincheonghelper.database.repository.MemberRepository;
import com.mjusugangsincheonghelper.database.repository.MultigameResultDetailRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import com.mjusugangsincheonghelper.multigame.stats.dto.DepartmentParticipationStatsResponse;
import com.mjusugangsincheonghelper.multigame.stats.dto.DepartmentParticipationStatsResponse.DepartmentRanking;
import com.mjusugangsincheonghelper.multigame.stats.dto.DepartmentParticipationStatsResponse.MyDepartmentInfo;
import com.mjusugangsincheonghelper.multigame.stats.dto.DepartmentSuccessRateStatsResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MultigameDepartmentStatsService {

	private static final int TOP_N = 10;

	private final MultigameResultDetailRepository resultDetailRepository;
	private final MemberRepository memberRepository;

	@Cacheable(cacheNames = "multigame-department-participation", key = "'all'")
	public DepartmentParticipationStatsResponse getParticipationStats(Long memberId) {
		List<Object[]> statsRaw = resultDetailRepository.findDepartmentParticipationStats();

		List<DepartmentRanking> rankings = new ArrayList<>();
		for (int i = 0; i < Math.min(TOP_N, statsRaw.size()); i++) {
			Object[] row = statsRaw.get(i);
			rankings.add(DepartmentRanking.builder()
					.rank(i + 1)
					.department((String) row[0])
					.participationCount(((Number) row[1]).longValue())
					.build());
		}

		MyDepartmentInfo myDepartment = getMyParticipationInfo(memberId, statsRaw);

		return DepartmentParticipationStatsResponse.builder()
				.rankings(rankings)
				.myDepartment(myDepartment)
				.build();
	}

	@Cacheable(cacheNames = "multigame-department-success-rate", key = "'all'")
	public DepartmentSuccessRateStatsResponse getSuccessRateStats(Long memberId) {
		List<Object[]> statsRaw = resultDetailRepository.findDepartmentSuccessRateStats();

		List<DepartmentSuccessRateStatsResponse.DepartmentRanking> rankings = new ArrayList<>();
		for (int i = 0; i < Math.min(TOP_N, statsRaw.size()); i++) {
			Object[] row = statsRaw.get(i);
			long totalCount = ((Number) row[1]).longValue();
			long successCount = ((Number) row[2]).longValue();
			double successRate = totalCount > 0 ? (double) successCount / totalCount * 100 : 0.0;
			rankings.add(DepartmentSuccessRateStatsResponse.DepartmentRanking.builder()
					.rank(i + 1)
					.department((String) row[0])
					.totalCount(totalCount)
					.successCount(successCount)
					.successRate(successRate)
					.build());
		}

		DepartmentSuccessRateStatsResponse.MyDepartmentInfo myDepartment = getMySuccessRateInfo(memberId, statsRaw);

		return DepartmentSuccessRateStatsResponse.builder()
				.rankings(rankings)
				.myDepartment(myDepartment)
				.build();
	}

	private MyDepartmentInfo getMyParticipationInfo(Long memberId, List<Object[]> statsRaw) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		String myDepartmentName = member.getDepartment();
		if (myDepartmentName == null) {
			return null;
		}

		for (int i = 0; i < statsRaw.size(); i++) {
			Object[] row = statsRaw.get(i);
			String department = (String) row[0];
			if (myDepartmentName.equals(department)) {
				return MyDepartmentInfo.builder()
						.department(myDepartmentName)
						.participationCount(((Number) row[1]).longValue())
						.rank(i + 1)
						.build();
			}
		}

		return MyDepartmentInfo.builder()
				.department(myDepartmentName)
				.participationCount(0)
				.rank(statsRaw.size() + 1)
				.build();
	}

	private DepartmentSuccessRateStatsResponse.MyDepartmentInfo getMySuccessRateInfo(Long memberId, List<Object[]> statsRaw) {
		Member member = memberRepository.findById(memberId)
				.orElseThrow(() -> new BaseException(ErrorCode.AUTH_MEMBER_NOT_FOUND));

		String myDepartmentName = member.getDepartment();
		if (myDepartmentName == null) {
			return null;
		}

		for (int i = 0; i < statsRaw.size(); i++) {
			Object[] row = statsRaw.get(i);
			String department = (String) row[0];
			if (myDepartmentName.equals(department)) {
				long totalCount = ((Number) row[1]).longValue();
				long successCount = ((Number) row[2]).longValue();
				double successRate = totalCount > 0 ? (double) successCount / totalCount * 100 : 0.0;
				return DepartmentSuccessRateStatsResponse.MyDepartmentInfo.builder()
						.department(myDepartmentName)
						.totalCount(totalCount)
						.successCount(successCount)
						.successRate(successRate)
						.rank(i + 1)
						.build();
			}
		}

		return DepartmentSuccessRateStatsResponse.MyDepartmentInfo.builder()
				.department(myDepartmentName)
				.totalCount(0)
				.successCount(0)
				.successRate(0.0)
				.rank(statsRaw.size() + 1)
				.build();
	}
}
