package com.mjusugangsincheonghelper.global.api.envelope;

import com.mjusugangsincheonghelper.global.api.meta.PageMeta;
import com.mjusugangsincheonghelper.global.api.meta.ResponseMeta;
import com.mjusugangsincheonghelper.global.api.support.MetaGenerator;
import java.util.List;
import lombok.Getter;
import org.springframework.data.domain.Page;

@Getter
public class PagedSuccessResponseEnvelope<T> extends ResponseEnvelope {

	private final List<T> data;
	private final PageMeta page;

	private PagedSuccessResponseEnvelope(ResponseMeta meta, List<T> data, PageMeta page) {
		super(meta);
		this.data = data;
		this.page = page;
	}

	public static <T> PagedSuccessResponseEnvelope<T> from(Page<T> page) {
		return new PagedSuccessResponseEnvelope<>(
				MetaGenerator.generate(),
				page.getContent(),
				PageMeta.from(page)
		);
	}
}
