package com.freightauction.auction.repository;

import com.freightauction.auction.domain.Load;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LoadRepository extends JpaRepository<Load, UUID> {
}
