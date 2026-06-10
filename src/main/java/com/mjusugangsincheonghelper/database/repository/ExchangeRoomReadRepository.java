package com.mjusugangsincheonghelper.database.repository;

import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadEntity;
import com.mjusugangsincheonghelper.database.entity.ExchangeRoomReadEntity.ExchangeRoomReadId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExchangeRoomReadRepository extends JpaRepository<ExchangeRoomReadEntity, ExchangeRoomReadId> {
}
