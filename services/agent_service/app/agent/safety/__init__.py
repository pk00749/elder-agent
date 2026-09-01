"""安全关键词模块（AGENTS.md §11 §B1 / §B2 / §B4）。"""

from services.agent_service.app.agent.safety.keywords import (
    DIMENSION_KEYWORDS,
    EMERGENCY_KEYWORDS,
    EXPLICIT_CLOSE_KEYWORDS,
    MEDICAL_QA_KEYWORDS,
    MONEY_KEYWORDS,
    count_dimensions,
    detect_emergency,
    detect_explicit_close,
    detect_money_or_medical,
)

__all__ = [
    "DIMENSION_KEYWORDS",
    "EMERGENCY_KEYWORDS",
    "EXPLICIT_CLOSE_KEYWORDS",
    "MEDICAL_QA_KEYWORDS",
    "MONEY_KEYWORDS",
    "count_dimensions",
    "detect_emergency",
    "detect_explicit_close",
    "detect_money_or_medical",
]
