"""account_service 共享依赖 —— SMS code 内存池（dev / test）。"""

from __future__ import annotations

import secrets
import threading
import time

# §6.1 限流：60s 1 次 / 手机号
SMS_RESEND_INTERVAL_SECONDS = 60
# §7.4 验证码 5 分钟有效
SMS_CODE_TTL_SECONDS = 5 * 60
# §6.1 登录失败 5 次锁定 5 分钟
SMS_LOGIN_MAX_FAILS = 5
SMS_LOGIN_LOCK_SECONDS = 5 * 60


def new_sms_code() -> str:
    """生成 6 位数字验证码。"""
    return f"{secrets.randbelow(1_000_000):06d}"


class _SmsCodeStore:
    """进程内 SMS 验证码池。dev / test 用；prod 换 Redis。"""

    def __init__(self) -> None:
        self._codes: dict[str, tuple[str, float]] = {}  # phone -> (code, expires_at)
        self._last_sent: dict[str, float] = {}
        self._fail_counts: dict[str, tuple[int, float]] = {}  # phone -> (fails, lock_until)
        self._lock = threading.Lock()

    def request_code(self, phone: str) -> str | None:
        """发送新验证码；返回 None 表示触发 §6.1 60s 限流。"""
        now = time.monotonic()
        with self._lock:
            last = self._last_sent.get(phone, 0.0)
            if now - last < SMS_RESEND_INTERVAL_SECONDS:
                return None
            code = new_sms_code()
            self._codes[phone] = (code, time.time() + SMS_CODE_TTL_SECONDS)
            self._last_sent[phone] = now
            return code

    def verify(self, phone: str, code: str) -> bool:
        """核对验证码；成功后清空码池。"""
        with self._lock:
            locked_until = self._fail_counts.get(phone, (0, 0.0))[1]
            if time.monotonic() < locked_until:
                return False
            entry = self._codes.get(phone)
            if entry is None:
                self._record_fail(phone)
                return False
            stored_code, expires_at = entry
            if time.time() > expires_at:
                self._codes.pop(phone, None)
                self._record_fail(phone)
                return False
            if stored_code != code:
                self._record_fail(phone)
                return False
            self._codes.pop(phone, None)
            self._fail_counts.pop(phone, None)
            return True

    def _record_fail(self, phone: str) -> None:
        fails, _ = self._fail_counts.get(phone, (0, 0.0))
        fails += 1
        if fails >= SMS_LOGIN_MAX_FAILS:
            self._fail_counts[phone] = (0, time.monotonic() + SMS_LOGIN_LOCK_SECONDS)
        else:
            self._fail_counts[phone] = (fails, 0.0)

    def dev_peek(self, phone: str) -> str | None:
        """dev / test 专用：取最近一次发的 code（绕过 SMS 上游）。"""
        entry = self._codes.get(phone)
        if entry is None:
            return None
        return entry[0]


_sms_store = _SmsCodeStore()


def get_sms_store() -> _SmsCodeStore:
    return _sms_store
