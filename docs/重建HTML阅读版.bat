@echo off
chcp 65001 >nul
cd /d "%~dp0.."
echo 正在把 docs 下的 Markdown 转成 HTML 阅读版...
node tools\md2html.js
echo.
echo 完成。按任意键关闭。
pause >nul
