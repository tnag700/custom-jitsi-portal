package com.acme.jitsi.shared.pipeline;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;
import org.springframework.util.ClassUtils;

class OrderedTerminalPipelineSupportTest {

  @Test
  void acceptsSubclassedStepWhenExpectedSequenceDeclaresBaseType() {
    assertThatCode(() -> OrderedTerminalPipelineSupport.sortAndValidate(
        "ProxySafePipeline",
        List.of(new FirstStep(), new ProxyLikeTerminalStep()),
        step -> step instanceof TerminalStep,
        step -> ClassUtils.getUserClass(step).getSimpleName(),
        OrderedTerminalPipelineSupport.expectedSequence(FirstStep.class, TerminalStep.class)))
        .doesNotThrowAnyException();
  }

  private interface Step {
  }

  private static class FirstStep implements Step, Ordered {

    @Override
    public int getOrder() {
      return 10;
    }
  }

  private static class TerminalStep implements Step, Ordered {

    @Override
    public int getOrder() {
      return 20;
    }
  }

  private static final class ProxyLikeTerminalStep extends TerminalStep {
  }
}