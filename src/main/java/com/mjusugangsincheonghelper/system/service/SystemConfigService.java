package com.mjusugangsincheonghelper.system.service;

import com.mjusugangsincheonghelper.system.dto.SystemConfigResponse;
import com.mjusugangsincheonghelper.system.dto.SystemConfigUpdateRequest;

public interface SystemConfigService {

	String EXPOSE_FIELD_DETAILS_KEY = "expose-field-details";

	SystemConfigResponse find(String configKey);

	SystemConfigResponse update(String configKey, SystemConfigUpdateRequest request);

	boolean getBoolean(String configKey, boolean defaultValue);
}
