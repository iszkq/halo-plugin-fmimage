package run.halo.fmimage.provider;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import run.halo.fmimage.model.ImageGenerationResponse;

class AbstractJsonProviderAdapterTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TestAdapter adapter = new TestAdapter(WebClient.builder());

    @Test
    void shouldParseInlineDataFromOpenAiCompatibleGeminiResponse() throws Exception {
        var root = OBJECT_MAPPER.readTree("""
            {
              "choices": [
                {
                  "message": {
                    "multi_mod_content": [
                      {
                        "inline_data": {
                          "mime_type": "image/png",
                          "data": "aGVsbG8="
                        }
                      }
                    ]
                  }
                }
              ]
            }
            """);

        var items = adapter.parse(root);

        assertEquals(1, items.size());
        assertTrue(items.getFirst().previewUrl().startsWith("data:image/png;base64,aGVsbG8="));
        assertEquals("b64_json", items.getFirst().sourceType());
        assertEquals("image/png", items.getFirst().mediaType());
    }

    private static final class TestAdapter extends AbstractJsonProviderAdapter {
        private TestAdapter(WebClient.Builder webClientBuilder) {
            super(webClientBuilder);
        }

        @Override
        public boolean supports(ProviderType providerType) {
            return false;
        }

        @Override
        public reactor.core.publisher.Mono<run.halo.fmimage.model.UpstreamImageResult> generate(
            ProviderType providerType,
            run.halo.fmimage.model.GenerateImageRequest request,
            run.halo.fmimage.config.ResolvedProviderConfig providerConfig,
            run.halo.fmimage.config.GeneralSettings generalSettings) {
            throw new UnsupportedOperationException("Not needed for parsing tests");
        }

        private List<ImageGenerationResponse.Item> parse(com.fasterxml.jackson.databind.JsonNode root) {
            return parseFlexibleOutputItems(root, "image/png");
        }
    }
}
