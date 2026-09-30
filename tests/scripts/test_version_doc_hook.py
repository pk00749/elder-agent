# AGENTS.md §A.16.1 hook 测试 —— 对应 scripts/hooks/{commit-msg,prepare-commit-msg}
# + scripts/new_version_doc.sh + scripts/install_hooks.sh。
# 覆盖路径:
#   1. commit-msg 无版本标记       -> 不创建 docs
#   2. commit-msg 仅 vX.Y          -> 创建 docs/vX.Y.md
#   3. commit-msg vX.Y.Z           -> 创建 docs/vX.Y.Z.md
#   4. commit-msg 带 -slug         -> 创建 docs/vX.Y.Z-slug.md
#   5. commit-msg 带括号 [v0.11.0] -> 同样创建
#   6. commit-msg 大写 V 拒收      -> 不创建
#   7. commit-msg slug 大写拒收    -> 不创建
#   8. commit-msg 文件已存在       -> 跳过,不覆盖
#   9. commit-msg 版本段后无空格   -> 拒收(v0.11.0_BAD 不应匹配)
#  10. prepare-commit-msg 检测 prd.md 修订记录新增行
#  11. prepare-commit-msg 不修改已有 [vX.Y.Z] 前缀的 message
#  12. new_version_doc.sh 独立调用 vX.Y / vX.Y.Z / vX.Y.Z-slug / vX.Y-slug
#  13. new_version_doc.sh 文件已存在 -> exit 2
#  14. new_version_doc.sh 非法 version -> exit 2
#  15. new_version_doc.sh 主题注入正确
#  16. install_hooks.sh --help / --uninstall
# 不依赖 git infra(用 tmp_path + git init)。

from __future__ import annotations

import shutil
import subprocess
from pathlib import Path

import pytest

REPO_ROOT = Path(__file__).resolve().parents[2]
HOOKS_DIR = REPO_ROOT / "scripts" / "hooks"
NEW_VER_SCRIPT = REPO_ROOT / "scripts" / "new_version_doc.sh"
INSTALLER = REPO_ROOT / "scripts" / "install_hooks.sh"


def _git(cwd: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[str]:
    """git wrapper that returns CompletedProcess with text output."""
    return subprocess.run(
        ("git", *args),
        cwd=cwd,
        capture_output=True,
        text=True,
        check=check,
    )


@pytest.fixture
def git_repo(tmp_path: Path) -> Path:
    """建立临时 git 仓库,内含 docs/ + 最小化 prd.md / gradle.properties。

    复用真实 REPO_ROOT 的 commit-msg / prepare-commit-msg hook(它们通过
    git rev-parse --show-toplevel 定位仓库根),所以 hook 在 tmp 仓库内运行
    时写入 tmp/docs/ 而非真 docs/。
    """
    repo = tmp_path / "repo"
    repo.mkdir()
    (repo / "docs").mkdir()
    (repo / "scripts").mkdir()
    (repo / "scripts" / "hooks").mkdir()

    # 复制真实 hook 源码到临时仓库的 scripts/hooks/ —— hook 自身通过
    # git rev-parse --show-toplevel 解析仓库根,所以会写入 tmp/repo/docs/
    shutil.copy(HOOKS_DIR / "commit-msg", repo / "scripts" / "hooks" / "commit-msg")
    shutil.copy(HOOKS_DIR / "prepare-commit-msg", repo / "scripts" / "hooks" / "prepare-commit-msg")

    # 复制 new_version_doc.sh 到临时仓库(它也通过 git rev-parse 定位根)
    shutil.copy(NEW_VER_SCRIPT, repo / "new_version_doc.sh")

    # git init + 初始 commit(让 hooks 可用;git 不在初始 commit 之前跑 hook)
    _git(repo, "init", "-q", "-b", "main")
    _git(repo, "config", "user.email", "test@elder.local")
    _git(repo, "config", "user.name", "Test")
    # 安装 hook 到 .git/hooks/
    (repo / ".git" / "hooks" / "commit-msg").write_bytes(
        (repo / "scripts" / "hooks" / "commit-msg").read_bytes()
    )
    (repo / ".git" / "hooks" / "commit-msg").chmod(0o755)
    (repo / ".git" / "hooks" / "prepare-commit-msg").write_bytes(
        (repo / "scripts" / "hooks" / "prepare-commit-msg").read_bytes()
    )
    (repo / ".git" / "hooks" / "prepare-commit-msg").chmod(0o755)

    # 最小化 prd.md 修订记录 + gradle.properties
    (repo / "prd.md").write_text(
        "<!-- placeholder prd.md for hook tests -->\n\n"
        "| version | date | author | note |\n"
        "|---------|------|--------|------|\n"
        "| v0.0.1  | 2026-01-01 | init | seed |\n"
        "\n"
        "## 12.5 文档索引表\n"
        "\n"
        "| 版本 | 文件 | 目的 |\n"
        "|------|------|------|\n"
        "| v0.0.1 | docs/v0.0.1.md | seed |\n"
    )
    (repo / "gradle.properties").write_text("version=0.0.1\n")

    _git(repo, "add", ".")
    _git(repo, "commit", "-q", "-m", "init: seed repo for hook tests")
    return repo


def _run_commit_msg(repo: Path, subject: str, body: str = "") -> subprocess.CompletedProcess[str]:
    """模拟 git commit 调用 commit-msg hook(传入临时 commit msg 文件)。"""
    msg_file = repo / "_commit-msg.txt"
    msg_file.write_text(f"{subject}\n{body}\n")
    return subprocess.run(
        ("bash", str(repo / "scripts" / "hooks" / "commit-msg"), str(msg_file)),
        cwd=repo,
        capture_output=True,
        text=True,
    )


def _docs(repo: Path) -> list[str]:
    """列出 repo/docs/ 下所有 .md 文件名。"""
    docs_dir = repo / "docs"
    return sorted(p.name for p in docs_dir.glob("*.md"))


# ---------------------------------------------------------------------------
# commit-msg hook 测试
# ---------------------------------------------------------------------------


def test_commit_msg_no_version_tag_does_nothing(git_repo: Path) -> None:
    """1. commit subject 无版本标记 -> hook 静默通过,docs/ 不变。"""
    before = _docs(git_repo)
    result = _run_commit_msg(git_repo, "fix: regular bug fix unrelated to any version")
    assert result.returncode == 0
    assert _docs(git_repo) == before
    assert result.stderr == "" or "未匹配" not in result.stderr


def test_commit_msg_v_xy_creates_doc(git_repo: Path) -> None:
    """2. commit subject 含 vX.Y (无 Z) -> 创建 docs/vX.Y.md。"""
    result = _run_commit_msg(git_repo, "agent-service: v0.11 add foo")
    assert result.returncode == 0
    assert "v0.11.md" in _docs(git_repo)
    content = (git_repo / "docs" / "v0.11.md").read_text()
    assert "# v0.11 目标定义" in content
    assert "AGENTS.md §A.16.1" in content
    assert "prd.md §12.5" in content


def test_commit_msg_v_xyz_creates_doc(git_repo: Path) -> None:
    """3. commit subject 含 vX.Y.Z -> 创建 docs/vX.Y.Z.md。"""
    result = _run_commit_msg(git_repo, "feat: v0.11.0 add OSS sync")
    assert result.returncode == 0
    assert "v0.11.0.md" in _docs(git_repo)


def test_commit_msg_v_xyz_with_slug_creates_doc(git_repo: Path) -> None:
    """4. commit subject 含 vX.Y.Z-slug -> 创建 docs/vX.Y.Z-slug.md。"""
    result = _run_commit_msg(git_repo, "feat: v0.11.0-oss-sync setup Aliyun OSS")
    assert result.returncode == 0
    assert "v0.11.0-oss-sync.md" in _docs(git_repo)
    # 不应同时创建 v0.11.0.md
    assert "v0.11.0.md" not in _docs(git_repo)


def test_commit_msg_bracketed_version_creates_doc(git_repo: Path) -> None:
    """5. commit subject 含 [v0.11.0] 括号 -> 同样创建 docs/v0.11.0.md。"""
    result = _run_commit_msg(git_repo, "[v0.11.0] feat: parenthetical brackets fix")
    assert result.returncode == 0
    assert "v0.11.0.md" in _docs(git_repo)


def test_commit_msg_uppercase_v_rejected(git_repo: Path) -> None:
    """6. commit subject 含大写 V (V0.11.0) -> hook 不创建文件。"""
    before = _docs(git_repo)
    result = _run_commit_msg(git_repo, "feat: V0.11.0 bad case")
    assert result.returncode == 0
    assert _docs(git_repo) == before


def test_commit_msg_uppercase_slug_rejected(git_repo: Path) -> None:
    """7. commit subject 含大写 slug (v0.11.0-OSS) -> hook 不创建文件。"""
    before = _docs(git_repo)
    result = _run_commit_msg(git_repo, "feat: v0.11.0-OSS bad slug case")
    assert result.returncode == 0
    assert _docs(git_repo) == before


def test_commit_msg_existing_doc_not_overwritten(git_repo: Path) -> None:
    """8. docs 文件已存在 -> hook 跳过,不覆盖(避免破坏人工内容)。"""
    (git_repo / "docs" / "v0.11.0.md").write_text("EXISTING CONTENT — DO NOT OVERWRITE\n")
    _git(git_repo, "add", "docs/v0.11.0.md")
    _git(git_repo, "commit", "-q", "-m", "add existing doc")

    before_content = (git_repo / "docs" / "v0.11.0.md").read_text()
    result = _run_commit_msg(git_repo, "feat: v0.11.0 another commit")
    assert result.returncode == 0
    after_content = (git_repo / "docs" / "v0.11.0.md").read_text()
    assert before_content == after_content
    assert "EXISTING CONTENT" in after_content


def test_commit_msg_no_space_after_version_rejected(git_repo: Path) -> None:
    """9. 版本段后必须紧跟空格/]/)/:;v0.11.0_BAD 不应匹配。"""
    before = _docs(git_repo)
    result = _run_commit_msg(git_repo, "feat: v0.11.0_BAD no separator")
    assert result.returncode == 0
    assert _docs(git_repo) == before


def test_commit_msg_staged_in_git_index(git_repo: Path) -> None:
    """10. hook 创建的 docs 文件自动 git add(commit 时一并入 commit)。"""
    _run_commit_msg(git_repo, "feat: v0.11.1 setup")
    status = _git(git_repo, "status", "--short").stdout
    assert "A  docs/v0.11.1.md" in status


# ---------------------------------------------------------------------------
# prepare-commit-msg hook 测试
# ---------------------------------------------------------------------------


def test_prepare_commit_msg_injects_prefix_from_prd_diff(git_repo: Path) -> None:
    """11. staged diff 含 prd.md 修订记录新增行 -> 注入 [vX.Y.Z] 前缀。"""
    (git_repo / "prd.md").write_text(
        (git_repo / "prd.md").read_text() + "| v0.11.0 | 2026-09-30 | Codex | test row |\n"
    )
    _git(git_repo, "add", "prd.md")

    msg_file = git_repo / "_commit-msg-prepare.txt"
    msg_file.write_text("\n")
    result = subprocess.run(
        (
            "bash",
            str(git_repo / "scripts" / "hooks" / "prepare-commit-msg"),
            str(msg_file),
            "template",
        ),
        cwd=git_repo,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0
    content = msg_file.read_text()
    assert content.startswith("[v0.11.0] ")
    assert "已注入" in result.stderr


def test_prepare_commit_msg_does_not_overwrite_existing_prefix(git_repo: Path) -> None:
    """12. message 已有 [vX.Y.Z] 前缀 -> 不重复加。"""
    msg_file = git_repo / "_commit-msg-prepare.txt"
    msg_file.write_text("[v0.10.0] feat: stuff\n")
    result = subprocess.run(
        (
            "bash",
            str(git_repo / "scripts" / "hooks" / "prepare-commit-msg"),
            str(msg_file),
            "template",
        ),
        cwd=git_repo,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0
    assert msg_file.read_text() == "[v0.10.0] feat: stuff\n"


def test_prepare_commit_msg_source_message_is_skipped(git_repo: Path) -> None:
    """13. source=message(用户已 -m) -> 不注入模板前缀。"""
    msg_file = git_repo / "_commit-msg-prepare.txt"
    msg_file.write_text("\n")
    result = subprocess.run(
        (
            "bash",
            str(git_repo / "scripts" / "hooks" / "prepare-commit-msg"),
            str(msg_file),
            "message",
        ),
        cwd=git_repo,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0
    assert msg_file.read_text() == "\n"


# ---------------------------------------------------------------------------
# new_version_doc.sh 独立脚本测试
# ---------------------------------------------------------------------------


@pytest.mark.parametrize(
    ("version_input", "expected_filename"),
    [
        ("v0.11", "v0.11.md"),
        ("v0.11.0", "v0.11.0.md"),
        ("v0.11.0-oss-sync", "v0.11.0-oss-sync.md"),
        ("v0.11-oss", "v0.11-oss.md"),
    ],
)
def test_new_version_doc_creates_correct_filename(
    git_repo: Path, version_input: str, expected_filename: str
) -> None:
    """14. 4 种合法 version 输入 -> 各自写出对应 filename。"""
    result = subprocess.run(
        ("bash", str(git_repo / "new_version_doc.sh"), version_input, "test topic"),
        cwd=git_repo,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0, result.stderr
    assert expected_filename in _docs(git_repo)


def test_new_version_doc_existing_file_rejected(git_repo: Path) -> None:
    """15. docs 文件已存在 -> exit 2,不覆盖。"""
    (git_repo / "docs" / "v0.11.0.md").write_text("EXISTING\n")
    before = (git_repo / "docs" / "v0.11.0.md").read_text()
    result = subprocess.run(
        ("bash", str(git_repo / "new_version_doc.sh"), "v0.11.0", "should fail"),
        cwd=git_repo,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 2
    assert "拒绝覆盖" in result.stderr
    assert (git_repo / "docs" / "v0.11.0.md").read_text() == before


@pytest.mark.parametrize(
    "bad_input",
    [
        "V0.11.0",  # 大写 V
        "v0.11.0_BAD",  # slug 含 _
        "v0.11.0-",  # slug 为空
        "v0.11.0-OSS",  # slug 大写
        "v0.11.0.5",  # 4 段
        "version",  # 完全不是版本
    ],
)
def test_new_version_doc_invalid_version_rejected(git_repo: Path, bad_input: str) -> None:
    """16. 6 种非法 version 输入 -> exit 2。"""
    result = subprocess.run(
        ("bash", str(git_repo / "new_version_doc.sh"), bad_input),
        cwd=git_repo,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 2
    assert "版本格式不合法" in result.stderr


def test_new_version_doc_topic_injected(git_repo: Path) -> None:
    """17. topic 参数被注入到生成的 docs/ 文件正文(本版本主题:**xxx**)。"""
    subprocess.run(
        ("bash", str(git_repo / "new_version_doc.sh"), "v0.11.0-oss-sync", "阿里云 OSS 同步"),
        cwd=git_repo,
        capture_output=True,
        text=True,
        check=True,
    )
    content = (git_repo / "docs" / "v0.11.0-oss-sync.md").read_text()
    assert "本版本主题:**阿里云 OSS 同步**" in content


# ---------------------------------------------------------------------------
# install_hooks.sh 测试(集成层)
# ---------------------------------------------------------------------------


def test_install_hooks_help_exits_zero(git_repo: Path) -> None:
    """18. --help 应干净退出 0。"""
    result = subprocess.run(
        ("bash", str(INSTALLER), "--help"),
        cwd=git_repo,
        capture_output=True,
        text=True,
    )
    assert result.returncode == 0
    assert "用法" in result.stdout


def test_install_hooks_uninstall_removes_hooks(git_repo: Path) -> None:
    """19. --uninstall 应删除 §A.16.1 标记的 hook,不动其他 hook。"""
    # 先放一个非本仓库的 hook(无 §A.16.1 标记)
    other_hook = git_repo / ".git" / "hooks" / "pre-commit"
    other_hook.write_text("#!/usr/bin/env bash\necho not ours\n")
    other_hook.chmod(0o755)

    subprocess.run(
        ("bash", str(INSTALLER), "--uninstall"),
        cwd=git_repo,
        capture_output=True,
        text=True,
        check=True,
    )
    # 本仓库 hook 被删
    assert not (git_repo / ".git" / "hooks" / "commit-msg").exists()
    assert not (git_repo / ".git" / "hooks" / "prepare-commit-msg").exists()
    # 其它 hook 保留
    assert other_hook.exists()
