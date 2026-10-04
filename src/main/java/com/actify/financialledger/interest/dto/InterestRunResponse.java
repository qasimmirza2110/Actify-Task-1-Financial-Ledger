package com.actify.financialledger.interest.dto;

public record InterestRunResponse(String period, int credited, int skipped, int failed) {
}
