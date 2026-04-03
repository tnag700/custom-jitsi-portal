from __future__ import annotations

import argparse

from _python_guardrails import fail, repo_root, run_command


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("-Image", dest="image", default="ubuntu:24.04")
    parser.add_argument("-OperatorAllowlist", dest="operator_allowlist", default="198.51.100.10/32")
    args = parser.parse_args()

    root = repo_root()
    sshd_baseline = root / "deploy/host/sshd/10-jitsi-production-hardening.conf.example"
    ufw_baseline = root / "deploy/host/ufw/ubuntu-24-production.rules.example"
    for path in [sshd_baseline, ufw_baseline]:
        if not path.exists():
            fail(f"Required host baseline artifact is missing: {path}")

    container_script = r'''set -eu
export DEBIAN_FRONTEND=noninteractive
export PATH=/usr/sbin:/usr/bin:/sbin:/bin

escape_replacement() {
  printf '%s' "$1" | sed -e 's/[&|\\]/\\&/g'
}

apt-get update > /tmp/apt-update.log
apt-get install -y --no-install-recommends openssh-server ufw ca-certificates > /tmp/apt-install.log

mkdir -p /run/sshd /etc/ssh/sshd_config.d
cp /workspace/deploy/host/sshd/10-jitsi-production-hardening.conf.example /etc/ssh/sshd_config.d/10-jitsi-production-hardening.conf
/usr/sbin/sshd -t

operator_allowlist_escaped="$(escape_replacement "__OPERATOR_ALLOWLIST__")"
sed -e "s|YOUR_OPERATOR_CIDR|$operator_allowlist_escaped|g" /workspace/deploy/host/ufw/ubuntu-24-production.rules.example \
  | awk '
      /^# Validate default policy commands/ { exit }
      /^[[:space:]]*#/ { next }
      /^[[:space:]]*$/ { next }
      { print }
    ' > /tmp/ufw-preview.sh

awk '
    /^ufw reset$/ { print; next }
    /^ufw default deny incoming$/ { print; next }
    /^ufw default allow outgoing$/ { print; next }
  ' /workspace/deploy/host/ufw/ubuntu-24-production.rules.example > /tmp/ufw-policy.sh

sh /tmp/ufw-preview.sh > /tmp/ufw-preview.log 2>&1
if grep -q '^ERROR:' /tmp/ufw-preview.log; then
  cat /tmp/ufw-preview.log
  exit 1
fi

sh /tmp/ufw-policy.sh > /tmp/ufw-policy.log 2>&1
if grep -q '^ERROR:' /tmp/ufw-policy.log; then
  cat /tmp/ufw-policy.log
  exit 1
fi

printf 'container-host-smoke: sshd snippet validated with sshd -t\n'
printf 'container-host-smoke: committed UFW preview commands and default policy commands validated with operator allowlist %s\n' '__OPERATOR_ALLOWLIST__'
printf 'container-host-smoke: apparmor, journald, timedatectl and live firewall enforcement remain host-only checks\n'
'''.replace("__OPERATOR_ALLOWLIST__", args.operator_allowlist)

    result = run_command(
        [
            "docker",
            "run",
            "--rm",
            "--pull",
            "missing",
            "-v",
            f"{root}:/workspace:ro",
            args.image,
            "bash",
            "-lc",
            container_script,
        ]
    )
    if result.returncode != 0:
        raise SystemExit((result.stdout + result.stderr).strip() or f"Container host baseline smoke failed with exit code {result.returncode}")
    print(result.stdout, end="")


if __name__ == "__main__":
    main()