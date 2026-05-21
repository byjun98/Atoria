"""Quality-control issue / report models for /story/intro responses (154)."""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import Literal

Severity = Literal["warning", "error"]


# Centralised issue codes — used by validator and tests for stable matching.
ISSUE_CODES: set[str] = {
    "EMPTY_INTRO",
    "INTRO_TOO_SHORT",
    "INTRO_TOO_LONG",
    "EMPTY_OUTRO",
    "OUTRO_TOO_SHORT",
    "OUTRO_TOO_LONG",
    "EMPTY_TITLE",
    "PLACE_COUNT_MISMATCH",
    "PLACE_SEQUENCE_MISMATCH",
    "PLACE_NAME_MISMATCH",
    "MISSING_PLACE_STORY",
    "PLACE_STORY_TOO_SHORT",
    "PLACE_STORY_TOO_LONG",
    "MISSING_MISSION",
    "MISSION_TITLE_EMPTY",
    "MISSION_DESCRIPTION_EMPTY",
    "MISSION_DESCRIPTION_TOO_SHORT",
    "MISSION_TYPE_INVALID",
    "MISSION_NOT_OBSERVABLE",
    "MISSION_RELATED_PLACE_MISSING",
    "DUPLICATE_MISSION",
    "STORY_TOO_GENERIC",
    "FACT_LEGEND_CONFUSION",
    "SYMBOLIC_AS_FACT",
    "MISSING_RAG_USAGE",
    "MISSING_SOURCE_TRACE",
    "PLACE_USED_CHUNK_MISSING",
    "PLACE_SOURCE_URLS_MISSING",
    # 154-prompt-pass: place specificity / generic detection
    "MISSION_TITLE_TOO_GENERIC",
    "MISSION_NOT_PLACE_SPECIFIC",
    "STORY_NOT_PLACE_SPECIFIC",
    "INTRO_TOO_GENERIC",
    "OUTRO_TOO_GENERIC",
    "RAG_CONTEXT_UNDERUSED",
    # Atoria quest quality
    "MISSION_PHOTO_AFTERTHOUGHT",
    "MISSION_CLEAR_CONDITION_MISSING",
    "MISSION_VERIFICATION_HINT_MISSING",
    "MISSION_NOT_GAMEFUL",
    "FORBIDDEN_ACCESS_INSTRUCTION",
    "PLACE_NAME_NOT_EARLY",
    "STORY_EXPOSITORY_TONE",
    "INTRO_APP_GUIDE_TONE",
    "OUTRO_APP_GUIDE_TONE",
    "STORY_SCENE_WEAK",
}


@dataclass
class StoryQualityIssue:
    code: str
    severity: Severity
    message: str
    location: str | None = None
    auto_fixable: bool = False


@dataclass
class StoryQualityReport:
    passed: bool = True
    issues: list[StoryQualityIssue] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    error_count: int = 0
    warning_count: int = 0
    fixed_count: int = 0

    # ---- mutators ------------------------------------------------------

    def add_issue(
        self,
        code: str,
        severity: Severity,
        message: str,
        *,
        location: str | None = None,
        auto_fixable: bool = False,
    ) -> StoryQualityIssue:
        issue = StoryQualityIssue(
            code=code,
            severity=severity,
            message=message,
            location=location,
            auto_fixable=auto_fixable,
        )
        self.issues.append(issue)
        if severity == "error":
            self.error_count += 1
            self.passed = False
        else:
            self.warning_count += 1
        self.warnings.append(self._fmt(issue))
        return issue

    def mark_fixed(self, issue: StoryQualityIssue, fix_message: str | None = None) -> None:
        self.fixed_count += 1
        if fix_message:
            self.warnings.append(f"[fixed:{issue.code}] {fix_message}")

    # ---- queries -------------------------------------------------------

    def has_errors(self) -> bool:
        return self.error_count > 0

    def has_warnings(self) -> bool:
        return self.warning_count > 0

    def to_warning_messages(self) -> list[str]:
        return list(self.warnings)

    # ---- summary for logging (no PII / no full prompt) -----------------

    def log_summary(self, *, request_id: str | None = None) -> dict:
        return {
            "request_id": request_id,
            "passed": self.passed,
            "error_count": self.error_count,
            "warning_count": self.warning_count,
            "fixed_count": self.fixed_count,
            "issue_codes": [i.code for i in self.issues],
        }

    # ---- helpers -------------------------------------------------------

    @staticmethod
    def _fmt(issue: StoryQualityIssue) -> str:
        loc = f" @ {issue.location}" if issue.location else ""
        return f"[{issue.severity}:{issue.code}{loc}] {issue.message}"
