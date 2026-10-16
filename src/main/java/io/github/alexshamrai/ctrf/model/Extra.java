
package io.github.alexshamrai.ctrf.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Extra fields that CTRF leaves to the producer. The reporter writes {@code startupDuration} into
 * {@code results.summary.extra} when asked to; {@code customData} is free for other producers.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Extra {

    private Map<String, Object> customData;
    private Long startupDuration;
}