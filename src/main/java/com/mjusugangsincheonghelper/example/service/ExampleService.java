package com.mjusugangsincheonghelper.example.service;

import com.mjusugangsincheonghelper.example.dto.ExampleCreateRequest;
import com.mjusugangsincheonghelper.example.dto.ExampleDetailResponse;
import com.mjusugangsincheonghelper.example.dto.ExampleEchoRequest;
import com.mjusugangsincheonghelper.example.dto.ExamplePageItem;
import com.mjusugangsincheonghelper.example.dto.ExampleResponse;
import com.mjusugangsincheonghelper.example.dto.ExampleUpdateRequest;
import org.springframework.data.domain.Page;

public interface ExampleService {

	ExampleResponse hello(String name);

	ExampleResponse echo(ExampleEchoRequest request);

	ExampleDetailResponse create(ExampleCreateRequest request);

	ExampleDetailResponse findById(Long id);

	Page<ExamplePageItem> list(int page, int size);

	ExampleDetailResponse update(Long id, ExampleUpdateRequest request);

	void delete(Long id);

	void throwNotFound();
}
