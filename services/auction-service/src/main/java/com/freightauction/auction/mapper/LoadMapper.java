package com.freightauction.auction.mapper;

import com.freightauction.auction.domain.Load;
import com.freightauction.auction.dto.CreateLoadRequest;
import com.freightauction.auction.dto.LoadResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.time.LocalDateTime;
import java.util.UUID;

@Mapper(
        componentModel = "spring",
        imports = {UUID.class, LocalDateTime.class}
)
public interface LoadMapper {
    @Mapping(target = "id", expression = "java(UUID.randomUUID())")
    @Mapping(target = "createdByUserId", source = "createdByUserId")
    @Mapping(target = "createdAt", expression = "java(LocalDateTime.now())")
    Load toEntity(CreateLoadRequest request, UUID createdByUserId);

    LoadResponse toResponse(Load load);

    // atualiza os campos de uma Load já existente com os dados do request
    @Mapping(target = "id", ignore = true)  // nunca muda o id
    @Mapping(target = "createdByUserId", ignore = true)  // nunca muda quem criou
    @Mapping(target = "createdAt", ignore = true)
    // nunca muda a data de criação
    void updateEntity(CreateLoadRequest request, @MappingTarget Load load);
}
