package lab.stoneshelter;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.core.util.Yaml31;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureRestTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.client.RestTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureRestTestClient
class OpenApiDocumentationTest extends IntegrationTest {
    @Autowired
    private RestTestClient restClient;

    @Test
    void documentsOnlySupportedStoneOperationsWithTheirSuccessfulStatusesAndDtos() throws Exception {
        JsonNode jsonNode = openApiJson();
        assertThat(jsonNode.path("openapi").asText()).startsWith("3.");
        JsonNode pathsJsonNode = jsonNode.path("paths");
        assertThat(pathsJsonNode.propertyStream().map(entry -> entry.getKey()).toList())
                .containsExactlyInAnyOrder("/api/v1/stones", "/api/v1/stones/{id}", "/api/v1/stones/search", "/api/v1/stones/{id}/photos", "/api/v1/stones/{id}/reservations", "/api/v1/stone-photo-drafts", "/api/v1/stone-photo-drafts/{id}", "/api/v1/chat/messages", "/api/v1/chat/conversations/{conversationId}/messages", "/api/stone-encyclopedia/index");
        assertThat(pathsJsonNode.path("/api/v1/stones").propertyStream().map(entry -> entry.getKey()).toList())
                .containsExactly("post");
        assertThat(pathsJsonNode.path("/api/v1/stones/{id}").propertyStream().map(entry -> entry.getKey()).toList())
                .containsExactlyInAnyOrder("get", "put", "delete");
        assertThat(pathsJsonNode.path("/api/v1/stones/search").propertyStream().map(entry -> entry.getKey()).toList())
                .containsExactly("post");
        assertOperation(pathsJsonNode.path("/api/v1/stones").path("post"), "201", "StoneCreateResponse", "400", "409", "500");
        assertOperation(pathsJsonNode.path("/api/v1/stones/{id}").path("get"), "200", "StoneResponse");
        assertOperation(pathsJsonNode.path("/api/v1/stones/{id}").path("put"), "200", "StoneUpdateResponse");
        assertOperation(pathsJsonNode.path("/api/v1/stones/{id}").path("delete"), "200", "StoneDeleteResponse");
        assertOperation(pathsJsonNode.path("/api/v1/stones/search").path("post"), "200", "StonesSearchResponse");
        assertThat(pathsJsonNode.path("/api/v1/stones").path("post")
                .at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/StoneCreateRequest");
        assertThat(pathsJsonNode.path("/api/v1/stones/{id}").path("put")
                .at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/StoneUpdateRequest");
        assertThat(pathsJsonNode.path("/api/v1/stones/search").path("post")
                .at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/StonesSearchRequest");
    }

    @Test
    void describesEveryDtoAndPropertyAndRetainsSupportedValidationConstraints() throws Exception {
        JsonNode jsonNode = openApiJson();
        JsonNode schemasJsonNode = jsonNode.at("/components/schemas");
        for (String name : List.of("StoneCreateRequest", "StoneUpdateRequest", "StoneCreateResponse",
                "StoneUpdateResponse", "StoneResponse", "StoneDeleteResponse", "StoneSearchResponse",
                "StonesSearchRequest", "StonesSearchResponse", "StoneSearchFilter", "SearchSort",
                "StonePhotoResponse", "StonePhotoUploadResponse", "StonePhotoUploadRequest",
                "StoneReservationCreateRequest", "StoneReservationCreateResponse")) {
            JsonNode schemaJsonNode = schemasJsonNode.path(name);
            assertThat(schemaJsonNode.path("description").asText()).as(name + " description").isNotBlank();
            assertThat(schemaJsonNode.path("properties").size()).as(name + " properties").isPositive();
            schemaJsonNode.path("properties").propertyStream().forEach(entry ->
                    assertThat(entry.getValue().path("description").asText())
                            .as(name + "." + entry.getKey() + " description").isNotBlank());
        }
        for (String name : List.of("StoneCreateRequest", "StoneUpdateRequest")) {
            JsonNode schemaJsonNode = schemasJsonNode.path(name);
            assertThat(schemaJsonNode.path("required")).contains(
                    Json31.mapper().valueToTree("name"), Json31.mapper().valueToTree("stoneType"),
                    Json31.mapper().valueToTree("stoneSize"), Json31.mapper().valueToTree("adoptionStatus"));
            assertThat(schemaJsonNode.at("/properties/name/maxLength").asInt()).isEqualTo(120);
            assertThat(schemaJsonNode.at("/properties/photo/maxLength").asInt()).isEqualTo(500);
            assertThat(schemaJsonNode.at("/properties/biography/maxLength").asInt()).isEqualTo(2048);
        }
        assertThat(schemasJsonNode.at("/StoneCreateRequest/properties/admissionDate").isMissingNode()).isTrue();
        assertThat(schemasJsonNode.at("/StoneCreateRequest/required"))
                .doesNotContain(Json31.mapper().valueToTree("admissionDate"));
        assertThat(schemasJsonNode.at("/StoneUpdateRequest/required")).contains(Json31.mapper().valueToTree("admissionDate"));
        assertThat(schemasJsonNode.at("/StoneUpdateRequest/properties/admissionDate/format").asText()).isEqualTo("date-time");
        assertThat(schemasJsonNode.at("/StoneCreateResponse/properties/admissionDate/format").asText()).isEqualTo("date-time");
        assertThat(schemasJsonNode.at("/StoneCreateResponse/properties/admissionDate/description").asText()).contains("backend");
        assertThat(schemasJsonNode.at("/StoneSearchFilter/properties/stoneSizes/type").asText()).isEqualTo("array");
        assertThat(schemasJsonNode.at("/StoneSearchFilter/properties/stoneTypes/type").asText()).isEqualTo("array");
        assertThat(jsonNode.at("/paths/~1api~1v1~1stones~1search/post/description").asText())
                .contains("AVAILABLE", "RESERVED", "ADOPTED", "totalElements 0");
        assertThat(schemasJsonNode.at("/StoneSearchFilter/properties/adoptionStatus/description").asText())
                .contains("AVAILABLE", "RESERVED", "ADOPTED", "totalElements 0");
        assertThat(schemasJsonNode.at("/StoneSearchFilter/properties/admissionDateFrom/format").asText()).isEqualTo("date");
        assertThat(schemasJsonNode.at("/StoneSearchFilter/properties/admissionDateTo/format").asText()).isEqualTo("date");
        assertThat(schemasJsonNode.at("/StonesSearchRequest/properties/page/minimum").asText()).isEqualTo("0");
        assertThat(schemasJsonNode.at("/StonesSearchRequest/properties/size/minimum").asInt()).isEqualTo(1);
        assertThat(schemasJsonNode.at("/StonesSearchRequest/properties/size/maximum").asInt()).isEqualTo(24);
        assertThat(schemasJsonNode.at("/SearchSort/properties/field/pattern").asText()).isEqualTo("name|stoneSize|admissionDate");
        assertThat(schemasJsonNode.at("/SearchSort/properties/direction/pattern").asText()).isEqualTo("asc|desc");
    }

    @Test
    void documentsBodylessEncyclopediaOperationsAndHiddenOutcome() throws Exception {
        JsonNode root = openApiJson();
        JsonNode path = root.at("/paths/~1api~1stone-encyclopedia~1index");
        assertThat(path.propertyStream().map(entry -> entry.getKey()).toList()).containsExactlyInAnyOrder("post", "delete");
        for (String method : List.of("post", "delete")) {
            JsonNode operation = path.path(method);
            assertThat(operation.has("requestBody")).isFalse();
            assertThat(operation.path("parameters")).anySatisfy(parameter -> {
                assertThat(parameter.path("name").asText()).isEqualTo("Idempotency-Key");
                assertThat(parameter.path("required").asBoolean()).isTrue();
                assertThat(parameter.at("/schema/format").asText()).isEqualTo("uuid");
            });
            for (String code : method.equals("post") ? List.of("400", "404", "409", "422", "503") : List.of("400", "409", "503")) {
                assertThat(operation.at("/responses/" + code + "/content/application~1problem+json/schema/$ref").asText())
                        .isEqualTo("#/components/schemas/ProblemDetail");
            }
        }
        assertThat(path.path("post").path("responses").propertyStream().map(entry -> entry.getKey()).toList())
                .contains("200", "201");
        assertThat(root.at("/components/schemas/StoneEncyclopediaIndexResponse/properties").propertyStream().map(entry -> entry.getKey()).toList())
                .containsExactlyInAnyOrder("documentKey", "sourceVersion", "indexedFragments");
        assertThat(root.at("/components/schemas/StoneEncyclopediaDeleteResponse/properties").propertyStream().map(entry -> entry.getKey()).toList())
                .containsExactly("id");
    }

    @Test
    void describesMultipartUploadAndOrderedGalleryWithoutExpandingSearchItems() throws Exception {
        var jsonNode = openApiJson();
        var operation = jsonNode.at("/paths/~1api~1v1~1stones~1{id}~1photos/post");
        assertThat(operation.at("/requestBody/content/multipart~1form-data/schema").isMissingNode()).isFalse();
        assertThat(operation.at("/responses/201/content/*~1*/schema/$ref").asText())
                .isEqualTo("#/components/schemas/StonePhotoUploadResponse");
        assertThat(operation.path("responses").propertyStream().map(entry -> entry.getKey()).toList())
                .containsExactlyInAnyOrder("201", "400", "404", "413", "415", "503");
        for (String code : List.of("400", "404", "413", "415", "503")) {
            assertThat(operation.at("/responses/" + code + "/content/application~1problem+json/schema/$ref").asText())
                    .isEqualTo("#/components/schemas/ProblemDetail");
        }
        var schemas = jsonNode.at("/components/schemas");
        assertThat(schemas.at("/StoneResponse/properties/photos/type").asText()).isEqualTo("array");
        assertThat(schemas.at("/StoneResponse/properties/photos/items/$ref").asText())
                .isEqualTo("#/components/schemas/StonePhotoResponse");
        assertThat(schemas.at("/StoneResponse/required")).contains(Json31.mapper().valueToTree("photos"));
        assertThat(schemas.at("/StoneSearchResponse/properties/photos").isMissingNode()).isTrue();
        assertThat(schemas.at("/StonePhotoUploadRequest/properties/file/format").asText()).isEqualTo("binary");
        assertThat(schemas.at("/StonePhotoUploadRequest/required")).contains(Json31.mapper().valueToTree("file"));
    }

    @Test
    void describesReservationCreationWithRequiredArbitraryTextAndOnlySuccessfulResponse() throws Exception {
        var jsonNode = openApiJson();
        var operation = jsonNode.at("/paths/~1api~1v1~1stones~1{id}~1reservations/post");
        assertThat(operation.at("/requestBody/content/application~1json/schema/$ref").asText())
                .isEqualTo("#/components/schemas/StoneReservationCreateRequest");
        assertOperation(operation, "201", "StoneReservationCreateResponse");
        var request = jsonNode.at("/components/schemas/StoneReservationCreateRequest");
        assertThat(request.path("required")).containsExactlyInAnyOrder(
                Json31.mapper().valueToTree("applicantName"), Json31.mapper().valueToTree("contactDetails"));
        for (String field : List.of("applicantName", "contactDetails")) {
            assertThat(request.at("/properties/" + field + "/type").asText()).isEqualTo("string");
            assertThat(request.at("/properties/" + field + "/maxLength").isMissingNode()).isTrue();
            assertThat(request.at("/properties/" + field + "/pattern").isMissingNode()).isTrue();
        }
        var response = jsonNode.at("/components/schemas/StoneReservationCreateResponse");
        assertThat(response.path("properties").propertyStream().map(entry -> entry.getKey()).toList())
                .containsExactlyInAnyOrder("id", "stoneId", "adoptionStatus", "createdAt");
        assertThat(response.at("/properties/createdAt/format").asText()).isEqualTo("date-time");
    }

    @Test
    void servesYamlWithTheSameGeneratedContractAsJson() throws Exception {
        assertThat(Yaml31.mapper().readTree(restClient.get().uri("/v3/api-docs.yaml")
                .exchange().expectStatus().isOk().expectBody(String.class)
                .returnResult().getResponseBody())).isEqualTo(openApiJson());
    }

    @Test
    void servesSwaggerUiAndItsDocumentationConfiguration() {
        restClient.get().uri("/swagger-ui.html").exchange().expectStatus().isFound()
                .expectHeader().valueEquals("Location", "/swagger-ui/index.html");
        restClient.get().uri("/swagger-ui/index.html").exchange().expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.TEXT_HTML)
                .expectBody(String.class).value(body -> assertThat(body).contains("swagger-ui-bundle.js"));
        restClient.get().uri("/swagger-ui/swagger-ui-bundle.js").exchange().expectStatus().isOk();
        restClient.get().uri("/v3/api-docs/swagger-config").exchange().expectStatus().isOk()
                .expectBody().jsonPath("$.url").isEqualTo("/v3/api-docs");
    }

    @Test
    void draftContractsDescribeUploadsPreviewAndOrderedCreationReferences() throws Exception {
        JsonNode root = openApiJson();
        JsonNode paths = root.path("paths");
        assertOperation(paths.at("/~1api~1v1~1stone-photo-drafts/post"), "201", "StonePhotoDraftUploadResponse", "400", "413", "415", "503", "500");
        assertOperation(paths.at("/~1api~1v1~1stone-photo-drafts~1{id}/get"), "200", "StonePhotoDraftResponse", "400", "404", "503");
        assertThat(paths.at("/~1api~1v1~1stone-photo-drafts/post/requestBody/content/multipart~1form-data/schema/$ref").asText())
                .isEqualTo("#/components/schemas/StonePhotoDraftUploadRequest");
        JsonNode schemas = root.path("components").path("schemas");
        assertThat(schemas.at("/StonePhotoDraftUploadRequest/properties/file/format").asText()).isEqualTo("binary");
        for (String schema : List.of("StonePhotoDraftUploadResponse", "StonePhotoDraftResponse")) {
            assertThat(schemas.path(schema).path("properties").propertyStream().map(entry -> entry.getKey()).toList())
                    .containsExactlyInAnyOrder("id", "url", "uploadedAt", "expiresAt");
            assertThat(schemas.path(schema).at("/properties/id/format").asText()).isEqualTo("uuid");
            assertThat(schemas.path(schema).at("/properties/expiresAt/format").asText()).isEqualTo("date-time");
        }
        assertThat(schemas.at("/StoneCreateRequest/properties/photoUploadIds/maxItems").asInt()).isEqualTo(16);
        assertThat(schemas.at("/StoneCreateRequest/properties/photoUploadIds/items/format").asText()).isEqualTo("uuid");
        assertThat(schemas.at("/StoneCreateResponse/properties/photos/items/$ref").asText()).isEqualTo("#/components/schemas/StonePhotoResponse");
        for (String status : List.of("400", "413", "415", "503", "500")) {
            assertThat(paths.at("/~1api~1v1~1stone-photo-drafts/post/responses/" + status).isMissingNode()).isFalse();
        }
        for (String status : List.of("400", "409", "500")) {
            assertThat(paths.at("/~1api~1v1~1stones/post/responses/" + status).isMissingNode()).isFalse();
        }
    }

    private JsonNode openApiJson() throws Exception {
        return Json31.mapper().readTree(restClient.get().uri("/v3/api-docs")
                .exchange().expectStatus().isOk()
                .expectHeader().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)
                .expectBody(String.class).returnResult().getResponseBody());
    }

    private void assertOperation(JsonNode operationJsonNode, String status, String responseType, String... errorStatuses) {
        assertThat(operationJsonNode.path("responses").propertyStream().map(entry -> entry.getKey()).toList())
                .containsExactlyInAnyOrderElementsOf(java.util.stream.Stream.concat(java.util.stream.Stream.of(status), java.util.Arrays.stream(errorStatuses)).toList());
        assertThat(operationJsonNode.path("responses").path(status).path("content").propertyStream()
                .map(entry -> entry.getValue().at("/schema/$ref").asText()).toList())
                .isNotEmpty().containsOnly("#/components/schemas/" + responseType);
    }
}
