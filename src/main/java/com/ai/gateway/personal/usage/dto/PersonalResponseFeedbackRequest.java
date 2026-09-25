package com.ai.gateway.personal.usage.dto;

import com.ai.gateway.personal.usage.entity.PersonalResponseRating;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PersonalResponseFeedbackRequest {
 @NotNull
 private PersonalResponseRating rating;
}
