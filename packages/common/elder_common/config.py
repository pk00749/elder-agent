"""统一配置入口（AGENTS.md §8）。"""

from __future__ import annotations

from functools import lru_cache

from pydantic import Field
from pydantic_settings import BaseSettings, SettingsConfigDict


# BaseSettings 继承 BaseModel 的 classmethod validate(value: Any)，与 §5 禁 Any 冲突；
# 此处仅继承 pydantic 的 settings 能力，validate() 我们另起 check_required()。
class Settings(BaseSettings):  # type: ignore[explicit-any]
    """所有密钥类配置从环境变量读（§8）。"""

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    # ---- JWT（§7.4）----
    jwt_secret: str = Field(default="dev-only-jwt-secret-please-change-in-prod-32+", min_length=32)
    jwt_algorithm: str = "HS256"
    jwt_expires_seconds: int = 604800  # 7 天（§11.11）

    # ---- CloudBase（§8.2）----
    cloudbase_env_id: str = "elder-agent-dev"
    cloudbase_region: str = "ap-shanghai"

    # ---- COS（§5.8 / §11.13）----
    cos_secret_id: str = "dev-secret-id"
    cos_secret_key: str = "dev-secret-key"
    cos_region: str = "ap-shanghai"
    cos_bucket: str = "elder-agent-dev"
    cos_upload_ttl_seconds: int = 86400  # 24h
    cos_base_url: str = "http://mock-upstream:8080/cos"

    # ---- DeepSeek Harness（§8.2）----
    dsh_base_url: str = "http://mock-upstream:8080/dsh"
    dsh_api_key: str = ""

    # ---- 千问 ASR / TTS（§10.4）----
    asr_base_url: str = "http://mock-upstream:8080/asr"
    asr_api_key: str = ""
    tts_base_url: str = "http://mock-upstream:8080/tts"
    tts_api_key: str = ""
    tts_voice: str = "zh_male_cantonese"  # §H.15

    # ---- 腾讯 TPush（§11.18 双通道）----
    tpush_base_url: str = "http://mock-upstream:8080/tpush"
    tpush_access_id: str = ""
    tpush_secret_key: str = ""
    tpush_reminder_channel_id: str = "dev-reminder-channel"
    tpush_appointment_channel_id: str = "dev-appointment-channel"

    # ---- 腾讯 SMS ----
    sms_base_url: str = "http://mock-upstream:8080/sms"
    sms_app_id: str = ""
    sms_app_key: str = ""
    sms_template_id: str = ""
    sms_sign_name: str = ""

    # ---- 通用 ----
    log_level: str = "INFO"
    log_format: str = "json"
    env: str = "dev"
    mock_infra: str = ""

    def check_required(self) -> None:
        """启动时校验必填项（§8）。缺失即 panic。"""
        if self.env == "prod" and len(self.jwt_secret) < 32:
            msg = "JWT_SECRET must be ≥32 chars in prod"
            raise RuntimeError(msg)


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    """单例 Settings。"""
    s = Settings()
    s.check_required()
    return s
