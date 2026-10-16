package com.mjusugangsincheonghelper.singlegame.dto;

import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyRecordResponse implements Serializable {

	private static final long serialVersionUID = 1L;

	private long gameId;
	private int totalCourses;
	private boolean completed;
	private int tTotal;
	private Instant createdAt;
}
