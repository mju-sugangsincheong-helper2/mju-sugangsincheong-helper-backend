package com.mjusugangsincheonghelper.example.service;

import com.mjusugangsincheonghelper.database.entity.ExampleEntity;
import com.mjusugangsincheonghelper.database.repository.ExampleRepository;
import com.mjusugangsincheonghelper.example.dto.ExampleCreateRequest;
import com.mjusugangsincheonghelper.example.dto.ExampleDetailResponse;
import com.mjusugangsincheonghelper.example.dto.ExampleEchoRequest;
import com.mjusugangsincheonghelper.example.dto.ExamplePageItem;
import com.mjusugangsincheonghelper.example.dto.ExampleResponse;
import com.mjusugangsincheonghelper.example.dto.ExampleUpdateRequest;
import com.mjusugangsincheonghelper.global.api.code.ErrorCode;
import com.mjusugangsincheonghelper.global.api.exception.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ExampleServiceImpl implements ExampleService {

	private final ExampleRepository exampleRepository;

	@Override
	public ExampleResponse hello(String name) {
		return ExampleResponse.of("hello " + name);
	}

	@Override
	public ExampleResponse echo(ExampleEchoRequest request) {
		return ExampleResponse.of(request.getMessage());
	}

	@Override
	@Transactional
	public ExampleDetailResponse create(ExampleCreateRequest request) {
		ExampleEntity entity = ExampleEntity.builder()
				.title(request.getTitle())
				.content(request.getContent())
				.build();
		ExampleEntity saved = exampleRepository.save(entity);
		return ExampleDetailResponse.from(saved);
	}

	@Override
	public ExampleDetailResponse findById(Long id) {
		ExampleEntity entity = exampleRepository.findById(id)
				.orElseThrow(() -> new BaseException(ErrorCode.GLOBAL_NOT_FOUND));
		return ExampleDetailResponse.from(entity);
	}

	@Override
	public Page<ExamplePageItem> list(int page, int size) {
		PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"));
		return exampleRepository.findByActiveTrue(pageRequest)
				.map(ExamplePageItem::from);
	}

	@Override
	@Transactional
	public ExampleDetailResponse update(Long id, ExampleUpdateRequest request) {
		ExampleEntity entity = exampleRepository.findById(id)
				.orElseThrow(() -> new BaseException(ErrorCode.GLOBAL_NOT_FOUND));
		entity.update(request.getTitle(), request.getContent());
		return ExampleDetailResponse.from(entity);
	}

	@Override
	@Transactional
	public void delete(Long id) {
		ExampleEntity entity = exampleRepository.findById(id)
				.orElseThrow(() -> new BaseException(ErrorCode.GLOBAL_NOT_FOUND));
		entity.deactivate();
	}

	@Override
	public void throwNotFound() {
		throw new BaseException(ErrorCode.GLOBAL_NOT_FOUND);
	}
}
