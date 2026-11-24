/*
 * Copyright 2026 Puneet Swarup.
 * Licensed under the PersonaVault project terms.
 */
package com.personavault.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.personavault.entity.Fact;
import com.personavault.entity.Policy;
import com.personavault.entity.VaultEntity;
import com.personavault.repository.FactRepository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FactIngestionService")
class FactIngestionServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-24T10:15:30Z"), ZoneOffset.UTC);
    private static final long DOC_ID = 3L;
    private static final long ENTITY_ID = 7L;

    @Mock
    private FactService factService;

    @Mock
    private FactRepository factRepository;

    private FactIngestionService service;
    private VaultEntity entity;

    @BeforeEach
    void setUp() {
        service = new FactIngestionService(factService, factRepository, CLOCK);
        entity = new VaultEntity();
        ReflectionTestUtils.setField(entity, "id", ENTITY_ID);
        entity.setEntityType("POLICY");
        entity.setCanonicalName("HP-1");
    }

    private static Policy fullPolicy() {
        Policy policy = new Policy();
        policy.setPolicyNumber("HP-1");
        policy.setInsurerName("HDFC Ergo");
        policy.setPolicyType("HEALTH");
        policy.setEffectiveDate(LocalDate.of(2025, 3, 15));
        policy.setExpiryDate(LocalDate.of(2026, 3, 14));
        policy.setAnnualPremium(new BigDecimal("24000"));
        policy.setPremiumFrequency("ANNUAL");
        return policy;
    }

    private void givenNoExistingFacts() {
        when(factRepository.findByEntityIdAndFactKeyAndValidToIsNull(anyLong(), anyString()))
                .thenReturn(List.of());
    }

    private void givenEntity() {
        when(factService.findOrCreateEntity(eq("POLICY"), anyString(), eq("insurance-policy")))
                .thenReturn(entity);
    }

    private Map<String, Fact> captureRecordedFacts(int expected) {
        ArgumentCaptor<Fact> captor = ArgumentCaptor.forClass(Fact.class);
        verify(factService, org.mockito.Mockito.times(expected)).recordFact(captor.capture());
        return captor.getAllValues().stream().collect(Collectors.toMap(Fact::getFactKey, Function.identity()));
    }

    @Test
    @DisplayName("should record one typed fact per populated policy field")
    void shouldRecordAllFields() {
        givenEntity();
        givenNoExistingFacts();
        when(factService.recordFact(any(Fact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        VaultEntity result = service.recordPolicyFacts(DOC_ID, fullPolicy());

        assertSame(entity, result, "should record one typed fact per populated policy field");
        Map<String, Fact> facts = captureRecordedFacts(7);

        assertEquals(
                "STRING",
                facts.get("policyNumber").getValueType(),
                "should record one typed fact per populated policy field");
        assertEquals(
                "HP-1",
                facts.get("policyNumber").getValueText(),
                "should record one typed fact per populated policy field");
        assertEquals(
                "STRING",
                facts.get("insurerName").getValueType(),
                "should record one typed fact per populated policy field");
        assertEquals(
                "HDFC Ergo",
                facts.get("insurerName").getValueText(),
                "should record one typed fact per populated policy field");
        assertEquals(
                "DATE",
                facts.get("expiryDate").getValueType(),
                "should record one typed fact per populated policy field");
        assertEquals(
                LocalDate.of(2026, 3, 14),
                facts.get("expiryDate").getValueDate(),
                "should record one typed fact per populated policy field");
        assertEquals(
                "MONEY",
                facts.get("annualPremium").getValueType(),
                "should record one typed fact per populated policy field");
        assertEquals(
                0,
                new BigDecimal("24000").compareTo(facts.get("annualPremium").getValueNum()),
                "should record one typed fact per populated policy field");
        assertEquals(
                "INR",
                facts.get("annualPremium").getCurrency(),
                "should record one typed fact per populated policy field");

        Fact expiry = facts.get("expiryDate");
        assertEquals(DOC_ID, expiry.getSourceDocId(), "should record one typed fact per populated policy field");
        assertEquals("LLM", expiry.getExtractedBy(), "should record one typed fact per populated policy field");
        assertEquals(
                0,
                FactIngestionService.LLM_CONFIDENCE.compareTo(expiry.getConfidence()),
                "should record one typed fact per populated policy field");
        assertEquals(ENTITY_ID, expiry.getEntityId(), "should record one typed fact per populated policy field");
        assertNotNull(expiry.getFirstSeenAt(), "should record one typed fact per populated policy field");
    }

    @Test
    @DisplayName("should skip fields the extraction left empty")
    void shouldSkipEmptyFields() {
        givenEntity();
        givenNoExistingFacts();
        when(factService.recordFact(any(Fact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Policy sparse = new Policy();
        sparse.setPolicyNumber("HP-1");
        sparse.setInsurerName("   ");

        service.recordPolicyFacts(DOC_ID, sparse);

        Map<String, Fact> facts = captureRecordedFacts(1);
        assertTrue(facts.containsKey("policyNumber"), "should skip fields the extraction left empty");
        assertFalse(facts.containsKey("insurerName"), "should skip fields the extraction left empty");
    }

    @Test
    @DisplayName("should fall back to a document-scoped name when the policy number is missing")
    void shouldFallBackWhenNumberMissing() {
        when(factService.findOrCreateEntity("POLICY", "POLICY-42", "insurance-policy"))
                .thenReturn(entity);
        givenNoExistingFacts();
        when(factService.recordFact(any(Fact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.recordPolicyFacts(42L, new Policy());

        Map<String, Fact> facts = captureRecordedFacts(1);
        assertEquals(
                "POLICY-42",
                facts.get("policyNumber").getValueText(),
                "should fall back to a document-scoped name when the policy number is missing");
    }

    @Test
    @DisplayName("should not rewrite a fact this document already contributed unchanged")
    void shouldBeIdempotentForSameDocument() {
        Fact existing = new Fact();
        existing.setEntityId(ENTITY_ID);
        existing.setFactKey("policyNumber");
        existing.setValueType("STRING");
        existing.setValueText("HP-1");
        existing.setSourceDocId(DOC_ID);

        when(factService.findOrCreateEntity("POLICY", "HP-1", "insurance-policy"))
                .thenReturn(entity);
        when(factRepository.findByEntityIdAndFactKeyAndValidToIsNull(ENTITY_ID, "policyNumber"))
                .thenReturn(List.of(existing));

        Policy policy = new Policy();
        policy.setPolicyNumber("HP-1");

        service.recordPolicyFacts(DOC_ID, policy);

        verify(factService, never()).recordFact(any());
    }

    @Test
    @DisplayName("should close an older fact from another document instead of overwriting it")
    void shouldSupersedeOlderFactFromAnotherDocument() {
        Fact older = new Fact();
        older.setEntityId(ENTITY_ID);
        older.setFactKey("policyNumber");
        older.setValueType("STRING");
        older.setValueText("HP-OLD");
        older.setSourceDocId(99L);

        when(factService.findOrCreateEntity("POLICY", "HP-1", "insurance-policy"))
                .thenReturn(entity);
        when(factRepository.findByEntityIdAndFactKeyAndValidToIsNull(ENTITY_ID, "policyNumber"))
                .thenReturn(List.of(older));
        when(factService.recordFact(any(Fact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Policy policy = new Policy();
        policy.setPolicyNumber("HP-1");

        service.recordPolicyFacts(DOC_ID, policy);

        assertEquals(LocalDate.of(2026, 11, 23), older.getValidTo(), "older fact should be closed yesterday");
        verify(factRepository).save(older);
    }

    @Test
    @DisplayName("should keep the open fact untouched when re-reading the same source")
    void shouldNotCloseFactFromSameDocument() {
        Fact existing = new Fact();
        existing.setEntityId(ENTITY_ID);
        existing.setFactKey("policyNumber");
        existing.setValueType("STRING");
        existing.setValueText("HP-OLD");
        existing.setSourceDocId(DOC_ID);

        when(factService.findOrCreateEntity("POLICY", "HP-1", "insurance-policy"))
                .thenReturn(entity);
        when(factRepository.findByEntityIdAndFactKeyAndValidToIsNull(ENTITY_ID, "policyNumber"))
                .thenReturn(List.of(existing));
        when(factService.recordFact(any(Fact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Policy policy = new Policy();
        policy.setPolicyNumber("HP-1");

        service.recordPolicyFacts(DOC_ID, policy);

        assertNull(existing.getValidTo(), "a fact from the same document must not be closed");
        verify(factRepository, never()).save(any());
    }
}
