package org.hypertrace.gateway.service.common.transformer;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import java.io.File;
import java.util.Map;
import java.util.Optional;
import org.hypertrace.core.attribute.service.v1.AttributeMetadata;
import org.hypertrace.core.attribute.service.v1.AttributeScope;
import org.hypertrace.gateway.service.common.AttributeMetadataProvider;
import org.hypertrace.gateway.service.common.RequestContext;
import org.hypertrace.gateway.service.common.config.ScopeFilterConfigs;
import org.hypertrace.gateway.service.common.util.QueryExpressionUtil;
import org.hypertrace.gateway.service.entity.EntitiesRequestContext;
import org.hypertrace.gateway.service.v1.common.Filter;
import org.hypertrace.gateway.service.v1.common.FunctionType;
import org.hypertrace.gateway.service.v1.common.Operator;
import org.hypertrace.gateway.service.v1.common.SortOrder;
import org.hypertrace.gateway.service.v1.entity.EntitiesRequest;
import org.hypertrace.gateway.service.v1.trace.TracesRequest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

public class RequestPreProcessorTest {
  private static final String TEST_TENANT_ID = "test-tenant-id";
  private RequestPreProcessor requestPreProcessor;

  @Mock private AttributeMetadataProvider attributeMetadataProvider;
  @Mock private ScopeFilterConfigs scopeFilterConfigs;

  @BeforeEach
  public void setup() {
    attributeMetadataProvider = mock(AttributeMetadataProvider.class);
    scopeFilterConfigs =
        initializeScopeFilterConfigs("configs/request-preprocessor-test/service-id-config.conf");
    requestPreProcessor = new RequestPreProcessor(attributeMetadataProvider, scopeFilterConfigs);
  }

  @Test
  public void
      testServiceEntitiesRequestDuplicateColumnSelectionIsRemovedAndScopeFilterConfigsAdded() {
    long endTime = System.currentTimeMillis();
    long startTime = endTime - 1000L;
    EntitiesRequestContext entitiesRequestContext =
        new EntitiesRequestContext(
            TEST_TENANT_ID, startTime, endTime, "SERVICE", "SERVICE.startTime", Map.of());
    mockAttributeMetadata(entitiesRequestContext, AttributeScope.SERVICE.name(), "id");
    mockAttributeMetadata(entitiesRequestContext, AttributeScope.SERVICE.name(), "name");
    mockAttributeMetadata(entitiesRequestContext, AttributeScope.SERVICE.name(), "startTime");

    EntitiesRequest entitiesRequest =
        EntitiesRequest.newBuilder()
            .setEntityType("SERVICE")
            .setStartTimeMillis(startTime)
            .setEndTimeMillis(endTime)
            .setFilter(QueryExpressionUtil.buildStringFilter("SERVICE.name", Operator.LIKE, "log"))
            .addSelection(QueryExpressionUtil.buildAttributeExpression("SERVICE.id"))
            .addSelection(
                QueryExpressionUtil.buildAttributeExpression(
                    "SERVICE.id")) // Duplicate should be removed by the processor
            .addSelection(QueryExpressionUtil.buildAttributeExpression("SERVICE.name"))
            .addSelection(
                QueryExpressionUtil.getAggregateFunctionExpression(
                    "SERVICE.duration", FunctionType.AVG, "AVG#SERVICE|duration"))
            .addSelection(
                QueryExpressionUtil.getAggregateFunctionExpression(
                    "SERVICE.errorCount", FunctionType.SUM, "SUM#SERVICE|errorCount"))
            .addOrderBy(
                QueryExpressionUtil.getOrderBy(
                    "SERVICE.duration", FunctionType.AVG, "AVG#SERVICE|duration", SortOrder.DESC))
            .build();

    EntitiesRequest transformedRequest =
        requestPreProcessor.process(entitiesRequest, entitiesRequestContext);

    // RequestPreProcessor should remove duplicate Service.Id selection and add scope filters
    // config.
    Assertions.assertEquals(
        EntitiesRequest.newBuilder()
            .setEntityType("SERVICE")
            .setStartTimeMillis(startTime)
            .setEndTimeMillis(endTime)
            .setFilter(
                Filter.newBuilder()
                    .setOperator(Operator.AND)
                    .addChildFilter(
                        QueryExpressionUtil.buildStringFilter("SERVICE.name", Operator.LIKE, "log"))
                    .addChildFilter(
                        Filter.newBuilder()
                            .setOperator(Operator.AND)
                            .addChildFilter(
                                QueryExpressionUtil.buildStringFilter(
                                    "SERVICE.id", Operator.NEQ, "null"))
                            .addChildFilter(
                                QueryExpressionUtil.buildStringFilter(
                                    "SERVICE.name", Operator.NEQ, "foo"))))
            .addSelection(QueryExpressionUtil.buildAttributeExpression("SERVICE.id"))
            .addSelection(QueryExpressionUtil.buildAttributeExpression("SERVICE.name"))
            .addSelection(
                QueryExpressionUtil.getAggregateFunctionExpression(
                    "SERVICE.duration", FunctionType.AVG, "AVG#SERVICE|duration"))
            .addSelection(
                QueryExpressionUtil.getAggregateFunctionExpression(
                    "SERVICE.errorCount", FunctionType.SUM, "SUM#SERVICE|errorCount"))
            .addOrderBy(
                QueryExpressionUtil.getOrderBy(
                    "SERVICE.duration", FunctionType.AVG, "AVG#SERVICE|duration", SortOrder.DESC))
            .build(),
        transformedRequest);
  }

  @Test
  public void testApiTracesRequestScopeFilterConfigsAdded() {
    long endTime = System.currentTimeMillis();
    long startTime = endTime - 1000L;
    RequestContext requestContext = new RequestContext(TEST_TENANT_ID, Map.of());
    mockAttributeMetadata(requestContext, AttributeScope.API_TRACE.name(), "apiBoundaryType");
    mockAttributeMetadata(requestContext, AttributeScope.API_TRACE.name(), "apiId");

    TracesRequest tracesRequest =
        TracesRequest.newBuilder()
            .setStartTimeMillis(startTime)
            .setEndTimeMillis(endTime)
            .setScope("API_TRACE")
            .setFilter(
                QueryExpressionUtil.buildStringFilter(
                    "API_TRACE.serviceName", Operator.LIKE, "log"))
            .addSelection(QueryExpressionUtil.buildAttributeExpression("API_TRACE.id"))
            .addSelection(QueryExpressionUtil.buildAttributeExpression("API_TRACE.serviceName"))
            .build();

    TracesRequest transformedRequest = requestPreProcessor.process(tracesRequest, requestContext);

    // RequestPreProcessor should add scope filters config.
    Assertions.assertEquals(
        TracesRequest.newBuilder()
            .setStartTimeMillis(startTime)
            .setEndTimeMillis(endTime)
            .setScope("API_TRACE")
            .setFilter(
                Filter.newBuilder()
                    .setOperator(Operator.AND)
                    .addChildFilter(
                        QueryExpressionUtil.buildStringFilter(
                            "API_TRACE.serviceName", Operator.LIKE, "log"))
                    .addChildFilter(
                        Filter.newBuilder()
                            .setOperator(Operator.AND)
                            .addChildFilter(
                                QueryExpressionUtil.buildStringFilter(
                                    "API_TRACE.apiBoundaryType", Operator.EQ, "ENTRY"))
                            .addChildFilter(
                                QueryExpressionUtil.buildStringFilter(
                                    "API_TRACE.apiId", Operator.NEQ, "null"))))
            .addSelection(QueryExpressionUtil.buildAttributeExpression("API_TRACE.id"))
            .addSelection(QueryExpressionUtil.buildAttributeExpression("API_TRACE.serviceName"))
            .build(),
        transformedRequest);
  }

  private void mockAttributeMetadata(
      RequestContext requestContext, String attributeScope, String key) {
    when(attributeMetadataProvider.getAttributeMetadata(requestContext, attributeScope, key))
        .thenReturn(createAttributeMetadata(attributeScope, key));
  }

  private Optional<AttributeMetadata> createAttributeMetadata(String attributeScope, String key) {
    return Optional.of(
        AttributeMetadata.newBuilder()
            .setScopeString(attributeScope)
            .setKey(key)
            .setId(attributeScope + "." + key)
            .build());
  }

  private ScopeFilterConfigs initializeScopeFilterConfigs(String filePath) {
    String configFilePath =
        Thread.currentThread().getContextClassLoader().getResource(filePath).getPath();
    if (configFilePath == null) {
      throw new RuntimeException("Cannot find config file in the classpath: " + filePath);
    }

    Config fileConfig = ConfigFactory.parseFile(new File(configFilePath));
    Config config = ConfigFactory.load(fileConfig);
    return new ScopeFilterConfigs(config);
  }
}
