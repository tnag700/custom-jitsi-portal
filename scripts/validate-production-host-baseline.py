from __future__ import annotations

from _python_guardrails import (
    assert_contains,
    assert_not_contains,
    assert_regex,
    read_repo_text,
)


def main() -> None:
    deploy_host_readme = read_repo_text("deploy/host/README.md", "Host README")
    sshd_baseline = read_repo_text(
        "deploy/host/sshd/10-jitsi-production-hardening.conf.example",
        "SSH baseline",
    )
    ufw_baseline = read_repo_text(
        "deploy/host/ufw/ubuntu-24-production.rules.example",
        "UFW baseline",
    )
    operator_access = read_repo_text("deploy/host/operator-access-model.md", "Operator access model")
    journald_baseline = read_repo_text(
        "deploy/host/journald/journald.conf.d/60-jitsi-host-baseline.conf.example",
        "Journald baseline",
    )
    deployment_guide = read_repo_text("docs/deployment-production.md", "Production deployment guide")
    readme = read_repo_text("README.md", "Repository README")
    break_glass_runbook = read_repo_text("deploy/vault/break-glass-runbook.md", "Break-glass runbook")

    assert_regex(sshd_baseline, r"^PubkeyAuthentication yes\r?$", "SSH baseline must explicitly require public key authentication.")
    assert_regex(sshd_baseline, r"^PasswordAuthentication no\r?$", "SSH baseline must disable password authentication.")
    assert_regex(sshd_baseline, r"^KbdInteractiveAuthentication no\r?$", "SSH baseline must disable keyboard-interactive authentication.")
    assert_regex(sshd_baseline, r"^PermitRootLogin no\r?$", "SSH baseline must disable root login.")
    assert_regex(sshd_baseline, r"^AuthenticationMethods publickey\r?$", "SSH baseline must enforce key-only login.")
    assert_contains(sshd_baseline, "Prefer ed25519 for newly issued operator keys", "SSH baseline must keep ed25519 as a preference for newly issued keys.")
    assert_not_contains(sshd_baseline, "PubkeyAcceptedAlgorithms ssh-ed25519", "Shared SSH baseline must not hard-pin accepted public key algorithms and risk operator lockout.")
    assert_not_contains(sshd_baseline, "HostKeyAlgorithms ssh-ed25519", "Shared SSH baseline must not hard-pin host key algorithms and risk client lockout.")
    assert_contains(deploy_host_readme, "sshd -t", "Host baseline README must require sshd -t validation before reload.")

    assert_regex(ufw_baseline, r"^ufw --dry-run allow 80/tcp comment 'public http redirect'\r?$", "Firewall baseline must preview HTTP allow rule.")
    assert_regex(ufw_baseline, r"^ufw --dry-run allow 443/tcp comment 'public https'\r?$", "Firewall baseline must preview HTTPS allow rule.")
    assert_regex(ufw_baseline, r"^ufw --dry-run allow 10000/udp comment 'jitsi media'\r?$", "Firewall baseline must preview Jitsi media allow rule.")
    assert_contains(ufw_baseline, "Validate default policy commands in a disposable container or maintenance console before live apply.", "Firewall baseline must explain the safe verification path for default policy commands.")
    assert_regex(ufw_baseline, r"^ufw default deny incoming\r?$", "Firewall baseline must use default deny for inbound traffic.")
    assert_regex(ufw_baseline, r"^ufw default allow outgoing\r?$", "Firewall baseline must state the outbound policy explicitly.")
    assert_regex(ufw_baseline, r"^ufw reset\r?$", "Firewall baseline must define a reset step before applying the live ruleset.")
    assert_regex(ufw_baseline, r"^ufw allow 80/tcp comment 'public http redirect'\r?$", "Firewall baseline must allow HTTP.")
    assert_regex(ufw_baseline, r"^ufw allow 443/tcp comment 'public https'\r?$", "Firewall baseline must allow HTTPS.")
    assert_regex(ufw_baseline, r"^ufw allow 10000/udp comment 'jitsi media'\r?$", "Firewall baseline must allow Jitsi media UDP.")
    assert_regex(ufw_baseline, r"ufw allow from\s+YOUR_OPERATOR_CIDR\s+to any port 22 proto tcp", "Firewall baseline must keep SSH restricted to an operator allowlist placeholder.")
    assert_contains(ufw_baseline, "deploy/host/local/operator-allowlist.local.txt", "Firewall baseline must document a private local override path for real operator CIDRs.")
    assert_contains(deploy_host_readme, "ufw --dry-run", "Host baseline README must include a safe firewall preview step.")

    for needle, message in [
        ("named admin accounts", "Operator access model must require named admin accounts."),
        ("sudo", "Operator access model must require sudo instead of shared root workflows."),
        ("Do not share the root password", "Operator access model must ban shared root credentials."),
        ("docker group grants root-equivalent access", "Operator access model must warn that Docker access is root-equivalent."),
        ("Vault unseal or recovery material", "Operator access model must describe separate handling for Vault recovery material."),
        ("break-glass", "Operator access model must document break-glass separation from deploy access."),
        ("day-2 Vault operator", "Operator access model must distinguish the day-2 Vault operator role."),
        ("bootstrap/init actor", "Operator access model must distinguish the bootstrap/init actor role."),
        ("recovery actor", "Operator access model must distinguish the recovery actor role."),
        ("break-glass-runbook.md", "Operator access model must reference the canonical break-glass runbook."),
    ]:
        assert_contains(operator_access, needle, message)

    for needle, message in [
        ("AppArmor", "Host baseline README must document AppArmor posture."),
        ("aa-status", "Host baseline README must include AppArmor verification."),
        ("timedatectl", "Host baseline README must include time sync verification."),
        ("container restarts", "Host baseline README must describe container restart evidence retention."),
        ("SSH login events", "Host baseline README must describe SSH audit evidence."),
        ("sudo use", "Host baseline README must describe sudo audit evidence."),
    ]:
        assert_contains(deploy_host_readme, needle, message)

    assert_contains(journald_baseline, "SystemMaxUse=", "Journald baseline must cap disk usage.")
    assert_contains(journald_baseline, "MaxRetentionSec=", "Journald baseline must define retention.")
    assert_regex(journald_baseline, r"^ForwardToSyslog=no\r?$", "Journald baseline must disable syslog forwarding by default to avoid duplicate retention.")
    assert_contains(deploy_host_readme, "Syslog forwarding disabled by default", "Host baseline README must explain why syslog forwarding is disabled by default.")

    for needle, message in [
        ("automatic security updates", "Host baseline README must define package patching policy."),
        ("patch window", "Host baseline README must allow a documented patch-window alternative."),
        ("fail2ban, VPN-only admin plane, and SSH MFA are strongly recommended next steps", "Host baseline README must keep stronger controls as follow-up guidance, not minimum baseline."),
        ("break-glass-runbook.md", "Host baseline README must reference the canonical break-glass runbook."),
        ("post-use rotation", "Host baseline README must mention post-use rotation expectations for break-glass actions."),
    ]:
        assert_contains(deploy_host_readme, needle, message)

    for needle, message in [
        ("approval path", "Break-glass runbook must document an approval path."),
        ("cleanup", "Break-glass runbook must document cleanup obligations."),
        ("post-use rotation", "Break-glass runbook must document post-use rotation obligations."),
    ]:
        assert_contains(break_glass_runbook, needle, message)

    for needle, message in [
        ("Ubuntu 24 host control plane baseline", "Production deployment guide must reference the host baseline section."),
        ("npm run prod:host:baseline:validate", "Production deployment guide must document the host validator entry point."),
        ("break-glass-runbook.md", "Production deployment guide must reference the canonical break-glass runbook."),
    ]:
        assert_contains(deployment_guide, needle, message)

    assert_contains(readme, "npm run prod:host:baseline:validate", "README must expose the host validator command.")
    assert_contains(readme, "break-glass-runbook.md", "README must reference the canonical break-glass runbook.")

    print("Production host baseline validation passed.")


if __name__ == "__main__":
    main()
