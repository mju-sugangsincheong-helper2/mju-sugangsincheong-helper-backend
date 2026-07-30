package com.mjusugangsincheonghelper.singlegame.dto;

import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentsResponse implements Serializable {

	private static final long serialVersionUID = 1L;

	private List<String> departments;
}
