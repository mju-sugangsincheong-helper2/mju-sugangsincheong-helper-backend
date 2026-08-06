package com.mjusugangsincheonghelper.course.service;

import com.mjusugangsincheonghelper.course.dto.CourseDepartmentResponse;
import com.mjusugangsincheonghelper.course.dto.CourseSectionDeleteResponse;
import com.mjusugangsincheonghelper.course.dto.CourseSectionImportRequest;
import com.mjusugangsincheonghelper.course.dto.CourseSectionImportResponse;
import com.mjusugangsincheonghelper.course.dto.CourseSectionResponse;
import com.mjusugangsincheonghelper.database.entity.CourseEntity;
import com.mjusugangsincheonghelper.database.repository.CourseRepository;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

	private static final int MAX_TERM_FALLBACKS = 20;
	private static final Map<String, String> DAY_CODE_TO_CHAR = Map.of(
			"1", "월", "2", "화", "3", "수", "4", "목", "5", "금", "6", "토"
	);

	private final CourseRepository courseRepository;

	@Transactional
	public CourseSectionImportResponse importSections(List<CourseSectionImportRequest> items) {
		List<CourseEntity> entities = items.stream()
				.map(this::toEntity)
				.toList();
		courseRepository.saveAll(entities);

		List<String> terms = entities.stream()
				.map(CourseEntity::getTerm)
				.distinct()
				.sorted()
				.toList();

		log.info("Imported course sections. count={}, terms={}", entities.size(), terms);

		return CourseSectionImportResponse.builder()
				.importedCount(entities.size())
				.terms(terms)
				.build();
	}

	@Transactional
	public CourseSectionDeleteResponse deleteSectionsByTerm(String term) {
		long deletedCount = courseRepository.deleteByTerm(term);
		log.info("Deleted course sections by term. term={}, deletedCount={}", term, deletedCount);
		return CourseSectionDeleteResponse.builder()
				.deletedCount(deletedCount)
				.build();
	}

	public List<CourseSectionResponse> findSections(String term, String deptcd, String campus, String keyword, List<String> excludeDays) {
		String likeKeyword = toLikePattern(keyword);
		return findInLatestTermWithCourseData(term,
				latestTerm -> findSectionsByTerm(latestTerm, deptcd, campus, likeKeyword, excludeDays));
	}

	/**
	 * 폴백 없이 특정 학기에서 강좌를 검색한다. (호출 전에 이미 데이터가 있는 학기로 결정된 경우에만 사용)
	 */
	private List<CourseSectionResponse> findSectionsByTerm(String term, String deptcd, String campus, String likeKeyword, List<String> excludeDays) {
		return courseRepository.searchSections(term, nullIfBlank(deptcd), nullIfBlank(campus), likeKeyword).stream()
				.filter(entity -> !meetsOnExcludedDay(entity, excludeDays))
				.map(CourseSectionResponse::from)
				.toList();
	}

	private static String nullIfBlank(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

	private static String toLikePattern(String keyword) {
		String normalized = nullIfBlank(keyword);
		return (normalized == null) ? null : "%" + normalized + "%";
	}

	private boolean meetsOnExcludedDay(CourseEntity entity, List<String> excludeDays) {
		if (excludeDays == null || excludeDays.isEmpty()) {
			return false;
		}
		String lecttime = entity.getLecttime();
		if (lecttime == null || lecttime.isBlank()) {
			return false;
		}
		return excludeDays.stream()
				.map(DAY_CODE_TO_CHAR::get)
				.filter(Objects::nonNull)
				.anyMatch(lecttime::contains);
	}

	public List<CourseDepartmentResponse> findDepartments(String term) {
		return findInLatestTermWithCourseData(term, this::findDepartmentsByTerm);
	}

	/**
	 * 해당 학기에 강좌 데이터가 없으면 MAX_TERM_FALLBACKS 횟수까지 직전 학기로 순차 폴백하고,
	 * 강좌 데이터가 있는 가장 최근 학기에서 lookup을 수행한다. 모든 학기에 데이터가 없으면 빈 목록을 반환한다.
	 * 폴백 트리거는 "강좌 데이터 유무"이며, 검색 필터 결과가 비어 있어도 학기에 데이터가 있으면 폴백하지 않는다.
	 */
	private <T> List<T> findInLatestTermWithCourseData(String term, Function<String, List<T>> lookup) {
		for (int i = 0; i < MAX_TERM_FALLBACKS; i++) {
			if (courseRepository.existsByTerm(term)) {
				return lookup.apply(term);
			}
			term = previousTerm(term);
		}
		return List.of();
	}

	/**
	 * 직전 학기 계산 (학기코드: 10=1학기, 15=여름학기, 20=2학기, 25=겨울학기)
	 * 202620 → 202610, 202625 → 202615, 202610 → 202520, 202615 → 202525
	 */
	private String previousTerm(String term) {
		int year = Integer.parseInt(term.substring(0, 4));
		String semester = term.substring(4);
		return switch (semester) {
			case "25" -> year + "15";
			case "20" -> year + "10";
			case "15" -> (year - 1) + "25";
			case "10" -> (year - 1) + "20";
			default -> throw new IllegalArgumentException("지원하지 않는 학기 코드: " + term);
		};
	}

	private List<CourseDepartmentResponse> findDepartmentsByTerm(String term) {
		return courseRepository.findDistinctDepartmentsByTerm(term).stream()
				.map(row -> CourseDepartmentResponse.builder()
						.deptcd((String) row[0])
						.deptnm((String) row[1])
						.campusdiv((String) row[2])
						.build())
				.toList();
	}

	private CourseEntity toEntity(CourseSectionImportRequest req) {
		return CourseEntity.builder()
				.coursecls(req.getCoursecls())
				.term(req.getCuriyear() + req.getCurismt())
				.campusdiv(req.getCampusdiv())
				.classdiv(req.getClassdiv())
				.gbn(req.getGbn())
				.curigbn(req.getCurigbn())
				.comyear(req.getComyear())
				.curinum(req.getCurinum())
				.curinum2(req.getCurinum2())
				.curinm(req.getCurinm())
				.groupcd(req.getGroupcd())
				.cdtnum(req.getCdtnum())
				.cdttime(req.getCdttime())
				.takelim(req.getTakelim())
				.listennow(req.getListennow())
				.deptcd(req.getDeptcd())
				.deptnm(req.getDeptnm())
				.profid(req.getProfid())
				.profnm(req.getProfnm())
				.largetp(req.getLargetp())
				.smalltp(req.getSmalltp())
				.abotp(req.getAbotp())
				.lecttime(req.getLecttime())
				.dislevel(req.getDislevel())
				.curicontent(req.getCuricontent())
				.bagcnt(req.getBagcnt())
				.dbtimelist(req.getDbtimelist())
				.sugyn(req.getSugyn())
				.addtime(req.getAddtime())
				.internetyn(req.getInternetyn())
				.flexyn(req.getFlexyn())
				.classtype(req.getClasstype())
				.lecperiod(req.getLecperiod())
				.build();
	}
}
