package com.mjusugangsincheonghelper.auth.test;

import com.mjusugangsincheonghelper.database.entity.Member.Role;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTestAccountRequest {

	private Role role;
}
