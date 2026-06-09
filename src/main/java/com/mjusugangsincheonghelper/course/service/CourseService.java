package com.mjusugangsincheonghelper.course.service;

import com.mjusugangsincheonghelper.course.dto.CourseSectionDeleteResponse;
import com.mjusugangsincheonghelper.course.dto.CourseSectionImportRequest;
import com.mjusugangsincheonghelper.course.dto.CourseSectionImportResponse;
import com.mjusugangsincheonghelper.course.dto.CourseSectionResponse;
import com.mjusugangsincheonghelper.database.entity.CourseEntity;
import com.mjusugangsincheonghelper.database.repository.CourseRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

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

		return CourseSectionImportResponse.builder()
				.importedCount(entities.size())
				.terms(terms)
				.build();
	}

	@Transactional
	public CourseSectionDeleteResponse deleteSectionsByTerm(String term) {
		long deletedCount = courseRepository.deleteByTerm(term);
		return CourseSectionDeleteResponse.builder()
				.deletedCount(deletedCount)
				.build();
	}

	public List<CourseSectionResponse> findSections(String term) {
		List<CourseEntity> entities;
		if (term != null && !term.isBlank()) {
			entities = courseRepository.findByTerm(term);
		} else {
			entities = courseRepository.findAll();
		}
		return entities.stream()
				.map(CourseSectionResponse::from)
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
