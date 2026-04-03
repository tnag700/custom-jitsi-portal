from __future__ import annotations

import re
import shutil
import subprocess
from pathlib import Path
from typing import Iterable


class ValidationError(RuntimeError):
	pass


def fail(message: str) -> None:
	raise ValidationError(message)


def repo_root() -> Path:
	return Path(__file__).resolve().parent.parent


def write_step(message: str) -> None:
	print(f"==> {message}")


def ensure_path_exists(path: Path, description: str) -> None:
	if not path.exists():
		fail(f"{description} is missing: {path}")


def read_text(path: Path, description: str | None = None) -> str:
	if not path.exists():
		if description:
			fail(f"{description} not found: {path}")
		fail(f"Required file not found: {path}")
	return path.read_text(encoding="utf-8")


def read_repo_text(relative_path: str, description: str | None = None) -> str:
	return read_text(repo_root() / relative_path, description)


def assert_contains(content: str, needle: str, message: str) -> None:
	if needle not in content:
		fail(message)


def assert_not_contains(content: str, needle: str, message: str) -> None:
	if needle in content:
		fail(message)


def assert_regex(content: str, pattern: str, message: str) -> None:
	if re.search(pattern, content, flags=re.MULTILINE) is None:
		fail(message)


def assert_regex_not_match(content: str, pattern: str, message: str) -> None:
	if re.search(pattern, content, flags=re.MULTILINE) is not None:
		fail(message)


def assert_regex_count_at_least(content: str, pattern: str, minimum_count: int, message: str) -> None:
	count = len(re.findall(pattern, content, flags=re.MULTILINE))
	if count < minimum_count:
		fail(f"{message} Current count={count}, expected at least {minimum_count}.")


def get_service_block(text: str, service_name: str) -> str:
	lines = text.splitlines()
	service_pattern = re.compile(rf"^  {re.escape(service_name)}:\s*$")
	block_lines: list[str] = []
	in_service = False

	for line in lines:
		if not in_service:
			if service_pattern.match(line):
				in_service = True
				block_lines.append(line)
			continue

		if re.match(r"^  [A-Za-z0-9_-]+:\s*$", line) or re.match(r"^(volumes|networks):\s*$", line):
			break

		block_lines.append(line)

	if not in_service:
		fail(f"Required service '{service_name}' is missing.")

	return "\n".join(block_lines)


def get_service_names(text: str) -> list[str]:
	lines = text.splitlines()
	service_names: list[str] = []
	in_services = False

	for line in lines:
		if not in_services:
			if re.match(r"^services:\s*$", line):
				in_services = True
			continue

		if re.match(r"^[A-Za-z0-9_-]+:\s*$", line):
			break

		match = re.match(r"^  ([A-Za-z0-9_-]+):\s*$", line)
		if match:
			service_names.append(match.group(1))

	return service_names


def get_list_section_items(block: str, section_name: str) -> list[str]:
	lines = block.splitlines()
	items: list[str] = []
	in_section = False
	section_pattern = re.compile(rf"^    {re.escape(section_name)}:\s*$")

	for line in lines:
		if not in_section:
			if section_pattern.match(line):
				in_section = True
			continue

		if re.match(r"^    [A-Za-z0-9_-]+:\s*$", line):
			break

		list_match = re.match(r'^\s{6}-\s+"?([^\"]+)"?\s*$', line)
		if list_match:
			items.append(list_match.group(1))
			continue

		if section_name == "networks":
			network_match = re.match(r"^\s{6}([A-Za-z0-9_-]+):(?:\s*\{\})?\s*$", line)
			if network_match:
				items.append(network_match.group(1))

	return items


def get_scalar_value(block: str, key: str) -> str | None:
	match = re.search(rf"^    {re.escape(key)}:\s*(.+?)\s*$", block, flags=re.MULTILINE)
	if match is None:
		return None
	return match.group(1).strip()


def assert_list_contains(items: Iterable[str], needle: str, message: str) -> None:
	if needle not in list(items):
		fail(message)


def assert_list_startswith(items: Iterable[str], prefix: str, message: str) -> None:
	if not any(item.startswith(prefix) for item in items):
		fail(message)


def assert_list_has_regex(items: Iterable[str], pattern: str, message: str) -> None:
	compiled = re.compile(pattern)
	if not any(compiled.search(item) for item in items):
		fail(message)


def command_exists(name: str) -> bool:
	return shutil.which(name) is not None


def run_command(args: list[str], cwd: Path | None = None, input_text: str | None = None) -> subprocess.CompletedProcess[str]:
	return subprocess.run(
		args,
		cwd=str(cwd) if cwd else None,
		input=input_text,
		text=True,
		capture_output=True,
		check=False,
	)

