package org.hypertrace.gateway.service.explore;

import com.google.common.collect.ImmutableMap;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.hypertrace.core.attribute.service.v1.AttributeMetadata;
import org.hypertrace.core.query.service.client.QueryServiceClient;
import org.hypertrace.core.serviceframework.metrics.PlatformMetricsRegistry;
import org.hypertrace.gateway.service.common.AttributeMetadataProvider;
import org.hypertrace.gateway.service.common.config.ScopeFilterConfigs;
import org.hypertrace.gateway.service.v1.explore.ExploreRequest;
import org.hypertrace.gateway.service.v1.explore.ExploreResponse;

public class ExploreService {

  private final AttributeMetadataProvider attributeMetadataProvider;
  private final ExploreRequestValidator exploreRequestValidator = new ExploreRequestValidator();

  private final RequestHandler normalRequestHandler;
  private final TimeAggregationsRequestHandler timeAggregationsRequestHandler;
  private final TimeAggregationsWithGroupByRequestHandler timeAggregationsWithGroupByRequestHandler;
  private final ScopeFilterConfigs scopeFilterConfigs;

  private Timer queryExecutionTimer;

  public ExploreService(
      QueryServiceClient queryServiceClient,
      int requestTimeout,
      AttributeMetadataProvider attributeMetadataProvider,
      ScopeFilterConfigs scopeFiltersConfig) {
    this.attributeMetadataProvider = attributeMetadataProvider;
    this.normalRequestHandler =
        new RequestHandler(queryServiceClient, requestTimeout, attributeMetadataProvider);
    this.timeAggregationsRequestHandler =
        new TimeAggregationsRequestHandler(
            queryServiceClient, requestTimeout, attributeMetadataProvider);
    this.timeAggregationsWithGroupByRequestHandler =
        new TimeAggregationsWithGroupByRequestHandler(
            queryServiceClient, requestTimeout, attributeMetadataProvider);
    this.scopeFilterConfigs = scopeFiltersConfig;
    initMetrics();
  }

  private void initMetrics() {
    queryExecutionTimer =
        PlatformMetricsRegistry.registerTimer(
            "hypertrace.explore.query.execution", ImmutableMap.of());
  }

  public ExploreResponse explore(
      String tenantId, ExploreRequest request, Map<String, String> requestHeaders) {

    final Instant start = Instant.now();
    try {
      ExploreRequestContext exploreRequestContext =
          new ExploreRequestContext(tenantId, request, requestHeaders);

      // Add extra filters based on the scope.
      request =
          ExploreRequest.newBuilder(request)
              .setFilter(
                  scopeFilterConfigs.createScopeFilter(
                      request.getContext(),
                      request.getFilter(),
                      attributeMetadataProvider,
                      exploreRequestContext))
              .build();
      ExploreRequestContext newExploreRequestContext =
          new ExploreRequestContext(tenantId, request, requestHeaders);

      Map<String, AttributeMetadata> attributeMetadataMap =
          attributeMetadataProvider.getAttributesMetadata(
              newExploreRequestContext, request.getContext());
      exploreRequestValidator.validate(request, attributeMetadataMap);

      IRequestHandler requestHandler = getRequestHandler(request);

      ExploreResponse.Builder responseBuilder =
          requestHandler.handleRequest(newExploreRequestContext, request);

      return responseBuilder.build();
    } finally {
      queryExecutionTimer.record(
          Duration.between(start, Instant.now()).toMillis(), TimeUnit.MILLISECONDS);
    }
  }

  private IRequestHandler getRequestHandler(ExploreRequest request) {
    if (hasTimeAggregationsAndGroupBy(request)) {
      return timeAggregationsWithGroupByRequestHandler;
    }
    if (hasTimeAggregations(request)) {
      return timeAggregationsRequestHandler;
    }
    return normalRequestHandler;
  }

  private boolean hasTimeAggregations(ExploreRequest request) {
    return !request.getTimeAggregationList().isEmpty();
  }

  private boolean hasTimeAggregationsAndGroupBy(ExploreRequest request) {
    return !request.getTimeAggregationList().isEmpty() && !request.getGroupByList().isEmpty();
  }
}
