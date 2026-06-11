package com.mjusugangsincheonghelper.exchange.dto.cache;

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
public class RoomActiveIntentsDto implements Serializable {

	private static final long serialVersionUID = 1L;

	private List<ActiveIntent> intents;

	public int calculateActiveCount() {
		if (intents == null) {
			return 0;
		}
		return (int) intents.stream().filter(i -> !i.isDeleted()).count();
	}

	@Getter
	@Builder
	@NoArgsConstructor
	@AllArgsConstructor
	public static class ActiveIntent implements Serializable {

		private static final long serialVersionUID = 1L;

		private Long intentId;
		private Long memberId;
		private boolean isDeleted;
	}
}
