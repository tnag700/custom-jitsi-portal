package com.acme.jitsi.domains.invites.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.acme.jitsi.shared.pipeline.OrderedPipelineConfigurationException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

class InviteValidationChainTest {

  @Test
  void executesValidatorsInFixedOrder() {
    InviteExchangeProperties.Invite invite = new InviteExchangeProperties.Invite();
    invite.setToken("invite");
    invite.setMeetingId("meeting-a");
    invite.setExpiresAt(Instant.now().plusSeconds(3600));

    InviteExchangeProperties properties = new InviteExchangeProperties();
    properties.setKnownMeetingIds(Set.of("meeting-a"));
    properties.setInvites(List.of(invite));

    InviteValidationChain chain = new InviteValidationChain(List.of(
        new InviteUsageLimitValidator(),
        new InviteTokenExistsValidator(),
      new InviteMeetingStateValidator(mock(InviteMeetingStatePort.class)),
        new InviteRevokedValidator(),
        new InviteTokenBlankValidator(),
        new InviteMeetingKnownValidator(),
        new InviteExpirationValidator()));
    InviteValidationContext context =
        new InviteValidationContext("invite", properties, mock(InviteUsageStoreRouter.class));

    chain.validate(context);

    assertThat(context.invite()).isSameAs(invite);
  }

  @Test
  void productionValidatorsHaveStableExecutionOrder() {
    List<InviteValidator> validators = new ArrayList<>(List.of(
        new InviteUsageLimitValidator(),
        new InviteTokenExistsValidator(),
      new InviteMeetingStateValidator(mock(InviteMeetingStatePort.class)),
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

  @Test
  void failsFastWhenPipelineHasNoTerminalValidator() {
    assertThatThrownBy(() -> new InviteValidationChain(List.of(
        new InviteTokenBlankValidator(),
        new InviteTokenExistsValidator(),
        new InviteRevokedValidator(),
        new InviteExpirationValidator(),
        new InviteMeetingKnownValidator(),
        new InviteMeetingStateValidator(mock(InviteMeetingStatePort.class)))))
      .isInstanceOf(OrderedPipelineConfigurationException.class)
        .hasMessageContaining("terminal")
        .hasMessageContaining("InviteValidationChain");
  }

  @Test
  void failsFastWhenTerminalValidatorIsNotOrderedLast() {
    assertThatThrownBy(() -> new InviteValidationChain(List.of(
        new InviteTokenBlankValidator(),
        new InviteTokenExistsValidator(),
        new InviteRevokedValidator(),
        new InviteExpirationValidator(),
        new InviteMeetingKnownValidator(),
        new InviteMeetingStateValidator(mock(InviteMeetingStatePort.class)),
        new InviteUsageLimitValidator(),
        new LatePassThroughValidator())))
      .isInstanceOf(OrderedPipelineConfigurationException.class)
      .hasMessageContaining("terminal step must be ordered last");
  }

  @Test
  void failsFastWhenInviteLoadingStepIsMissingBeforeInviteDependentValidators() {
    assertThatThrownBy(() -> new InviteValidationChain(List.of(
        new InviteTokenBlankValidator(),
        new InviteRevokedValidator(),
        new InviteExpirationValidator(),
        new InviteMeetingKnownValidator(),
        new InviteMeetingStateValidator(mock(InviteMeetingStatePort.class)),
        new InviteUsageLimitValidator())))
      .isInstanceOf(OrderedPipelineConfigurationException.class)
        .hasMessageContaining("explicit step sequence")
        .hasMessageContaining("InviteTokenExistsValidator");
  }

  private static class OrderedValidator implements InviteValidator, Ordered {

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
    public boolean requiresResolvedInvite() {
      return false;
    }

    @Override
    public void validate(InviteValidationContext context) {
      calls.add(order);
    }
  }

  private static final class TerminalOrderedValidator extends OrderedValidator {

    private TerminalOrderedValidator(int order, List<Integer> calls) {
      super(order, calls);
    }

    @Override
    public boolean isTerminalValidator() {
      return true;
    }
  }

  private static final class LoadingOrderedValidator extends OrderedValidator {

    private LoadingOrderedValidator(int order, List<Integer> calls) {
      super(order, calls);
    }

    @Override
    public boolean loadsResolvedInvite() {
      return true;
    }

    @Override
    public void validate(InviteValidationContext context) {
      super.validate(context);
      InviteExchangeProperties.Invite invite = new InviteExchangeProperties.Invite();
      invite.setToken("invite");
      invite.setMeetingId("meeting-a");
      context.setInvite(invite);
    }
  }

  private static final class LatePassThroughValidator extends OrderedValidator {

    private LatePassThroughValidator() {
      super(80, new ArrayList<>());
    }
  }
}