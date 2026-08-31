# 统一错误处理（AGENTS.md §4）。
from __future__ import annotations

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from elder_common.constants import ErrorCode


class AppError(Exception):
    """业务异常基类（§4）。

    路由层禁止手动 JSONResponse；必须 raise AppError。
    """

    def __init__(
        self,
        code: str,
        message: str,
        *,
        field: str | None = None,
        http_status: int = 400,
        **extra: object,
    ) -> None:
        super().__init__(message)
        self.code = code
        self.message = message
        self.field = field
        self.http_status = http_status
        self.extra = dict(extra)


def _format(
    code: str, message: str, field: str | None = None, **extra: object
) -> dict[str, object]:
    body: dict[str, object] = {"code": code, "message": message}
    if field is not None:
        body["field"] = field
    if extra:
        body.update(extra)
    return body


def register_exception_handlers(app: FastAPI) -> None:
    """注册全局异常处理器（§4 / §6.2）。"""

    @app.exception_handler(AppError)
    async def _app_error(request: Request, exc: AppError) -> JSONResponse:
        return JSONResponse(
            status_code=exc.http_status,
            content=_format(exc.code, exc.message, exc.field, **exc.extra),
        )

    @app.exception_handler(RequestValidationError)
    async def _validation(request: Request, exc: RequestValidationError) -> JSONResponse:
        # §4: field 必须是 JSON Pointer 形式（RFC 6901）
        errors = exc.errors()
        first = errors[0] if errors else {}
        loc = first.get("loc", [])
        field = ".".join(str(p) for p in loc)
        return JSONResponse(
            status_code=422,
            content=_format(
                ErrorCode.VALIDATION_ERROR,
                "request validation failed",
                f"body.{field}" if field else None,
            ),
        )

    @app.exception_handler(Exception)
    async def _unhandled(request: Request, exc: Exception) -> JSONResponse:
        # 5xx 兜底
        return JSONResponse(
            status_code=500,
            content=_format(ErrorCode.INTERNAL_ERROR, "internal server error"),
        )
