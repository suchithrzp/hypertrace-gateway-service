package org.hypertrace.gateway.service.entity.query;

import static org.hypertrace.core.attribute.service.v1.AttributeSource.EDS;
import static org.hypertrace.core.attribute.service.v1.AttributeSource.QS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.hypertrace.gateway.service.v1.common.Expression;
import org.hypertrace.gateway.service.v1.common.OrderByExpression;
import org.hypertrace.gateway.service.v1.common.TimeAggregation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ExecutionTreeUtilsTest {
  @Test
  void testGetSingleSourceForAllAttributes_allSourceExpressionMapKeySetsHaveOneSource() {
    // All source expression map keysets have one source.
    ExecutionContext executionContext =
        getMockExecutionContext(
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            Map.of(
                "API.id", Set.of(QS.name()),
                "API.name", Set.of(QS.name()),
                "API.duration", Set.of(QS.name()),
                "API.startTime", Set.of(QS.name())));

    assertEquals(
        Optional.of("QS"), ExecutionTreeUtils.getSingleSourceForAllAttributes(executionContext));
  }

  @Test
  void testGetSingleSourceForAllAttributes_someSourceExpressionMapKeySetsHaveMultipleSources() {
    ExecutionContext executionContext =
        getMockExecutionContext(
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS", "EDS")),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            Map.of(
                "API.id", Set.of(QS.name()),
                "API.name", Set.of(QS.name()),
                "API.duration", Set.of(QS.name(), EDS.name()),
                "API.startTime", Set.of(QS.name())));

    assertEquals(
        Optional.of("QS"), ExecutionTreeUtils.getSingleSourceForAllAttributes(executionContext));
  }

  @Test
  void testGetSingleSourceForAllAttributes_someSourceExpressionMapKeySetsHaveDifferentSources() {
    ExecutionContext executionContext =
        getMockExecutionContext(
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("EDS")),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            Map.of(
                "API.id", Set.of(QS.name()),
                "API.name", Set.of(QS.name()),
                "API.duration", Set.of(EDS.name()),
                "API.startTime", Set.of(QS.name())));

    assertEquals(
        Optional.empty(), ExecutionTreeUtils.getSingleSourceForAllAttributes(executionContext));
  }

  @Test
  void testGetSingleSourceForAllAttributes_someSourceExpressionMapKeySetsAreEmpty() {
    // All source expression map keysets have one source but dont have sources defined.
    ExecutionContext executionContext =
        getMockExecutionContext(
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of()),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of("QS")),
            createSourceToExpressionsMap(List.of()),
            Map.of(
                "API.id", Set.of(QS.name()),
                "API.name", Set.of(QS.name()),
                "API.duration", Set.of(),
                "API.startTime", Set.of(QS.name())));

    assertEquals(
        Optional.of("QS"), ExecutionTreeUtils.getSingleSourceForAllAttributes(executionContext));
  }

  // This scenario is not possible but for academic purposes let's test it.
  @Test
  void testGetSingleSourceForAllAttributes_allEmptySourceExpressionMapKeySetsAndAttributes() {
    ExecutionContext executionContext =
        getMockExecutionContext(
            createSourceToExpressionsMap(List.of()),
            createSourceToExpressionsMap(List.of()),
            createSourceToExpressionsMap(List.of()),
            createSourceToExpressionsMap(List.of()),
            createSourceToExpressionsMap(List.of()),
            createSourceToExpressionsMap(List.of()),
            Map.of(
                "API.id", Set.of(),
                "API.name", Set.of(),
                "API.duration", Set.of(),
                "API.startTime", Set.of()));

    assertEquals(
        Optional.empty(), ExecutionTreeUtils.getSingleSourceForAllAttributes(executionContext));
  }

  @Test
  void test_filtersAndOrderByFromSameSingleSourceSet() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    // filters
    // API.id -> ["QS", "EDS"]
    // API.name -> ["QS"]
    when(executionContext.getSourceToFilterAttributeMap())
        .thenReturn(Map.of("QS", Set.of("API.id", "API.name"), "EDS", Set.of("API.id")));

    // order bys
    // API.status -> ["QS"]
    when(executionContext.getSourceToSelectionOrderByAttributeMap())
        .thenReturn(Map.of("QS", Set.of("API.status")));
    when(executionContext.getSourceToMetricOrderByAttributeMap())
        .thenReturn(Collections.emptyMap());

    Set<String> sourceSets =
        ExecutionTreeUtils.getSourceSetsIfFilterAndOrderByAreFromSameSourceSets(executionContext);
    assertEquals(1, sourceSets.size());
    assertTrue(sourceSets.contains("QS"));
  }

  @Test
  void test_filtersAndOrderByFromSameMultipleSourceSets() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    // filters
    // API.id -> ["QS", "EDS"]
    // API.name -> ["QS", "EDS"]
    when(executionContext.getSourceToFilterAttributeMap())
        .thenReturn(
            Map.of("QS", Set.of("API.id", "API.name"), "EDS", Set.of("API.id", "API.name")));

    // order bys
    // API.status -> ["QS", "EDS"]
    // API.latency -> ["QS", "EDS"]
    when(executionContext.getSourceToSelectionOrderByAttributeMap())
        .thenReturn(Map.of("QS", Set.of("API.status", "API.latency")));
    when(executionContext.getSourceToMetricOrderByAttributeMap())
        .thenReturn(Map.of("EDS", Set.of("API.status", "API.latency")));

    Set<String> sourceSets =
        ExecutionTreeUtils.getSourceSetsIfFilterAndOrderByAreFromSameSourceSets(executionContext);
    assertEquals(2, sourceSets.size());
    assertTrue(sourceSets.contains("QS"));
    assertTrue(sourceSets.contains("EDS"));
  }

  @Test
  void test_filtersAndOrderByFromEmptyOrderBy() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    // filters
    // API.id -> ["QS", "EDS"]
    // API.name -> ["QS", "EDS"]
    when(executionContext.getSourceToFilterAttributeMap())
        .thenReturn(
            Map.of("QS", Set.of("API.id", "API.name"), "EDS", Set.of("API.id", "API.name")));
    when(executionContext.getFilterAttributeToSourceMap())
        .thenReturn(Map.of("API.id", Set.of("QS", "EDS"), "API.name", Set.of("QS", "EDS")));

    // order bys
    when(executionContext.getSourceToSelectionOrderByAttributeMap())
        .thenReturn(Collections.emptyMap());
    when(executionContext.getSourceToMetricOrderByAttributeMap())
        .thenReturn(Collections.emptyMap());

    Set<String> sourceSets =
        ExecutionTreeUtils.getSourceSetsIfFilterAndOrderByAreFromSameSourceSets(executionContext);
    assertEquals(2, sourceSets.size());
    assertTrue(sourceSets.contains("QS"));
    assertTrue(sourceSets.contains("EDS"));
  }

  @Test
  void test_filtersAndOrderByFromEmptyFilters() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    // filters
    when(executionContext.getSourceToFilterAttributeMap()).thenReturn(Collections.emptyMap());

    // order bys
    // API.status -> ["QS", "EDS"]
    // API.latency -> ["QS"]
    when(executionContext.getSourceToSelectionOrderByAttributeMap())
        .thenReturn(Map.of("QS", Set.of("API.status", "API.latency")));
    when(executionContext.getSourceToMetricOrderByAttributeMap())
        .thenReturn(Map.of("EDS", Set.of("API.status")));

    Set<String> sourceSets =
        ExecutionTreeUtils.getSourceSetsIfFilterAndOrderByAreFromSameSourceSets(executionContext);
    assertEquals(1, sourceSets.size());
    assertTrue(sourceSets.contains("QS"));
  }

  @Test
  void test_filtersAndOrderByFromDifferentSourceSets() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    // filters
    // API.id -> ["QS", "EDS"]
    // API.name -> ["QS"]
    when(executionContext.getSourceToFilterAttributeMap())
        .thenReturn(Map.of("QS", Set.of("API.id", "API.name"), "EDS", Set.of("API.id")));
    when(executionContext.getFilterAttributeToSourceMap())
        .thenReturn(Map.of("API.id", Set.of("QS", "EDS"), "API.name", Set.of("QS")));

    // order bys
    // API.status -> ["QS", "EDS"]
    // API.latency -> ["EDS"]
    when(executionContext.getSourceToSelectionOrderByAttributeMap())
        .thenReturn(Map.of("QS", Set.of("API.status")));
    when(executionContext.getSourceToMetricOrderByAttributeMap())
        .thenReturn(Map.of("EDS", Set.of("API.status", "API.latency")));

    Set<String> sourceSets =
        ExecutionTreeUtils.getSourceSetsIfFilterAndOrderByAreFromSameSourceSets(executionContext);
    assertTrue(sourceSets.isEmpty());
  }

  @Test
  @DisplayName(
      "filters are applied on a single data source, for filters check on other data source")
  void test_areFiltersOnCurrentDataSource_onlyPresentOnTheCurrentDataSource() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    // filters
    // API.id -> ["QS", "EDS"]
    // API.name -> ["QS"]
    when(executionContext.getSourceToFilterAttributeMap())
        .thenReturn(Map.of("QS", Set.of("API.id", "API.name"), "EDS", Set.of("API.id")));
    when(executionContext.getFilterAttributeToSourceMap())
        .thenReturn(Map.of("API.id", Set.of("QS", "EDS"), "API.name", Set.of("QS")));

    assertTrue(ExecutionTreeUtils.areFiltersOnlyOnCurrentDataSource(executionContext, "QS"));
    assertFalse(ExecutionTreeUtils.areFiltersOnlyOnCurrentDataSource(executionContext, "EDS"));
  }

  @Test
  @DisplayName(
      "are filters applied on a single data source, if attributes are on multiple data sources")
  void test_areFiltersOnCurrentDataSource_multipleSourcesForAttributes() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    // filters
    // API.id -> ["QS", "EDS"]
    // API.name -> ["QS", "EDS"]
    when(executionContext.getSourceToFilterAttributeMap())
        .thenReturn(
            Map.of("QS", Set.of("API.id", "API.name"), "EDS", Set.of("API.id", "API.name")));
    when(executionContext.getFilterAttributeToSourceMap())
        .thenReturn(Map.of("API.id", Set.of("QS", "EDS"), "API.name", Set.of("QS", "EDS")));

    assertTrue(ExecutionTreeUtils.areFiltersOnlyOnCurrentDataSource(executionContext, "QS"));
    assertTrue(ExecutionTreeUtils.areFiltersOnlyOnCurrentDataSource(executionContext, "EDS"));
  }

  @Test
  @DisplayName(
      "are filters applied on a single data source, if attributes are on different data sources")
  void test_areFiltersOnCurrentDataSource_attributesOnDifferentSources() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    // filters
    // API.id -> ["QS"]
    // API.name -> ["EDS"]
    when(executionContext.getSourceToFilterAttributeMap())
        .thenReturn(Map.of("QS", Set.of("API.id"), "EDS", Set.of("API.name")));
    when(executionContext.getFilterAttributeToSourceMap())
        .thenReturn(Map.of("API.id", Set.of("QS"), "API.name", Set.of("EDS")));

    assertFalse(ExecutionTreeUtils.areFiltersOnlyOnCurrentDataSource(executionContext, "QS"));
    assertFalse(ExecutionTreeUtils.areFiltersOnlyOnCurrentDataSource(executionContext, "EDS"));
  }

  @Test
  @DisplayName("are filters applied on a single data source, if no filters")
  void test_areFiltersOnCurrentDataSource_noFilters() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    // filters
    when(executionContext.getSourceToFilterAttributeMap()).thenReturn(Collections.emptyMap());

    assertTrue(ExecutionTreeUtils.areFiltersOnlyOnCurrentDataSource(executionContext, "QS"));
    assertTrue(ExecutionTreeUtils.areFiltersOnlyOnCurrentDataSource(executionContext, "EDS"));
  }

  @Test
  void removeDuplicateSelectionAttributes_invalidSource() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    when(executionContext.getSourceToSelectionAttributeMap()).thenReturn(Collections.emptyMap());

    ExecutionTreeUtils.removeDuplicateSelectionAttributes(executionContext, "INVALID");
    verify(executionContext, never()).removeSelectionAttributes(eq("INVALID"), anySet());
  }

  @Test
  void removeDuplicateSelectionAttributes() {
    ExecutionContext executionContext = mock(ExecutionContext.class);
    // API.id -> ["QS"]
    // API.name -> ["QS", "EDS"]
    // API.status -> ["EDS", "AS"]
    // API.latency -> ["QS", "AS"]
    when(executionContext.getSourceToSelectionAttributeMap())
        .thenReturn(
            Map.of(
                "QS",
                Set.of("API.id", "API.name", "API.latency"),
                "EDS",
                Set.of("API.name", "API.status"),
                "AS",
                Set.of("API.status", "API.latency")));

    ExecutionTreeUtils.removeDuplicateSelectionAttributes(executionContext, "QS");
    verify(executionContext).removeSelectionAttributes("EDS", Set.of("API.name"));
    verify(executionContext).removeSelectionAttributes("AS", Set.of("API.latency"));
  }

  private ExecutionContext getMockExecutionContext(
      Map<String, List<Expression>> sourceToSelectionExpressionMap,
      Map<String, List<Expression>> sourceToMetricExpressionMap,
      Map<String, List<TimeAggregation>> sourceToTimeAggregationMap,
      Map<String, List<OrderByExpression>> sourceToSelectionOrderByExpressionMap,
      Map<String, List<OrderByExpression>> sourceToMetricOrderByExpressionMap,
      Map<String, List<Expression>> sourceToFilterExpressionMap,
      Map<String, Set<String>> attributeToSourcesMap) {
    ExecutionContext executionContext = mock(ExecutionContext.class);

    when(executionContext.getSourceToSelectionExpressionMap())
        .thenReturn(sourceToSelectionExpressionMap);
    when(executionContext.getSourceToMetricExpressionMap()).thenReturn(sourceToMetricExpressionMap);
    when(executionContext.getSourceToTimeAggregationMap()).thenReturn(sourceToTimeAggregationMap);
    when(executionContext.getSourceToSelectionOrderByExpressionMap())
        .thenReturn(sourceToSelectionOrderByExpressionMap);
    when(executionContext.getSourceToMetricOrderByExpressionMap())
        .thenReturn(sourceToMetricOrderByExpressionMap);
    when(executionContext.getSourceToFilterExpressionMap()).thenReturn(sourceToFilterExpressionMap);

    when(executionContext.getAllAttributesToSourcesMap()).thenReturn(attributeToSourcesMap);

    return executionContext;
  }

  // We don't care about the values. Just they keySets.
  private <T> Map<String, List<T>> createSourceToExpressionsMap(List<String> sourceKeys) {
    return sourceKeys.stream().collect(Collectors.toUnmodifiableMap(s -> s, s -> List.of()));
  }
}
