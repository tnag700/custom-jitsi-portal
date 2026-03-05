package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.acme.jitsi.domains.meetings.service.MeetingStateGuard;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

class InviteValidationChainTest {

  @Test
  void executesValidatorsInFixedOrder() {
    List<Integer> calls = new ArrayList<>();
    InviteValidator third = new OrderedValidator(3, calls);
    InviteValidator first = new OrderedValidator(1, calls);
    InviteValidator second = new OrderedValidator(2, calls);

    InviteValidationChain chain = new InviteValidationChain(List.of(third, first, second));
    InviteExchangeProperties properties = new InviteExchangeProperties();
    chain.validate(new InviteValidationContext("invite", properties, org.mockito.Mockito.mock(InviteUsageStoreRouter.class)));

    assertThat(calls).containsExactly(1, 2, 3);
  }

  @Test
  void productionValidatorsHaveStableExecutionOrder() {
    List<InviteValidator> validators = new ArrayList<>(List.of(
        new InviteUsageLimitValidator(),
        new InviteTokenExistsValidator(),
        new InviteMeetingStateValidator(mock(MeetingStateGuard.class)),
        new InviteRevokedValidator(),
        new InviteTokenBlankValidator(),
        new InviteMeetingKnownValidator(),
        new InviteExpirationValidator()));

    AnnotationAwareOrderComparator.sort(validators);

    assertThat(validators)
        .extracting(v -> v.getClass().getSimpleName())
        .containsExactly(
            "InviteTokenBlankValidator",
            "InviteTokenExistsValidator",
            "InviteRevokedValidator",
            "InviteExpirationValidator",
            "InviteMeetingKnownValidator",
            "InviteMeetingStateValidator",
            "InviteUsageLimitValidator");
  }

  private static final class OrderedValidator implements InviteValidator, Ordered {

    private final int order;
    private final List<Integer> calls;

    private OrderedValidator(int order, List<Integer> calls) {
      this.order = order;
      this.calls = calls;
    }

    @Override
    public int getOrder() {
      return order;
    }

    @Override
    public void validate(InviteValidationContext context) {
      calls.add(order);
    }
  }
}