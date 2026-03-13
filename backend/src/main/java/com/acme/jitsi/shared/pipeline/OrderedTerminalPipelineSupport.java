package com.acme.jitsi.shared.pipeline;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;
import org.springframework.util.ClassUtils;

public final class OrderedTerminalPipelineSupport {

  private OrderedTerminalPipelineSupport() {
  }

  public static <T> List<T> sortAndValidate(
      String pipelineName,
      List<T> steps,
      Predicate<T> terminalStep,
      Function<T, String> stepName) {
    return sortAndValidate(pipelineName, steps, terminalStep, stepName, List.of());
  }

  public static <T> List<T> sortAndValidate(
      String pipelineName,
      List<T> steps,
      Predicate<T> terminalStep,
      Function<T, String> stepName,
      List<Class<? extends T>> expectedSequence) {
    List<T> orderedSteps = new ArrayList<>(steps);
    AnnotationAwareOrderComparator.sort(orderedSteps);

    if (orderedSteps.isEmpty()) {
      throw new OrderedPipelineConfigurationException(pipelineName + " requires at least one ordered step.");
    }

    List<T> terminalSteps = orderedSteps.stream()
        .filter(terminalStep)
        .toList();

    if (terminalSteps.isEmpty()) {
      throw new OrderedPipelineConfigurationException(pipelineName + " requires exactly one terminal step.");
    }

    if (terminalSteps.size() > 1) {
      throw new OrderedPipelineConfigurationException(
          pipelineName + " requires exactly one terminal step but found: "
              + terminalSteps.stream().map(stepName).toList());
    }

    T terminal = terminalSteps.get(0);
    if (orderedSteps.get(orderedSteps.size() - 1) != terminal) {
      throw new OrderedPipelineConfigurationException(
          pipelineName + " terminal step must be ordered last: " + stepName.apply(terminal));
    }

    if (!expectedSequence.isEmpty()) {
      validateExpectedSequence(pipelineName, orderedSteps, stepName, expectedSequence);
    }

    return List.copyOf(orderedSteps);
  }

  @SafeVarargs
  public static <T> List<Class<? extends T>> expectedSequence(Class<? extends T>... stepTypes) {
    return List.copyOf(Arrays.asList(stepTypes));
  }

  private static <T> void validateExpectedSequence(
      String pipelineName,
      List<T> orderedSteps,
      Function<T, String> stepName,
      List<Class<? extends T>> expectedSequence) {
    List<String> expectedNames = expectedSequence.stream().map(Class::getSimpleName).toList();
    List<String> actualNames = orderedSteps.stream().map(stepName).toList();

    if (orderedSteps.size() != expectedSequence.size()) {
      throw new OrderedPipelineConfigurationException(
          pipelineName + " requires explicit step sequence " + expectedNames + " but found " + actualNames);
    }

    for (int index = 0; index < expectedSequence.size(); index++) {
      T orderedStep = orderedSteps.get(index);
      Class<? extends T> expectedType = expectedSequence.get(index);
      Class<?> actualType = ClassUtils.getUserClass(orderedStep);
      if (!expectedType.isAssignableFrom(actualType)) {
        throw new OrderedPipelineConfigurationException(
            pipelineName + " requires explicit step sequence " + expectedNames + " but found " + actualNames);
      }
    }
  }
}