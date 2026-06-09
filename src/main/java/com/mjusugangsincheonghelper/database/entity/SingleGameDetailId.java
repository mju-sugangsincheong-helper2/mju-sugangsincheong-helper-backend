package com.mjusugangsincheonghelper.database.entity;

import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SingleGameDetailId implements Serializable {

	private Long gameId;
	private int sequence;

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		SingleGameDetailId that = (SingleGameDetailId) o;
		return sequence == that.sequence && Objects.equals(gameId, that.gameId);
	}

	@Override
	public int hashCode() {
		return Objects.hash(gameId, sequence);
	}
}
