package com.acme.jitsi.domains.meetings.service;

import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class BulkInviteRecipientValidator {

  private static final Pattern EMAIL_PATTERN =
      Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);

  private final MeetingParticipantAssignmentRepository assignmentRepository;

  public BulkInviteRecipientValidator(MeetingParticipantAssignmentRepository assignmentRepository) {
    this.assignmentRepository = assignmentRepository;
  }

  public void validate(BulkInviteRecipient recipient, MeetingRole roleToUse) {
    String email = recipient.normalizedEmail();
    String userId = recipient.normalizedUserId();
    boolean hasEmail = email != null && !email.isBlank();
    boolean hasUserId = userId != null && !userId.isBlank();

    validateRecipientIdentitySource(recipient, hasEmail, hasUserId);
    validateEmailFormat(recipient, email, hasEmail);
    validateUserId(recipient, userId, hasUserId);
    validateRole(roleToUse, recipient);
  }

  public BulkInviteError toError(InvalidRecipientFormatException exception) {
    return new BulkInviteError(
        exception.rowIndex(),
        exception.recipient(),
        "INVALID_RECIPIENT_FORMAT",
        exception.getMessage());
  }

  private InvalidRecipientFormatException invalid(BulkInviteRecipient recipient, String message) {
    return invalid(recipient, message, null);
  }

  private InvalidRecipientFormatException invalid(
      BulkInviteRecipient recipient,
      String message,
      Throwable cause) {
    return new InvalidRecipientFormatException(recipient.rowIndex(), recipient.displayRecipient(), message, cause);
  }

  private void validateRecipientIdentitySource(BulkInviteRecipient recipient, boolean hasEmail, boolean hasUserId) {
    if (hasEmail == hasUserId) {
      throw invalid(recipient, "Recipient must contain exactly one of email or userId");
    }
  }

  private void validateEmailFormat(BulkInviteRecipient recipient, String email, boolean hasEmail) {
    if (hasEmail && !EMAIL_PATTERN.matcher(email).matches()) {
      throw invalid(recipient, "Invalid email format");
    }
  }

  private void validateUserId(BulkInviteRecipient recipient, String userId, boolean hasUserId) {
    if (!hasUserId) {
      return;
    }

    assertValidUuid(userId, recipient);
    if (assignmentRepository.findBySubjectId(userId).isEmpty()) {
      throw invalid(recipient, "Unknown userId");
    }
  }

  private void assertValidUuid(String userId, BulkInviteRecipient recipient) {
    try {
      UUID.fromString(userId);
    } catch (IllegalArgumentException ex) {
      throw invalid(recipient, "userId must be a valid UUID", ex);
    }
  }

  private void validateRole(MeetingRole roleToUse, BulkInviteRecipient recipient) {
    if (roleToUse == MeetingRole.HOST) {
      throw invalid(recipient, "Role HOST is not allowed for invites");
    }
  }
}