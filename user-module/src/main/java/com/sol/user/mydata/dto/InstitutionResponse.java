package com.sol.user.mydata.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sol.user.mydata.type.InstitutionCode;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record InstitutionResponse(
        String id,
        String name,
        String type,
        String label,
        String brandColor,
        String labelColor,
        boolean connected,
        List<String> connectedProducts,
        Long totalAmountKrw
) {
    public static InstitutionResponse of(InstitutionCode code, boolean connected,
                                         List<String> connectedProducts, Long totalAmountKrw) {
        return new InstitutionResponse(
                code.getId(),
                code.getName(),
                code.getType(),
                code.getLabel(),
                code.getBrandColor(),
                code.getLabelColor(),
                connected,
                connected ? connectedProducts : null,
                connected ? totalAmountKrw : null
        );
    }
}
