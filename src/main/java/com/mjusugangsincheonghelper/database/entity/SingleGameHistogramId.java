package com.mjusugangsincheonghelper.database.entity;

import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SingleGameHistogramId implements Serializable {

	private int totalCourses;
	private short scope;
	private String department;
	private int sequence;
	private short metric;
	private int bucketMs;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SingleGameHistogramId that = (SingleGameHistogramId) o;
		return totalCourses == that.totalCourses
				&& scope == that.scope
				&& sequence == that.sequence
				&& metric == that.metric
				&& bucketMs == that.bucketMs
				&& Objects.equals(department, that.department);
	}

	@Override
	public int hashCode() {
		return Objects.hash(totalCourses, scope, department, sequence, metric, bucketMs);
	}
}
